// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {Ownable} from "@openzeppelin/contracts/access/Ownable.sol";
import {ReentrancyGuard} from "@openzeppelin/contracts/utils/ReentrancyGuard.sol";
import {Math} from "@openzeppelin/contracts/utils/math/Math.sol";

import {CPEFToken} from "../core/CPEFToken.sol";
import {IPriceOracle} from "../interfaces/IPriceOracle.sol";
import {IInvestorRegistry} from "../interfaces/IInvestorRegistry.sol";
import {CashTokenTND} from "./CashTokenTND.sol";

/// @notice Minimal liquidity pool “execution” contract.
/// @dev In this repo we model cash settlement off-chain; on-chain we mint/burn and emit events.
contract LiquidityPool is Ownable, ReentrancyGuard {
    using Math for uint256;
    uint256 internal constant PRICE_SCALE = 1e8; // TND scale
    uint256 internal constant BPS = 10_000;
    uint256 public constant VAT_BPS = 1_900; // 19%

    IPriceOracle public oracle;
    IInvestorRegistry public investorRegistry;
    CashTokenTND public cashToken;

    address public treasury;

    // Inventory reserved for ReservationOption (per token)
    mapping(address => uint256) public reservedInventory;

    address public reservationOption;

    // Fee schedule by feeLevel (0..4): BRONZE, SILVER, GOLD, DIAMOND, PLATINUM
    mapping(uint8 => uint256) public poolFeeBps; // base fee (before VAT)
    mapping(uint8 => uint256) public spreadDiscountBps; // % discount applied to base spread
    uint256 public baseSpreadBps = 20; // 0.20% default

    // Dynamic spread parameters
    uint256 public alphaVolBps = 100; // multiplier applied to volatility bps (scaled by BPS)
    uint256 public betaReserveBps = 200; // penalty multiplier when reserve is low (scaled by BPS)
    uint256 public minReserveRatioBps = 2_000; // 20% (circuit threshold baseline)

    event Bought(
        address indexed token,
        address indexed user,
        uint256 tndIn,
        uint256 priceClient,
        uint256 mintedAmount,
        uint256 feeBase,
        uint256 vat,
        uint256 totalFee
    );
    event Sold(
        address indexed token,
        address indexed user,
        uint256 tokenAmount,
        uint256 priceClient,
        uint256 tndOut,
        uint256 feeBase,
        uint256 vat,
        uint256 totalFee
    );
    event InventoryReserved(address indexed token, uint256 amount);
    event InventoryReleased(address indexed token, uint256 amount);
    event ReservationOptionUpdated(address indexed oldOption, address indexed newOption);
    event TreasuryUpdated(address indexed oldTreasury, address indexed newTreasury);
    event InvestorRegistryUpdated(address indexed oldRegistry, address indexed newRegistry);
    event CashTokenUpdated(address indexed oldToken, address indexed newToken);
    event PoolFeeUpdated(uint8 indexed feeLevel, uint256 feeBps);
    event SpreadDiscountUpdated(uint8 indexed feeLevel, uint256 discountBps);
    event BaseSpreadUpdated(uint256 oldBps, uint256 newBps);
    event SpreadParamsUpdated(uint256 alphaVolBps, uint256 betaReserveBps, uint256 minReserveRatioBps);

    constructor(address owner_, address oracle_) Ownable(owner_) {
        oracle = IPriceOracle(oracle_);
        treasury = owner_;

        // Default pool fees (before VAT), aligned with PRICING.md
        poolFeeBps[0] = 100; // Bronze 1.00%
        poolFeeBps[1] = 95;  // Silver 0.95%
        poolFeeBps[2] = 90;  // Gold 0.90%
        poolFeeBps[3] = 85;  // Diamond 0.85%
        poolFeeBps[4] = 80;  // Platinum 0.80%

        // Example spread discounts (percentage of base spread)
        spreadDiscountBps[0] = 0;    // 0%
        spreadDiscountBps[1] = 500;  // 5%
        spreadDiscountBps[2] = 1000; // 10%
        spreadDiscountBps[3] = 1500; // 15%
        spreadDiscountBps[4] = 2000; // 20%
    }

    modifier onlyReservationOptionOrOwner() {
        require(msg.sender == reservationOption || msg.sender == owner(), "LP: not authorized");
        _;
    }

    function setOracle(address newOracle) external onlyOwner {
        oracle = IPriceOracle(newOracle);
    }

    function setInvestorRegistry(address newRegistry) external onlyOwner {
        address old = address(investorRegistry);
        investorRegistry = IInvestorRegistry(newRegistry);
        emit InvestorRegistryUpdated(old, newRegistry);
    }

    function setCashToken(address newToken) external onlyOwner {
        address old = address(cashToken);
        cashToken = CashTokenTND(newToken);
        emit CashTokenUpdated(old, newToken);
    }

    function setTreasury(address newTreasury) external onlyOwner {
        address old = treasury;
        treasury = newTreasury;
        emit TreasuryUpdated(old, newTreasury);
    }

    function setPoolFeeBps(uint8 feeLevel, uint256 feeBps) external onlyOwner {
        require(feeLevel <= 4, "LP: bad level");
        require(feeBps <= 500, "LP: fee too high");
        poolFeeBps[feeLevel] = feeBps;
        emit PoolFeeUpdated(feeLevel, feeBps);
    }

    function setSpreadDiscountBps(uint8 feeLevel, uint256 discountBps) external onlyOwner {
        require(feeLevel <= 4, "LP: bad level");
        require(discountBps <= BPS, "LP: bad discount");
        spreadDiscountBps[feeLevel] = discountBps;
        emit SpreadDiscountUpdated(feeLevel, discountBps);
    }

    function setBaseSpreadBps(uint256 newBps) external onlyOwner {
        require(newBps <= 500, "LP: spread too high");
        uint256 old = baseSpreadBps;
        baseSpreadBps = newBps;
        emit BaseSpreadUpdated(old, newBps);
    }

    function setSpreadParams(uint256 newAlphaVolBps, uint256 newBetaReserveBps, uint256 newMinReserveRatioBps) external onlyOwner {
        require(newMinReserveRatioBps <= BPS, "LP: bad min reserve");
        alphaVolBps = newAlphaVolBps;
        betaReserveBps = newBetaReserveBps;
        minReserveRatioBps = newMinReserveRatioBps;
        emit SpreadParamsUpdated(newAlphaVolBps, newBetaReserveBps, newMinReserveRatioBps);
    }

    function setReservationOption(address newOption) external onlyOwner {
        address old = reservationOption;
        reservationOption = newOption;
        emit ReservationOptionUpdated(old, newOption);
    }

    /// @notice Buy CPEF using on-chain TND. User must `approve` the pool on `cashToken` first.
    function buy(address token, uint256 tndIn) external nonReentrant returns (uint256 minted) {
        return _buyFor(token, msg.sender, tndIn);
    }

    /// @notice Buy on behalf of a user (platform-driven).
    function buyFor(address token, address user, uint256 tndIn) external onlyOwner nonReentrant returns (uint256 minted) {
        return _buyFor(token, user, tndIn);
    }

    function _buyFor(address token, address user, uint256 tndIn) internal returns (uint256 minted) {
        require(address(cashToken) != address(0), "LP: cash token not set");
        require(address(investorRegistry) != address(0), "LP: investor registry not set");
        require(tndIn > 0, "LP: tndIn=0");

        // Pull TND from user
        require(cashToken.transferFrom(user, address(this), tndIn), "LP: TND transferFrom failed");

        uint8 feeLevel = investorRegistry.getFeeLevel(user);
        uint256 feeBase = (tndIn * poolFeeBps[feeLevel]) / BPS;
        uint256 vat = (feeBase * VAT_BPS) / BPS;
        uint256 totalFee = feeBase + vat;
        require(totalFee < tndIn, "LP: fees exceed");

        // Send fees to treasury
        if (totalFee > 0) {
            require(cashToken.transfer(treasury, totalFee), "LP: fee transfer failed");
        }

        uint256 vni = oracle.getVNI(token);
        require(vni > 0, "LP: VNI not set");

        uint256 spreadBps = _effectiveSpreadBps(token, feeLevel);
        uint256 priceClient = (vni * (BPS + spreadBps)) / BPS;

        uint256 netTnd = tndIn - totalFee;
        minted = (netTnd * PRICE_SCALE) / priceClient;
        require(minted > 0, "LP: amount too small");

        // PRM uses execution price (priceClient)
        CPEFToken(token).mint(user, minted, priceClient);

        emit Bought(token, user, tndIn, priceClient, minted, feeBase, vat, totalFee);
    }

    /// @notice Sell CPEF for on-chain TND. Pool burns tokens from the user and pays out net TND.
    function sell(address token, uint256 tokenAmount) external nonReentrant returns (uint256 tndOut) {
        return _sellFor(token, msg.sender, tokenAmount);
    }

    /// @notice Sell on behalf of a user (platform-driven).
    function sellFor(address token, address user, uint256 tokenAmount) external onlyOwner nonReentrant returns (uint256 tndOut) {
        return _sellFor(token, user, tokenAmount);
    }

    function _sellFor(address token, address user, uint256 tokenAmount) internal returns (uint256 tndOut) {
        require(address(cashToken) != address(0), "LP: cash token not set");
        require(address(investorRegistry) != address(0), "LP: investor registry not set");
        require(tokenAmount > 0, "LP: tokenAmount=0");

        uint256 vni = oracle.getVNI(token);
        require(vni > 0, "LP: VNI not set");

        uint8 feeLevel = investorRegistry.getFeeLevel(user);
        uint256 spreadBps = _effectiveSpreadBps(token, feeLevel);
        uint256 priceClient = (vni * (BPS - spreadBps)) / BPS;

        // grossTnd = tokenAmount * priceClient / 1e8
        uint256 grossTnd = Math.mulDiv(tokenAmount, priceClient, PRICE_SCALE);

        uint256 feeBase = (grossTnd * poolFeeBps[feeLevel]) / BPS;
        uint256 vat = (feeBase * VAT_BPS) / BPS;
        uint256 totalFee = feeBase + vat;
        require(totalFee < grossTnd, "LP: fees exceed");

        tndOut = grossTnd - totalFee;
        require(cashToken.balanceOf(address(this)) >= grossTnd, "LP: insufficient liquidity");

        // Burn first (platform-only burn)
        CPEFToken(token).burnFromUser(user, tokenAmount);

        // Pay user and treasury
        require(cashToken.transfer(user, tndOut), "LP: payout transfer failed");
        if (totalFee > 0) {
            require(cashToken.transfer(treasury, totalFee), "LP: fee transfer failed");
        }

        emit Sold(token, user, tokenAmount, priceClient, tndOut, feeBase, vat, totalFee);
    }

    /// @notice Expose a buy quote (for UI/backend) using current on-chain parameters.
    function quoteBuy(address token, address user, uint256 tndIn)
        external
        view
        returns (uint256 priceClient, uint256 minted, uint256 feeBase, uint256 vat, uint256 totalFee)
    {
        uint8 feeLevel = investorRegistry.getFeeLevel(user);
        feeBase = (tndIn * poolFeeBps[feeLevel]) / BPS;
        vat = (feeBase * VAT_BPS) / BPS;
        totalFee = feeBase + vat;
        uint256 vni = oracle.getVNI(token);
        uint256 spreadBps = _effectiveSpreadBps(token, feeLevel);
        priceClient = (vni * (BPS + spreadBps)) / BPS;
        uint256 netTnd = tndIn - totalFee;
        minted = (netTnd * PRICE_SCALE) / priceClient;
    }

    /// @notice Expose a sell quote.
    function quoteSell(address token, address user, uint256 tokenAmount)
        external
        view
        returns (uint256 priceClient, uint256 tndOut, uint256 feeBase, uint256 vat, uint256 totalFee)
    {
        uint8 feeLevel = investorRegistry.getFeeLevel(user);
        uint256 vni = oracle.getVNI(token);
        uint256 spreadBps = _effectiveSpreadBps(token, feeLevel);
        priceClient = (vni * (BPS - spreadBps)) / BPS;
        uint256 grossTnd = Math.mulDiv(tokenAmount, priceClient, PRICE_SCALE);
        feeBase = (grossTnd * poolFeeBps[feeLevel]) / BPS;
        vat = (feeBase * VAT_BPS) / BPS;
        totalFee = feeBase + vat;
        tndOut = grossTnd - totalFee;
    }

    function _effectiveSpreadBps(address token, uint8 feeLevel) internal view returns (uint256) {
        uint256 dyn = _dynamicSpreadBps(token);
        uint256 disc = spreadDiscountBps[feeLevel];
        if (disc == 0) return dyn;
        // spread = dyn * (1 - disc)
        return (dyn * (BPS - disc)) / BPS;
    }

    function _dynamicSpreadBps(address token) internal view returns (uint256) {
        // base + alpha*vol + beta*reservePenalty
        uint256 vni = oracle.getVNI(token);
        if (vni == 0) return baseSpreadBps;

        uint256 volBps = oracle.getVolatilityBps(token); // 0..10000
        uint256 volComponent = (volBps * alphaVolBps) / BPS;

        uint256 reserveRatioBps = _reserveRatioBps(token, vni);
        uint256 reservePenalty = 0;
        if (reserveRatioBps < minReserveRatioBps) {
            uint256 gap = minReserveRatioBps - reserveRatioBps;
            reservePenalty = (gap * betaReserveBps) / BPS;
        }

        return baseSpreadBps + volComponent + reservePenalty;
    }

    function _reserveRatioBps(address token, uint256 vni) internal view returns (uint256) {
        // reserveRatio = cash / (cash + tokenSupply*VNI) in bps
        if (address(cashToken) == address(0)) return BPS;
        uint256 cash = cashToken.balanceOf(address(this));
        uint256 supply = CPEFToken(token).totalSupply();
        uint256 liability = Math.mulDiv(supply, vni, PRICE_SCALE); // TND
        uint256 denom = cash + liability;
        if (denom == 0) return BPS;
        return (cash * BPS) / denom;
    }

    /// @notice Reserve inventory for an option. MVP uses accounting only.
    /// @dev Production version should lock actual token inventory held by the pool.
    function lockInventory(address token, uint256 amount) external onlyReservationOptionOrOwner {
        reservedInventory[token] += amount;
        emit InventoryReserved(token, amount);
    }

    function unlockInventory(address token, uint256 amount) external onlyReservationOptionOrOwner {
        require(reservedInventory[token] >= amount, "LP: insufficient reserved");
        reservedInventory[token] -= amount;
        emit InventoryReleased(token, amount);
    }
}

