// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";

/// @notice Circuit breaker with global pause capability (Panic Button).
/// @dev Designed for permissioned setup where the pool and platform are controlled entities.
/// Implements both reserve-based pauses (per pool) and global emergency pause (all contracts).
contract CircuitBreaker is AccessControl {
    uint256 internal constant BPS = 10_000;
    bytes32 public constant GOVERNANCE_ROLE = keccak256("GOVERNANCE_ROLE");
    bytes32 public constant PANIC_KEY_ROLE = keccak256("PANIC_KEY_ROLE"); // Separate role for emergency pause

    /// @notice Redemption pause threshold in bps (default 20% = 2000).
    uint256 public thresholdBps = 2_000;

    /// @notice Global pause state (Panic Button) - pauses ALL operations across all contracts.
    bool public globalPaused;

    mapping(address => bool) public redemptionsPaused;
    mapping(address => bool) public isPoolRegistered;
    mapping(address => bool) public isContractRegistered; // For global pause tracking

    event ThresholdUpdated(uint256 oldBps, uint256 newBps);
    event PoolRegistered(address indexed pool, uint256 timestamp);
    event ContractRegistered(address indexed contractAddr, uint256 timestamp);
    event RedemptionsPaused(address indexed pool, uint256 reserveRatioBps, uint256 timestamp);
    event RedemptionsResumed(address indexed pool, uint256 timestamp);
    event GlobalPaused(address indexed triggeredBy, string reason, uint256 timestamp);
    event GlobalResumed(address indexed triggeredBy, uint256 timestamp);

    constructor(address admin_) {
        _grantRole(DEFAULT_ADMIN_ROLE, admin_);
        _grantRole(GOVERNANCE_ROLE, admin_);
        // Panic Key can be granted separately (cold storage)
        // _grantRole(PANIC_KEY_ROLE, panicKeyAddress);
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
        require(!globalPaused, "CB: global pause active");
        if (redemptionsPaused[pool]) {
            redemptionsPaused[pool] = false;
            emit RedemptionsResumed(pool, block.timestamp);
        }
    }

    /// @notice Register a contract for global pause tracking (optional, for monitoring).
    function registerContract(address contractAddr) external onlyRole(GOVERNANCE_ROLE) {
        if (!isContractRegistered[contractAddr]) {
            isContractRegistered[contractAddr] = true;
            emit ContractRegistered(contractAddr, block.timestamp);
        }
    }

    /// @notice Panic Button: Pause ALL operations across all registered contracts.
    /// @dev Can be called by GOVERNANCE_ROLE or PANIC_KEY_ROLE (cold storage).
    /// This is the emergency stop mechanism described in the Livre Blanc Technique.
    function pauseAll(string calldata reason) external {
        require(
            hasRole(GOVERNANCE_ROLE, msg.sender) || hasRole(PANIC_KEY_ROLE, msg.sender),
            "CB: not authorized"
        );
        if (!globalPaused) {
            globalPaused = true;
            emit GlobalPaused(msg.sender, reason, block.timestamp);
        }
    }

    /// @notice Resume all operations (requires GOVERNANCE_ROLE, not PANIC_KEY_ROLE for safety).
    function resumeAll() external onlyRole(GOVERNANCE_ROLE) {
        if (globalPaused) {
            globalPaused = false;
            emit GlobalResumed(msg.sender, block.timestamp);
        }
    }

    /// @notice Check if global pause is active (used by other contracts).
    function isPaused() external view returns (bool) {
        return globalPaused;
    }
}

