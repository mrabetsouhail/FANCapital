import { ethers, network } from "hardhat";

/**
 * Envoie de l'ETH natif au wallet utilisateur pour payer le gas (approve, buy, etc.).
 * Sur Hardhat local, seul le deployer (compte 0) a de l'ETH par défaut.
 * Les wallets WaaS/MetaMask n'en ont pas — ce script les alimente.
 *
 * Usage:
 *   USER_ADDRESS=0x4ebe7059efce49f3dcfe4b48080a90c94c6b30bf npx hardhat run scripts/fund-eth-for-gas.ts --network localhost
 *   USER_ADDRESS=0x... AMOUNT_ETH=0.1 npx hardhat run scripts/fund-eth-for-gas.ts --network localhost
 */
async function main() {
  const [deployer] = await ethers.getSigners();
  const userAddress = process.env.USER_ADDRESS;
  const amountEth = process.env.AMOUNT_ETH ? parseFloat(process.env.AMOUNT_ETH) : 0.05;

  if (!userAddress) {
    throw new Error("USER_ADDRESS requis. Ex: USER_ADDRESS=0x4ebe7059efce49f3dcfe4b48080a90c94c6b30bf npm run fund-eth");
  }

  const addr = ethers.getAddress(userAddress);
  const amountWei = ethers.parseEther(amountEth.toString());

  console.log("Network:", network.name);
  console.log("Deployer (expéditeur):", deployer.address);
  console.log("Destinataire:", addr);
  console.log("Montant:", amountEth, "ETH");
  console.log("");

  const balBefore = await ethers.provider.getBalance(addr);
  console.log("Solde ETH avant:", ethers.formatEther(balBefore), "ETH");

  const tx = await deployer.sendTransaction({
    to: addr,
    value: amountWei,
    data: "0x",
  });
  await tx.wait();

  const balAfter = await ethers.provider.getBalance(addr);
  console.log("Solde ETH après:", ethers.formatEther(balAfter), "ETH");
  console.log("Tx:", tx.hash);
  console.log("✓ OK — le wallet peut maintenant payer le gas (approve, achat, etc.)");
}

main().catch((err) => {
  console.error(err);
  process.exitCode = 1;
});
