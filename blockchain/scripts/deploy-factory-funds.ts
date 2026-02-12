import { ethers, network } from "hardhat";
import fs from "node:fs";
import path from "node:path";

async function main() {
  const [deployer] = await ethers.getSigners();

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

  // Factory
  const treasury = deployer.address;
  const oracleUpdater = deployer.address;
  const poolOperator = deployer.address;

  const CPEFFactory = await ethers.getContractFactory("CPEFFactory");
  const factory = await CPEFFactory.deploy(
    deployer.address,
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

  // Allow factory to auto-wire pools into shared infra
  await (await taxVault.grantRole(await taxVault.GOVERNANCE_ROLE(), await factory.getAddress())).wait();
  await (await cb.grantRole(await cb.GOVERNANCE_ROLE(), await factory.getAddress())).wait();

  // Deploy the 2 flagship funds
  const tx1 = await factory.deployFund("CPEF Atlas", "$ATLAS$");
  await tx1.wait();
  const tx2 = await factory.deployFund("CPEF Didon", "$DIDON$");
  await tx2.wait();

  const fund0 = await factory.getFund(0);
  const fund1 = await factory.getFund(1);

  // AST (Avance sur Titres): shared PriceOracle + EscrowRegistry + CreditModelA
  const PriceOracle = await ethers.getContractFactory("PriceOracle");
  const astOracle = await PriceOracle.deploy(deployer.address);
  await astOracle.waitForDeployment();
  const PRICE_SCALE = BigInt(1e8);
  await (await astOracle.updateVNI(fund0.token, BigInt(10) * PRICE_SCALE)).wait(); // 10 TND Atlas
  await (await astOracle.updateVNI(fund1.token, BigInt(5) * PRICE_SCALE)).wait();  // 5 TND Didon

  const EscrowRegistry = await ethers.getContractFactory("EscrowRegistry");
  const escrow = await EscrowRegistry.deploy(deployer.address);
  await escrow.waitForDeployment();

  const CreditModelA = await ethers.getContractFactory("CreditModelA");
  const creditA = await CreditModelA.deploy(deployer.address, await astOracle.getAddress(), await escrow.getAddress());
  await creditA.waitForDeployment();
  await (await creditA.setKYCRegistry(await kyc.getAddress())).wait();
  await (await creditA.setInvestorRegistry(await investors.getAddress())).wait();
  await (await escrow.setAuthorizedCaller(await creditA.getAddress(), true)).wait();

  const CPEFToken = await ethers.getContractFactory("CPEFToken");
  const atlasToken = CPEFToken.attach(fund0.token) as { setEscrowManager: (a: string) => Promise<unknown> };
  const didonToken = CPEFToken.attach(fund1.token) as { setEscrowManager: (a: string) => Promise<unknown> };
  await (await atlasToken.setEscrowManager(await escrow.getAddress())).wait();
  await (await didonToken.setEscrowManager(await escrow.getAddress())).wait();

  console.log("Network:", network.name);
  console.log("Deployer:", deployer.address);
  console.log("CPEFFactory:", await factory.getAddress());
  console.log("CPEF Atlas:", fund0);
  console.log("CPEF Didon:", fund1);
  console.log("CreditModelA:", await creditA.getAddress());
  console.log("EscrowRegistry:", await escrow.getAddress());

  const deployment = {
    network: network.name,
    deployer: deployer.address,
    infra: {
      KYCRegistry: await kyc.getAddress(),
      InvestorRegistry: await investors.getAddress(),
      CashTokenTND: await tnd.getAddress(),
      TaxVault: await taxVault.getAddress(),
      CircuitBreaker: await cb.getAddress(),
      CreditModelA: await creditA.getAddress(),
      EscrowRegistry: await escrow.getAddress(),
    },
    deployers: {
      OracleDeployer: await oracleDeployer.getAddress(),
      PoolDeployer: await poolDeployer.getAddress(),
      TokenDeployer: await tokenDeployer.getAddress(),
    },
    factory: {
      CPEFFactory: await factory.getAddress(),
      treasury,
      oracleUpdater,
      poolOperator,
    },
    funds: [
      { id: 0, name: "CPEF Atlas", symbol: "$ATLAS$", token: fund0.token, pool: fund0.pool, oracle: fund0.oracle },
      { id: 1, name: "CPEF Didon", symbol: "$DIDON$", token: fund1.token, pool: fund1.pool, oracle: fund1.oracle },
    ],
    generatedAt: new Date().toISOString(),
  };

  const outDir = path.join(__dirname, "..", "deployments");
  fs.mkdirSync(outDir, { recursive: true });
  const outPath = path.join(outDir, `${network.name}.factory-funds.json`);
  fs.writeFileSync(outPath, JSON.stringify(deployment, null, 2), { encoding: "utf-8" });
  console.log("Saved deployments to:", outPath);
}

main().catch((err) => {
  console.error(err);
  process.exitCode = 1;
});

