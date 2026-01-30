import { ethers, network } from "hardhat";
import fs from "node:fs";
import path from "node:path";

async function main() {
  const [deployer] = await ethers.getSigners();

  const KYCRegistry = await ethers.getContractFactory("KYCRegistry");
  const kyc = await KYCRegistry.deploy(deployer.address);
  await kyc.waitForDeployment();

  const PriceOracle = await ethers.getContractFactory("PriceOracle");
  const oracle = await PriceOracle.deploy(deployer.address);
  await oracle.waitForDeployment();

  const LiquidityPool = await ethers.getContractFactory("LiquidityPool");
  const pool = await LiquidityPool.deploy(deployer.address, await oracle.getAddress());
  await pool.waitForDeployment();

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

  const P2PExchange = await ethers.getContractFactory("P2PExchange");
  const p2p = await P2PExchange.deploy(deployer.address, await tnd.getAddress(), await investors.getAddress(), deployer.address);
  await p2p.waitForDeployment();
  await (await p2p.setKYCRegistry(await kyc.getAddress())).wait();

  const EscrowRegistry = await ethers.getContractFactory("EscrowRegistry");
  const escrow = await EscrowRegistry.deploy(deployer.address);
  await escrow.waitForDeployment();

  const CPEFEquityHigh = await ethers.getContractFactory("CPEFEquityHigh");
  const eqHigh = await CPEFEquityHigh.deploy(deployer.address);
  await eqHigh.waitForDeployment();

  const CPEFEquityMedium = await ethers.getContractFactory("CPEFEquityMedium");
  const eqMed = await CPEFEquityMedium.deploy(deployer.address);
  await eqMed.waitForDeployment();

  // Wire dependencies
  await (await pool.setInvestorRegistry(await investors.getAddress())).wait();
  await (await pool.setKYCRegistry(await kyc.getAddress())).wait();
  await (await pool.setCashToken(await tnd.getAddress())).wait();
  await (await pool.setTaxVault(await taxVault.getAddress())).wait();
  await (await pool.setCircuitBreaker(await cb.getAddress())).wait();
  await (await taxVault.setAuthorizedCaller(await pool.getAddress(), true)).wait();
  await (await cb.registerPool(await pool.getAddress())).wait();

  await (await eqHigh.setLiquidityPool(await pool.getAddress())).wait();
  await (await eqMed.setLiquidityPool(await pool.getAddress())).wait();
  await (await eqHigh.setKYCRegistry(await kyc.getAddress())).wait();
  await (await eqMed.setKYCRegistry(await kyc.getAddress())).wait();
  await (await eqHigh.setPriceOracle(await oracle.getAddress())).wait();
  await (await eqMed.setPriceOracle(await oracle.getAddress())).wait();
  await (await eqHigh.setEscrowManager(await escrow.getAddress())).wait();
  await (await eqMed.setEscrowManager(await escrow.getAddress())).wait();

  const ReservationOption = await ethers.getContractFactory("ReservationOption");
  const opt = await ReservationOption.deploy(deployer.address, await oracle.getAddress(), await pool.getAddress());
  await opt.waitForDeployment();

  await (await pool.setReservationOption(await opt.getAddress())).wait();
  await (await opt.setKYCRegistry(await kyc.getAddress())).wait();
  await (await opt.setInvestorRegistry(await investors.getAddress())).wait();

  const CreditModelA = await ethers.getContractFactory("CreditModelA");
  const creditA = await CreditModelA.deploy(deployer.address, await oracle.getAddress(), await escrow.getAddress());
  await creditA.waitForDeployment();
  await (await creditA.setKYCRegistry(await kyc.getAddress())).wait();
  await (await creditA.setInvestorRegistry(await investors.getAddress())).wait();

  const CreditModelBPGP = await ethers.getContractFactory("CreditModelBPGP");
  const creditB = await CreditModelBPGP.deploy(deployer.address, await oracle.getAddress(), await escrow.getAddress());
  await creditB.waitForDeployment();
  await (await creditB.setKYCRegistry(await kyc.getAddress())).wait();
  await (await creditB.setInvestorRegistry(await investors.getAddress())).wait();

  // Allow credit contracts to lock/unlock collateral
  await (await escrow.setAuthorizedCaller(await creditA.getAddress(), true)).wait();
  await (await escrow.setAuthorizedCaller(await creditB.getAddress(), true)).wait();

  console.log("Deployer:", deployer.address);
  console.log("KYCRegistry:", await kyc.getAddress());
  console.log("InvestorRegistry:", await investors.getAddress());
  console.log("CashTokenTND:", await tnd.getAddress());
  console.log("TaxVault:", await taxVault.getAddress());
  console.log("CircuitBreaker:", await cb.getAddress());
  console.log("P2PExchange:", await p2p.getAddress());
  console.log("PriceOracle:", await oracle.getAddress());
  console.log("LiquidityPool:", await pool.getAddress());
  console.log("EscrowRegistry:", await escrow.getAddress());
  console.log("CPEFEquityHigh:", await eqHigh.getAddress());
  console.log("CPEFEquityMedium:", await eqMed.getAddress());
  console.log("ReservationOption:", await opt.getAddress());
  console.log("CreditModelA:", await creditA.getAddress());
  console.log("CreditModelBPGP:", await creditB.getAddress());

  const deployment = {
    network: network.name,
    deployer: deployer.address,
    contracts: {
      KYCRegistry: await kyc.getAddress(),
      InvestorRegistry: await investors.getAddress(),
      CashTokenTND: await tnd.getAddress(),
      TaxVault: await taxVault.getAddress(),
      CircuitBreaker: await cb.getAddress(),
      P2PExchange: await p2p.getAddress(),
      PriceOracle: await oracle.getAddress(),
      LiquidityPool: await pool.getAddress(),
      EscrowRegistry: await escrow.getAddress(),
      CPEFEquityHigh: await eqHigh.getAddress(),
      CPEFEquityMedium: await eqMed.getAddress(),
      ReservationOption: await opt.getAddress(),
      CreditModelA: await creditA.getAddress(),
      CreditModelBPGP: await creditB.getAddress(),
    },
    generatedAt: new Date().toISOString(),
  };

  const outDir = path.join(__dirname, "..", "deployments");
  fs.mkdirSync(outDir, { recursive: true });
  const outPath = path.join(outDir, `${network.name}.json`);
  fs.writeFileSync(outPath, JSON.stringify(deployment, null, 2), { encoding: "utf-8" });
  console.log("Saved deployments to:", outPath);
}

main().catch((err) => {
  console.error(err);
  process.exitCode = 1;
});

