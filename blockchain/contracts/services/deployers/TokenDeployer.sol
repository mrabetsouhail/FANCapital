// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {CPEFToken} from "../../core/CPEFToken.sol";

/// @notice Minimal helper contract to deploy CPEFToken.
/// @dev Used to keep the main Factory bytecode under the size limit.
contract TokenDeployer {
    function deploy(string calldata name, string calldata symbol, address admin) external returns (address token) {
        token = address(new CPEFToken(name, symbol, admin));
    }
}

