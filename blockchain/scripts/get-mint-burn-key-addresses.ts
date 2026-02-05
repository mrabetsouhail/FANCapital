import { ethers } from "hardhat";

/**
 * Script pour obtenir les adresses des Mint Key et Burn Key à partir de leurs clés privées.
 * 
 * Usage:
 *   MINT_PRIVATE_KEY=0x... BURN_PRIVATE_KEY=0x... npx hardhat run scripts/get-mint-burn-key-addresses.ts
 * 
 * Ou directement (utilise les clés Hardhat de test):
 *   npx hardhat run scripts/get-mint-burn-key-addresses.ts
 */
async function main() {
  const MINT_PRIVATE_KEY = process.env.MINT_PRIVATE_KEY || "0x5de4111afa1a4b94908f83103eb1f1706367c2e68ca870fc3fb9a804cdab365a";
  const BURN_PRIVATE_KEY = process.env.BURN_PRIVATE_KEY || "0x7c852118294e51e653712a81e05800f419141751be58f605c371e15141b007a6";
  
  if (!MINT_PRIVATE_KEY.startsWith("0x") || MINT_PRIVATE_KEY.length !== 66) {
    throw new Error("MINT_PRIVATE_KEY must be a valid hex private key (0x + 64 chars)");
  }
  if (!BURN_PRIVATE_KEY.startsWith("0x") || BURN_PRIVATE_KEY.length !== 66) {
    throw new Error("BURN_PRIVATE_KEY must be a valid hex private key (0x + 64 chars)");
  }

  const mintWallet = new ethers.Wallet(MINT_PRIVATE_KEY);
  const burnWallet = new ethers.Wallet(BURN_PRIVATE_KEY);
  
  console.log("=".repeat(60));
  console.log("MINT KEY & BURN KEY INFORMATION");
  console.log("=".repeat(60));
  console.log("Mint Key Private Key:", MINT_PRIVATE_KEY);
  console.log("Mint Key Address:    ", mintWallet.address);
  console.log("");
  console.log("Burn Key Private Key:", BURN_PRIVATE_KEY);
  console.log("Burn Key Address:    ", burnWallet.address);
  console.log("=".repeat(60));
  console.log("\nNext steps:");
  console.log("1. Add these to IntelliJ IDEA environment variables:");
  console.log(`   MINT_PRIVATE_KEY=${MINT_PRIVATE_KEY}`);
  console.log(`   BURN_PRIVATE_KEY=${BURN_PRIVATE_KEY}`);
  console.log("\n2. Grant MINTER_ROLE to Mint Key address:");
  console.log(`   $env:MINT_KEY_ADDRESS='${mintWallet.address}'`);
  console.log("   npm run hardhat run scripts/grant-minter-role.ts --network localhost");
  console.log("\n3. Grant BURNER_ROLE to Burn Key address:");
  console.log(`   $env:BURN_KEY_ADDRESS='${burnWallet.address}'`);
  console.log("   npm run hardhat run scripts/grant-burner-role.ts --network localhost");
  console.log("\n");
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });
