// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";
import {ReentrancyGuard} from "@openzeppelin/contracts/utils/ReentrancyGuard.sol";
import {Math} from "@openzeppelin/contracts/utils/math/Math.sol";

import {CPEFToken} from "../core/CPEFToken.sol";
import {IPriceOracle} from "../interfaces/IPriceOracle.sol";
import {IKYCRegistry} from "../interfaces/IKYCRegistry.sol";
import {IInvestorRegistry} from "../interfaces/IInvestorRegistry.sol";
import {CashTokenTND} from "./CashTokenTND.sol";
import {TaxVault} from "./TaxVault.sol";
import {CircuitBreaker} from "../governance/CircuitBreaker.sol";

/// @notice Minimal liquidity pool “execution” contract.
/// @dev In this repo we model cash settlement off-chain; on-chain we mint/burn and emit events.
contract LiquidityPool is AccessControl, ReentrancyGuard {
    using Math for uint256;
    uint256 internal constant PRICE_SCALE = 1e8; // TND scale
    uint256 internal constant BPS = 10_000;
    uint256 public constant VAT_BPS = 1_900; // 19%
    uint256 public constant MAX_PRICE_AGE_SEC = 24 hours;

    bytes32 public constant OPERATOR_ROLE = keccak256("OPERATOR_ROLE");
    bytes32 public constant GOVERNANCE_ROLE = keccak256("GOVERNANCE_ROLE");

    IPriceOracle public oracle;
    IKYCRegistry public kycRegistry;
    IInvestorRegistry public investorRegistry;
    CashTokenTND public cashToken;
    TaxVault public taxVault;
    CircuitBreaker public circuitBreaker;

    address public treasury; // Piscine C (Compte de Revenus)
    address public guaranteeFund; // Piscine D (Fonds de Garantie), 0 = disabled
    uint256 public guaranteeFundBps; // % of fees to D (e.g. 100 = 1%)

    uint256 public constant RAS_RESIDENT_BPS = 1_000; // 10%
    uint256 public constant RAS_NON_RESIDENT_BPS = 1_500; // 15%

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
    event GuaranteeFundUpdated(address indexed oldAddr, address indexed newAddr, uint256 bps);
    event InvestorRegistryUpdated(address indexed oldRegistry, address indexed newRegistry);
    event CashTokenUpdated(address indexed oldToken, address indexed newToken);
    event KYCRegistryUpdated(address indexed oldRegistry, address indexed newRegistry);
    event TaxVaultUpdated(address indexed oldVault, address indexed newVault);
    event CircuitBreakerUpdated(address indexed oldBreaker, address indexed newBreaker);
    event PoolFeeUpdated(uint8 indexed feeLevel, uint256 feeBps);
    event SpreadDiscountUpdated(uint8 indexed feeLevel, uint256 discountBps);
    event BaseSpreadUpdated(uint256 oldBps, uint256 newBps);
    event SpreadParamsUpdated(uint256 alphaVolBps, uint256 betaReserveBps, uint256 minReserveRatioBps);

    constructor(address admin_, address oracle_) {
        _grantRole(DEFAULT_ADMIN_ROLE, admin_);
        _grantRole(OPERATOR_ROLE, admin_);
        _grantRole(GOVERNANCE_ROLE, admin_);

        oracle = IPriceOracle(oracle_);
        treasury = admin_;

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
        require(msg.sender == reservationOption || hasRole(DEFAULT_ADMIN_ROLE, msg.sender), "LP: not authorized");
        _;
    }

    function setOracle(address newOracle) external onlyRole(GOVERNANCE_ROLE) {
        oracle = IPriceOracle(newOracle);
    }

    function setInvestorRegistry(address newRegistry) external onlyRole(GOVERNANCE_ROLE) {
        address old = address(investorRegistry);
        investorRegistry = IInvestorRegistry(newRegistry);
        emit InvestorRegistryUpdated(old, newRegistry);
    }

    function setKYCRegistry(address newRegistry) external onlyRole(GOVERNANCE_ROLE) {
        address old = address(kycRegistry);
        kycRegistry = IKYCRegistry(newRegistry);
        emit KYCRegistryUpdated(old, newRegistry);
    }

    function setCashToken(address newToken) external onlyRole(GOVERNANCE_ROLE) {
        address old = address(cashToken);
        cashToken = CashTokenTND(newToken);
        emit CashTokenUpdated(old, newToken);
    }

    function setTaxVault(address newVault) external onlyRole(GOVERNANCE_ROLE) {
        address old = address(taxVault);
        taxVault = TaxVault(newVault);
        emit TaxVaultUpdated(old, newVault);
    }

    function setCircuitBreaker(address newBreaker) external onlyRole(GOVERNANCE_ROLE) {
        address old = address(circuitBreaker);
        circuitBreaker = CircuitBreaker(newBreaker);
        emit CircuitBreakerUpdated(old, newBreaker);
    }

    function setTreasury(address newTreasury) external onlyRole(GOVERNANCE_ROLE) {
        address old = treasury;
        treasury = newTreasury;
        emit TreasuryUpdated(old, newTreasury);
    }

    /// @notice Configure le Fonds de Garantie (Piscine D) : % des frais alloué.
    function setGuaranteeFund(address newAddr, uint256 bps) external onlyRole(GOVERNANCE_ROLE) {
        require(bps <= BPS, "LP: bad bps");
        address old = guaranteeFund;
        guaranteeFund = newAddr;
        guaranteeFundBps = bps;
        emit GuaranteeFundUpdated(old, newAddr, bps);
    }

    function setPoolFeeBps(uint8 feeLevel, uint256 feeBps) external onlyRole(GOVERNANCE_ROLE) {
        require(feeLevel <= 4, "LP: bad level");
        require(feeBps <= 500, "LP: fee too high");
        poolFeeBps[feeLevel] = feeBps;
        emit PoolFeeUpdated(feeLevel, feeBps);
    }

    function setSpreadDiscountBps(uint8 feeLevel, uint256 discountBps) external onlyRole(GOVERNANCE_ROLE) {
        require(feeLevel <= 4, "LP: bad level");
        require(discountBps <= BPS, "LP: bad discount");
        spreadDiscountBps[feeLevel] = discountBps;
        emit SpreadDiscountUpdated(feeLevel, discountBps);
    }

    function setBaseSpreadBps(uint256 newBps) external onlyRole(GOVERNANCE_ROLE) {
        require(newBps <= 500, "LP: spread too high");
        uint256 old = baseSpreadBps;
        baseSpreadBps = newBps;
        emit BaseSpreadUpdated(old, newBps);
    }

    function setSpreadParams(uint256 newAlphaVolBps, uint256 newBetaReserveBps, uint256 newMinReserveRatioBps) external onlyRole(GOVERNANCE_ROLE) {
        require(newMinReserveRatioBps <= BPS, "LP: bad min reserve");
        alphaVolBps = newAlphaVolBps;
        betaReserveBps = newBetaReserveBps;
        minReserveRatioBps = newMinReserveRatioBps;
        emit SpreadParamsUpdated(newAlphaVolBps, newBetaReserveBps, newMinReserveRatioBps);
    }

    function setReservationOption(address newOption) external onlyRole(GOVERNANCE_ROLE) {
        address old = reservationOption;
        reservationOption = newOption;
        emit ReservationOptionUpdated(old, newOption);
    }

    /// @notice Buy CPEF using on-chain TND. User must `approve` the pool on `cashToken` first.
    function buy(address token, uint256 tndIn) external nonReentrant returns (uint256 minted) {
        _requireFreshPrice(token);
        return _buyFor(token, msg.sender, tndIn);
    }

    /// @notice Buy on behalf of a user (platform-driven).
    function buyFor(address token, address user, uint256 tndIn) external onlyRole(OPERATOR_ROLE) nonReentrant returns (uint256 minted) {
        _requireFreshPrice(token);
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

        // Send fees: part to Piscine D (Fonds de Garantie), rest to Piscine C (Revenus)
        if (totalFee > 0) {
            if (guaranteeFund != address(0) && guaranteeFundBps > 0) {
                uint256 toGuarantee = (totalFee * guaranteeFundBps) / BPS;
                uint256 toTreasury = totalFee - toGuarantee;
                if (toGuarantee > 0) require(cashToken.transfer(guaranteeFund, toGuarantee), "LP: guarantee transfer failed");
                if (toTreasury > 0) require(cashToken.transfer(treasury, toTreasury), "LP: fee transfer failed");
            } else {
                require(cashToken.transfer(treasury, totalFee), "LP: fee transfer failed");
            }
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
        _requireFreshPrice(token);
        return _sellFor(token, msg.sender, tokenAmount);
    }

    /// @notice Sell on behalf of a user (platform-driven).
    function sellFor(address token, address user, uint256 tokenAmount) external onlyRole(OPERATOR_ROLE) nonReentrant returns (uint256 tndOut) {
        _requireFreshPrice(token);
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

        (uint256 tax, uint256 gainTnd, bool resident) = _computeRAS(token, user, tokenAmount, vni);

        require(totalFee + tax < grossTnd, "LP: fees+tax exceed");

        tndOut = grossTnd - totalFee - tax;

        // Global pause check (Panic Button) - blocks ALL operations
        if (address(circuitBreaker) != address(0)) {
            require(!circuitBreaker.isPaused(), "LP: global pause active");
        }

        // Circuit breaker: pause redemptions when reserve ratio is too low.
        // We keep buys enabled to allow replenishing liquidity.
        if (address(circuitBreaker) != address(0)) {
            if (circuitBreaker.redemptionsPaused(address(this))) {
                revert("LP: redemptions paused");
            }
            uint256 reserveRatioBps = _reserveRatioBps(token, vni);
            if (reserveRatioBps < circuitBreaker.thresholdBps()) {
                // NOTE: we do NOT trip-and-revert here because revert would roll back the trip.
                // A separate keeper / backend should call `checkAndTripRedemptions()` to persist the pause.
                revert("LP: reserve too low");
            }
        }

        require(cashToken.balanceOf(address(this)) >= grossTnd, "LP: insufficient liquidity");

        // Burn first (platform-only burn)
        CPEFToken(token).burnFromUser(user, tokenAmount);

        // Pay user; fees: part to Piscine D, rest to Piscine C
        require(cashToken.transfer(user, tndOut), "LP: payout transfer failed");
        if (totalFee > 0) {
            if (guaranteeFund != address(0) && guaranteeFundBps > 0) {
                uint256 toGuarantee = (totalFee * guaranteeFundBps) / BPS;
                uint256 toTreasury = totalFee - toGuarantee;
                if (toGuarantee > 0) require(cashToken.transfer(guaranteeFund, toGuarantee), "LP: guarantee transfer failed");
                if (toTreasury > 0) require(cashToken.transfer(treasury, toTreasury), "LP: fee transfer failed");
            } else {
                require(cashToken.transfer(treasury, totalFee), "LP: fee transfer failed");
            }
        }
        if (tax > 0) {
            require(address(taxVault) != address(0), "LP: tax vault not set");
            require(cashToken.transfer(address(taxVault), tax), "LP: tax transfer failed");
            taxVault.recordRAS(user, token, gainTnd, tax, resident);
        }

        emit Sold(token, user, tokenAmount, priceClient, tndOut, feeBase, vat, totalFee);
    }

    /// @notice Expose a buy quote (for UI/backend) using current on-chain parameters.
    function quoteBuy(address token, address user, uint256 tndIn)
        external
        view
        returns (uint256 priceClient, uint256 minted, uint256 feeBase, uint256 vat, uint256 totalFee)
    {
        _requireFreshPriceView(token);
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
        returns (uint256 priceClient, uint256 tndOut, uint256 feeBase, uint256 vat, uint256 totalFee, uint256 tax)
    {
        _requireFreshPriceView(token);
        uint8 feeLevel = investorRegistry.getFeeLevel(user);
        uint256 vni = oracle.getVNI(token);
        uint256 spreadBps = _effectiveSpreadBps(token, feeLevel);
        priceClient = (vni * (BPS - spreadBps)) / BPS;
        uint256 grossTnd = Math.mulDiv(tokenAmount, priceClient, PRICE_SCALE);
        feeBase = (grossTnd * poolFeeBps[feeLevel]) / BPS;
        vat = (feeBase * VAT_BPS) / BPS;
        totalFee = feeBase + vat;
        (tax, , ) = _computeRAS(token, user, tokenAmount, vni);
        tndOut = grossTnd - totalFee - tax;
    }

    function _computeRAS(address token, address user, uint256 tokenAmount, uint256 vni)
        internal
        view
        returns (uint256 tax, uint256 gainTnd, bool resident)
    {
        if (address(kycRegistry) == address(0)) return (0, 0, false);
        resident = kycRegistry.isResident(user);

        uint256 prm = CPEFToken(token).getPRM(user); // TND per token (1e8)
        if (vni <= prm) return (0, 0, resident);

        uint256 gainPerToken = vni - prm;
        gainTnd = Math.mulDiv(tokenAmount, gainPerToken, PRICE_SCALE);
        uint256 rate = resident ? RAS_RESIDENT_BPS : RAS_NON_RESIDENT_BPS;
        tax = (gainTnd * rate) / BPS;
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

    /// @notice Expose current reserve ratio (bps) for a given token.
    function getReserveRatioBps(address token) external view returns (uint256) {
        _requireFreshPriceView(token);
        uint256 vni = oracle.getVNI(token);
        if (vni == 0) return 0;
        return _reserveRatioBps(token, vni);
    }

    /// @notice Check reserve ratio and, if below threshold, trip the circuit breaker persistently.
    /// @dev This call must NOT revert for the trip to persist.
    function checkAndTripRedemptions(address token) external returns (bool tripped, uint256 reserveRatioBps) {
        require(address(circuitBreaker) != address(0), "LP: circuit breaker not set");
        _requireFreshPrice(token);
        uint256 vni = oracle.getVNI(token);
        reserveRatioBps = _reserveRatioBps(token, vni);
        if (reserveRatioBps < circuitBreaker.thresholdBps()) {
            circuitBreaker.tripRedemptions(address(this), reserveRatioBps);
            return (true, reserveRatioBps);
        }
        return (false, reserveRatioBps);
    }

    function _requireFreshPrice(address token) internal view {
        (, uint64 updatedAt) = oracle.getVNIData(token);
        require(updatedAt != 0, "LP: price not set");
        require(block.timestamp - uint256(updatedAt) <= MAX_PRICE_AGE_SEC, "LP: price stale");
    }

    function _requireFreshPriceView(address token) internal view {
        (, uint64 updatedAt) = oracle.getVNIData(token);
        require(updatedAt != 0, "LP: price not set");
        require(block.timestamp - uint256(updatedAt) <= MAX_PRICE_AGE_SEC, "LP: price stale");
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

