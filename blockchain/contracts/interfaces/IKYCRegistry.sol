// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

interface IKYCRegistry {
    function isWhitelisted(address user) external view returns (bool);
    function getUserLevel(address user) external view returns (uint8);
    function isResident(address user) external view returns (bool);
    function checkTransferAllowed(address from, address to) external view returns (bool);
}

