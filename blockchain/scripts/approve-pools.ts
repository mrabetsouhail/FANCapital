import { ethers, network } from "hardhat";
import fs from "node:fs";
import path from "node:path";

/**
 * Approve liquidity pools to spend user's TND tokens.
 * This is required before users can buy CPEF tokens.
 * Usage:
 *   USER_ADDRESS=0x... npx hardhat run scripts/approve-pools.ts --network localhost
 */
async function main() {
  const [deployer] = await ethers.getSigners();
  const userAddress = process.env.USER_ADDRESS;

  if (!userAddress) {
    throw new Error("USER_ADDRESS environment variable is required");
  }

  // Validate address format
  if (!userAddress.startsWith("0x") || userAddress.length !== 42) {
    throw new Error(`Invalid address format: ${userAddress}. Must be a valid Ethereum address (0x followed by 40 hex characters)`);
  }

  // Validate address checksum
  try {
    ethers.getAddress(userAddress); // This will throw if invalid
  } catch (error) {
    throw new Error(`Invalid Ethereum address: ${userAddress}. ${error instanceof Error ? error.message : String(error)}`);
  }

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
  console.log("User address:", userAddress);
  console.log("CashTokenTND:", cashTokenAddress);
  console.log("");

  const CashTokenTND = await ethers.getContractFactory("CashTokenTND");
  const cashToken = CashTokenTND.attach(cashTokenAddress);

  // Check user balance
  const balance = await cashToken.balanceOf(userAddress);
  console.log(`User TND balance: ${ethers.formatUnits(balance, 8)} TND`);
  
  if (balance === 0n) {
    console.log("⚠️  User has no TND balance. Mint some TND first using mint-tnd.ts");
    process.exit(1);
  }

  // Maximum approval (2^256 - 1)
  const MAX_APPROVE = ethers.MaxUint256;

  for (const fund of funds) {
    if (!fund.pool) {
      console.log(`⚠️  Skipping fund ${fund.name || fund.id}: no pool address`);
      continue;
    }

    console.log(`\n${fund.name || fund.id}:`);
    console.log(`  Pool: ${fund.pool}`);

    // Check current allowance
    const currentAllowance = await cashToken.allowance(userAddress, fund.pool);
    console.log(`  Current allowance: ${ethers.formatUnits(currentAllowance, 8)} TND`);

    if (currentAllowance >= balance) {
      console.log(`  ✓ Allowance is sufficient (${ethers.formatUnits(currentAllowance, 8)} TND >= ${ethers.formatUnits(balance, 8)} TND)`);
      continue;
    }

    // Approve
    console.log(`  Approving pool to spend up to ${ethers.formatUnits(MAX_APPROVE, 8)} TND...`);
    
    // Note: This requires the user's private key, not the deployer's
    // For testing, we'll use the deployer's key, but in production this should be done by the user
    console.log(`  ⚠️  WARNING: This script uses the deployer's key. In production, users should approve via their own wallet.`);
    
    // We need to impersonate the user or use their private key
    // For local testing, we can use hardhat's impersonateAccount
    await network.provider.request({
      method: "hardhat_impersonateAccount",
      params: [userAddress],
    });

    const userSigner = await ethers.getSigner(userAddress);
    const cashTokenUser = cashToken.connect(userSigner);

    try {
      const tx = await cashTokenUser.approve(fund.pool, MAX_APPROVE);
      await tx.wait();
      console.log(`  ✓ Approved. Transaction: ${tx.hash}`);

      // Verify
      const newAllowance = await cashToken.allowance(userAddress, fund.pool);
      console.log(`  New allowance: ${ethers.formatUnits(newAllowance, 8)} TND`);
    } catch (error: any) {
      console.error(`  ✗ Failed to approve: ${error.message}`);
      // Stop impersonation
      await network.provider.request({
        method: "hardhat_stopImpersonatingAccount",
        params: [userAddress],
      });
      throw error;
    }

    // Stop impersonation
    await network.provider.request({
      method: "hardhat_stopImpersonatingAccount",
      params: [userAddress],
    });
  }

  console.log("\n✅ Done!");
}

main().catch((err) => {
  console.error(err);
  process.exitCode = 1;
});
