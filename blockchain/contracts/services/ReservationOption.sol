// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";
import {ReentrancyGuard} from "@openzeppelin/contracts/utils/ReentrancyGuard.sol";

import {IKYCRegistry} from "../interfaces/IKYCRegistry.sol";
import {IInvestorRegistry} from "../interfaces/IInvestorRegistry.sol";
import {IPriceOracle} from "../interfaces/IPriceOracle.sol";
import {LiquidityPool} from "./LiquidityPool.sol";

/// @notice Option de réservation sur stock CPEF.
/// @dev MVP: cash settlement is modeled off-chain; this contract stores state and emits events.
contract ReservationOption is AccessControl, ReentrancyGuard {
    bytes32 public constant GOVERNANCE_ROLE = keccak256("GOVERNANCE_ROLE");
    bytes32 public constant OPERATOR_ROLE = keccak256("OPERATOR_ROLE");
    uint256 internal constant BPS = 10_000;
    uint256 internal constant PRICE_SCALE = 1e8; // TND scale

    struct Reservation {
        address user;
        address token;
        uint256 qty; // token units (8 decimals)
        uint256 strikePrice; // K in TND per token (scaled 1e8), fixed at t0
        uint256 expiry; // timestamp
        uint256 advancePaid; // A in TND (scaled 1e8)
        bool exercised;
        bool cancelled;
    }

    uint256 public nextReservationId = 1;
    mapping(uint256 => Reservation) public reservations;

    IPriceOracle public oracle;
    LiquidityPool public liquidityPool;
    IKYCRegistry public kycRegistry;
    IInvestorRegistry public investorRegistry;

    event Reserved(
        uint256 indexed reservationId,
        address indexed user,
        address indexed token,
        uint256 qty,
        uint256 strikePrice,
        uint256 expiry,
        uint256 advancePaid
    );
    event Exercised(uint256 indexed reservationId);
    event Cancelled(uint256 indexed reservationId);
    event Purged(uint256 indexed reservationId);

    constructor(address admin_, address oracle_, address liquidityPool_) {
        _grantRole(DEFAULT_ADMIN_ROLE, admin_);
        _grantRole(GOVERNANCE_ROLE, admin_);
        _grantRole(OPERATOR_ROLE, admin_);
        oracle = IPriceOracle(oracle_);
        liquidityPool = LiquidityPool(liquidityPool_);
    }

    function setOracle(address newOracle) external onlyRole(GOVERNANCE_ROLE) {
        oracle = IPriceOracle(newOracle);
    }

    function setLiquidityPool(address newPool) external onlyRole(GOVERNANCE_ROLE) {
        liquidityPool = LiquidityPool(newPool);
    }

    function setKYCRegistry(address newKyc) external onlyRole(GOVERNANCE_ROLE) {
        kycRegistry = IKYCRegistry(newKyc);
    }

    function setInvestorRegistry(address newRegistry) external onlyRole(GOVERNANCE_ROLE) {
        investorRegistry = IInvestorRegistry(newRegistry);
    }

    /// @notice Create a reservation.
    /// @param token CPEF token address.
    /// @param qty Token quantity (8 decimals).
    /// @param expiry Expiry timestamp.
    /// @param rBaseBps base reservation rate in bps (e.g. 500 = 5%).
    /// @param sigmaAdjBps volatility adjustment in bps (e.g. 100 = 1%).
    /// @dev Advance A is computed and recorded; actual payment is handled off-chain by backend.
    function reserve(
        address token,
        uint256 qty,
        uint256 expiry,
        uint256 rBaseBps,
        uint256 sigmaAdjBps
    ) external nonReentrant returns (uint256 reservationId) {
        // Freemium gate:
        // - Bronze (KYC level 1) cannot reserve
        // - Reservation is a Premium (30%) service for Platinum/Diamond tiers with active subscription
        require(address(kycRegistry) != address(0), "OPT: KYC not set");
        require(kycRegistry.isWhitelisted(msg.sender), "OPT: not whitelisted");
        require(kycRegistry.getUserLevel(msg.sender) >= 2, "OPT: requires KYC level 2");

        require(address(investorRegistry) != address(0), "OPT: investor registry not set");
        require(investorRegistry.canUseReservation(msg.sender), "OPT: reservation requires premium tier+sub");

        require(qty > 0, "OPT: qty=0");
        require(expiry > block.timestamp, "OPT: expiry");

        uint256 K = oracle.getVNI(token);
        require(K > 0, "OPT: VNI not set");

        // Notional = K * Q / 1e8  (TND scaled 1e8)
        uint256 notional = (K * qty) / PRICE_SCALE;
        uint256 rateBps = rBaseBps + sigmaAdjBps;
        require(rateBps > 0, "OPT: rate=0");
        uint256 A = (notional * rateBps) / BPS;

        reservationId = nextReservationId++;
        reservations[reservationId] = Reservation({
            user: msg.sender,
            token: token,
            qty: qty,
            strikePrice: K,
            expiry: expiry,
            advancePaid: A,
            exercised: false,
            cancelled: false
        });

        // Reserve inventory (accounting) on the pool
        liquidityPool.lockInventory(token, qty);

        emit Reserved(reservationId, msg.sender, token, qty, K, expiry, A);
    }

    function exercise(uint256 reservationId) external nonReentrant {
        Reservation storage r = reservations[reservationId];
        require(r.user == msg.sender, "OPT: not owner");
        require(!r.exercised && !r.cancelled, "OPT: closed");
        require(block.timestamp <= r.expiry, "OPT: expired");

        r.exercised = true;

        // MVP: off-chain settlement collects R = (K*Q) - A, then backend transfers/mints tokens.
        // On-chain we just release inventory lock for now; a production version would deliver tokens.
        liquidityPool.unlockInventory(r.token, r.qty);
        emit Exercised(reservationId);
    }

    function cancel(uint256 reservationId) external nonReentrant {
        Reservation storage r = reservations[reservationId];
        require(r.user == msg.sender, "OPT: not owner");
        require(!r.exercised && !r.cancelled, "OPT: closed");
        require(block.timestamp <= r.expiry, "OPT: expired");

        r.cancelled = true;
        liquidityPool.unlockInventory(r.token, r.qty);
        emit Cancelled(reservationId);
    }

    function purge(uint256 reservationId) external nonReentrant {
        Reservation storage r = reservations[reservationId];
        require(!r.exercised && !r.cancelled, "OPT: closed");
        require(block.timestamp > r.expiry, "OPT: not expired");

        r.cancelled = true;
        liquidityPool.unlockInventory(r.token, r.qty);
        emit Purged(reservationId);
    }
}

