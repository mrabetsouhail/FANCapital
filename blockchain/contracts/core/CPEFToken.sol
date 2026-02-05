// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {ERC20} from "@openzeppelin/contracts/token/ERC20/ERC20.sol";
import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";
import {ReentrancyGuard} from "@openzeppelin/contracts/utils/ReentrancyGuard.sol";
import {Math} from "@openzeppelin/contracts/utils/math/Math.sol";

import {IERC1404} from "../interfaces/IERC1404.sol";
import {IKYCRegistry} from "../interfaces/IKYCRegistry.sol";
import {IPriceOracle} from "../interfaces/IPriceOracle.sol";
import {CircuitBreaker} from "../governance/CircuitBreaker.sol";

/// @notice Base CPEF token implementing ERC-1404 style transfer restrictions.
/// @dev Token decimals are 8 to match the CPEF spec.
contract CPEFToken is ERC20, IERC1404, AccessControl, ReentrancyGuard {
    using Math for uint256;

    bytes32 public constant GOVERNANCE_ROLE = keccak256("GOVERNANCE_ROLE");

    uint8 public constant KYC_LEVEL_NONE = 0;
    uint8 public constant KYC_LEVEL_GREEN = 1;
    uint8 public constant KYC_LEVEL_WHITE = 2;

    // ERC-1404 restriction codes (aligned with docs)
    uint8 public constant RESTRICTION_NONE = 0;
    uint8 public constant RESTRICTION_NOT_WHITELISTED = 1;
    uint8 public constant RESTRICTION_LIMIT_EXCEEDED = 2; // reserved for future caps
    uint8 public constant RESTRICTION_P2P_DISABLED = 3;
    uint8 public constant RESTRICTION_ESCROW_LOCKED = 4;
    uint8 public constant RESTRICTION_PAUSED = 5; // reserved for future pausable

    uint256 internal constant PRICE_SCALE = 1e8; // TND prices scaled to 8 decimals
    uint256 public constant GREEN_LEVEL_MAX_TND_BALANCE = 5_000 * PRICE_SCALE; // 5000.00000000 TND

    IKYCRegistry public kycRegistry;
    IPriceOracle public priceOracle;
    CircuitBreaker public circuitBreaker;

    /// @notice Average cost basis per user (PRM) in TND (scaled 1e8)
    mapping(address => uint256) private _prm;

    /// @notice Optional escrow registry: addresses locked (e.g. collateral)
    mapping(address => bool) public isEscrowLocked;

    /// @notice Address allowed to lock/unlock escrow (typically EscrowRegistry)
    address public escrowManager;

    /// @notice Address allowed to mint (typically LiquidityPool)
    address public liquidityPool;

    event LiquidityPoolUpdated(address indexed oldPool, address indexed newPool);
    event EscrowManagerUpdated(address indexed oldManager, address indexed newManager);
    event KYCRegistryUpdated(address indexed oldRegistry, address indexed newRegistry);
    event PriceOracleUpdated(address indexed oldOracle, address indexed newOracle);
    event CircuitBreakerUpdated(address indexed oldBreaker, address indexed newBreaker);
    event PRMUpdated(address indexed user, uint256 oldPRM, uint256 newPRM);
    event EscrowLockUpdated(address indexed user, bool locked);

    modifier onlyLiquidityPool() {
        require(msg.sender == liquidityPool, "CPEF: only liquidity pool");
        _;
    }

    modifier onlyEscrowManagerOrOwner() {
        require(msg.sender == escrowManager || hasRole(GOVERNANCE_ROLE, msg.sender), "CPEF: only escrow manager");
        _;
    }

    constructor(
        string memory name_,
        string memory symbol_,
        address admin_
    ) ERC20(name_, symbol_) {
        _grantRole(DEFAULT_ADMIN_ROLE, admin_);
        _grantRole(GOVERNANCE_ROLE, admin_);
    }

    function decimals() public pure override returns (uint8) {
        return 8;
    }

    function setLiquidityPool(address newPool) external onlyRole(GOVERNANCE_ROLE) {
        address old = liquidityPool;
        liquidityPool = newPool;
        emit LiquidityPoolUpdated(old, newPool);
    }

    function setEscrowManager(address newManager) external onlyRole(GOVERNANCE_ROLE) {
        address old = escrowManager;
        escrowManager = newManager;
        emit EscrowManagerUpdated(old, newManager);
    }

    function setKYCRegistry(address newRegistry) external onlyRole(GOVERNANCE_ROLE) {
        address old = address(kycRegistry);
        kycRegistry = IKYCRegistry(newRegistry);
        emit KYCRegistryUpdated(old, newRegistry);
    }

    function setPriceOracle(address newOracle) external onlyRole(GOVERNANCE_ROLE) {
        address old = address(priceOracle);
        priceOracle = IPriceOracle(newOracle);
        emit PriceOracleUpdated(old, newOracle);
    }

    function setCircuitBreaker(address newBreaker) external onlyRole(GOVERNANCE_ROLE) {
        address old = address(circuitBreaker);
        circuitBreaker = CircuitBreaker(newBreaker);
        emit CircuitBreakerUpdated(old, newBreaker);
    }

    /// @notice Lock/unlock a user for escrow (collateral); blocks transfers/burns when locked.
    /// @dev In production this should be controlled by an EscrowRegistry contract.
    function setEscrowLocked(address user, bool locked) external onlyEscrowManagerOrOwner {
        isEscrowLocked[user] = locked;
        emit EscrowLockUpdated(user, locked);
    }

    /// @notice Mint tokens; callable only by LiquidityPool.
    /// @param to Recipient.
    /// @param amount Token units (8 decimals).
    /// @param pricePerTokenTnd Price per 1 token in TND scaled 1e8 (used to update PRM).
    function mint(address to, uint256 amount, uint256 pricePerTokenTnd) external onlyLiquidityPool nonReentrant {
        // Global pause check (Panic Button)
        if (address(circuitBreaker) != address(0)) {
            require(!circuitBreaker.isPaused(), "CPEF: global pause active");
        }
        require(address(kycRegistry) != address(0), "CPEF: KYC not set");
        require(kycRegistry.isWhitelisted(to), "CPEF: recipient not whitelisted");
        // Green-list cap is enforced only on NEW acquisitions (mint).
        // We do NOT block organic portfolio value growth caused by VNI increases.
        _checkGreenLevelCapOnAcquisition(to, amount);
        _mint(to, amount);
        _updatePrmOnMint(to, amount, pricePerTokenTnd);
    }

    /// @notice Burn tokens from a user account (redemption).
    /// @dev Only the platform (LiquidityPool) can burn. Cash settlement happens off-chain.
    function burnFromUser(address from, uint256 amount) external onlyLiquidityPool nonReentrant {
        // Global pause check (Panic Button)
        if (address(circuitBreaker) != address(0)) {
            require(!circuitBreaker.isPaused(), "CPEF: global pause active");
        }
        require(!isEscrowLocked[from], "CPEF: escrow locked");
        _burn(from, amount);
        // PRM is kept as-is; backend can compute gains using PRM and oracle VNI.
    }

    function getPRM(address user) external view returns (uint256) {
        return _prm[user];
    }

    function getVNI() external view returns (uint256) {
        require(address(priceOracle) != address(0), "CPEF: oracle not set");
        return priceOracle.getVNI(address(this));
    }

    /// @inheritdoc IERC1404
    function detectTransferRestriction(address from, address to, uint256 amount) public view returns (uint8) {
        // basic checks
        if (amount == 0) return RESTRICTION_NONE;

        // escrow locks
        if (isEscrowLocked[from] || isEscrowLocked[to]) return RESTRICTION_ESCROW_LOCKED;

        // KYC checks
        if (address(kycRegistry) == address(0)) return RESTRICTION_NOT_WHITELISTED;
        if (!kycRegistry.isWhitelisted(from) || !kycRegistry.isWhitelisted(to)) return RESTRICTION_NOT_WHITELISTED;

        // P2P disabled for Green List (doc: Green list no P2P)
        uint8 fromLevel = kycRegistry.getUserLevel(from);
        uint8 toLevel = kycRegistry.getUserLevel(to);
        if (fromLevel == KYC_LEVEL_GREEN || toLevel == KYC_LEVEL_GREEN) return RESTRICTION_P2P_DISABLED;

        // Future: Green-list cap checks are enforced on mint (platform order execution).
        // If one day Green List is allowed to receive P2P, add cap checks here for 'to'.

        // future: caps, pause, etc.
        return RESTRICTION_NONE;
    }

    /// @inheritdoc IERC1404
    function messageForTransferRestriction(uint8 restrictionCode) public pure returns (string memory) {
        if (restrictionCode == RESTRICTION_NONE) return "SUCCESS";
        if (restrictionCode == RESTRICTION_NOT_WHITELISTED) return "Address not whitelisted";
        if (restrictionCode == RESTRICTION_LIMIT_EXCEEDED) return "Transfer limit exceeded";
        if (restrictionCode == RESTRICTION_P2P_DISABLED) return "P2P disabled for Green List";
        if (restrictionCode == RESTRICTION_ESCROW_LOCKED) return "Escrow locked";
        if (restrictionCode == RESTRICTION_PAUSED) return "Token paused";
        return "Unknown restriction";
    }

    function transfer(address to, uint256 amount) public override returns (bool) {
        // Global pause check (Panic Button)
        if (address(circuitBreaker) != address(0)) {
            require(!circuitBreaker.isPaused(), "CPEF: global pause active");
        }
        uint8 code = detectTransferRestriction(_msgSender(), to, amount);
        require(code == RESTRICTION_NONE, messageForTransferRestriction(code));
        bool ok = super.transfer(to, amount);
        if (ok) _updatePrmOnTransfer(_msgSender(), to, amount);
        return ok;
    }

    function transferFrom(address from, address to, uint256 amount) public override returns (bool) {
        uint8 code = detectTransferRestriction(from, to, amount);
        require(code == RESTRICTION_NONE, messageForTransferRestriction(code));
        bool ok = super.transferFrom(from, to, amount);
        if (ok) _updatePrmOnTransfer(from, to, amount);
        return ok;
    }

    function _updatePrmOnMint(address user, uint256 mintAmount, uint256 pricePerTokenTnd) internal {
        // PRM_new = (PRM_old*bal_old + price*mintAmount) / (bal_old + mintAmount)
        uint256 balAfter = balanceOf(user);
        uint256 balBefore = balAfter - mintAmount;

        uint256 oldPrm = _prm[user];
        uint256 newPrm;
        if (balAfter == 0) {
            newPrm = 0;
        } else if (balBefore == 0) {
            newPrm = pricePerTokenTnd;
        } else {
            uint256 weightedOld = oldPrm.mulDiv(balBefore, PRICE_SCALE);
            uint256 weightedNew = pricePerTokenTnd.mulDiv(mintAmount, PRICE_SCALE);
            uint256 totalCost = weightedOld + weightedNew; // TND (1e8)
            newPrm = totalCost.mulDiv(PRICE_SCALE, balAfter); // back to price per token (1e8)
        }

        _prm[user] = newPrm;
        emit PRMUpdated(user, oldPrm, newPrm);
    }

    function _updatePrmOnTransfer(address from, address to, uint256 amount) internal {
        // Conservative approach:
        // - Reduce sender cost basis proportionally (keep PRM constant; total cost shrinks with balance)
        // - Recipient PRM becomes weighted average of existing and sender PRM for the transferred lot
        uint256 senderPrm = _prm[from];
        uint256 recipientOldPrm = _prm[to];

        uint256 toBalAfter = balanceOf(to);
        uint256 toBalBefore = toBalAfter - amount;
        uint256 newRecipientPrm;

        if (toBalAfter == 0) {
            newRecipientPrm = 0;
        } else if (toBalBefore == 0) {
            newRecipientPrm = senderPrm;
        } else {
            uint256 weightedOld = recipientOldPrm.mulDiv(toBalBefore, PRICE_SCALE);
            uint256 weightedLot = senderPrm.mulDiv(amount, PRICE_SCALE);
            uint256 totalCost = weightedOld + weightedLot; // TND (1e8)
            newRecipientPrm = totalCost.mulDiv(PRICE_SCALE, toBalAfter);
        }

        _prm[to] = newRecipientPrm;
        emit PRMUpdated(to, recipientOldPrm, newRecipientPrm);
    }

    function _checkGreenLevelCapOnAcquisition(address to, uint256 acquiredAmount) internal view {
        // Green List users: portfolio value must not exceed 5000 TND AFTER the acquisition.
        // Important: cap is checked only on acquisitions (mint / incoming transfers if enabled later).
        // We do NOT re-check cap when VNI changes; organic value growth is allowed.
        //
        // Value(TND) = balanceTokens * VNI / 1e8
        if (address(kycRegistry) == address(0)) return;
        uint8 level = kycRegistry.getUserLevel(to);
        if (level != KYC_LEVEL_GREEN) return;

        require(address(priceOracle) != address(0), "CPEF: oracle not set");
        uint256 vni = priceOracle.getVNI(address(this));
        require(vni > 0, "CPEF: VNI not set");

        uint256 newBal = balanceOf(to) + acquiredAmount;
        uint256 valueTnd = Math.mulDiv(newBal, vni, PRICE_SCALE); // TND scaled 1e8
        require(valueTnd <= GREEN_LEVEL_MAX_TND_BALANCE, "CPEF: green cap 5000 TND");
    }
}

