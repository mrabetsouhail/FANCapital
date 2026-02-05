import { ethers, network } from "hardhat";
import fs from "node:fs";
import path from "node:path";

/**
 * Check wallet balance (TND and CPEF tokens).
 * Usage:
 *   USER_ADDRESS=0x... npx hardhat run scripts/check-wallet-balance.ts --network localhost
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

  console.log("Network:", network.name);
  console.log("User address:", userAddress);
  console.log("");

  const CashTokenTND = await ethers.getContractFactory("CashTokenTND");
  const cashToken = CashTokenTND.attach(cashTokenAddress);

  // Check TND balance
  const tndBalance = await cashToken.balanceOf(userAddress);
  console.log("=== Cash Wallet (TND) ===");
  console.log(`Balance: ${ethers.formatUnits(tndBalance, 8)} TND`);
  console.log(`Raw (1e8): ${tndBalance.toString()}`);
  console.log("");

  // Check CPEF token balances
  if (funds.length > 0) {
    console.log("=== CPEF Tokens ===");
    for (const fund of funds) {
      if (!fund.token) continue;
      
      const CPEFToken = await ethers.getContractFactory("CPEFToken");
      const token = CPEFToken.attach(fund.token);
      
      const balance = await token.balanceOf(userAddress);
      const prm = await token.getPRM(userAddress);
      
      console.log(`${fund.name || fund.id}:`);
      console.log(`  Token: ${fund.token}`);
      console.log(`  Balance: ${ethers.formatUnits(balance, 8)} tokens`);
      console.log(`  PRM: ${ethers.formatUnits(prm, 8)} TND per token`);
      console.log("");
    }
  }

  // Check native ETH balance (for gas)
  const ethBalance = await ethers.provider.getBalance(userAddress);
  console.log("=== Native ETH (for gas) ===");
  console.log(`Balance: ${ethers.formatEther(ethBalance)} ETH`);
  console.log("");

  console.log("✅ Done!");
}

main().catch((err) => {
  console.error(err);
  process.exitCode = 1;
});
