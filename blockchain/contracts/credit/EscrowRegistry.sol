// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";

import {CPEFToken} from "../core/CPEFToken.sol";

/// @notice Collateral escrow registry for credit products.
/// @dev MVP model locks at the USER level (cannot transfer/burn while any loan is active).
contract EscrowRegistry is AccessControl {
    bytes32 public constant GOVERNANCE_ROLE = keccak256("GOVERNANCE_ROLE");

    struct LockInfo {
        address token;
        uint256 amount; // token units (8 decimals)
        bool active;
    }

    // loanId => lock info
    mapping(uint256 => LockInfo) public locks;
    // user => number of active locks (across loans)
    mapping(address => uint256) public activeLocksCount;

    mapping(address => bool) public isAuthorizedCaller;

    event CollateralLocked(uint256 indexed loanId, address indexed user, address indexed token, uint256 amount);
    event CollateralUnlocked(uint256 indexed loanId, address indexed user, address indexed token, uint256 amount);
    event AuthorizedCallerUpdated(address indexed caller, bool authorized);

    constructor(address admin_) {
        _grantRole(DEFAULT_ADMIN_ROLE, admin_);
        _grantRole(GOVERNANCE_ROLE, admin_);
    }

    modifier onlyAuthorized() {
        require(isAuthorizedCaller[msg.sender] || hasRole(GOVERNANCE_ROLE, msg.sender), "ESCROW: not authorized");
        _;
    }

    function setAuthorizedCaller(address caller, bool authorized) external onlyRole(GOVERNANCE_ROLE) {
        isAuthorizedCaller[caller] = authorized;
        emit AuthorizedCallerUpdated(caller, authorized);
    }

    function lockCollateral(uint256 loanId, address user, address token, uint256 amount) external onlyAuthorized {
        require(!locks[loanId].active, "ESCROW: already locked");
        require(amount > 0, "ESCROW: amount=0");

        locks[loanId] = LockInfo({token: token, amount: amount, active: true});

        activeLocksCount[user] += 1;
        CPEFToken(token).addEscrowLockedAmount(user, amount);

        emit CollateralLocked(loanId, user, token, amount);
    }

    function unlockCollateral(uint256 loanId, address user) external onlyAuthorized {
        LockInfo storage info = locks[loanId];
        require(info.active, "ESCROW: not active");

        uint256 amt = info.amount;
        info.active = false;
        info.amount = 0;

        require(activeLocksCount[user] > 0, "ESCROW: bad count");
        activeLocksCount[user] -= 1;
        CPEFToken(info.token).reduceEscrowLockedAmount(user, amt);

        emit CollateralUnlocked(loanId, user, info.token, amt);
    }

    /// @notice Release collateral proportionally to repayment (prorata).
    /// Tokens_Libérés = (Montant_Remboursé / Dette_Totale) × Tokens_Séquestrés
    function unlockCollateralPartial(
        uint256 loanId,
        address user,
        uint256 amountRepaidTnd,
        uint256 totalDebtTnd
    ) external onlyAuthorized {
        LockInfo storage info = locks[loanId];
        require(info.active, "ESCROW: not active");
        require(totalDebtTnd > 0, "ESCROW: totalDebt=0");
        require(amountRepaidTnd > 0, "ESCROW: amountRepaid=0");

        uint256 tokensToRelease = (amountRepaidTnd * info.amount) / totalDebtTnd;
        if (tokensToRelease == 0) return;

        require(info.amount >= tokensToRelease, "ESCROW: overflow");
        info.amount -= tokensToRelease;

        CPEFToken(info.token).reduceEscrowLockedAmount(user, tokensToRelease);

        emit CollateralUnlocked(loanId, user, info.token, tokensToRelease);

        if (info.amount == 0) {
            info.active = false;
            require(activeLocksCount[user] > 0, "ESCROW: bad count");
            activeLocksCount[user] -= 1;
        }
    }
}

