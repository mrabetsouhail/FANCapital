// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";
import {ReentrancyGuard} from "@openzeppelin/contracts/utils/ReentrancyGuard.sol";
import {Math} from "@openzeppelin/contracts/utils/math/Math.sol";

import {IKYCRegistry} from "../interfaces/IKYCRegistry.sol";
import {IInvestorRegistry} from "../interfaces/IInvestorRegistry.sol";
import {IPriceOracle} from "../interfaces/IPriceOracle.sol";
import {EscrowRegistry} from "./EscrowRegistry.sol";

/// @notice Credit Model B (PGP - profit sharing), gated by Platinum/Diamond+ with active subscription.
/// @dev MVP: stores start/end VNI and computes a performance reference; cash distribution is off-chain.
contract CreditModelBPGP is AccessControl, ReentrancyGuard {
    using Math for uint256;

    bytes32 public constant GOVERNANCE_ROLE = keccak256("GOVERNANCE_ROLE");
    bytes32 public constant OPERATOR_ROLE = keccak256("OPERATOR_ROLE");

    uint256 internal constant PRICE_SCALE = 1e8; // TND
    uint256 internal constant LTV_BPS = 5_000; // 50%
    uint256 internal constant BPS = 10_000;

    // Default parameters (can be tuned by governance later)
    uint256 public hurdleRateBps = 250; // 2.5%
    uint256 public fanShareBps = 1_000; // 10% FAN / 90% client (for tier Platinum/Diamond group)

    enum Status {
        Requested,
        Active,
        Closed,
        Cancelled
    }

    struct Loan {
        address user;
        address token;
        uint256 collateralAmount;
        uint256 vniAtStart;
        uint256 vniAtClose;
        uint256 principalTnd;
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
    event LoanClosed(uint256 indexed loanId, int256 performanceTnd, uint256 fanShareTnd, uint256 clientShareTnd);
    event LoanCancelled(uint256 indexed loanId);
    event ParamsUpdated(uint256 hurdleRateBps, uint256 fanShareBps);

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

    function setParams(uint256 newHurdleRateBps, uint256 newFanShareBps) external onlyRole(GOVERNANCE_ROLE) {
        require(newHurdleRateBps <= 2_000, "B: hurdle too high"); // sanity
        require(newFanShareBps <= BPS, "B: bad share");
        hurdleRateBps = newHurdleRateBps;
        fanShareBps = newFanShareBps;
        emit ParamsUpdated(newHurdleRateBps, newFanShareBps);
    }

    function requestAdvance(address token, uint256 collateralAmount, uint64 durationDays) external nonReentrant returns (uint256 loanId) {
        require(address(kycRegistry) != address(0), "B: KYC not set");
        require(kycRegistry.isWhitelisted(msg.sender), "B: not whitelisted");
        require(kycRegistry.getUserLevel(msg.sender) >= 2, "B: requires KYC level 2");

        require(address(investorRegistry) != address(0), "B: investor registry not set");
        require(investorRegistry.canUseCreditModelB(msg.sender), "B: requires premium tier+sub");

        require(collateralAmount > 0, "B: collateral=0");
        require(durationDays > 0, "B: duration=0");

        uint256 vni = oracle.getVNI(token);
        require(vni > 0, "B: VNI not set");

        uint256 collateralValueTnd = collateralAmount.mulDiv(vni, PRICE_SCALE);
        uint256 principalTnd = collateralValueTnd.mulDiv(LTV_BPS, BPS);
        require(principalTnd > 0, "B: principal=0");

        loanId = nextLoanId++;
        loans[loanId] = Loan({
            user: msg.sender,
            token: token,
            collateralAmount: collateralAmount,
            vniAtStart: vni,
            vniAtClose: 0,
            principalTnd: principalTnd,
            startAt: 0,
            durationDays: durationDays,
            status: Status.Requested
        });

        emit LoanRequested(loanId, msg.sender, token, collateralAmount, principalTnd);
    }

    function activateAdvance(uint256 loanId) external onlyRole(OPERATOR_ROLE) {
        Loan storage l = loans[loanId];
        require(l.status == Status.Requested, "B: not requested");
        l.status = Status.Active;
        l.startAt = uint64(block.timestamp);
        escrow.lockCollateral(loanId, l.user, l.token, l.collateralAmount);
        emit LoanActivated(loanId, l.startAt, l.durationDays);
    }

    /// @notice Close and compute a performance reference.
    /// @dev Performance is based on VNI variation of the collateral lot: (VNI_close - VNI_start) * amount.
    function closeAdvance(uint256 loanId) external onlyRole(OPERATOR_ROLE) {
        Loan storage l = loans[loanId];
        require(l.status == Status.Active, "B: not active");

        uint256 vniClose = oracle.getVNI(l.token);
        require(vniClose > 0, "B: VNI not set");
        l.vniAtClose = vniClose;
        l.status = Status.Closed;

        // perfTnd = (vniClose - vniStart) * amount / 1e8
        int256 diff = int256(vniClose) - int256(l.vniAtStart);
        int256 perfTnd = (diff * int256(l.collateralAmount)) / int256(PRICE_SCALE);

        uint256 fanShareTnd = 0;
        uint256 clientShareTnd = 0;

        if (perfTnd > 0) {
            // Apply hurdle on principal: hurdle = principal * hurdleRate
            uint256 hurdleTnd = l.principalTnd.mulDiv(hurdleRateBps, BPS);
            uint256 perfAbs = uint256(perfTnd);
            if (perfAbs > hurdleTnd) {
                uint256 excess = perfAbs - hurdleTnd;
                fanShareTnd = excess.mulDiv(fanShareBps, BPS);
                clientShareTnd = excess - fanShareTnd;
            } else {
                clientShareTnd = perfAbs; // below hurdle: all to client in this simplified model
            }
        }

        escrow.unlockCollateral(loanId, l.user);
        emit LoanClosed(loanId, perfTnd, fanShareTnd, clientShareTnd);
    }

    function cancelRequest(uint256 loanId) external onlyRole(OPERATOR_ROLE) {
        Loan storage l = loans[loanId];
        require(l.status == Status.Requested, "B: not requested");
        l.status = Status.Cancelled;
        emit LoanCancelled(loanId);
    }
}

