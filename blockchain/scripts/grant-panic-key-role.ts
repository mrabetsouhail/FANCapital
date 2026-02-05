import { ethers } from "hardhat";
import * as fs from "fs";
import * as path from "path";

async function main() {
  const PANIC_KEY_ADDRESS = process.env.PANIC_KEY_ADDRESS;
  
  if (!PANIC_KEY_ADDRESS || !ethers.isAddress(PANIC_KEY_ADDRESS)) {
    throw new Error("PANIC_KEY_ADDRESS environment variable must be set to a valid Ethereum address");
  }

  console.log("Granting PANIC_KEY_ROLE to:", PANIC_KEY_ADDRESS);

  // Try to load deployments from localhost.json or localhost.factory-funds.json
  const deploymentsDir = path.join(__dirname, "..", "deployments");
  let circuitBreakerAddr: string | null = null;

  const files = ["localhost.json", "localhost.factory-funds.json", "localhost.council-funds.json"];
  for (const file of files) {
    const filePath = path.join(deploymentsDir, file);
    if (fs.existsSync(filePath)) {
      const content = JSON.parse(fs.readFileSync(filePath, "utf-8"));
      circuitBreakerAddr = content.contracts?.CircuitBreaker || content.infra?.CircuitBreaker || null;
      if (circuitBreakerAddr) {
        console.log(`Found CircuitBreaker at ${circuitBreakerAddr} in ${file}`);
        break;
      }
    }
  }

  if (!circuitBreakerAddr) {
    throw new Error("CircuitBreaker address not found in deployments. Ensure contracts are deployed.");
  }

  const [deployer] = await ethers.getSigners();
  console.log("Using deployer:", deployer.address);

  const CircuitBreaker = await ethers.getContractAt("CircuitBreaker", circuitBreakerAddr);
  
  // Get PANIC_KEY_ROLE
  const PANIC_KEY_ROLE = await CircuitBreaker.PANIC_KEY_ROLE();
  console.log("PANIC_KEY_ROLE:", PANIC_KEY_ROLE);

  // Check if role is already granted
  const hasRole = await CircuitBreaker.hasRole(PANIC_KEY_ROLE, PANIC_KEY_ADDRESS);
  if (hasRole) {
    console.log("✓ PANIC_KEY_ROLE already granted to", PANIC_KEY_ADDRESS);
    return;
  }

  // Grant role
  console.log("Granting PANIC_KEY_ROLE...");
  const tx = await CircuitBreaker.grantRole(PANIC_KEY_ROLE, PANIC_KEY_ADDRESS);
  console.log("Transaction hash:", tx.hash);
  await tx.wait();
  
  console.log("✓ PANIC_KEY_ROLE granted successfully to", PANIC_KEY_ADDRESS);
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });
