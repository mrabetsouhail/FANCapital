// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {Ownable} from "@openzeppelin/contracts/access/Ownable.sol";

import {IPriceOracle} from "../interfaces/IPriceOracle.sol";

/// @notice Minimal oracle storing VNI per token address (scaled 1e8).
contract PriceOracle is IPriceOracle, Ownable {
    mapping(address => uint256) private _vni;
    mapping(address => uint256) private _volBps;

    event VNIUpdated(address indexed token, uint256 oldVni, uint256 newVni, uint256 timestamp);
    event VolatilityUpdated(address indexed token, uint256 oldBps, uint256 newBps, uint256 timestamp);

    constructor(address owner_) Ownable(owner_) {}

    function getVNI(address token) external view returns (uint256) {
        return _vni[token];
    }

    function getVolatilityBps(address token) external view returns (uint256) {
        return _volBps[token];
    }

    function updateVNI(address token, uint256 newVni) external onlyOwner {
        uint256 old = _vni[token];
        _vni[token] = newVni;
        emit VNIUpdated(token, old, newVni, block.timestamp);
    }

    /// @notice Update volatility indicator in bps (0..10000).
    function updateVolatilityBps(address token, uint256 newBps) external onlyOwner {
        require(newBps <= 10_000, "ORACLE: bad bps");
        uint256 old = _volBps[token];
        _volBps[token] = newBps;
        emit VolatilityUpdated(token, old, newBps, block.timestamp);
    }
}

