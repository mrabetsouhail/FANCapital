import { ethers, network } from "hardhat";
import fs from "node:fs";
import path from "node:path";

/**
 * Mint TND tokens to a specific address (user wallet or pool).
 * Usage:
 *   TARGET_ADDRESS=0x... npx hardhat run scripts/mint-tnd.ts --network localhost
 *   TARGET_ADDRESS=0x... AMOUNT_TND=5000 npx hardhat run scripts/mint-tnd.ts --network localhost
 */
async function main() {
  const [deployer] = await ethers.getSigners();
  const targetAddress = process.env.TARGET_ADDRESS;
  const amountTnd = process.env.AMOUNT_TND ? parseFloat(process.env.AMOUNT_TND) : 5000;

  if (!targetAddress) {
    throw new Error("TARGET_ADDRESS environment variable is required");
  }

  // Validate address format
  if (!targetAddress.startsWith("0x") || targetAddress.length !== 42) {
    throw new Error(`Invalid address format: ${targetAddress}. Must be a valid Ethereum address (0x followed by 40 hex characters)`);
  }

  // Validate address checksum
  try {
    ethers.getAddress(targetAddress); // This will throw if invalid
  } catch (error) {
    throw new Error(`Invalid Ethereum address: ${targetAddress}. ${error instanceof Error ? error.message : String(error)}`);
  }

  // Load deployments
  const deploymentsPath = path.join(__dirname, "..", "deployments", `${network.name}.factory-funds.json`);
  if (!fs.existsSync(deploymentsPath)) {
    throw new Error(`Deployments file not found: ${deploymentsPath}`);
  }

  const deployments = JSON.parse(fs.readFileSync(deploymentsPath, "utf-8"));
  const cashTokenAddress = deployments.infra?.CashTokenTND;

  if (!cashTokenAddress) {
    throw new Error("CashTokenTND address not found in deployments file");
  }

  console.log("Network:", network.name);
  console.log("Deployer:", deployer.address);
  console.log("CashTokenTND:", cashTokenAddress);
  console.log("Target address:", targetAddress);
  console.log("Amount:", amountTnd, "TND");
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
    console.log("✓ Granted MINTER_ROLE to deployer");
  }

  // Check current balance
  const currentBalance = await cashToken.balanceOf(targetAddress);
  console.log(`Current balance: ${ethers.formatUnits(currentBalance, 8)} TND`);

  // Mint amount (8 decimals)
  const amountWei = ethers.parseUnits(amountTnd.toString(), 8);

  // Mint
  console.log(`\nMinting ${ethers.formatUnits(amountWei, 8)} TND to ${targetAddress}...`);
  const tx = await cashToken.mint(targetAddress, amountWei);
  await tx.wait();

  // Check new balance
  const newBalance = await cashToken.balanceOf(targetAddress);
  console.log(`New balance: ${ethers.formatUnits(newBalance, 8)} TND`);

  console.log("\n✅ Done!");
  console.log(`Transaction hash: ${tx.hash}`);
}

main().catch((err) => {
  console.error(err);
  process.exitCode = 1;
});
