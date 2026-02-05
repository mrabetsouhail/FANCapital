import { ethers, network } from "hardhat";
import fs from "node:fs";
import path from "node:path";

/**
 * Check VNI status for all funds and initialize if needed.
 * Usage:
 *   npx hardhat run scripts/check-oracle-vni.ts --network localhost
 */
async function main() {
  const [deployer] = await ethers.getSigners();

  // Load deployments
  const deploymentsPath = path.join(__dirname, "..", "deployments", `${network.name}.factory-funds.json`);
  if (!fs.existsSync(deploymentsPath)) {
    throw new Error(`Deployments file not found: ${deploymentsPath}`);
  }

  const deployments = JSON.parse(fs.readFileSync(deploymentsPath, "utf-8"));
  const funds = deployments.funds || [];

  if (funds.length === 0) {
    throw new Error("No funds found in deployments file");
  }

  console.log("Network:", network.name);
  console.log("Deployer:", deployer.address);
  console.log("");

  const PriceOracle = await ethers.getContractFactory("PriceOracle");

  for (const fund of funds) {
    if (!fund.oracle || !fund.token) {
      console.log(`⚠️  Skipping fund ${fund.name || fund.id}: missing oracle or token`);
      continue;
    }

    const oracle = PriceOracle.attach(fund.oracle);
    const [vni, updatedAt] = await oracle.getVNIData(fund.token);

    console.log(`\n${fund.name || fund.id}:`);
    console.log(`  Token: ${fund.token}`);
    console.log(`  Oracle: ${fund.oracle}`);
    console.log(`  VNI: ${vni.toString()} (1e8 scale)`);
    console.log(`  Updated at: ${updatedAt.toString()} (${updatedAt.toString() === "0" ? "NOT INITIALIZED" : new Date(Number(updatedAt) * 1000).toISOString()})`);

    if (vni.toString() === "0") {
      console.log(`  ⚠️  VNI not initialized! Initializing with default values...`);
      
      // Default VNI values (from application.yml)
      const defaultVni = fund.name?.toLowerCase().includes("atlas") || fund.id === 0 
        ? ethers.parseUnits("10", 8)  // 10 TND for Atlas
        : ethers.parseUnits("5", 8);   // 5 TND for Didon

      try {
        // Check if deployer has ORACLE_ROLE
        const ORACLE_ROLE = await oracle.ORACLE_ROLE();
        const hasRole = await oracle.hasRole(ORACLE_ROLE, deployer.address);
        
        if (!hasRole) {
          console.log(`  ✗ Deployer does not have ORACLE_ROLE. Granting...`);
          const tx1 = await oracle.grantRole(ORACLE_ROLE, deployer.address);
          await tx1.wait();
          console.log(`  ✓ Granted ORACLE_ROLE to deployer`);
        }

        const tx2 = await oracle.updateVNI(fund.token, defaultVni);
        await tx2.wait();
        console.log(`  ✓ Initialized VNI to ${ethers.formatUnits(defaultVni, 8)} TND`);
      } catch (error: any) {
        console.error(`  ✗ Failed to initialize VNI:`, error.message);
      }
    } else {
      console.log(`  ✓ VNI is initialized`);
    }
  }

  console.log("\n✅ Done!");
}

main().catch((err) => {
  console.error(err);
  process.exitCode = 1;
});
