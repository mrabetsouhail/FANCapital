/**
 * Déploie uniquement CreditModelBPGP et l'ajoute à localhost.factory-funds.json.
 * À utiliser quand le PGP n'est pas présent (déploiement factory antérieur).
 * Préserve tout l'état existant (KYC, tokens, etc.).
 */
import { ethers } from "hardhat";
import fs from "node:fs";
import path from "node:path";

async function main() {
  const [deployer] = await ethers.getSigners();
  const deploymentsPath = path.join(__dirname, "..", "deployments", "localhost.factory-funds.json");

  if (!fs.existsSync(deploymentsPath)) {
    throw new Error("localhost.factory-funds.json not found. Run deploy:factory-funds:localhost first.");
  }

  const deployments = JSON.parse(fs.readFileSync(deploymentsPath, "utf-8"));
  if (deployments.infra?.CreditModelBPGP) {
    console.log("CreditModelBPGP already deployed at:", deployments.infra.CreditModelBPGP);
    return;
  }

  const kycAddr = deployments.infra?.KYCRegistry;
  const investorsAddr = deployments.infra?.InvestorRegistry;
  const escrowAddr = deployments.infra?.EscrowRegistry;
  const creditAAddr = deployments.infra?.CreditModelA;

  if (!kycAddr || !investorsAddr || !escrowAddr || !creditAAddr) {
    throw new Error("Missing infra contracts (KYCRegistry, InvestorRegistry, EscrowRegistry, CreditModelA)");
  }

  const creditA = await ethers.getContractAt("CreditModelA", creditAAddr);
  const oracleAddr = await creditA.oracle();
  const escrow = await ethers.getContractAt("EscrowRegistry", escrowAddr);

  const CreditModelBPGP = await ethers.getContractFactory("CreditModelBPGP");
  const creditB = await CreditModelBPGP.deploy(deployer.address, oracleAddr, escrowAddr);
  await creditB.waitForDeployment();

  await (await creditB.setKYCRegistry(kycAddr)).wait();
  await (await creditB.setInvestorRegistry(investorsAddr)).wait();
  await (await escrow.setAuthorizedCaller(await creditB.getAddress(), true)).wait();

  const addr = await creditB.getAddress();
  deployments.infra.CreditModelBPGP = addr;
  fs.writeFileSync(deploymentsPath, JSON.stringify(deployments, null, 2), "utf-8");

  console.log("CreditModelBPGP deployed at:", addr);
  console.log("Updated", deploymentsPath);
}

main().catch((err) => {
  console.error(err);
  process.exitCode = 1;
});
