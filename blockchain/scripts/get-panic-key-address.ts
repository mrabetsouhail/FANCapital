import { ethers } from "hardhat";

/**
 * Script pour obtenir l'adresse d'une Panic Key à partir de sa clé privée.
 * 
 * Usage:
 *   PANIC_PRIVATE_KEY=0x... npx hardhat run scripts/get-panic-key-address.ts
 * 
 * Ou directement:
 *   npx hardhat run scripts/get-panic-key-address.ts
 *   (utilisera la clé Hardhat #1 par défaut pour dev)
 */
async function main() {
  const PANIC_PRIVATE_KEY = process.env.PANIC_PRIVATE_KEY || "0x59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d";
  
  if (!PANIC_PRIVATE_KEY.startsWith("0x") || PANIC_PRIVATE_KEY.length !== 66) {
    throw new Error("PANIC_PRIVATE_KEY must be a valid hex private key (0x + 64 chars)");
  }

  const wallet = new ethers.Wallet(PANIC_PRIVATE_KEY);
  
  console.log("=".repeat(60));
  console.log("PANIC KEY INFORMATION");
  console.log("=".repeat(60));
  console.log("Private Key:", PANIC_PRIVATE_KEY);
  console.log("Address:    ", wallet.address);
  console.log("=".repeat(60));
  console.log("\nNext steps:");
  console.log("1. Add this to IntelliJ IDEA environment variables:");
  console.log(`   PANIC_PRIVATE_KEY=${PANIC_PRIVATE_KEY}`);
  console.log("\n2. Grant PANIC_KEY_ROLE to this address:");
  console.log(`   $env:PANIC_KEY_ADDRESS='${wallet.address}'`);
  console.log("   npm run hardhat run scripts/grant-panic-key-role.ts --network localhost");
  console.log("\n");
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });
