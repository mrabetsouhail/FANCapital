// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

interface IInvestorRegistry {
    /// @notice Returns the tier group: 0=Bronze, 1=Silver/Gold, 2=Platinum/Diamond
    function getTier(address user) external view returns (uint8);
    function isSubscriptionActive(address user) external view returns (bool);
    function getFeeLevel(address user) external view returns (uint8);

    /// @notice Premium services gates
    function canUseCreditModelA(address user) external view returns (bool);
    function canUseReservation(address user) external view returns (bool);
    function canUseCreditModelB(address user) external view returns (bool);
}

