import { ethers, network } from "hardhat";
import fs from "node:fs";
import path from "node:path";

/**
 * Check TaxVault configuration and verify pools are authorized to record RAS.
 * Usage:
 *   npx hardhat run scripts/check-taxvault-config.ts --network localhost
 */
async function main() {
  const [deployer] = await ethers.getSigners();

  // Load deployments
  const deploymentsPath = path.join(__dirname, "..", "deployments", `${network.name}.factory-funds.json`);
  if (!fs.existsSync(deploymentsPath)) {
    throw new Error(`Deployments file not found: ${deploymentsPath}`);
  }

  const deployments = JSON.parse(fs.readFileSync(deploymentsPath, "utf-8"));
  const taxVaultAddress = deployments.infra?.TaxVault;
  const cashTokenAddress = deployments.infra?.CashTokenTND;
  const funds = deployments.funds || [];

  if (!taxVaultAddress) {
    throw new Error("TaxVault address not found in deployments file");
  }

  if (!cashTokenAddress) {
    throw new Error("CashTokenTND address not found in deployments file");
  }

  console.log("Network:", network.name);
  console.log("Deployer:", deployer.address);
  console.log("");

  const TaxVault = await ethers.getContractFactory("TaxVault");
  const CashTokenTND = await ethers.getContractFactory("CashTokenTND");
  const taxVault = TaxVault.attach(taxVaultAddress);
  const cashToken = CashTokenTND.attach(cashTokenAddress);

  // Check TaxVault balance
  const balance = await cashToken.balanceOf(taxVaultAddress);
  console.log("=== TaxVault Configuration ===");
  console.log(`TaxVault address: ${taxVaultAddress}`);
  console.log(`Balance: ${ethers.formatUnits(balance, 8)} TND`);
  
  // Check fiscAddress
  const fiscAddress = await taxVault.fiscAddress();
  console.log(`Fisc address: ${fiscAddress}`);

  // Check authorized callers
  console.log("\n=== Authorized Callers ===");
  for (const fund of funds) {
    if (!fund.pool) continue;
    
    const isAuthorized = await taxVault.isAuthorizedCaller(fund.pool);
    console.log(`${fund.name || fund.id}:`);
    console.log(`  Pool: ${fund.pool}`);
    console.log(`  Authorized: ${isAuthorized ? "✓ Yes" : "✗ No"}`);
    
    if (!isAuthorized) {
      console.log(`  ⚠️  Pool is NOT authorized to call recordRAS!`);
      console.log(`  This means RAS will not be recorded when users sell tokens.`);
    }
    console.log("");
  }

  // Check if deployer has GOVERNANCE_ROLE to authorize pools
  const GOVERNANCE_ROLE = await taxVault.GOVERNANCE_ROLE();
  const hasGovRole = await taxVault.hasRole(GOVERNANCE_ROLE, deployer.address);
  console.log("=== Permissions ===");
  console.log(`Deployer has GOVERNANCE_ROLE: ${hasGovRole ? "✓ Yes" : "✗ No"}`);

  if (!hasGovRole) {
    console.log("⚠️  Deployer cannot authorize pools. Granting GOVERNANCE_ROLE...");
    // Note: This requires DEFAULT_ADMIN_ROLE
    const DEFAULT_ADMIN_ROLE = await taxVault.DEFAULT_ADMIN_ROLE();
    const hasAdminRole = await taxVault.hasRole(DEFAULT_ADMIN_ROLE, deployer.address);
    if (hasAdminRole) {
      const tx = await taxVault.grantRole(GOVERNANCE_ROLE, deployer.address);
      await tx.wait();
      console.log("✓ Granted GOVERNANCE_ROLE to deployer");
    } else {
      console.log("✗ Deployer does not have DEFAULT_ADMIN_ROLE to grant GOVERNANCE_ROLE");
    }
  }

  // Authorize pools if needed
  console.log("\n=== Authorizing Pools ===");
  for (const fund of funds) {
    if (!fund.pool) continue;
    
    const isAuthorized = await taxVault.isAuthorizedCaller(fund.pool);
    if (!isAuthorized) {
      if (hasGovRole || await taxVault.hasRole(GOVERNANCE_ROLE, deployer.address)) {
        console.log(`Authorizing ${fund.name || fund.id} pool...`);
        const tx = await taxVault.setAuthorizedCaller(fund.pool, true);
        await tx.wait();
        console.log(`✓ Authorized. Transaction: ${tx.hash}`);
      } else {
        console.log(`✗ Cannot authorize ${fund.name || fund.id} pool (no GOVERNANCE_ROLE)`);
      }
    } else {
      console.log(`✓ ${fund.name || fund.id} pool is already authorized`);
    }
  }

  console.log("\n✅ Done!");
}

main().catch((err) => {
  console.error(err);
  process.exitCode = 1;
});
