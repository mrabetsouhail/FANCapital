// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";

import {CashTokenTND} from "./CashTokenTND.sol";

/// @notice Vault to collect taxes (RAS) in on-chain TND and allow withdrawal to the fisc.
contract TaxVault is AccessControl {
    bytes32 public constant GOVERNANCE_ROLE = keccak256("GOVERNANCE_ROLE");

    CashTokenTND public cashToken;
    mapping(address => bool) public isAuthorizedCaller;
    address public fiscAddress;

    event AuthorizedCallerUpdated(address indexed caller, bool authorized);
    event RASRecorded(address indexed user, address indexed token, uint256 gainTnd, uint256 taxTnd, bool resident, uint256 timestamp);
    event WithdrawnToFisc(address indexed fiscAddress, uint256 amount, uint256 timestamp);
    event FiscAddressUpdated(address indexed oldAddress, address indexed newAddress);

    constructor(address admin_, address cashToken_) {
        _grantRole(DEFAULT_ADMIN_ROLE, admin_);
        _grantRole(GOVERNANCE_ROLE, admin_);
        cashToken = CashTokenTND(cashToken_);
        fiscAddress = admin_; // default; MUST be updated in production
    }

    modifier onlyAuthorized() {
        require(isAuthorizedCaller[msg.sender] || hasRole(GOVERNANCE_ROLE, msg.sender), "TAX: not authorized");
        _;
    }

    function setAuthorizedCaller(address caller, bool authorized) external onlyRole(GOVERNANCE_ROLE) {
        isAuthorizedCaller[caller] = authorized;
        emit AuthorizedCallerUpdated(caller, authorized);
    }

    /// @dev Pool transfers TND to this contract, then calls record for audit traceability.
    function recordRAS(address user, address token, uint256 gainTnd, uint256 taxTnd, bool resident) external onlyAuthorized {
        emit RASRecorded(user, token, gainTnd, taxTnd, resident, block.timestamp);
    }

    /// @notice Set the official fiscal treasury address (recette des finances).
    /// @dev Only the council should change this.
    function setFiscAddress(address newFiscAddress) external onlyRole(DEFAULT_ADMIN_ROLE) {
        require(newFiscAddress != address(0), "TAX: fisc=0");
        address old = fiscAddress;
        fiscAddress = newFiscAddress;
        emit FiscAddressUpdated(old, newFiscAddress);
    }

    /// @notice Withdraw collected taxes to the official fisc address.
    /// @dev Cannot redirect to arbitrary address.
    function withdrawToFisc(uint256 amount) external onlyRole(GOVERNANCE_ROLE) {
        require(fiscAddress != address(0), "TAX: fisc not set");
        require(cashToken.transfer(fiscAddress, amount), "TAX: transfer failed");
        emit WithdrawnToFisc(fiscAddress, amount, block.timestamp);
    }
}

