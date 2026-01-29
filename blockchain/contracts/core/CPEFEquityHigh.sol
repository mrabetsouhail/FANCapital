// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {CPEFToken} from "./CPEFToken.sol";

contract CPEFEquityHigh is CPEFToken {
    constructor(address owner_) CPEFToken("CPEF Equity High", "CPEF-EQ-H", owner_) {}
}

