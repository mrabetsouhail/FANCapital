// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

/// @notice Minimal ERC-1404 interface (Security Token with transfer restrictions)
interface IERC1404 {
    function detectTransferRestriction(address from, address to, uint256 amount) external view returns (uint8);
    function messageForTransferRestriction(uint8 restrictionCode) external pure returns (string memory);
}

