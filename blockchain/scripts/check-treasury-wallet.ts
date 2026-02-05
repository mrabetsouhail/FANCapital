import { ethers, network } from "hardhat";
import fs from "node:fs";
import path from "node:path";

/**
 * Check treasury wallet balance and configuration.
 * Usage:
 *   npx hardhat run scripts/check-treasury-wallet.ts --network localhost
 */
async function main() {
  const [deployer] = await ethers.getSigners();

  const deploymentsPath = path.join(__dirname, "..", "deployments", `${network.name}.factory-funds.json`);
  if (!fs.existsSync(deploymentsPath)) {
    throw new Error(`Deployments file not found: ${deploymentsPath}`);
  }

  const deployments = JSON.parse(fs.readFileSync(deploymentsPath, "utf-8"));
  const cashTokenAddress = deployments.infra?.CashTokenTND;
  const funds = deployments.funds || [];
  const treasuryAddress = deployments.factory?.treasury;

  if (!cashTokenAddress) {
    throw new Error("CashTokenTND address not found in deployments file");
  }
  if (funds.length === 0) {
    throw new Error("No funds found in deployments file");
  }

  console.log("Network:", network.name);
  console.log("Deployer:", deployer.address);
  console.log("");

  const CashTokenTND = await ethers.getContractFactory("CashTokenTND");
  const cashToken = CashTokenTND.attach(cashTokenAddress);

  // Check treasury address from first pool
  const LiquidityPool = await ethers.getContractFactory("LiquidityPool");
  const firstPool = LiquidityPool.attach(funds[0].pool);
  const treasuryFromPool = await firstPool.treasury();

  console.log("=== Treasury Wallet Configuration ===");
  console.log("Treasury (from factory):", treasuryAddress || "Not set in factory");
  console.log("Treasury (from pool):", treasuryFromPool);
  console.log("");

  // Check treasury balance
  const treasuryBalance = await cashToken.balanceOf(treasuryFromPool);
  console.log("=== Treasury Wallet Balance ===");
  console.log(`Address: ${treasuryFromPool}`);
  console.log(`Balance: ${ethers.formatUnits(treasuryBalance, 8)} TND`);
  console.log(`Raw (1e8): ${treasuryBalance.toString()}`);
  console.log("");

  // Verify all pools use the same treasury
  console.log("=== Treasury Address Verification ===");
  let allMatch = true;
  for (const fund of funds) {
    if (!fund.pool) continue;
    const pool = LiquidityPool.attach(fund.pool);
    const poolTreasury = await pool.treasury();
    const match = poolTreasury.toLowerCase() === treasuryFromPool.toLowerCase();
    console.log(`${fund.name || fund.id}:`);
    console.log(`  Pool: ${fund.pool}`);
    console.log(`  Treasury: ${poolTreasury}`);
    console.log(`  Match: ${match ? '✓' : '✗'}`);
    if (!match) allMatch = false;
  }
  console.log("");

  if (allMatch) {
    console.log("✓ All pools use the same treasury address");
  } else {
    console.log("⚠️  Warning: Not all pools use the same treasury address!");
  }

  console.log("\n✅ Done!");
}

main().catch((err) => {
  console.error(err);
  process.exitCode = 1;
});
