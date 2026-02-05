import { ethers, network } from "hardhat";
import fs from "node:fs";
import path from "node:path";

/**
 * Whitelist a user in KYCRegistry (required for buying/selling CPEF tokens).
 * Green List (KYC1) can only buy/sell via liquidity pool, not P2P.
 * Usage:
 *   USER_ADDRESS=0x... KYC_LEVEL=1 IS_RESIDENT=true npx hardhat run scripts/whitelist-user.ts --network localhost
 */
async function main() {
  const [deployer] = await ethers.getSigners();
  const userAddress = process.env.USER_ADDRESS;
  const kycLevel = process.env.KYC_LEVEL ? parseInt(process.env.KYC_LEVEL) : 1; // Default to Green List (KYC1)
  const isResident = process.env.IS_RESIDENT ? process.env.IS_RESIDENT.toLowerCase() === "true" : true;

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

  // Validate KYC level
  if (kycLevel !== 1 && kycLevel !== 2) {
    throw new Error(`Invalid KYC level: ${kycLevel}. Must be 1 (Green List) or 2 (White List)`);
  }

  // Load deployments
  const deploymentsPath = path.join(__dirname, "..", "deployments", `${network.name}.factory-funds.json`);
  if (!fs.existsSync(deploymentsPath)) {
    throw new Error(`Deployments file not found: ${deploymentsPath}`);
  }

  const deployments = JSON.parse(fs.readFileSync(deploymentsPath, "utf-8"));
  const kycRegistryAddress = deployments.infra?.KYCRegistry;

  if (!kycRegistryAddress) {
    throw new Error("KYCRegistry address not found in deployments file");
  }

  console.log("Network:", network.name);
  console.log("Deployer:", deployer.address);
  console.log("KYCRegistry:", kycRegistryAddress);
  console.log("User address:", userAddress);
  console.log("KYC Level:", kycLevel, kycLevel === 1 ? "(Green List - Pool only)" : "(White List - Pool + P2P)");
  console.log("Is Resident:", isResident);
  console.log("");

  const KYCRegistry = await ethers.getContractFactory("KYCRegistry");
  const kycRegistry = KYCRegistry.attach(kycRegistryAddress);

  // Check if user is already whitelisted
  const isWhitelisted = await kycRegistry.isWhitelisted(userAddress);
  const currentLevel = await kycRegistry.getUserLevel(userAddress);
  const currentResident = await kycRegistry.isResident(userAddress);

  console.log("Current status:");
  console.log(`  Whitelisted: ${isWhitelisted}`);
  console.log(`  Level: ${currentLevel} (0 = none, 1 = Green, 2 = White)`);
  console.log(`  Resident: ${currentResident}`);
  console.log("");

  if (isWhitelisted && currentLevel === kycLevel && currentResident === isResident) {
    console.log("✓ User is already whitelisted with the same level and residency status.");
    return;
  }

  // Check if deployer has KYC_VALIDATOR_ROLE
  const KYC_VALIDATOR_ROLE = await kycRegistry.KYC_VALIDATOR_ROLE();
  const hasRole = await kycRegistry.hasRole(KYC_VALIDATOR_ROLE, deployer.address);

  if (!hasRole) {
    console.log("⚠️  Deployer does not have KYC_VALIDATOR_ROLE. Granting...");
    const txGrant = await kycRegistry.grantRole(KYC_VALIDATOR_ROLE, deployer.address);
    await txGrant.wait();
    console.log("✓ Granted KYC_VALIDATOR_ROLE to deployer\n");
  }

  // Whitelist user
  console.log(`Adding user to whitelist (Level ${kycLevel}, Resident: ${isResident})...`);
  const tx = await kycRegistry.addToWhitelist(userAddress, kycLevel, isResident);
  await tx.wait();

  // Verify
  const newIsWhitelisted = await kycRegistry.isWhitelisted(userAddress);
  const newLevel = await kycRegistry.getUserLevel(userAddress);
  const newResident = await kycRegistry.isResident(userAddress);

  console.log("\n✓ User whitelisted successfully!");
  console.log(`Transaction: ${tx.hash}`);
  console.log("\nNew status:");
  console.log(`  Whitelisted: ${newIsWhitelisted}`);
  console.log(`  Level: ${newLevel} (${newLevel === 1 ? "Green List - Pool only" : newLevel === 2 ? "White List - Pool + P2P" : "None"})`);
  console.log(`  Resident: ${newResident}`);

  if (kycLevel === 1) {
    console.log("\n⚠️  Note: Green List (KYC1) users can only buy/sell via liquidity pool, not P2P.");
  }

  console.log("\n✅ Done!");
}

main().catch((err) => {
  console.error(err);
  process.exitCode = 1;
});
