import { ethers, network } from "hardhat";
import fs from "node:fs";
import path from "node:path";

type Addr = string;

function envAddr(name: string, fallback: Addr): Addr {
  const v = process.env[name];
  return v && v.length > 0 ? v : fallback;
}

async function main() {
  const signers = await ethers.getSigners();
  const deployer = signers[0];

  // Council members (default: first 5 hardhat signers)
  const defaultOwners = signers.slice(0, 5).map((s) => s.address);
  const ownersEnv = process.env.COUNCIL_OWNERS;
  const owners = ownersEnv ? ownersEnv.split(",").map((s) => s.trim()).filter(Boolean) : defaultOwners;
  const threshold = Number(process.env.COUNCIL_THRESHOLD ?? "3");

  const MultiSigCouncil = await ethers.getContractFactory("MultiSigCouncil");
  const council = await MultiSigCouncil.deploy(owners, threshold);
  await council.waitForDeployment();

  // Actors
  const treasury = envAddr("TREASURY", deployer.address);
  const oracleUpdater = envAddr("ORACLE_UPDATER", deployer.address);
  const poolOperator = envAddr("POOL_OPERATOR", deployer.address);
  const kycValidator = envAddr("KYC_VALIDATOR", deployer.address);
  const cashMinter = envAddr("CASH_MINTER", deployer.address);
  const cashBurner = envAddr("CASH_BURNER", deployer.address);
  const fisc = envAddr("FISC_ADDRESS", deployer.address);

  // Shared infra
  const KYCRegistry = await ethers.getContractFactory("KYCRegistry");
  const kyc = await KYCRegistry.deploy(deployer.address);
  await kyc.waitForDeployment();

  const InvestorRegistry = await ethers.getContractFactory("InvestorRegistry");
  const investors = await InvestorRegistry.deploy(deployer.address);
  await investors.waitForDeployment();

  const CashTokenTND = await ethers.getContractFactory("CashTokenTND");
  const tnd = await CashTokenTND.deploy(deployer.address);
  await tnd.waitForDeployment();

  const TaxVault = await ethers.getContractFactory("TaxVault");
  const taxVault = await TaxVault.deploy(deployer.address, await tnd.getAddress());
  await taxVault.waitForDeployment();

  const CircuitBreaker = await ethers.getContractFactory("CircuitBreaker");
  const cb = await CircuitBreaker.deploy(deployer.address);
  await cb.waitForDeployment();

  // Deployer helpers (keep Factory bytecode small)
  const OracleDeployer = await ethers.getContractFactory("OracleDeployer");
  const oracleDeployer = await OracleDeployer.deploy();
  await oracleDeployer.waitForDeployment();

  const PoolDeployer = await ethers.getContractFactory("PoolDeployer");
  const poolDeployer = await PoolDeployer.deploy();
  await poolDeployer.waitForDeployment();

  const TokenDeployer = await ethers.getContractFactory("TokenDeployer");
  const tokenDeployer = await TokenDeployer.deploy();
  await tokenDeployer.waitForDeployment();

  // Factory (council is admin)
  const CPEFFactory = await ethers.getContractFactory("CPEFFactory");
  const factory = await CPEFFactory.deploy(
    await council.getAddress(),
    await kyc.getAddress(),
    await investors.getAddress(),
    await tnd.getAddress(),
    await taxVault.getAddress(),
    await cb.getAddress(),
    await oracleDeployer.getAddress(),
    await poolDeployer.getAddress(),
    await tokenDeployer.getAddress(),
    treasury,
    oracleUpdater,
    poolOperator
  );
  await factory.waitForDeployment();

  // Role assignment to council + delegation to operators
  const councilAddr = await council.getAddress();

  // KYC: admin -> council, validator -> backend
  await (await kyc.grantRole(await kyc.DEFAULT_ADMIN_ROLE(), councilAddr)).wait();
  await (await kyc.grantRole(await kyc.KYC_VALIDATOR_ROLE(), kycValidator)).wait();
  await (await kyc.renounceRole(await kyc.DEFAULT_ADMIN_ROLE(), deployer.address)).wait();

  // Investor: admin+gov -> council, operator -> backend
  await (await investors.grantRole(await investors.DEFAULT_ADMIN_ROLE(), councilAddr)).wait();
  await (await investors.grantRole(await investors.GOVERNANCE_ROLE(), councilAddr)).wait();
  await (await investors.grantRole(await investors.OPERATOR_ROLE(), poolOperator)).wait();
  await (await investors.renounceRole(await investors.DEFAULT_ADMIN_ROLE(), deployer.address)).wait();

  // Cash: admin+gov -> council, minter/burner -> bank
  await (await tnd.grantRole(await tnd.DEFAULT_ADMIN_ROLE(), councilAddr)).wait();
  await (await tnd.grantRole(await tnd.GOVERNANCE_ROLE(), councilAddr)).wait();
  await (await tnd.grantRole(await tnd.MINTER_ROLE(), cashMinter)).wait();
  await (await tnd.grantRole(await tnd.BURNER_ROLE(), cashBurner)).wait();
  await (await tnd.renounceRole(await tnd.DEFAULT_ADMIN_ROLE(), deployer.address)).wait();

  // TaxVault: admin -> council, gov -> council, set fisc
  await (await taxVault.grantRole(await taxVault.DEFAULT_ADMIN_ROLE(), councilAddr)).wait();
  await (await taxVault.grantRole(await taxVault.GOVERNANCE_ROLE(), councilAddr)).wait();
  await (await taxVault.setFiscAddress(fisc)).wait();

  // CircuitBreaker: admin+gov -> council
  await (await cb.grantRole(await cb.DEFAULT_ADMIN_ROLE(), councilAddr)).wait();
  await (await cb.grantRole(await cb.GOVERNANCE_ROLE(), councilAddr)).wait();

  // Allow factory to wire pools into infra (needs GOVERNANCE_ROLE on TaxVault + CircuitBreaker)
  await (await taxVault.grantRole(await taxVault.GOVERNANCE_ROLE(), await factory.getAddress())).wait();
  await (await cb.grantRole(await cb.GOVERNANCE_ROLE(), await factory.getAddress())).wait();

  // Now that all required roles are assigned, remove deployer admin powers
  await (await taxVault.renounceRole(await taxVault.DEFAULT_ADMIN_ROLE(), deployer.address)).wait();
  await (await cb.renounceRole(await cb.DEFAULT_ADMIN_ROLE(), deployer.address)).wait();

  console.log("Network:", network.name);
  console.log("Deployer:", deployer.address);
  console.log("Council (MultiSig):", councilAddr);
  console.log("CPEFFactory:", await factory.getAddress());

  const out = {
    network: network.name,
    deployer: deployer.address,
    council: {
      address: councilAddr,
      owners,
      threshold,
    },
    actors: {
      treasury,
      oracleUpdater,
      poolOperator,
      kycValidator,
      cashMinter,
      cashBurner,
      fisc,
    },
    infra: {
      KYCRegistry: await kyc.getAddress(),
      InvestorRegistry: await investors.getAddress(),
      CashTokenTND: await tnd.getAddress(),
      TaxVault: await taxVault.getAddress(),
      CircuitBreaker: await cb.getAddress(),
    },
    deployers: {
      OracleDeployer: await oracleDeployer.getAddress(),
      PoolDeployer: await poolDeployer.getAddress(),
      TokenDeployer: await tokenDeployer.getAddress(),
    },
    factory: {
      CPEFFactory: await factory.getAddress(),
    },
    generatedAt: new Date().toISOString(),
  };

  const outDir = path.join(__dirname, "..", "deployments");
  fs.mkdirSync(outDir, { recursive: true });
  const outPath = path.join(outDir, `${network.name}.council.json`);
  fs.writeFileSync(outPath, JSON.stringify(out, null, 2), { encoding: "utf-8" });
  console.log("Saved deployments to:", outPath);
}

main().catch((err) => {
  console.error(err);
  process.exitCode = 1;
});

