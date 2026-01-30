// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";

import {IKYCRegistry} from "../interfaces/IKYCRegistry.sol";

/// @notice Minimal KYC registry with Green/White list levels.
contract KYCRegistry is IKYCRegistry, AccessControl {
    bytes32 public constant GOVERNANCE_ROLE = keccak256("GOVERNANCE_ROLE");
    bytes32 public constant KYC_VALIDATOR_ROLE = keccak256("KYC_VALIDATOR_ROLE");

    struct UserKyc {
        bool whitelisted;
        uint8 level; // 1 = Green, 2 = White
        bool resident;
    }

    mapping(address => UserKyc) private _users;

    event WhitelistUpdated(address indexed user, bool whitelisted, uint8 level);
    event ResidencyUpdated(address indexed user, bool resident);
    event ValidatorUpdated(address indexed validator, bool enabled);

    constructor(address admin_) {
        _grantRole(DEFAULT_ADMIN_ROLE, admin_);
        _grantRole(GOVERNANCE_ROLE, admin_);
        _grantRole(KYC_VALIDATOR_ROLE, admin_);
    }

    function setValidator(address validator, bool enabled) external onlyRole(DEFAULT_ADMIN_ROLE) {
        if (enabled) {
            _grantRole(KYC_VALIDATOR_ROLE, validator);
        } else {
            _revokeRole(KYC_VALIDATOR_ROLE, validator);
        }
        emit ValidatorUpdated(validator, enabled);
    }

    function addToWhitelist(address user, uint8 level, bool resident) external onlyRole(KYC_VALIDATOR_ROLE) {
        require(level == 1 || level == 2, "KYC: invalid level");
        _users[user] = UserKyc({whitelisted: true, level: level, resident: resident});
        emit WhitelistUpdated(user, true, level);
        emit ResidencyUpdated(user, resident);
    }

    function removeFromWhitelist(address user) external onlyRole(KYC_VALIDATOR_ROLE) {
        _users[user].whitelisted = false;
        _users[user].level = 0;
        emit WhitelistUpdated(user, false, 0);
    }

    function setUserResidency(address user, bool resident) external onlyRole(KYC_VALIDATOR_ROLE) {
        _users[user].resident = resident;
        emit ResidencyUpdated(user, resident);
    }

    function isWhitelisted(address user) external view returns (bool) {
        return _users[user].whitelisted;
    }

    function getUserLevel(address user) external view returns (uint8) {
        return _users[user].level;
    }

    function isResident(address user) external view returns (bool) {
        return _users[user].resident;
    }

    /// @notice Transfer allowed iff both parties are whitelisted.
    /// @dev Additional rules (caps, etc.) can be added here later.
    function checkTransferAllowed(address from, address to) external view returns (bool) {
        return _users[from].whitelisted && _users[to].whitelisted;
    }
}

