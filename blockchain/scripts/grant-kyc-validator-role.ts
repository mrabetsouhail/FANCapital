import { ethers } from "hardhat";
import * as fs from "fs";
import * as path from "path";

async function main() {
  const ONBOARDING_KEY_ADDRESS = process.env.ONBOARDING_KEY_ADDRESS;
  
  if (!ONBOARDING_KEY_ADDRESS || !ethers.isAddress(ONBOARDING_KEY_ADDRESS)) {
    throw new Error("ONBOARDING_KEY_ADDRESS environment variable must be set to a valid Ethereum address");
  }

  console.log("Granting KYC_VALIDATOR_ROLE to:", ONBOARDING_KEY_ADDRESS);

  // Try to load deployments from localhost.json or localhost.factory-funds.json
  const deploymentsDir = path.join(__dirname, "..", "deployments");
  let kycRegistryAddr: string | null = null;

  const files = ["localhost.json", "localhost.factory-funds.json", "localhost.council-funds.json"];
  for (const file of files) {
    const filePath = path.join(deploymentsDir, file);
    if (fs.existsSync(filePath)) {
      const content = JSON.parse(fs.readFileSync(filePath, "utf-8"));
      kycRegistryAddr = content.contracts?.KYCRegistry || content.infra?.KYCRegistry || null;
      if (kycRegistryAddr) {
        console.log(`Found KYCRegistry at ${kycRegistryAddr} in ${file}`);
        break;
      }
    }
  }

  if (!kycRegistryAddr) {
    throw new Error("KYCRegistry address not found in deployments. Ensure contracts are deployed.");
  }

  const [deployer] = await ethers.getSigners();
  console.log("Using deployer:", deployer.address);

  const KYCRegistry = await ethers.getContractAt("KYCRegistry", kycRegistryAddr);
  
  // Get KYC_VALIDATOR_ROLE
  const KYC_VALIDATOR_ROLE = await KYCRegistry.KYC_VALIDATOR_ROLE();
  console.log("KYC_VALIDATOR_ROLE:", KYC_VALIDATOR_ROLE);

  // Check if role is already granted
  const hasRole = await KYCRegistry.hasRole(KYC_VALIDATOR_ROLE, ONBOARDING_KEY_ADDRESS);
  if (hasRole) {
    console.log("✓ KYC_VALIDATOR_ROLE already granted to", ONBOARDING_KEY_ADDRESS);
    return;
  }

  // Grant role
  console.log("Granting KYC_VALIDATOR_ROLE...");
  const tx = await KYCRegistry.grantRole(KYC_VALIDATOR_ROLE, ONBOARDING_KEY_ADDRESS);
  console.log("Transaction hash:", tx.hash);
  await tx.wait();
  
  console.log("✓ KYC_VALIDATOR_ROLE granted successfully to", ONBOARDING_KEY_ADDRESS);
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });
