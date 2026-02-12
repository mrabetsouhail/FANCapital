import { ethers } from "hardhat";
import * as fs from "fs";
import * as path from "path";

async function main() {
  // Adresse à laquelle accorder KYC_VALIDATOR_ROLE (celle qui signe addToWhitelist).
  // Priorité : ONBOARDING_KEY_ADDRESS > OPERATOR_PRIVATE_KEY (fallback, car bootstrap utilise operator)
  let TARGET_ADDRESS = process.env.ONBOARDING_KEY_ADDRESS;
  if (!TARGET_ADDRESS || !ethers.isAddress(TARGET_ADDRESS)) {
    const OPERATOR_PK = process.env.OPERATOR_PRIVATE_KEY;
    if (OPERATOR_PK && OPERATOR_PK.startsWith("0x") && OPERATOR_PK.length === 66) {
      TARGET_ADDRESS = new ethers.Wallet(OPERATOR_PK.trim()).address;
      console.log("Derived from OPERATOR_PRIVATE_KEY:", TARGET_ADDRESS);
    } else {
      throw new Error("Set ONBOARDING_KEY_ADDRESS or OPERATOR_PRIVATE_KEY");
    }
  }
  console.log("Granting KYC_VALIDATOR_ROLE to:", TARGET_ADDRESS);

  const deploymentsDir = path.join(__dirname, "..", "deployments");
  let kycRegistryAddr: string | null = null;
  const files = ["localhost.factory-funds.json", "localhost.json", "localhost.council-funds.json"];
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

  const code = await ethers.provider.getCode(kycRegistryAddr);
  if (!code || code === "0x" || code.length <= 2) {
    throw new Error(`No contract at ${kycRegistryAddr}. Redeploy with: npm run deploy:factory-funds:localhost`);
  }

  const KYCRegistry = await ethers.getContractAt("KYCRegistry", kycRegistryAddr);
  const KYC_VALIDATOR_ROLE = ethers.keccak256(ethers.toUtf8Bytes("KYC_VALIDATOR_ROLE"));
  console.log("KYC_VALIDATOR_ROLE:", KYC_VALIDATOR_ROLE);

  const hasRole = await KYCRegistry.hasRole(KYC_VALIDATOR_ROLE, TARGET_ADDRESS);
  if (hasRole) {
    console.log("✓ KYC_VALIDATOR_ROLE already granted to", TARGET_ADDRESS);
    return;
  }

  console.log("Granting KYC_VALIDATOR_ROLE...");
  const tx = await KYCRegistry.grantRole(KYC_VALIDATOR_ROLE, TARGET_ADDRESS);
  console.log("Transaction hash:", tx.hash);
  await tx.wait();
  console.log("✓ KYC_VALIDATOR_ROLE granted successfully to", TARGET_ADDRESS);
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });
