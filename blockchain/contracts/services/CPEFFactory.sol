// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";

import {TaxVault} from "./TaxVault.sol";
import {CashTokenTND} from "./CashTokenTND.sol";
import {IInvestorRegistry} from "../interfaces/IInvestorRegistry.sol";
import {IKYCRegistry} from "../interfaces/IKYCRegistry.sol";
import {CircuitBreaker} from "../governance/CircuitBreaker.sol";

interface IOracleDeployer {
    function deploy(address admin) external returns (address oracle);
}

interface IPoolDeployer {
    function deploy(address admin, address oracle) external returns (address pool);
}

interface ITokenDeployer {
    function deploy(string calldata name, string calldata symbol, address owner) external returns (address token);
}

interface ICPEFToken {
    function setLiquidityPool(address newPool) external;
    function setKYCRegistry(address newRegistry) external;
    function setPriceOracle(address newOracle) external;
    function setCircuitBreaker(address newBreaker) external;
    function DEFAULT_ADMIN_ROLE() external view returns (bytes32);
    function GOVERNANCE_ROLE() external view returns (bytes32);
    function grantRole(bytes32 role, address account) external;
    function renounceRole(bytes32 role, address account) external;

    function liquidityPool() external view returns (address);
    function kycRegistry() external view returns (address);
    function priceOracle() external view returns (address);
    function circuitBreaker() external view returns (address);
}

interface ILiquidityPool {
    function setInvestorRegistry(address newRegistry) external;
    function setKYCRegistry(address newRegistry) external;
    function setCashToken(address newToken) external;
    function setTaxVault(address newVault) external;
    function setCircuitBreaker(address newBreaker) external;
    function setTreasury(address newTreasury) external;

    function oracle() external view returns (address);
    function investorRegistry() external view returns (address);
    function kycRegistry() external view returns (address);
    function cashToken() external view returns (address);
    function taxVault() external view returns (address);
    function circuitBreaker() external view returns (address);

    function DEFAULT_ADMIN_ROLE() external view returns (bytes32);
    function GOVERNANCE_ROLE() external view returns (bytes32);
    function OPERATOR_ROLE() external view returns (bytes32);
    function grantRole(bytes32 role, address account) external;
    function renounceRole(bytes32 role, address account) external;
}

interface IPriceOracle {
    function DEFAULT_ADMIN_ROLE() external view returns (bytes32);
    function GOVERNANCE_ROLE() external view returns (bytes32);
    function ORACLE_ROLE() external view returns (bytes32);
    function grantRole(bytes32 role, address account) external;
    function renounceRole(bytes32 role, address account) external;
}

/// @notice Factory to deploy a full "fund" (CPEFToken + dedicated LiquidityPool + dedicated PriceOracle) atomically.
/// @dev Central infra (TaxVault/InvestorRegistry/CircuitBreaker/CashToken/KYC) is shared across funds.
contract CPEFFactory is AccessControl {
    bytes32 public constant GOVERNANCE_ROLE = keccak256("GOVERNANCE_ROLE");
    bytes32 public constant OPERATOR_ROLE = keccak256("OPERATOR_ROLE");

    struct Fund {
        address token;
        address pool;
        address oracle;
        uint64 createdAt;
    }

    IKYCRegistry public immutable kycRegistry;
    IInvestorRegistry public immutable investorRegistry;
    CashTokenTND public immutable cashToken;
    TaxVault public immutable taxVault;
    CircuitBreaker public immutable circuitBreaker;
    IOracleDeployer public immutable oracleDeployer;
    IPoolDeployer public immutable poolDeployer;
    ITokenDeployer public immutable tokenDeployer;

    address public treasury;
    address public oracleUpdater; // keeper / operator that pushes prices (ORACLE_ROLE on new oracles)
    address public poolOperator;  // platform operator for buyFor/sellFor (OPERATOR_ROLE on new pools)

    Fund[] private _funds;

    event TreasuryUpdated(address indexed oldTreasury, address indexed newTreasury);
    event OracleUpdaterUpdated(address indexed oldUpdater, address indexed newUpdater);
    event PoolOperatorUpdated(address indexed oldOperator, address indexed newOperator);
    event FundDeployed(uint256 indexed fundId, address indexed token, address indexed pool, address oracle);

    constructor(
        address council_,
        address kycRegistry_,
        address investorRegistry_,
        address cashToken_,
        address taxVault_,
        address circuitBreaker_,
        address oracleDeployer_,
        address poolDeployer_,
        address tokenDeployer_,
        address treasury_,
        address oracleUpdater_,
        address poolOperator_
    ) {
        require(council_ != address(0), "FACTORY: council=0");
        require(kycRegistry_ != address(0), "FACTORY: kyc=0");
        require(investorRegistry_ != address(0), "FACTORY: inv=0");
        require(cashToken_ != address(0), "FACTORY: cash=0");
        require(taxVault_ != address(0), "FACTORY: tax=0");
        require(circuitBreaker_ != address(0), "FACTORY: cb=0");
        require(oracleDeployer_ != address(0), "FACTORY: oracle deployer=0");
        require(poolDeployer_ != address(0), "FACTORY: pool deployer=0");
        require(tokenDeployer_ != address(0), "FACTORY: token deployer=0");
        require(treasury_ != address(0), "FACTORY: treasury=0");

        _grantRole(DEFAULT_ADMIN_ROLE, council_);
        _grantRole(GOVERNANCE_ROLE, council_);
        _grantRole(OPERATOR_ROLE, council_);

        kycRegistry = IKYCRegistry(kycRegistry_);
        investorRegistry = IInvestorRegistry(investorRegistry_);
        cashToken = CashTokenTND(cashToken_);
        taxVault = TaxVault(taxVault_);
        circuitBreaker = CircuitBreaker(circuitBreaker_);
        oracleDeployer = IOracleDeployer(oracleDeployer_);
        poolDeployer = IPoolDeployer(poolDeployer_);
        tokenDeployer = ITokenDeployer(tokenDeployer_);

        treasury = treasury_;
        oracleUpdater = oracleUpdater_;
        poolOperator = poolOperator_;
    }

    function setTreasury(address newTreasury) external onlyRole(GOVERNANCE_ROLE) {
        require(newTreasury != address(0), "FACTORY: treasury=0");
        address old = treasury;
        treasury = newTreasury;
        emit TreasuryUpdated(old, newTreasury);
    }

    function setOracleUpdater(address newUpdater) external onlyRole(GOVERNANCE_ROLE) {
        address old = oracleUpdater;
        oracleUpdater = newUpdater;
        emit OracleUpdaterUpdated(old, newUpdater);
    }

    function setPoolOperator(address newOperator) external onlyRole(GOVERNANCE_ROLE) {
        address old = poolOperator;
        poolOperator = newOperator;
        emit PoolOperatorUpdated(old, newOperator);
    }

    function fundsCount() external view returns (uint256) {
        return _funds.length;
    }

    function getFund(uint256 fundId) external view returns (Fund memory) {
        require(fundId < _funds.length, "FACTORY: bad id");
        return _funds[fundId];
    }

    /// @notice Deploy a complete fund (token + pool + oracle) and wire it to shared infra in one transaction.
    function deployFund(string calldata name_, string calldata symbol_) external onlyRole(GOVERNANCE_ROLE) returns (uint256 fundId) {
        require(bytes(name_).length > 0, "FACTORY: name empty");
        require(bytes(symbol_).length > 0, "FACTORY: symbol empty");

        // Deploy with this factory as temporary admin/owner for atomic wiring
        address oracleAddr = oracleDeployer.deploy(address(this));
        address poolAddr = poolDeployer.deploy(address(this), oracleAddr);
        address tokenAddr = tokenDeployer.deploy(name_, symbol_, address(this));

        IPriceOracle oracle = IPriceOracle(oracleAddr);
        ILiquidityPool pool = ILiquidityPool(poolAddr);
        ICPEFToken token = ICPEFToken(tokenAddr);

        // Wire token <-> pool/oracle/kyc/circuitBreaker
        token.setLiquidityPool(poolAddr);
        token.setKYCRegistry(address(kycRegistry));
        token.setPriceOracle(oracleAddr);
        token.setCircuitBreaker(address(circuitBreaker));

        // Wire pool to shared infra
        pool.setInvestorRegistry(address(investorRegistry));
        pool.setKYCRegistry(address(kycRegistry));
        pool.setCashToken(address(cashToken));
        pool.setTaxVault(address(taxVault));
        pool.setCircuitBreaker(address(circuitBreaker));
        pool.setTreasury(treasury);

        // Register pool with shared safety system and tax vault
        circuitBreaker.registerPool(poolAddr);
        taxVault.setAuthorizedCaller(poolAddr, true);

        // Assign roles on new oracle/pool to real operators and governance
        // PriceOracle roles
        oracle.grantRole(oracle.DEFAULT_ADMIN_ROLE(), msg.sender);
        oracle.grantRole(oracle.GOVERNANCE_ROLE(), msg.sender);
        if (oracleUpdater != address(0)) {
            oracle.grantRole(oracle.ORACLE_ROLE(), oracleUpdater);
        } else {
            // fallback: governance can still update via ORACLE_ROLE if needed
            oracle.grantRole(oracle.ORACLE_ROLE(), msg.sender);
        }
        oracle.renounceRole(oracle.ORACLE_ROLE(), address(this));
        oracle.renounceRole(oracle.GOVERNANCE_ROLE(), address(this));
        oracle.renounceRole(oracle.DEFAULT_ADMIN_ROLE(), address(this));

        // LiquidityPool roles
        pool.grantRole(pool.DEFAULT_ADMIN_ROLE(), msg.sender);
        pool.grantRole(pool.GOVERNANCE_ROLE(), msg.sender);
        if (poolOperator != address(0)) {
            pool.grantRole(pool.OPERATOR_ROLE(), poolOperator);
        } else {
            pool.grantRole(pool.OPERATOR_ROLE(), msg.sender);
        }
        pool.renounceRole(pool.OPERATOR_ROLE(), address(this));
        pool.renounceRole(pool.GOVERNANCE_ROLE(), address(this));
        pool.renounceRole(pool.DEFAULT_ADMIN_ROLE(), address(this));

        // Token roles: transfer control to council and renounce factory roles
        token.grantRole(token.DEFAULT_ADMIN_ROLE(), msg.sender);
        token.grantRole(token.GOVERNANCE_ROLE(), msg.sender);
        token.renounceRole(token.GOVERNANCE_ROLE(), address(this));
        token.renounceRole(token.DEFAULT_ADMIN_ROLE(), address(this));

        fundId = _funds.length;
        _funds.push(
            Fund({
                token: tokenAddr,
                pool: poolAddr,
                oracle: oracleAddr,
                createdAt: uint64(block.timestamp)
            })
        );

        emit FundDeployed(fundId, tokenAddr, poolAddr, oracleAddr);
    }
}

