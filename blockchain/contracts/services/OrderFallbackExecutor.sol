// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";

import {LiquidityPool} from "./LiquidityPool.sol";

/// @notice Hybrid Order Book: fallback des ordres P2P non matchés vers la piscine de liquidité.
/// @dev L'opérateur (backend) appelle executeFallbackToPool pour transférer le reliquat d'ordre
///      vers la piscine. Le contract doit avoir OPERATOR_ROLE sur le LiquidityPool.
contract OrderFallbackExecutor is AccessControl {
    bytes32 public constant OPERATOR_ROLE = keccak256("OPERATOR_ROLE");

    LiquidityPool public pool;

    event FallbackToPoolExecuted(
        address indexed token,
        address indexed user,
        uint256 amount,
        bool isBuy,
        string orderId
    );

    constructor(address admin_, address pool_) {
        _grantRole(DEFAULT_ADMIN_ROLE, admin_);
        _grantRole(OPERATOR_ROLE, admin_);
        pool = LiquidityPool(pool_);
    }

    function setPool(address newPool) external onlyRole(DEFAULT_ADMIN_ROLE) {
        pool = LiquidityPool(newPool);
    }

    /// @notice Exécute le reliquat d'un ordre P2P vers la piscine de liquidité.
    /// @param token Adresse du token CPEF (Atlas ou Didon)
    /// @param user Adresse de l'investisseur
    /// @param amount Pour isBuy: tndIn (1e8). Pour isSell: tokenAmount (1e8)
    /// @param isBuy true = achat via pool, false = vente via pool
    /// @param orderId Identifiant de l'ordre (pour audit)
    function executeFallbackToPool(
        address token,
        address user,
        uint256 amount,
        bool isBuy,
        string calldata orderId
    ) external onlyRole(OPERATOR_ROLE) {
        require(amount > 0, "OFB: amount=0");
        require(address(pool) != address(0), "OFB: pool not set");

        if (isBuy) {
            pool.buyFor(token, user, amount);
        } else {
            pool.sellFor(token, user, amount);
        }

        emit FallbackToPoolExecuted(token, user, amount, isBuy, orderId);
    }
}
