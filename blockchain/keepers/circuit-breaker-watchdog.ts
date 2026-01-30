import { argValue, getContract, loadDeploymentsOrThrow, loadJson, resolvePath } from "./common";

type WatchdogConfig = {
  deploymentsPath?: string;
  tokens: string[];
  // Optional: override threshold (bps). If omitted, uses CircuitBreaker.thresholdBps().
  thresholdBps?: number;
  // If true, prints extra details.
  verbose?: boolean;
};

async function main() {
  const configPath = argValue("--config", "keepers/circuit-breaker-watchdog.example.json");
  const cfg = loadJson<WatchdogConfig>(resolvePath(configPath!));

  const dep = loadDeploymentsOrThrow(cfg.deploymentsPath);
  const poolAddr = dep.contracts.LiquidityPool;
  const cbAddr = dep.contracts.CircuitBreaker;
  if (!poolAddr) throw new Error("Missing LiquidityPool address in deployments file");
  if (!cbAddr) throw new Error("Missing CircuitBreaker address in deployments file");

  const pool = getContract("LiquidityPool", poolAddr);
  const cb = getContract("CircuitBreaker", cbAddr);

  const threshold = cfg.thresholdBps ?? Number(await cb.thresholdBps());
  console.log(`Network=${dep.network} Pool=${poolAddr} ThresholdBps=${threshold}`);

  for (const token of cfg.tokens) {
    try {
      const ratio = Number(await pool.getReserveRatioBps(token));
      if (cfg.verbose) console.log(`token=${token} reserveRatioBps=${ratio}`);

      if (ratio < threshold) {
        const tx = await pool.checkAndTripRedemptions(token);
        const receipt = await tx.wait();
        console.log(`TRIP token=${token} ratioBps=${ratio} tx=${tx.hash} gas=${receipt?.gasUsed?.toString?.() ?? "?"}`);
      }
    } catch (e: any) {
      const msg = e?.shortMessage ?? e?.message ?? String(e);
      console.warn(`WARN token=${token} error=${msg}`);
    }
  }
}

main().catch((err) => {
  console.error(err);
  process.exitCode = 1;
});

