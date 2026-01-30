// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {CPEFToken} from "./CPEFToken.sol";

contract CPEFEquityMedium is CPEFToken {
    constructor(address admin_) CPEFToken("CPEF Equity Medium", "CPEF-EQ-M", admin_) {}
}

