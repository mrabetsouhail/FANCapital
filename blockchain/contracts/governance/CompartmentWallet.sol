// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";
import {CashTokenTND} from "../services/CashTokenTND.sol";

/// @notice Portefeuille d'un compartiment de la Matrice (Piscine B, C ou D).
/// @dev Reçoit les TND et permet à la gouvernance d'effectuer les transferts sortants.
contract CompartmentWallet is AccessControl {
    bytes32 public constant GOVERNANCE_ROLE = keccak256("GOVERNANCE_ROLE");

    CashTokenTND public cashToken;

    event Received(address indexed from, uint256 amount);
    event Transferred(address indexed to, uint256 amount);

    constructor(address admin_, address cashToken_) {
        _grantRole(DEFAULT_ADMIN_ROLE, admin_);
        _grantRole(GOVERNANCE_ROLE, admin_);
        cashToken = CashTokenTND(cashToken_);
    }

    /// @notice Transfère des TND vers une adresse (ex: B→A après validation).
    function transferTo(address to, uint256 amount) external onlyRole(GOVERNANCE_ROLE) {
        require(to != address(0), "CW: to=0");
        require(amount > 0, "CW: amount=0");
        require(cashToken.transfer(to, amount), "CW: transfer failed");
        emit Transferred(to, amount);
    }

    /// @notice Solde TND de ce compartiment.
    function balance() external view returns (uint256) {
        return cashToken.balanceOf(address(this));
    }
}
