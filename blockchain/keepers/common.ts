import fs from "node:fs";
import path from "node:path";
import { Contract, JsonRpcProvider, Wallet } from "ethers";

export type DeploymentFile = {
  network: string;
  deployer: string;
  contracts: Record<string, string>;
  generatedAt: string;
};

export function argValue(flag: string, defaultValue?: string): string | undefined {
  const idx = process.argv.indexOf(flag);
  if (idx === -1) return defaultValue;
  return process.argv[idx + 1] ?? defaultValue;
}

export function loadJson<T>(filePath: string): T {
  const raw = fs.readFileSync(filePath, { encoding: "utf-8" });
  return JSON.parse(raw) as T;
}

export function resolvePath(p: string): string {
  if (path.isAbsolute(p)) return p;
  return path.resolve(process.cwd(), p);
}

export function loadDeploymentsOrThrow(p?: string): DeploymentFile {
  const defaultPath = path.join(process.cwd(), "deployments", "localhost.json");
  const fp = resolvePath(p ?? defaultPath);
  if (!fs.existsSync(fp)) {
    throw new Error(
      `Deployments file not found: ${fp}\n` +
        `Hint: run a node + deploy first: \`npm run node\` then \`npm run deploy:localhost\``
    );
  }
  return loadJson<DeploymentFile>(fp);
}

export function getRpcUrl(): string {
  return argValue("--rpc", process.env.RPC_URL ?? "http://127.0.0.1:8545")!;
}

export function getPrivateKeyOrThrow(): string {
  const pk = argValue("--pk", process.env.PRIVATE_KEY);
  if (!pk) {
    throw new Error(
      "Missing PRIVATE_KEY. Provide via env PRIVATE_KEY or CLI --pk.\n" +
        "Local Hardhat node default #0 key (dev only): 0xac0974...ff80"
    );
  }
  return pk;
}

export function getProvider(): JsonRpcProvider {
  return new JsonRpcProvider(getRpcUrl());
}

export function getSigner(): Wallet {
  return new Wallet(getPrivateKeyOrThrow(), getProvider());
}

type Artifact = { abi: any[] };
const ARTIFACTS: Record<string, string> = {
  PriceOracle: "artifacts/contracts/services/PriceOracle.sol/PriceOracle.json",
  LiquidityPool: "artifacts/contracts/services/LiquidityPool.sol/LiquidityPool.json",
  CircuitBreaker: "artifacts/contracts/governance/CircuitBreaker.sol/CircuitBreaker.json",
  InvestorRegistry: "artifacts/contracts/services/InvestorRegistry.sol/InvestorRegistry.json",
};

export function loadAbiOrThrow(contractName: keyof typeof ARTIFACTS): any[] {
  const rel = ARTIFACTS[contractName];
  const fp = resolvePath(rel);
  if (!fs.existsSync(fp)) {
    throw new Error(`Missing artifact for ${contractName}: ${fp}. Run \`npm run compile\` first.`);
  }
  const art = loadJson<Artifact>(fp);
  return art.abi;
}

export function getContract(contractName: keyof typeof ARTIFACTS, address: string): Contract {
  return new Contract(address, loadAbiOrThrow(contractName), getSigner());
}

