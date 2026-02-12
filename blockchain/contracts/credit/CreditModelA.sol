// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";
import {ReentrancyGuard} from "@openzeppelin/contracts/utils/ReentrancyGuard.sol";
import {Math} from "@openzeppelin/contracts/utils/math/Math.sol";

import {IKYCRegistry} from "../interfaces/IKYCRegistry.sol";
import {IInvestorRegistry} from "../interfaces/IInvestorRegistry.sol";
import {IPriceOracle} from "../interfaces/IPriceOracle.sol";
import {EscrowRegistry} from "./EscrowRegistry.sol";

/// @notice Credit Lombard - Model A (fixed rate) gated by Silver/Gold+ and active subscription.
/// @dev Cash settlement is off-chain; on-chain stores the loan state + collateral lock.
contract CreditModelA is AccessControl, ReentrancyGuard {
    using Math for uint256;

    bytes32 public constant GOVERNANCE_ROLE = keccak256("GOVERNANCE_ROLE");
    bytes32 public constant OPERATOR_ROLE = keccak256("OPERATOR_ROLE");

    uint256 internal constant PRICE_SCALE = 1e8; // TND
    uint256 internal constant LTV_BPS = 7_000; // 70% (MAX_LTV_EMISSION rehaussé)
    uint256 internal constant BPS = 10_000;

    enum Status {
        Requested,
        Active,
        Closed,
        Cancelled
    }

    struct Loan {
        address user;
        address token;
        uint256 collateralAmount; // token units
        uint256 vniAtStart; // TND per token
        uint256 principalTnd; // computed from LTV
        uint64 startAt;
        uint64 durationDays;
        Status status;
    }

    uint256 public nextLoanId = 1;
    mapping(uint256 => Loan) public loans;

    IKYCRegistry public kycRegistry;
    IInvestorRegistry public investorRegistry;
    IPriceOracle public oracle;
    EscrowRegistry public escrow;

    event LoanRequested(uint256 indexed loanId, address indexed user, address indexed token, uint256 collateralAmount, uint256 principalTnd);
    event LoanActivated(uint256 indexed loanId, uint64 startAt, uint64 durationDays);
    event LoanClosed(uint256 indexed loanId);
    event LoanCancelled(uint256 indexed loanId);
    event RepaymentRecorded(uint256 indexed loanId, uint256 amountRepaidTnd);

    constructor(address admin_, address oracle_, address escrow_) {
        _grantRole(DEFAULT_ADMIN_ROLE, admin_);
        _grantRole(GOVERNANCE_ROLE, admin_);
        _grantRole(OPERATOR_ROLE, admin_);
        oracle = IPriceOracle(oracle_);
        escrow = EscrowRegistry(escrow_);
    }

    function setKYCRegistry(address kyc) external onlyRole(GOVERNANCE_ROLE) {
        kycRegistry = IKYCRegistry(kyc);
    }

    function setInvestorRegistry(address reg) external onlyRole(GOVERNANCE_ROLE) {
        investorRegistry = IInvestorRegistry(reg);
    }

    function setOracle(address newOracle) external onlyRole(GOVERNANCE_ROLE) {
        oracle = IPriceOracle(newOracle);
    }

    function setEscrow(address newEscrow) external onlyRole(GOVERNANCE_ROLE) {
        escrow = EscrowRegistry(newEscrow);
    }

    /// @notice User requests a fixed-rate advance; platform later activates it after off-chain disbursement.
    function requestAdvance(address token, uint256 collateralAmount, uint64 durationDays) external nonReentrant returns (uint256 loanId) {
        require(address(kycRegistry) != address(0), "A: KYC not set");
        require(kycRegistry.isWhitelisted(msg.sender), "A: not whitelisted");
        require(kycRegistry.getUserLevel(msg.sender) >= 1, "A: requires KYC 1 (Green List)");

        require(address(investorRegistry) != address(0), "A: investor registry not set");
        require(investorRegistry.canUseCreditModelA(msg.sender), "A: requires tier+sub");

        require(collateralAmount > 0, "A: collateral=0");
        require(durationDays > 0, "A: duration=0");

        uint256 vni = oracle.getVNI(token);
        require(vni > 0, "A: VNI not set");

        // collateralValueTnd = collateralAmount * vni / 1e8
        uint256 collateralValueTnd = collateralAmount.mulDiv(vni, PRICE_SCALE);
        uint256 principalTnd = collateralValueTnd.mulDiv(LTV_BPS, BPS);
        require(principalTnd > 0, "A: principal=0");

        loanId = nextLoanId++;
        loans[loanId] = Loan({
            user: msg.sender,
            token: token,
            collateralAmount: collateralAmount,
            vniAtStart: vni,
            principalTnd: principalTnd,
            startAt: 0,
            durationDays: durationDays,
            status: Status.Requested
        });

        emit LoanRequested(loanId, msg.sender, token, collateralAmount, principalTnd);
    }

    /// @notice Platform activates and locks collateral AFTER cash is disbursed off-chain.
    function activateAdvance(uint256 loanId) external onlyRole(OPERATOR_ROLE) {
        Loan storage l = loans[loanId];
        require(l.status == Status.Requested, "A: not requested");
        l.status = Status.Active;
        l.startAt = uint64(block.timestamp);

        escrow.lockCollateral(loanId, l.user, l.token, l.collateralAmount);
        emit LoanActivated(loanId, l.startAt, l.durationDays);
    }

    /// @notice Record partial repayment and release collateral prorata.
    /// Tokens_Libérés = (amountRepaidTnd / principalTnd) * collateralAmount
    function recordRepayment(uint256 loanId, uint256 amountRepaidTnd) external onlyRole(OPERATOR_ROLE) {
        Loan storage l = loans[loanId];
        require(l.status == Status.Active, "A: not active");
        require(amountRepaidTnd > 0, "A: amount=0");
        require(amountRepaidTnd <= l.principalTnd, "A: overpaid");

        escrow.unlockCollateralPartial(loanId, l.user, amountRepaidTnd, l.principalTnd);
        l.principalTnd -= amountRepaidTnd;

        emit RepaymentRecorded(loanId, amountRepaidTnd);

        if (l.principalTnd == 0) {
            l.status = Status.Closed;
            emit LoanClosed(loanId);
        }
    }

    /// @notice Platform closes loan after full repayment off-chain (legacy / manual).
    function closeAdvance(uint256 loanId) external onlyRole(OPERATOR_ROLE) {
        Loan storage l = loans[loanId];
        require(l.status == Status.Active, "A: not active");
        l.status = Status.Closed;
        l.principalTnd = 0;
        escrow.unlockCollateral(loanId, l.user);
        emit LoanClosed(loanId);
    }

    /// @notice Platform cancels a request (no disbursement) and does not lock collateral.
    function cancelRequest(uint256 loanId) external onlyRole(OPERATOR_ROLE) {
        Loan storage l = loans[loanId];
        require(l.status == Status.Requested, "A: not requested");
        l.status = Status.Cancelled;
        emit LoanCancelled(loanId);
    }
}

