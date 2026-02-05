import { ethers } from "hardhat";
import * as fs from "fs";
import * as path from "path";

async function main() {
  const BURN_KEY_ADDRESS = process.env.BURN_KEY_ADDRESS;
  
  if (!BURN_KEY_ADDRESS || !ethers.isAddress(BURN_KEY_ADDRESS)) {
    throw new Error("BURN_KEY_ADDRESS environment variable must be set to a valid Ethereum address");
  }

  console.log("Granting BURNER_ROLE to:", BURN_KEY_ADDRESS);

  // Try to load deployments from localhost.json or localhost.factory-funds.json
  const deploymentsDir = path.join(__dirname, "..", "deployments");
  let cashTokenAddr: string | null = null;

  const files = ["localhost.json", "localhost.factory-funds.json", "localhost.council-funds.json"];
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

  const CashTokenTND = await ethers.getContractAt("CashTokenTND", cashTokenAddr);
  
  // Get BURNER_ROLE
  const BURNER_ROLE = await CashTokenTND.BURNER_ROLE();
  console.log("BURNER_ROLE:", BURNER_ROLE);

  // Check if role is already granted
  const hasRole = await CashTokenTND.hasRole(BURNER_ROLE, BURN_KEY_ADDRESS);
  if (hasRole) {
    console.log("✓ BURNER_ROLE already granted to", BURN_KEY_ADDRESS);
    return;
  }

  // Grant role
  console.log("Granting BURNER_ROLE...");
  const tx = await CashTokenTND.grantRole(BURNER_ROLE, BURN_KEY_ADDRESS);
  console.log("Transaction hash:", tx.hash);
  await tx.wait();
  
  console.log("✓ BURNER_ROLE granted successfully to", BURN_KEY_ADDRESS);
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });
