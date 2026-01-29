// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {Ownable} from "@openzeppelin/contracts/access/Ownable.sol";
import {ReentrancyGuard} from "@openzeppelin/contracts/utils/ReentrancyGuard.sol";

import {CashTokenTND} from "./CashTokenTND.sol";
import {IKYCRegistry} from "../interfaces/IKYCRegistry.sol";
import {IInvestorRegistry} from "../interfaces/IInvestorRegistry.sol";

/// @notice Atomic P2P settlement: buyer pays TND, seller delivers CPEF (via allowance).
/// @dev Fees (P2P) + VAT are charged on notional and sent to treasury.
contract P2PExchange is Ownable, ReentrancyGuard {
    uint256 internal constant BPS = 10_000;
    uint256 internal constant PRICE_SCALE = 1e8;
    uint256 public constant VAT_BPS = 1_900;

    CashTokenTND public cashToken;
    IKYCRegistry public kycRegistry;
    IInvestorRegistry public investorRegistry;
    address public treasury;

    // P2P fee schedule (before VAT), by feeLevel (0..4)
    mapping(uint8 => uint256) public p2pFeeBps;

    event Settled(
        address indexed token,
        address indexed seller,
        address indexed buyer,
        uint256 tokenAmount,
        uint256 pricePerToken,
        uint256 notionalTnd,
        uint256 feeBase,
        uint256 vat,
        uint256 totalFee
    );

    constructor(address owner_, address cashToken_, address investorRegistry_) Ownable(owner_) {
        cashToken = CashTokenTND(cashToken_);
        investorRegistry = IInvestorRegistry(investorRegistry_);
        treasury = owner_;

        // Default P2P fees (before VAT), aligned with PRICING.md
        p2pFeeBps[0] = 80; // Bronze 0.80%
        p2pFeeBps[1] = 75; // Silver 0.75%
        p2pFeeBps[2] = 70; // Gold 0.70%
        p2pFeeBps[3] = 60; // Diamond 0.60%
        p2pFeeBps[4] = 50; // Platinum 0.50%
    }

    function setTreasury(address newTreasury) external onlyOwner {
        treasury = newTreasury;
    }

    function setCashToken(address newToken) external onlyOwner {
        cashToken = CashTokenTND(newToken);
    }

    function setKYCRegistry(address newKyc) external onlyOwner {
        kycRegistry = IKYCRegistry(newKyc);
    }

    function setInvestorRegistry(address newRegistry) external onlyOwner {
        investorRegistry = IInvestorRegistry(newRegistry);
    }

    function setP2PFeeBps(uint8 feeLevel, uint256 feeBps) external onlyOwner {
        require(feeLevel <= 4, "P2P: bad level");
        require(feeBps <= 500, "P2P: fee too high");
        p2pFeeBps[feeLevel] = feeBps;
    }

    /// @notice Settle a P2P trade at an agreed price (provided by backend/matching engine).
    /// @dev Buyer pays (notional + fees). Seller receives notional. Treasury receives fees.
    /// Seller must approve this contract on the CPEF token for `tokenAmount`.
    /// Buyer must approve this contract on `cashToken` for (notional + fees).
    function settle(
        address token,
        address seller,
        address buyer,
        uint256 tokenAmount,
        uint256 pricePerToken
    ) external nonReentrant {
        // Commercial gating (70/30 model):
        // - Bronze users should not access P2P at all (upsell trigger)
        // - Minimum required: KYC Level 2 (legal) + Tier >= Silver/Gold (commercial)
        require(address(kycRegistry) != address(0), "P2P: KYC not set");
        require(kycRegistry.isWhitelisted(seller) && kycRegistry.isWhitelisted(buyer), "P2P: not whitelisted");
        require(kycRegistry.getUserLevel(seller) >= 2 && kycRegistry.getUserLevel(buyer) >= 2, "P2P: requires KYC level 2");
        require(investorRegistry.getTier(seller) >= 1 && investorRegistry.getTier(buyer) >= 1, "P2P: requires Silver tier");

        require(tokenAmount > 0, "P2P: amount=0");
        require(pricePerToken > 0, "P2P: price=0");

        // notionalTnd = tokenAmount * price / 1e8 (TND scaled 1e8)
        uint256 notionalTnd = (tokenAmount * pricePerToken) / PRICE_SCALE;
        require(notionalTnd > 0, "P2P: notional=0");

        uint8 feeLevel = investorRegistry.getFeeLevel(buyer);
        uint256 feeBase = (notionalTnd * p2pFeeBps[feeLevel]) / BPS;
        uint256 vat = (feeBase * VAT_BPS) / BPS;
        uint256 totalFee = feeBase + vat;

        uint256 totalFromBuyer = notionalTnd + totalFee;

        // Pull cash from buyer
        require(cashToken.transferFrom(buyer, address(this), totalFromBuyer), "P2P: TND transferFrom failed");
        // Pay seller
        require(cashToken.transfer(seller, notionalTnd), "P2P: pay seller failed");
        // Pay treasury fees
        if (totalFee > 0) {
            require(cashToken.transfer(treasury, totalFee), "P2P: pay fee failed");
        }

        // Transfer tokens from seller to buyer (requires allowance)
        // NOTE: Token-level KYC restrictions already apply (transferFrom checks restrictions).
        // We intentionally use transferFrom to make the exchange the operator.
        (bool ok, bytes memory data) = token.call(
            abi.encodeWithSignature("transferFrom(address,address,uint256)", seller, buyer, tokenAmount)
        );
        require(ok && (data.length == 0 || abi.decode(data, (bool))), "P2P: token transfer failed");

        emit Settled(token, seller, buyer, tokenAmount, pricePerToken, notionalTnd, feeBase, vat, totalFee);
    }
}

