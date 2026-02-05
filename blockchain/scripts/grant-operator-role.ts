import { ethers, network } from "hardhat";
import fs from "node:fs";
import path from "node:path";

/**
 * Grant OPERATOR_ROLE to a specific address on all deployed pools.
 * Usage:
 *   OPERATOR_ADDRESS=0x... npx hardhat run scripts/grant-operator-role.ts --network localhost
 */
async function main() {
  const [deployer] = await ethers.getSigners();
  const operatorAddress = process.env.OPERATOR_ADDRESS;
  
  if (!operatorAddress) {
    throw new Error("OPERATOR_ADDRESS environment variable is required");
  }

  // Load deployments
  const deploymentsPath = path.join(__dirname, "..", "deployments", `${network.name}.factory-funds.json`);
  if (!fs.existsSync(deploymentsPath)) {
    throw new Error(`Deployments file not found: ${deploymentsPath}`);
  }

  const deployments = JSON.parse(fs.readFileSync(deploymentsPath, "utf-8"));
  const funds = deployments.funds || [];
  
  // Also check if we need to load from contracts key (deploy.ts format)
  if (funds.length === 0 && deployments.contracts) {
    console.log("⚠️  No funds found, but contracts found. This script requires factory-funds.json format.");
    process.exit(1);
  }

  if (funds.length === 0) {
    throw new Error("No funds found in deployments file");
  }

  console.log("Network:", network.name);
  console.log("Deployer:", deployer.address);
  console.log("Operator address:", operatorAddress);
  console.log("");

  const LiquidityPool = await ethers.getContractFactory("LiquidityPool");

  for (const fund of funds) {
    if (!fund.pool) {
      console.log(`⚠️  Skipping fund ${fund.name || fund.id}: no pool address`);
      continue;
    }

    const pool = LiquidityPool.attach(fund.pool);
    const OPERATOR_ROLE_HASH = await pool.OPERATOR_ROLE();

    // Check if already has role
    const hasRole = await pool.hasRole(OPERATOR_ROLE_HASH, operatorAddress);
    if (hasRole) {
      console.log(`✓ ${fund.name || fund.id}: Operator already has OPERATOR_ROLE`);
      continue;
    }

    // Grant role
    try {
      const tx = await pool.grantRole(OPERATOR_ROLE_HASH, operatorAddress);
      await tx.wait();
      console.log(`✓ ${fund.name || fund.id}: Granted OPERATOR_ROLE to ${operatorAddress}`);
    } catch (error: any) {
      console.error(`✗ ${fund.name || fund.id}: Failed to grant role:`, error.message);
    }
  }

  console.log("\n✅ Done!");
}

main().catch((err) => {
  console.error(err);
  process.exitCode = 1;
});
