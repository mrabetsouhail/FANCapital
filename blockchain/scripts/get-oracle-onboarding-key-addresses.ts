import { ethers } from "hardhat";

/**
 * Script pour obtenir les adresses des Oracle Key et Onboarding Key à partir de leurs clés privées.
 * 
 * Usage:
 *   ORACLE_PRIVATE_KEY=0x... ONBOARDING_PRIVATE_KEY=0x... npx hardhat run scripts/get-oracle-onboarding-key-addresses.ts
 * 
 * Ou directement (utilise les clés Hardhat de test):
 *   npx hardhat run scripts/get-oracle-onboarding-key-addresses.ts
 */
async function main() {
  // Use Hardhat test keys #4 and #5
  const ORACLE_PRIVATE_KEY = process.env.ORACLE_PRIVATE_KEY || "0x47e179ec197488593b187f80a00eb0da91f1b9d0b13f8733639f19c30a34926a";
  const ONBOARDING_PRIVATE_KEY = process.env.ONBOARDING_PRIVATE_KEY || "0x8b3a350cf5c34c9194ca85829a2df0ec3153be0318b5e2d3348e872092edffba";
  
  if (!ORACLE_PRIVATE_KEY.startsWith("0x") || ORACLE_PRIVATE_KEY.length !== 66) {
    throw new Error("ORACLE_PRIVATE_KEY must be a valid hex private key (0x + 64 chars)");
  }
  if (!ONBOARDING_PRIVATE_KEY.startsWith("0x") || ONBOARDING_PRIVATE_KEY.length !== 66) {
    throw new Error("ONBOARDING_PRIVATE_KEY must be a valid hex private key (0x + 64 chars)");
  }

  const oracleWallet = new ethers.Wallet(ORACLE_PRIVATE_KEY);
  const onboardingWallet = new ethers.Wallet(ONBOARDING_PRIVATE_KEY);
  
  console.log("=".repeat(60));
  console.log("ORACLE KEY & ONBOARDING KEY INFORMATION");
  console.log("=".repeat(60));
  console.log("Oracle Key Private Key:", ORACLE_PRIVATE_KEY);
  console.log("Oracle Key Address:    ", oracleWallet.address);
  console.log("");
  console.log("Onboarding Key Private Key:", ONBOARDING_PRIVATE_KEY);
  console.log("Onboarding Key Address:    ", onboardingWallet.address);
  console.log("=".repeat(60));
  console.log("\nNext steps:");
  console.log("1. Add these to IntelliJ IDEA environment variables:");
  console.log(`   ORACLE_PRIVATE_KEY=${ORACLE_PRIVATE_KEY}`);
  console.log(`   ONBOARDING_PRIVATE_KEY=${ONBOARDING_PRIVATE_KEY}`);
  console.log("\n2. Grant ORACLE_ROLE to Oracle Key address:");
  console.log(`   $env:ORACLE_KEY_ADDRESS='${oracleWallet.address}'`);
  console.log("   npm run hardhat run scripts/grant-oracle-role.ts --network localhost");
  console.log("\n3. Grant KYC_VALIDATOR_ROLE to Onboarding Key address:");
  console.log(`   $env:ONBOARDING_KEY_ADDRESS='${onboardingWallet.address}'`);
  console.log("   npm run hardhat run scripts/grant-kyc-validator-role.ts --network localhost");
  console.log("\n");
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });
