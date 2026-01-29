// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

interface IPriceOracle {
    function getVNI(address token) external view returns (uint256);
    /// @notice Volatility indicator for spread calculations (in bps, e.g. 250 = 2.50%)
    function getVolatilityBps(address token) external view returns (uint256);
}

