import { ethers, network } from "hardhat";
import fs from "node:fs";
import path from "node:path";

/**
 * Mint TND tokens for testing:
 * - 5000 TND to user wallet (for buying tokens)
 * - 5000 TND to each pool (for liquidity to allow selling)
 * 
 * Usage:
 *   USER_ADDRESS=0x... npx hardhat run scripts/mint-tnd-for-testing.ts --network localhost
 *   USER_ADDRESS=0x... USER_AMOUNT=5000 POOL_AMOUNT=5000 npx hardhat run scripts/mint-tnd-for-testing.ts --network localhost
 */
async function main() {
  const [deployer] = await ethers.getSigners();
  const userAddress = process.env.USER_ADDRESS;
  const userAmountTnd = process.env.USER_AMOUNT ? parseFloat(process.env.USER_AMOUNT) : 5000;
  const poolAmountTnd = process.env.POOL_AMOUNT ? parseFloat(process.env.POOL_AMOUNT) : 5000;

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

  console.log("Network:", network.name);
  console.log("Deployer:", deployer.address);
  console.log("CashTokenTND:", cashTokenAddress);
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

  // 1. Mint to user wallet (if provided)
  if (userAddress) {
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

    const userAmountWei = ethers.parseUnits(userAmountTnd.toString(), 8);
    const currentBalance = await cashToken.balanceOf(userAddress);
    
    console.log("=== Minting to User Wallet ===");
    console.log(`Address: ${userAddress}`);
    console.log(`Current balance: ${ethers.formatUnits(currentBalance, 8)} TND`);
    console.log(`Minting: ${ethers.formatUnits(userAmountWei, 8)} TND...`);
    
    const txUser = await cashToken.mint(userAddress, userAmountWei);
    await txUser.wait();
    
    const newBalance = await cashToken.balanceOf(userAddress);
    console.log(`New balance: ${ethers.formatUnits(newBalance, 8)} TND`);
    console.log(`Transaction: ${txUser.hash}\n`);
  } else {
    console.log("⚠️  USER_ADDRESS not provided, skipping user wallet mint");
    console.log("   Set USER_ADDRESS=0x... to mint to user wallet\n");
  }

  // 2. Mint to all pools
  if (funds.length > 0) {
    const poolAmountWei = ethers.parseUnits(poolAmountTnd.toString(), 8);
    
    console.log("=== Minting to Liquidity Pools ===");
    console.log(`Amount per pool: ${ethers.formatUnits(poolAmountWei, 8)} TND\n`);
    
    for (const fund of funds) {
      if (!fund.pool) {
        console.log(`⚠️  Skipping fund ${fund.name || fund.id}: no pool address`);
        continue;
      }

      const currentBalance = await cashToken.balanceOf(fund.pool);
      console.log(`${fund.name || fund.id}:`);
      console.log(`  Pool: ${fund.pool}`);
      console.log(`  Current balance: ${ethers.formatUnits(currentBalance, 8)} TND`);

      const tx = await cashToken.mint(fund.pool, poolAmountWei);
      await tx.wait();

      const newBalance = await cashToken.balanceOf(fund.pool);
      console.log(`  New balance: ${ethers.formatUnits(newBalance, 8)} TND`);
      console.log(`  Transaction: ${tx.hash}\n`);
    }
  }

  console.log("✅ Done!");
}

main().catch((err) => {
  console.error(err);
  process.exitCode = 1;
});
