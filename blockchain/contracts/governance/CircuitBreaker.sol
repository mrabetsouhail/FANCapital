// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";

/// @notice Circuit breaker focused on reserve safety: can pause redemptions on LiquidityPool.
/// @dev Designed for permissioned setup where the pool and platform are controlled entities.
contract CircuitBreaker is AccessControl {
    uint256 internal constant BPS = 10_000;
    bytes32 public constant GOVERNANCE_ROLE = keccak256("GOVERNANCE_ROLE");

    /// @notice Redemption pause threshold in bps (default 20% = 2000).
    uint256 public thresholdBps = 2_000;

    mapping(address => bool) public redemptionsPaused;
    mapping(address => bool) public isPoolRegistered;

    event ThresholdUpdated(uint256 oldBps, uint256 newBps);
    event PoolRegistered(address indexed pool, uint256 timestamp);
    event RedemptionsPaused(address indexed pool, uint256 reserveRatioBps, uint256 timestamp);
    event RedemptionsResumed(address indexed pool, uint256 timestamp);

    constructor(address admin_) {
        _grantRole(DEFAULT_ADMIN_ROLE, admin_);
        _grantRole(GOVERNANCE_ROLE, admin_);
    }

    function registerPool(address pool) external onlyRole(GOVERNANCE_ROLE) {
        if (!isPoolRegistered[pool]) {
            isPoolRegistered[pool] = true;
            emit PoolRegistered(pool, block.timestamp);
        }
    }

    function setThresholdBps(uint256 newBps) external onlyRole(GOVERNANCE_ROLE) {
        require(newBps <= BPS, "CB: bad bps");
        uint256 old = thresholdBps;
        thresholdBps = newBps;
        emit ThresholdUpdated(old, newBps);
    }

    /// @notice Trip the breaker for a given pool.
    /// @dev Expected caller: the pool itself (or the platform owner).
    function tripRedemptions(address pool, uint256 reserveRatioBps) external {
        require(msg.sender == pool || hasRole(GOVERNANCE_ROLE, msg.sender), "CB: not authorized");
        if (!redemptionsPaused[pool]) {
            redemptionsPaused[pool] = true;
            emit RedemptionsPaused(pool, reserveRatioBps, block.timestamp);
        }
    }

    function resumeRedemptions(address pool) external onlyRole(GOVERNANCE_ROLE) {
        if (redemptionsPaused[pool]) {
            redemptionsPaused[pool] = false;
            emit RedemptionsResumed(pool, block.timestamp);
        }
    }
}

