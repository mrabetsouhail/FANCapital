// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {PriceOracle} from "../PriceOracle.sol";

/// @notice Minimal helper contract to deploy PriceOracle.
/// @dev Used to keep the main Factory bytecode under the size limit.
contract OracleDeployer {
    function deploy(address admin) external returns (address oracle) {
        oracle = address(new PriceOracle(admin));
    }
}

