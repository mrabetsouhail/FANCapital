import { ethers } from "hardhat";
import * as fs from "fs";
import * as path from "path";

async function main() {
  const ORACLE_KEY_ADDRESS = process.env.ORACLE_KEY_ADDRESS;
  
  if (!ORACLE_KEY_ADDRESS || !ethers.isAddress(ORACLE_KEY_ADDRESS)) {
    throw new Error("ORACLE_KEY_ADDRESS environment variable must be set to a valid Ethereum address");
  }

  console.log("Granting ORACLE_ROLE to:", ORACLE_KEY_ADDRESS);

  // Try to load deployments from localhost.json or localhost.factory-funds.json
  const deploymentsDir = path.join(__dirname, "..", "deployments");
  let priceOracleAddr: string | null = null;

  const files = ["localhost.json", "localhost.factory-funds.json", "localhost.council-funds.json"];
  for (const file of files) {
    const filePath = path.join(deploymentsDir, file);
    if (fs.existsSync(filePath)) {
      const content = JSON.parse(fs.readFileSync(filePath, "utf-8"));
      // PriceOracle might be in infra or per-fund
      priceOracleAddr = content.contracts?.PriceOracle || content.infra?.PriceOracle || null;
      if (priceOracleAddr) {
        console.log(`Found PriceOracle at ${priceOracleAddr} in ${file}`);
        break;
      }
    }
  }

  if (!priceOracleAddr) {
    throw new Error("PriceOracle address not found in deployments. Ensure contracts are deployed.");
  }

  const [deployer] = await ethers.getSigners();
  console.log("Using deployer:", deployer.address);

  const PriceOracle = await ethers.getContractAt("PriceOracle", priceOracleAddr);
  
  // Get ORACLE_ROLE
  const ORACLE_ROLE = await PriceOracle.ORACLE_ROLE();
  console.log("ORACLE_ROLE:", ORACLE_ROLE);

  // Check if role is already granted
  const hasRole = await PriceOracle.hasRole(ORACLE_ROLE, ORACLE_KEY_ADDRESS);
  if (hasRole) {
    console.log("✓ ORACLE_ROLE already granted to", ORACLE_KEY_ADDRESS);
    return;
  }

  // Grant role
  console.log("Granting ORACLE_ROLE...");
  const tx = await PriceOracle.grantRole(ORACLE_ROLE, ORACLE_KEY_ADDRESS);
  console.log("Transaction hash:", tx.hash);
  await tx.wait();
  
  console.log("✓ ORACLE_ROLE granted successfully to", ORACLE_KEY_ADDRESS);
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });
