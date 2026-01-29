// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {Ownable} from "@openzeppelin/contracts/access/Ownable.sol";

import {IInvestorRegistry} from "../interfaces/IInvestorRegistry.sol";

/// @notice On-chain registry for investor score, tier and premium subscription flag.
/// @dev Score calculation is off-chain (oracle) and pushed by the platform periodically.
contract InvestorRegistry is IInvestorRegistry, Ownable {
    // Tier groups aligned with the doc:
    // Bronze (0-20), Silver/Gold (21-50), Platinum/Diamond (51+)
    uint8 public constant TIER_BRONZE = 0;
    uint8 public constant TIER_SILVER_GOLD = 1;
    uint8 public constant TIER_PLATINUM_DIAMOND = 2;

    struct Investor {
        uint16 score; // 0..100 (or more if needed)
        bool subscriptionActive;
        uint64 lastUpdatedAt;
        // Fee level for pricing/commissions (0..4):
        // 0=BRONZE, 1=SILVER, 2=GOLD, 3=DIAMOND, 4=PLATINUM
        uint8 feeLevel;
    }

    mapping(address => Investor) private _investors;

    event ScoreUpdated(address indexed user, uint16 score, uint64 lastUpdatedAt);
    event SubscriptionUpdated(address indexed user, bool active);
    event FeeLevelUpdated(address indexed user, uint8 feeLevel);

    constructor(address owner_) Ownable(owner_) {}

    /// @notice Upsert score (oracle push). Recommended cadence: quarterly.
    function setScore(address user, uint16 score) external onlyOwner {
        _investors[user].score = score;
        _investors[user].lastUpdatedAt = uint64(block.timestamp);
        emit ScoreUpdated(user, score, _investors[user].lastUpdatedAt);
    }

    /// @notice Toggle premium subscription status.
    /// @dev In production, this might be driven by a billing system + backend proof.
    function setSubscriptionActive(address user, bool active) external onlyOwner {
        _investors[user].subscriptionActive = active;
        emit SubscriptionUpdated(user, active);
    }

    function setFeeLevel(address user, uint8 feeLevel) external onlyOwner {
        require(feeLevel <= 4, "INV: bad fee level");
        _investors[user].feeLevel = feeLevel;
        emit FeeLevelUpdated(user, feeLevel);
    }

    function getScore(address user) external view returns (uint16) {
        return _investors[user].score;
    }

    function getFeeLevel(address user) external view returns (uint8) {
        return _investors[user].feeLevel;
    }

    function getTier(address user) public view returns (uint8) {
        uint16 s = _investors[user].score;
        if (s <= 20) return TIER_BRONZE;
        if (s <= 50) return TIER_SILVER_GOLD;
        return TIER_PLATINUM_DIAMOND;
    }

    function isSubscriptionActive(address user) public view returns (bool) {
        return _investors[user].subscriptionActive;
    }

    // Premium gates (30%)
    function canUseCreditModelA(address user) external view returns (bool) {
        // Silver/Gold+ and premium active
        return getTier(user) >= TIER_SILVER_GOLD && isSubscriptionActive(user);
    }

    function canUseReservation(address user) external view returns (bool) {
        // Platinum/Diamond+ and premium active
        return getTier(user) >= TIER_PLATINUM_DIAMOND && isSubscriptionActive(user);
    }

    function canUseCreditModelB(address user) external view returns (bool) {
        // Platinum/Diamond+ and premium active
        return getTier(user) >= TIER_PLATINUM_DIAMOND && isSubscriptionActive(user);
    }
}

