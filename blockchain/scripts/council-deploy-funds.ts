import { ethers, network } from "hardhat";
import fs from "node:fs";
import path from "node:path";

type CouncilDeployment = {
  network: string;
  deployer: string;
  council: { address: string; owners: string[]; threshold: number };
  infra: Record<string, string>;
  factory: { CPEFFactory: string };
};

function loadJson<T>(p: string): T {
  return JSON.parse(fs.readFileSync(p, { encoding: "utf-8" })) as T;
}

async function ensureConfirmed(council: any, txId: bigint, signer: any) {
  const already = await council.confirmedBy(txId, signer.address);
  if (!already) {
    await (await council.connect(signer).confirmTransaction(txId)).wait();
  }
}

async function main() {
  const root = path.join(__dirname, "..");
  const inPath = path.join(root, "deployments", "localhost.council.json");
  if (!fs.existsSync(inPath)) {
    throw new Error(`Missing ${inPath}. Run deploy:council:localhost first.`);
  }
  const dep = loadJson<CouncilDeployment>(inPath);

  const signers = await ethers.getSigners();
  const owner0 = signers[0];
  const owner1 = signers[1];
  const owner2 = signers[2];

  const council = await ethers.getContractAt("MultiSigCouncil", dep.council.address);
  const factory = await ethers.getContractAt("CPEFFactory", dep.factory.CPEFFactory);

  console.log("Network:", network.name);
  console.log("Council:", dep.council.address);
  console.log("Factory:", dep.factory.CPEFFactory);

  async function submitConfirmExecute(name: string, symbol: string) {
    const data = factory.interface.encodeFunctionData("deployFund", [name, symbol]);
    const submitTx = await council.connect(owner0).submitTransaction(await factory.getAddress(), 0n, data);
    const submitRcpt = await submitTx.wait();
    // txId is transactions.length-1, easiest to read after submit
    const txId: bigint = (await council.transactionsCount()) - 1n;

    await ensureConfirmed(council, txId, owner0);
    await ensureConfirmed(council, txId, owner1);
    await ensureConfirmed(council, txId, owner2);

    const execTx = await council.connect(owner0).executeTransaction(txId);
    const execRcpt = await execTx.wait();
    console.log(`Executed ${name} ${symbol} txId=${txId} submitGas=${submitRcpt?.gasUsed?.toString?.() ?? "?"} execGas=${execRcpt?.gasUsed?.toString?.() ?? "?"}`);
  }

  await submitConfirmExecute("CPEF Atlas", "$ATLAS$");
  await submitConfirmExecute("CPEF Didon", "$DIDON$");

  const count = await factory.fundsCount();
  const funds: any[] = [];
  for (let i = 0n; i < count; i++) {
    const f = await factory.getFund(i);
    funds.push({
      id: Number(i),
      token: f.token,
      pool: f.pool,
      oracle: f.oracle,
      createdAt: f.createdAt.toString(),
      // keep friendly names for dashboards
      name: i === 0n ? "CPEF Atlas" : i === 1n ? "CPEF Didon" : "CPEF Fund",
      symbol: i === 0n ? "$ATLAS$" : i === 1n ? "$DIDON$" : "",
    });
  }

  const out = {
    network: network.name,
    council: dep.council,
    infra: dep.infra,
    factory: dep.factory,
    funds,
    generatedAt: new Date().toISOString(),
  };

  const outPath = path.join(root, "deployments", "localhost.council-funds.json");
  fs.writeFileSync(outPath, JSON.stringify(out, null, 2), { encoding: "utf-8" });
  console.log("Saved deployments to:", outPath);
}

main().catch((err) => {
  console.error(err);
  process.exitCode = 1;
});

