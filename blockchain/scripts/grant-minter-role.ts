import { ethers } from "hardhat";
import * as fs from "fs";
import * as path from "path";

async function main() {
  // Adresse à laquelle accorder MINTER_ROLE (celle qui signe les mint).
  // Priorité : MINT_KEY_ADDRESS > MINT_PRIVATE_KEY > OPERATOR_PRIVATE_KEY (fallback)
  let MINT_KEY_ADDRESS = process.env.MINT_KEY_ADDRESS;
  if (!MINT_KEY_ADDRESS || !ethers.isAddress(MINT_KEY_ADDRESS)) {
    const MINT_PK = process.env.MINT_PRIVATE_KEY;
    const OPERATOR_PK = process.env.OPERATOR_PRIVATE_KEY;
    if (MINT_PK && MINT_PK.startsWith("0x") && MINT_PK.length === 66) {
      MINT_KEY_ADDRESS = new ethers.Wallet(MINT_PK.trim()).address;
      console.log("Derived address from MINT_PRIVATE_KEY:", MINT_KEY_ADDRESS);
    } else if (OPERATOR_PK && OPERATOR_PK.startsWith("0x") && OPERATOR_PK.length === 66) {
      MINT_KEY_ADDRESS = new ethers.Wallet(OPERATOR_PK.trim()).address;
      console.log("Derived address from OPERATOR_PRIVATE_KEY (fallback):", MINT_KEY_ADDRESS);
    } else {
      throw new Error(
        "Set MINT_KEY_ADDRESS, MINT_PRIVATE_KEY (recommended), or OPERATOR_PRIVATE_KEY as fallback"
      );
    }
  }
  console.log("Granting MINTER_ROLE to:", MINT_KEY_ADDRESS);

  // Try to load deployments from localhost.json or localhost.factory-funds.json
  const deploymentsDir = path.join(__dirname, "..", "deployments");
  let cashTokenAddr: string | null = null;

  // Même priorité que le backend : factory-funds d'abord
  const files = ["localhost.factory-funds.json", "localhost.json", "localhost.council-funds.json"];
  for (const file of files) {
    const filePath = path.join(deploymentsDir, file);
    if (fs.existsSync(filePath)) {
      const content = JSON.parse(fs.readFileSync(filePath, "utf-8"));
      cashTokenAddr = content.contracts?.CashTokenTND || content.infra?.CashTokenTND || null;
      if (cashTokenAddr) {
        console.log(`Found CashTokenTND at ${cashTokenAddr} in ${file}`);
        break;
      }
    }
  }

  if (!cashTokenAddr) {
    throw new Error("CashTokenTND address not found in deployments. Ensure contracts are deployed.");
  }

  const [deployer] = await ethers.getSigners();
  console.log("Using deployer:", deployer.address);

  // Vérifier que le contrat existe (évite erreur BAD_DATA si chaîne réinitialisée)
  const provider = ethers.provider;
  const code = await provider.getCode(cashTokenAddr);
  if (!code || code === "0x" || code.length <= 2) {
    throw new Error(
      `No contract at ${cashTokenAddr}. Chain may have been reset. ` +
      "Redeploy with: npm run deploy:factory-funds:localhost"
    );
  }

  const CashTokenTND = await ethers.getContractAt("CashTokenTND", cashTokenAddr);
  // keccak256("MINTER_ROLE") - valeur standard OpenZeppelin AccessControl (évite appel MINTER_ROLE() qui peut échouer)
  const MINTER_ROLE = ethers.keccak256(ethers.toUtf8Bytes("MINTER_ROLE"));
  console.log("MINTER_ROLE:", MINTER_ROLE);

  // Check if role is already granted
  const hasRole = await CashTokenTND.hasRole(MINTER_ROLE, MINT_KEY_ADDRESS);
  if (hasRole) {
    console.log("✓ MINTER_ROLE already granted to", MINT_KEY_ADDRESS);
    return;
  }

  // Grant role
  console.log("Granting MINTER_ROLE...");
  const tx = await CashTokenTND.grantRole(MINTER_ROLE, MINT_KEY_ADDRESS);
  console.log("Transaction hash:", tx.hash);
  await tx.wait();
  
  console.log("✓ MINTER_ROLE granted successfully to", MINT_KEY_ADDRESS);
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });
