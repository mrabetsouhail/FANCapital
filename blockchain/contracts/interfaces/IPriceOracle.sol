// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

interface IPriceOracle {
    function getVNI(address token) external view returns (uint256);
    /// @notice Returns VNI and its last update timestamp (seconds).
    function getVNIData(address token) external view returns (uint256 vni, uint64 updatedAt);
    /// @notice Volatility indicator for spread calculations (in bps, e.g. 250 = 2.50%)
    function getVolatilityBps(address token) external view returns (uint256);
}

