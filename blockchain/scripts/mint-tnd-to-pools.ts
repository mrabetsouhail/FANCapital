import { ethers, network } from "hardhat";
import fs from "node:fs";
import path from "node:path";

/**
 * Mint TND tokens to all liquidity pools for testing.
 * This provides liquidity so users can sell their tokens.
 * Usage:
 *   AMOUNT_TND=5000 npx hardhat run scripts/mint-tnd-to-pools.ts --network localhost
 */
async function main() {
  const [deployer] = await ethers.getSigners();
  const amountTnd = process.env.AMOUNT_TND ? parseFloat(process.env.AMOUNT_TND) : 5000;

  // Load deployments
  const deploymentsPath = path.join(__dirname, "..", "deployments", `${network.name}.factory-funds.json`);
  if (!fs.existsSync(deploymentsPath)) {
    throw new Error(`Deployments file not found: ${deploymentsPath}`);
  }

  const deployments = JSON.parse(fs.readFileSync(deploymentsPath, "utf-8"));
  const cashTokenAddress = deployments.infra?.CashTokenTND;
  const funds = deployments.funds || [];

  if (!cashTokenAddress) {
    throw new Error("CashTokenTND address not found in deployments file");
  }

  if (funds.length === 0) {
    throw new Error("No funds found in deployments file");
  }

  console.log("Network:", network.name);
  console.log("Deployer:", deployer.address);
  console.log("CashTokenTND:", cashTokenAddress);
  console.log("Amount per pool:", amountTnd, "TND");
  console.log("");

  const CashTokenTND = await ethers.getContractFactory("CashTokenTND");
  const cashToken = CashTokenTND.attach(cashTokenAddress);

  // Check if deployer has MINTER_ROLE
  const MINTER_ROLE = await cashToken.MINTER_ROLE();
  const hasRole = await cashToken.hasRole(MINTER_ROLE, deployer.address);

  if (!hasRole) {
    console.log("⚠️  Deployer does not have MINTER_ROLE. Granting...");
    const txGrant = await cashToken.grantRole(MINTER_ROLE, deployer.address);
    await txGrant.wait();
    console.log("✓ Granted MINTER_ROLE to deployer\n");
  }

  // Mint amount (8 decimals)
  const amountWei = ethers.parseUnits(amountTnd.toString(), 8);

  for (const fund of funds) {
    if (!fund.pool) {
      console.log(`⚠️  Skipping fund ${fund.name || fund.id}: no pool address`);
      continue;
    }

    // Check current balance
    const currentBalance = await cashToken.balanceOf(fund.pool);
    console.log(`${fund.name || fund.id}:`);
    console.log(`  Pool: ${fund.pool}`);
    console.log(`  Current balance: ${ethers.formatUnits(currentBalance, 8)} TND`);

    // Mint
    console.log(`  Minting ${ethers.formatUnits(amountWei, 8)} TND...`);
    const tx = await cashToken.mint(fund.pool, amountWei);
    await tx.wait();

    // Check new balance
    const newBalance = await cashToken.balanceOf(fund.pool);
    console.log(`  New balance: ${ethers.formatUnits(newBalance, 8)} TND`);
    console.log(`  Transaction: ${tx.hash}\n`);
  }

  console.log("✅ Done!");
}

main().catch((err) => {
  console.error(err);
  process.exitCode = 1;
});
