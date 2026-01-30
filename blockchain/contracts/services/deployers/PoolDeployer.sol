// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {LiquidityPool} from "../LiquidityPool.sol";

/// @notice Minimal helper contract to deploy LiquidityPool.
/// @dev Used to keep the main Factory bytecode under the size limit.
contract PoolDeployer {
    function deploy(address admin, address oracle) external returns (address pool) {
        pool = address(new LiquidityPool(admin, oracle));
    }
}

