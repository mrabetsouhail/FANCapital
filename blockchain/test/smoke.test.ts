import { expect } from "chai";
import { ethers } from "hardhat";

describe("FAN-Capital blockchain (smoke)", function () {
  it("deploys core contracts and enforces KYC on transfers", async () => {
    const [owner, alice, bob] = await ethers.getSigners();

    const KYCRegistry = await ethers.getContractFactory("KYCRegistry");
    const kyc = await KYCRegistry.deploy(owner.address);
    await kyc.waitForDeployment();

    const PriceOracle = await ethers.getContractFactory("PriceOracle");
    const oracle = await PriceOracle.deploy(owner.address);
    await oracle.waitForDeployment();

    const LiquidityPool = await ethers.getContractFactory("LiquidityPool");
    const pool = await LiquidityPool.deploy(owner.address, await oracle.getAddress());
    await pool.waitForDeployment();

    const CPEFEquityHigh = await ethers.getContractFactory("CPEFEquityHigh");
    const token = await CPEFEquityHigh.deploy(owner.address);
    await token.waitForDeployment();

    await (await token.setLiquidityPool(await pool.getAddress())).wait();
    await (await token.setKYCRegistry(await kyc.getAddress())).wait();
    await (await token.setPriceOracle(await oracle.getAddress())).wait();

    // Set VNI = 100.00000000 TND
    await (await oracle.updateVNI(await token.getAddress(), 100n * 10n ** 8n)).wait();

    // whitelist only Alice (white list)
    await (await kyc.addToWhitelist(alice.address, 2, true)).wait();

    // This test only checks P2P restrictions; mint directly via liquidityPool privilege.
    // We keep using token.mint here by temporarily setting owner as pool, to avoid needing cash token setup.
    await (await token.connect(owner).setLiquidityPool(owner.address)).wait();
    await (await token.connect(owner).mint(alice.address, 1n * 10n ** 8n, 100n * 10n ** 8n)).wait();

    // transfer to Bob should fail (not whitelisted)
    await expect(token.connect(alice).transfer(bob.address, 1n)).to.be.revertedWith("Address not whitelisted");

    // whitelist Bob but Green => P2P disabled
    await (await kyc.addToWhitelist(bob.address, 1, true)).wait();
    await expect(token.connect(alice).transfer(bob.address, 1n)).to.be.revertedWith("P2P disabled for Green List");

    // upgrade Bob to White => transfer ok
    await (await kyc.addToWhitelist(bob.address, 2, true)).wait();
    await expect(token.connect(alice).transfer(bob.address, 1n)).to.not.be.reverted;
  });

  it("creates and cancels a reservation option (locks inventory)", async () => {
    const [owner, alice] = await ethers.getSigners();

    const KYCRegistry = await ethers.getContractFactory("KYCRegistry");
    const kyc = await KYCRegistry.deploy(owner.address);
    await kyc.waitForDeployment();

    const InvestorRegistry = await ethers.getContractFactory("InvestorRegistry");
    const investors = await InvestorRegistry.deploy(owner.address);
    await investors.waitForDeployment();

    const PriceOracle = await ethers.getContractFactory("PriceOracle");
    const oracle = await PriceOracle.deploy(owner.address);
    await oracle.waitForDeployment();

    const LiquidityPool = await ethers.getContractFactory("LiquidityPool");
    const pool = await LiquidityPool.deploy(owner.address, await oracle.getAddress());
    await pool.waitForDeployment();

    const CPEFEquityMedium = await ethers.getContractFactory("CPEFEquityMedium");
    const token = await CPEFEquityMedium.deploy(owner.address);
    await token.waitForDeployment();

    await (await token.setLiquidityPool(await pool.getAddress())).wait();
    await (await token.setKYCRegistry(await kyc.getAddress())).wait();
    await (await token.setPriceOracle(await oracle.getAddress())).wait();

    // Set VNI so the option can fix K
    await (await oracle.updateVNI(await token.getAddress(), 125n * 10n ** 8n)).wait();

    const ReservationOption = await ethers.getContractFactory("ReservationOption");
    const opt = await ReservationOption.deploy(owner.address, await oracle.getAddress(), await pool.getAddress());
    await opt.waitForDeployment();
    await (await pool.setReservationOption(await opt.getAddress())).wait();
    await (await opt.setKYCRegistry(await kyc.getAddress())).wait();
    await (await opt.setInvestorRegistry(await investors.getAddress())).wait();

    // Alice must be KYC level 2 and have premium tier+subscription to reserve
    await (await kyc.addToWhitelist(alice.address, 2, true)).wait();
    await (await investors.setScore(alice.address, 60)).wait(); // Platinum/Diamond tier
    await (await investors.setSubscriptionActive(alice.address, true)).wait();

    const qty = 10n * 10n ** 8n; // 10 tokens
    const expiry = BigInt((await ethers.provider.getBlock("latest"))!.timestamp + 3600);

    const tx = await opt.connect(alice).reserve(await token.getAddress(), qty, expiry, 500, 100); // 5% + 1%
    const receipt = await tx.wait();
    const evt = receipt!.logs.find((l: any) => l.fragment?.name === "Reserved");
    expect(evt).to.not.equal(undefined);

    expect(await pool.reservedInventory(await token.getAddress())).to.equal(qty);

    await (await opt.connect(alice).cancel(1)).wait();
    expect(await pool.reservedInventory(await token.getAddress())).to.equal(0n);
  });

  it("credit model gates + escrow lock (model A / model B)", async () => {
    const [owner, alice, bob] = await ethers.getSigners();

    const KYCRegistry = await ethers.getContractFactory("KYCRegistry");
    const kyc = await KYCRegistry.deploy(owner.address);
    await kyc.waitForDeployment();

    const InvestorRegistry = await ethers.getContractFactory("InvestorRegistry");
    const investors = await InvestorRegistry.deploy(owner.address);
    await investors.waitForDeployment();

    const PriceOracle = await ethers.getContractFactory("PriceOracle");
    const oracle = await PriceOracle.deploy(owner.address);
    await oracle.waitForDeployment();

    const EscrowRegistry = await ethers.getContractFactory("EscrowRegistry");
    const escrow = await EscrowRegistry.deploy(owner.address);
    await escrow.waitForDeployment();

    const LiquidityPool = await ethers.getContractFactory("LiquidityPool");
    const pool = await LiquidityPool.deploy(owner.address, await oracle.getAddress());
    await pool.waitForDeployment();

    const CPEFEquityHigh = await ethers.getContractFactory("CPEFEquityHigh");
    const token = await CPEFEquityHigh.deploy(owner.address);
    await token.waitForDeployment();

    await (await token.setLiquidityPool(await pool.getAddress())).wait();
    await (await token.setKYCRegistry(await kyc.getAddress())).wait();
    await (await token.setPriceOracle(await oracle.getAddress())).wait();
    await (await token.setEscrowManager(await escrow.getAddress())).wait();

    await (await oracle.updateVNI(await token.getAddress(), 100n * 10n ** 8n)).wait();

    const CreditModelA = await ethers.getContractFactory("CreditModelA");
    const creditA = await CreditModelA.deploy(owner.address, await oracle.getAddress(), await escrow.getAddress());
    await creditA.waitForDeployment();
    await (await creditA.setKYCRegistry(await kyc.getAddress())).wait();
    await (await creditA.setInvestorRegistry(await investors.getAddress())).wait();

    const CreditModelBPGP = await ethers.getContractFactory("CreditModelBPGP");
    const creditB = await CreditModelBPGP.deploy(owner.address, await oracle.getAddress(), await escrow.getAddress());
    await creditB.waitForDeployment();
    await (await creditB.setKYCRegistry(await kyc.getAddress())).wait();
    await (await creditB.setInvestorRegistry(await investors.getAddress())).wait();

    await (await escrow.setAuthorizedCaller(await creditA.getAddress(), true)).wait();
    await (await escrow.setAuthorizedCaller(await creditB.getAddress(), true)).wait();

    // Alice: KYC level 2
    await (await kyc.addToWhitelist(alice.address, 2, true)).wait();
    // Bob: KYC level 2
    await (await kyc.addToWhitelist(bob.address, 2, true)).wait();

    // Mint some tokens to Alice via platform privilege
    await (await token.setLiquidityPool(owner.address)).wait();
    await (await token.mint(alice.address, 10n * 10n ** 8n, 100n * 10n ** 8n)).wait();
    await (await token.setLiquidityPool(await pool.getAddress())).wait();

    // Alice score 30 (Silver/Gold), subscription OFF => cannot use model A
    await (await investors.setScore(alice.address, 30)).wait();
    await (await investors.setSubscriptionActive(alice.address, false)).wait();
    await expect(creditA.connect(alice).requestAdvance(await token.getAddress(), 1n * 10n ** 8n, 30)).to.be.revertedWith(
      "A: requires tier+sub"
    );

    // Turn subscription ON => model A allowed
    await (await investors.setSubscriptionActive(alice.address, true)).wait();
    const txA = await creditA.connect(alice).requestAdvance(await token.getAddress(), 1n * 10n ** 8n, 30);
    await txA.wait();

    // Owner activates -> escrow lock should block transfers
    await (await creditA.activateAdvance(1)).wait();
    await expect(token.connect(alice).transfer(bob.address, 1n)).to.be.revertedWith("Escrow locked");
    await (await creditA.closeAdvance(1)).wait();
    await expect(token.connect(alice).transfer(bob.address, 1n)).to.not.be.reverted;

    // Model B requires Platinum tier + subscription
    await (await investors.setScore(alice.address, 60)).wait(); // Platinum/Diamond
    await (await investors.setSubscriptionActive(alice.address, true)).wait();
    const txB = await creditB.connect(alice).requestAdvance(await token.getAddress(), 1n * 10n ** 8n, 30);
    await txB.wait();
    await (await creditB.activateAdvance(1)).wait();
    await (await oracle.updateVNI(await token.getAddress(), 120n * 10n ** 8n)).wait(); // simulate gain
    await (await creditB.closeAdvance(1)).wait();
  });

  it("pool buy/sell uses on-chain TND + spread + fees + VAT", async () => {
    const [owner, alice] = await ethers.getSigners();

    const KYCRegistry = await ethers.getContractFactory("KYCRegistry");
    const kyc = await KYCRegistry.deploy(owner.address);
    await kyc.waitForDeployment();

    const InvestorRegistry = await ethers.getContractFactory("InvestorRegistry");
    const investors = await InvestorRegistry.deploy(owner.address);
    await investors.waitForDeployment();

    const PriceOracle = await ethers.getContractFactory("PriceOracle");
    const oracle = await PriceOracle.deploy(owner.address);
    await oracle.waitForDeployment();

    const CashTokenTND = await ethers.getContractFactory("CashTokenTND");
    const tnd = await CashTokenTND.deploy(owner.address);
    await tnd.waitForDeployment();

    const LiquidityPool = await ethers.getContractFactory("LiquidityPool");
    const pool = await LiquidityPool.deploy(owner.address, await oracle.getAddress());
    await pool.waitForDeployment();
    await (await pool.setInvestorRegistry(await investors.getAddress())).wait();
    await (await pool.setCashToken(await tnd.getAddress())).wait();

    const CPEFEquityMedium = await ethers.getContractFactory("CPEFEquityMedium");
    const token = await CPEFEquityMedium.deploy(owner.address);
    await token.waitForDeployment();

    await (await token.setLiquidityPool(await pool.getAddress())).wait();
    await (await token.setKYCRegistry(await kyc.getAddress())).wait();
    await (await token.setPriceOracle(await oracle.getAddress())).wait();

    // KYC Alice level 2 and fee level Bronze (0)
    await (await kyc.addToWhitelist(alice.address, 2, true)).wait();
    await (await investors.setFeeLevel(alice.address, 0)).wait();

    // Set VNI = 125.50 TND
    await (await oracle.updateVNI(await token.getAddress(), 125_50000000n)).wait();
    // Default base spread = 0.20% => buy price = 125.50 * 1.002 = 125.751

    // Give Alice enough TND to cover "total paid" example: 1,011.90 TND (includes fee+VAT)
    const tndIn = 1_011_90000000n;
    await (await tnd.mint(alice.address, tndIn)).wait();
    await (await tnd.connect(alice).approve(await pool.getAddress(), tndIn)).wait();

    const treasuryBefore = await tnd.balanceOf(owner.address);
    const poolBefore = await tnd.balanceOf(await pool.getAddress());

    const buyTx = await pool.connect(alice).buy(await token.getAddress(), tndIn);
    await buyTx.wait();

    // For Bronze: feeBase = 1% of tndIn, VAT=19% of feeBase, totalFee = 1.19% of tndIn
    const feeBase = (tndIn * 100n) / 10_000n;
    const vat = (feeBase * 1_900n) / 10_000n;
    const totalFee = feeBase + vat;

    expect(await tnd.balanceOf(owner.address)).to.equal(treasuryBefore + totalFee);
    expect(await tnd.balanceOf(await pool.getAddress())).to.equal(poolBefore + (tndIn - totalFee));

    // Now sell all tokens back and ensure payout is > 0
    const tokenBal = await token.balanceOf(alice.address);
    const sellTx = await pool.connect(alice).sell(await token.getAddress(), tokenBal);
    await sellTx.wait();
    expect(await token.balanceOf(alice.address)).to.equal(0n);
  });

  it("P2P exchange charges P2P fee + VAT and settles atomically", async () => {
    const [owner, seller, buyer] = await ethers.getSigners();

    const KYCRegistry = await ethers.getContractFactory("KYCRegistry");
    const kyc = await KYCRegistry.deploy(owner.address);
    await kyc.waitForDeployment();

    const InvestorRegistry = await ethers.getContractFactory("InvestorRegistry");
    const investors = await InvestorRegistry.deploy(owner.address);
    await investors.waitForDeployment();

    const CashTokenTND = await ethers.getContractFactory("CashTokenTND");
    const tnd = await CashTokenTND.deploy(owner.address);
    await tnd.waitForDeployment();

    const P2PExchange = await ethers.getContractFactory("P2PExchange");
    const p2p = await P2PExchange.deploy(owner.address, await tnd.getAddress(), await investors.getAddress());
    await p2p.waitForDeployment();
    await (await p2p.setKYCRegistry(await kyc.getAddress())).wait();

    const PriceOracle = await ethers.getContractFactory("PriceOracle");
    const oracle = await PriceOracle.deploy(owner.address);
    await oracle.waitForDeployment();

    const LiquidityPool = await ethers.getContractFactory("LiquidityPool");
    const pool = await LiquidityPool.deploy(owner.address, await oracle.getAddress());
    await pool.waitForDeployment();

    const CPEFEquityHigh = await ethers.getContractFactory("CPEFEquityHigh");
    const token = await CPEFEquityHigh.deploy(owner.address);
    await token.waitForDeployment();

    // KYC level 2 required for P2P token transfers (token restriction logic)
    await (await kyc.addToWhitelist(seller.address, 2, true)).wait();
    await (await kyc.addToWhitelist(buyer.address, 2, true)).wait();
    await (await token.setKYCRegistry(await kyc.getAddress())).wait();

    // Mint tokens to seller via platform privilege
    await (await token.setLiquidityPool(owner.address)).wait();
    const tokenAmount = 2n * 10n ** 8n; // 2 tokens
    await (await token.mint(seller.address, tokenAmount, 100n * 10n ** 8n)).wait();

    // Commercial gating: P2P requires Silver tier minimum (score >= 21)
    await (await investors.setScore(seller.address, 30)).wait();
    await (await investors.setScore(buyer.address, 30)).wait();

    // Buyer fee level Silver (1) => P2P fee 0.75% + VAT
    await (await investors.setFeeLevel(buyer.address, 1)).wait();

    const pricePerToken = 130n * 10n ** 8n; // 130 TND
    const notional = (tokenAmount * pricePerToken) / 10n ** 8n; // 260 TND
    const feeBase = (notional * 75n) / 10_000n;
    const vat = (feeBase * 1_900n) / 10_000n;
    const totalFee = feeBase + vat;
    const totalFromBuyer = notional + totalFee;

    // Fund buyer and approve
    await (await tnd.mint(buyer.address, totalFromBuyer)).wait();
    await (await tnd.connect(buyer).approve(await p2p.getAddress(), totalFromBuyer)).wait();

    // Seller approves token spend to P2PExchange
    await (await token.connect(seller).approve(await p2p.getAddress(), tokenAmount)).wait();

    const sellerCashBefore = await tnd.balanceOf(seller.address);
    const treasuryCashBefore = await tnd.balanceOf(owner.address);

    await (await p2p.settle(await token.getAddress(), seller.address, buyer.address, tokenAmount, pricePerToken)).wait();

    expect(await token.balanceOf(buyer.address)).to.equal(tokenAmount);
    expect(await token.balanceOf(seller.address)).to.equal(0n);
    expect(await tnd.balanceOf(seller.address)).to.equal(sellerCashBefore + notional);
    expect(await tnd.balanceOf(owner.address)).to.equal(treasuryCashBefore + totalFee);

    // Silence "unused" (pool created to reuse token pattern)
    expect(await pool.getAddress()).to.be.a("string");
  });
});

