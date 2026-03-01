// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";

/// @notice Registre des 4 compartiments de la Matrice (Architecture des Compartiments).
/// @dev Séparation stricte des fonds : A=Réserve Liquidité, B=Sas Partenaires, C=Revenus, D=Fonds Garantie.
contract CompartmentsRegistry is AccessControl {
    bytes32 public constant GOVERNANCE_ROLE = keccak256("GOVERNANCE_ROLE");

    address public piscineA; // Réserve de Liquidité (pools)
    address public piscineB; // Sas Partenaires (D17, Flouci, virements)
    address public piscineC; // Compte de Revenus (intérêts AST, frais, spread)
    address public piscineD; // Fonds de Garantie (bad debt)

    event PiscineAUpdated(address indexed oldAddr, address indexed newAddr);
    event PiscineBUpdated(address indexed oldAddr, address indexed newAddr);
    event PiscineCUpdated(address indexed oldAddr, address indexed newAddr);
    event PiscineDUpdated(address indexed oldAddr, address indexed newAddr);

    constructor(address admin_) {
        _grantRole(DEFAULT_ADMIN_ROLE, admin_);
        _grantRole(GOVERNANCE_ROLE, admin_);
    }

    function setPiscineA(address newAddr) external onlyRole(GOVERNANCE_ROLE) {
        address old = piscineA;
        piscineA = newAddr;
        emit PiscineAUpdated(old, newAddr);
    }

    function setPiscineB(address newAddr) external onlyRole(GOVERNANCE_ROLE) {
        address old = piscineB;
        piscineB = newAddr;
        emit PiscineBUpdated(old, newAddr);
    }

    function setPiscineC(address newAddr) external onlyRole(GOVERNANCE_ROLE) {
        address old = piscineC;
        piscineC = newAddr;
        emit PiscineCUpdated(old, newAddr);
    }

    function setPiscineD(address newAddr) external onlyRole(GOVERNANCE_ROLE) {
        address old = piscineD;
        piscineD = newAddr;
        emit PiscineDUpdated(old, newAddr);
    }
}
