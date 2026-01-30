import { argValue, getContract, loadDeploymentsOrThrow, loadJson, resolvePath } from "./common";

type SubscriptionEntry = {
  user: string;
  // UNIX timestamp in seconds (off-chain source of truth)
  expiresAt?: number;
  // Optional override (forces true/false regardless of expiresAt)
  active?: boolean;
};

type SubscriptionConfig = {
  deploymentsPath?: string;
  // If true, will dry-run without sending txs.
  dryRun?: boolean;
  subscriptions: SubscriptionEntry[];
};

async function main() {
  const configPath = argValue("--config", "keepers/subscription-manager.example.json");
  const cfg = loadJson<SubscriptionConfig>(resolvePath(configPath!));

  const dep = loadDeploymentsOrThrow(cfg.deploymentsPath);
  const invAddr = dep.contracts.InvestorRegistry;
  if (!invAddr) throw new Error("Missing InvestorRegistry address in deployments file");

  const investors = getContract("InvestorRegistry", invAddr);
  const now = Math.floor(Date.now() / 1000);

  console.log(`Network=${dep.network} InvestorRegistry=${invAddr} now=${now} dryRun=${cfg.dryRun ? "yes" : "no"}`);

  for (const s of cfg.subscriptions) {
    const desired =
      s.active !== undefined ? s.active : s.expiresAt !== undefined ? now <= s.expiresAt : false;

    if (cfg.dryRun) {
      console.log(`DRYRUN user=${s.user} desiredActive=${desired}`);
      continue;
    }

    const tx = await investors.setSubscriptionActive(s.user, desired);
    const receipt = await tx.wait();
    console.log(
      `setSubscriptionActive user=${s.user} active=${desired} tx=${tx.hash} gas=${receipt?.gasUsed?.toString?.() ?? "?"}`
    );
  }
}

main().catch((err) => {
  console.error(err);
  process.exitCode = 1;
});

