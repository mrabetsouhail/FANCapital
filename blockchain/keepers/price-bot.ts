import { argValue, getContract, loadDeploymentsOrThrow, loadJson, resolvePath } from "./common";

type PriceUpdate = {
  token: string;
  vni: string; // uint256 as decimal string (scaled 1e8)
  volBps?: number; // 0..10000
};

type PriceBotConfig = {
  deploymentsPath?: string;
  // If true, uses governance override (forceUpdateVNI) instead of guarded updateVNI.
  force?: boolean;
  updates: PriceUpdate[];
};

async function main() {
  const configPath = argValue("--config", "keepers/price-bot.example.json");
  const cfg = loadJson<PriceBotConfig>(resolvePath(configPath!));

  const dep = loadDeploymentsOrThrow(cfg.deploymentsPath);
  const oracleAddr = dep.contracts.PriceOracle;
  if (!oracleAddr) throw new Error("Missing PriceOracle address in deployments file");

  const oracle = getContract("PriceOracle", oracleAddr);

  console.log(`Network=${dep.network} Oracle=${oracleAddr} Force=${cfg.force ? "yes" : "no"}`);

  for (const u of cfg.updates) {
    const token = u.token;
    const vni = BigInt(u.vni);

    if (cfg.force) {
      const tx = await oracle.forceUpdateVNI(token, vni);
      const receipt = await tx.wait();
      console.log(`forceUpdateVNI token=${token} vni=${u.vni} tx=${tx.hash} gas=${receipt?.gasUsed?.toString?.() ?? "?"}`);
    } else {
      const tx = await oracle.updateVNI(token, vni);
      const receipt = await tx.wait();
      console.log(`updateVNI token=${token} vni=${u.vni} tx=${tx.hash} gas=${receipt?.gasUsed?.toString?.() ?? "?"}`);
    }

    if (u.volBps !== undefined) {
      const tx2 = await oracle.updateVolatilityBps(token, u.volBps);
      const receipt2 = await tx2.wait();
      console.log(
        `updateVolatility token=${token} volBps=${u.volBps} tx=${tx2.hash} gas=${receipt2?.gasUsed?.toString?.() ?? "?"}`
      );
    }
  }
}

main().catch((err) => {
  console.error(err);
  process.exitCode = 1;
});

