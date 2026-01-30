// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";
import {Math} from "@openzeppelin/contracts/utils/math/Math.sol";

import {IPriceOracle} from "../interfaces/IPriceOracle.sol";

/// @notice Minimal oracle storing VNI per token address (scaled 1e8).
contract PriceOracle is IPriceOracle, AccessControl {
    using Math for uint256;

    bytes32 public constant ORACLE_ROLE = keccak256("ORACLE_ROLE");
    bytes32 public constant GOVERNANCE_ROLE = keccak256("GOVERNANCE_ROLE");

    uint256 internal constant BPS = 10_000;
    uint256 public constant MAX_DEVIATION_BPS = 1_000; // 10%

    mapping(address => uint256) private _vni;
    mapping(address => uint256) private _volBps;
    mapping(address => uint64) private _vniUpdatedAt;

    event VNIUpdated(address indexed token, uint256 oldVni, uint256 newVni, uint256 timestamp);
    event VNIForceUpdated(address indexed token, uint256 oldVni, uint256 newVni, uint256 timestamp);
    event VolatilityUpdated(address indexed token, uint256 oldBps, uint256 newBps, uint256 timestamp);

    constructor(address admin_) {
        _grantRole(DEFAULT_ADMIN_ROLE, admin_);
        _grantRole(ORACLE_ROLE, admin_);
        _grantRole(GOVERNANCE_ROLE, admin_);
    }

    function getVNI(address token) external view returns (uint256) {
        return _vni[token];
    }

    function getVNIData(address token) external view returns (uint256 vni, uint64 updatedAt) {
        return (_vni[token], _vniUpdatedAt[token]);
    }

    function getVolatilityBps(address token) external view returns (uint256) {
        return _volBps[token];
    }

    /// @notice Update VNI with anti-manipulation guard (max ±10% vs previous).
    function updateVNI(address token, uint256 newVni) external onlyRole(ORACLE_ROLE) {
        uint256 old = _vni[token];
        require(newVni > 0, "ORACLE: VNI=0");

        // If no previous value, allow initialization without deviation check.
        if (old != 0) {
            uint256 diff = old > newVni ? old - newVni : newVni - old;
            // deviationBps = diff / old
            uint256 deviationBps = diff.mulDiv(BPS, old);
            require(deviationBps <= MAX_DEVIATION_BPS, "ORACLE: deviation > 10%");
        }

        _vni[token] = newVni;
        _vniUpdatedAt[token] = uint64(block.timestamp);
        emit VNIUpdated(token, old, newVni, block.timestamp);
    }

    /// @notice Force update VNI even if deviation > 10% (governance / multi-sig).
    function forceUpdateVNI(address token, uint256 newVni) external onlyRole(GOVERNANCE_ROLE) {
        uint256 old = _vni[token];
        require(newVni > 0, "ORACLE: VNI=0");
        _vni[token] = newVni;
        _vniUpdatedAt[token] = uint64(block.timestamp);
        emit VNIForceUpdated(token, old, newVni, block.timestamp);
    }

    /// @notice Update volatility indicator in bps (0..10000).
    function updateVolatilityBps(address token, uint256 newBps) external onlyRole(ORACLE_ROLE) {
        require(newBps <= 10_000, "ORACLE: bad bps");
        uint256 old = _volBps[token];
        _volBps[token] = newBps;
        emit VolatilityUpdated(token, old, newBps, block.timestamp);
    }
}

