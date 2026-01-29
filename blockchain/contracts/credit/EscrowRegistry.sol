// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {Ownable} from "@openzeppelin/contracts/access/Ownable.sol";

import {CPEFToken} from "../core/CPEFToken.sol";

/// @notice Collateral escrow registry for credit products.
/// @dev MVP model locks at the USER level (cannot transfer/burn while any loan is active).
contract EscrowRegistry is Ownable {
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

    constructor(address owner_) Ownable(owner_) {}

    modifier onlyAuthorized() {
        require(isAuthorizedCaller[msg.sender] || msg.sender == owner(), "ESCROW: not authorized");
        _;
    }

    function setAuthorizedCaller(address caller, bool authorized) external onlyOwner {
        isAuthorizedCaller[caller] = authorized;
        emit AuthorizedCallerUpdated(caller, authorized);
    }

    function lockCollateral(uint256 loanId, address user, address token, uint256 amount) external onlyAuthorized {
        require(!locks[loanId].active, "ESCROW: already locked");
        require(amount > 0, "ESCROW: amount=0");

        locks[loanId] = LockInfo({token: token, amount: amount, active: true});

        activeLocksCount[user] += 1;
        if (activeLocksCount[user] == 1) {
            CPEFToken(token).setEscrowLocked(user, true);
        }

        emit CollateralLocked(loanId, user, token, amount);
    }

    function unlockCollateral(uint256 loanId, address user) external onlyAuthorized {
        LockInfo storage info = locks[loanId];
        require(info.active, "ESCROW: not active");

        info.active = false;

        require(activeLocksCount[user] > 0, "ESCROW: bad count");
        activeLocksCount[user] -= 1;
        if (activeLocksCount[user] == 0) {
            CPEFToken(info.token).setEscrowLocked(user, false);
        }

        emit CollateralUnlocked(loanId, user, info.token, info.amount);
    }
}

