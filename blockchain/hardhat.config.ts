import type { HardhatUserConfig } from "hardhat/config";
import "@nomicfoundation/hardhat-toolbox";

const config: HardhatUserConfig = {
  solidity: {
    version: "0.8.24",
    settings: {
      optimizer: { enabled: true, runs: 200 },
    },
  },
  paths: {
    sources: "./contracts",
    tests: "./test",
    cache: "./cache",
    artifacts: "./artifacts",
  },
  networks: {
    // Example Besu/Quorum JSON-RPC network (fill via env when needed)
    // besu: { url: process.env.BESU_RPC_URL ?? "", accounts: [process.env.DEPLOYER_PRIVATE_KEY ?? ""] }
  },
};

export default config;

