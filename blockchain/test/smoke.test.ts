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
    // Oracle guard: update within 10%
    await (await oracle.updateVNI(await token.getAddress(), 109n * 10n ** 8n)).wait(); // simulate gain (+9%)
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

    const TaxVault = await ethers.getContractFactory("TaxVault");
    const taxVault = await TaxVault.deploy(owner.address, await tnd.getAddress());
    await taxVault.waitForDeployment();

    const LiquidityPool = await ethers.getContractFactory("LiquidityPool");
    const pool = await LiquidityPool.deploy(owner.address, await oracle.getAddress());
    await pool.waitForDeployment();
    await (await pool.setInvestorRegistry(await investors.getAddress())).wait();
    await (await pool.setKYCRegistry(await kyc.getAddress())).wait();
    await (await pool.setCashToken(await tnd.getAddress())).wait();
    await (await pool.setTaxVault(await taxVault.getAddress())).wait();
    await (await taxVault.setAuthorizedCaller(await pool.getAddress(), true)).wait();

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

  it("sell with profit withholds RAS to TaxVault (resident)", async () => {
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

    const TaxVault = await ethers.getContractFactory("TaxVault");
    const taxVault = await TaxVault.deploy(owner.address, await tnd.getAddress());
    await taxVault.waitForDeployment();

    const LiquidityPool = await ethers.getContractFactory("LiquidityPool");
    const pool = await LiquidityPool.deploy(owner.address, await oracle.getAddress());
    await pool.waitForDeployment();
    await (await pool.setInvestorRegistry(await investors.getAddress())).wait();
    await (await pool.setKYCRegistry(await kyc.getAddress())).wait();
    await (await pool.setCashToken(await tnd.getAddress())).wait();
    await (await pool.setTaxVault(await taxVault.getAddress())).wait();
    await (await taxVault.setAuthorizedCaller(await pool.getAddress(), true)).wait();

    const CPEFEquityMedium = await ethers.getContractFactory("CPEFEquityMedium");
    const token = await CPEFEquityMedium.deploy(owner.address);
    await token.waitForDeployment();
    await (await token.setLiquidityPool(await pool.getAddress())).wait();
    await (await token.setKYCRegistry(await kyc.getAddress())).wait();
    await (await token.setPriceOracle(await oracle.getAddress())).wait();

    // Alice resident, KYC level 2, Bronze fee level
    await (await kyc.addToWhitelist(alice.address, 2, true)).wait();
    await (await investors.setFeeLevel(alice.address, 0)).wait();

    // Buy at VNI=100 (with spread/fees), then increase VNI to create a positive gain vs PRM.
    await (await oracle.updateVNI(await token.getAddress(), 100n * 10n ** 8n)).wait();
    const buyTnd = 1_000n * 10n ** 8n;
    await (await tnd.mint(alice.address, buyTnd)).wait();
    await (await tnd.connect(alice).approve(await pool.getAddress(), buyTnd)).wait();
    await (await pool.connect(alice).buy(await token.getAddress(), buyTnd)).wait();

    // Provide liquidity for redemption
    await (await tnd.mint(await pool.getAddress(), 2_000n * 10n ** 8n)).wait();

    // Pump VNI
    // Use governance override for a large move (force update)
    await (await oracle.forceUpdateVNI(await token.getAddress(), 140n * 10n ** 8n)).wait();

    const tokenBal = await token.balanceOf(alice.address);

    const taxBefore = await tnd.balanceOf(await taxVault.getAddress());
    await (await pool.connect(alice).sell(await token.getAddress(), tokenBal)).wait();
    const taxAfter = await tnd.balanceOf(await taxVault.getAddress());

    // Tax should be > 0 (profit exists when VNI > PRM)
    expect(taxAfter).to.be.greaterThan(taxBefore);
  });

  it("circuit breaker pauses redemptions when reserve ratio < 20%", async () => {
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

    const TaxVault = await ethers.getContractFactory("TaxVault");
    const taxVault = await TaxVault.deploy(owner.address, await tnd.getAddress());
    await taxVault.waitForDeployment();

    const CircuitBreaker = await ethers.getContractFactory("CircuitBreaker");
    const cb = await CircuitBreaker.deploy(owner.address);
    await cb.waitForDeployment();

    const LiquidityPool = await ethers.getContractFactory("LiquidityPool");
    const pool = await LiquidityPool.deploy(owner.address, await oracle.getAddress());
    await pool.waitForDeployment();
    await (await pool.setInvestorRegistry(await investors.getAddress())).wait();
    await (await pool.setKYCRegistry(await kyc.getAddress())).wait();
    await (await pool.setCashToken(await tnd.getAddress())).wait();
    await (await pool.setTaxVault(await taxVault.getAddress())).wait();
    await (await pool.setCircuitBreaker(await cb.getAddress())).wait();
    await (await taxVault.setAuthorizedCaller(await pool.getAddress(), true)).wait();

    const CPEFEquityHigh = await ethers.getContractFactory("CPEFEquityHigh");
    const token = await CPEFEquityHigh.deploy(owner.address);
    await token.waitForDeployment();
    await (await token.setLiquidityPool(await pool.getAddress())).wait();
    await (await token.setKYCRegistry(await kyc.getAddress())).wait();
    await (await token.setPriceOracle(await oracle.getAddress())).wait();

    // Alice resident, level 2, Bronze fee level
    await (await kyc.addToWhitelist(alice.address, 2, true)).wait();
    await (await investors.setFeeLevel(alice.address, 0)).wait();

    // VNI=100
    await (await oracle.updateVNI(await token.getAddress(), 100n * 10n ** 8n)).wait();

    // Seed pool with low cash and create token liability by minting to Alice via buy().
    // Give Alice 1000 TND, but only seed pool with 100 TND after buy to simulate low reserve.
    const buyTnd = 1_000n * 10n ** 8n;
    await (await tnd.mint(alice.address, buyTnd)).wait();
    await (await tnd.connect(alice).approve(await pool.getAddress(), buyTnd)).wait();
    await (await pool.connect(alice).buy(await token.getAddress(), buyTnd)).wait();

    // Drain pool cash to force reserve ratio < 20%:
    // Move almost all pool cash to owner (simulating external withdrawal / liquidity stress)
    const poolCash = await tnd.balanceOf(await pool.getAddress());
    // transfer out 90% of pool cash to owner
    const drain = (poolCash * 9n) / 10n;
    await (await tnd.connect(owner).mint(await pool.getAddress(), 0n)).wait(); // no-op to keep pattern
    // owner can move funds only if it controls pool; in this MVP we simulate by direct transfer from pool using owner-only call:
    // easiest in test: mint cash to pool then burn? instead, just set pool cash very low by burning from pool via owner
    await (await tnd.burn(await pool.getAddress(), drain)).wait();

    const tokenBal = await token.balanceOf(alice.address);
    await expect(pool.connect(alice).sell(await token.getAddress(), tokenBal)).to.be.revertedWith("LP: reserve too low");

    // Trip persistently via a separate call (keeper/backend), then redemptions are paused
    await (await pool.checkAndTripRedemptions(await token.getAddress())).wait();
    expect(await cb.redemptionsPaused(await pool.getAddress())).to.equal(true);
    await expect(pool.connect(alice).sell(await token.getAddress(), tokenBal)).to.be.revertedWith("LP: redemptions paused");

    // Replenish cash and resume by owner
    await (await tnd.mint(await pool.getAddress(), 10_000n * 10n ** 8n)).wait();
    await (await cb.resumeRedemptions(await pool.getAddress())).wait();

    // Now sell should go through (pool has liquidity)
    await (await pool.connect(alice).sell(await token.getAddress(), tokenBal)).wait();
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
    const p2p = await P2PExchange.deploy(owner.address, await tnd.getAddress(), await investors.getAddress(), owner.address);
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

  it("CPEFFactory deployFund deploys token/pool/oracle and wires shared infra", async () => {
    const [owner] = await ethers.getSigners();

    const KYCRegistry = await ethers.getContractFactory("KYCRegistry");
    const kyc = await KYCRegistry.deploy(owner.address);
    await kyc.waitForDeployment();

    const InvestorRegistry = await ethers.getContractFactory("InvestorRegistry");
    const investors = await InvestorRegistry.deploy(owner.address);
    await investors.waitForDeployment();

    const CashTokenTND = await ethers.getContractFactory("CashTokenTND");
    const tnd = await CashTokenTND.deploy(owner.address);
    await tnd.waitForDeployment();

    const TaxVault = await ethers.getContractFactory("TaxVault");
    const taxVault = await TaxVault.deploy(owner.address, await tnd.getAddress());
    await taxVault.waitForDeployment();

    const CircuitBreaker = await ethers.getContractFactory("CircuitBreaker");
    const cb = await CircuitBreaker.deploy(owner.address);
    await cb.waitForDeployment();

    const OracleDeployer = await ethers.getContractFactory("OracleDeployer");
    const oracleDeployer = await OracleDeployer.deploy();
    await oracleDeployer.waitForDeployment();

    const PoolDeployer = await ethers.getContractFactory("PoolDeployer");
    const poolDeployer = await PoolDeployer.deploy();
    await poolDeployer.waitForDeployment();

    const TokenDeployer = await ethers.getContractFactory("TokenDeployer");
    const tokenDeployer = await TokenDeployer.deploy();
    await tokenDeployer.waitForDeployment();

    const CPEFFactory = await ethers.getContractFactory("CPEFFactory");
    const factory = await CPEFFactory.deploy(
      owner.address,
      await kyc.getAddress(),
      await investors.getAddress(),
      await tnd.getAddress(),
      await taxVault.getAddress(),
      await cb.getAddress(),
      await oracleDeployer.getAddress(),
      await poolDeployer.getAddress(),
      await tokenDeployer.getAddress(),
      owner.address, // treasury
      owner.address, // oracleUpdater
      owner.address // poolOperator
    );
    await factory.waitForDeployment();

    // Allow factory to link funds into shared infra
    await (await taxVault.grantRole(await taxVault.GOVERNANCE_ROLE(), await factory.getAddress())).wait();
    await (await cb.grantRole(await cb.GOVERNANCE_ROLE(), await factory.getAddress())).wait();

    // Deploy one fund atomically
    const tx = await factory.deployFund("CPEF Fund Alpha", "CPEF-ALPHA");
    await tx.wait();

    expect(await factory.fundsCount()).to.equal(1n);
    const fund = await factory.getFund(0);
    expect(fund.token).to.not.equal(ethers.ZeroAddress);
    expect(fund.pool).to.not.equal(ethers.ZeroAddress);
    expect(fund.oracle).to.not.equal(ethers.ZeroAddress);

    // Token wiring
    const token = await ethers.getContractAt("CPEFToken", fund.token);
    expect(await token.liquidityPool()).to.equal(fund.pool);
    expect(await token.hasRole(await token.DEFAULT_ADMIN_ROLE(), owner.address)).to.equal(true);
    expect(await token.kycRegistry()).to.equal(await kyc.getAddress());
    expect(await token.priceOracle()).to.equal(fund.oracle);

    // Pool wiring
    const pool = await ethers.getContractAt("LiquidityPool", fund.pool);
    expect(await pool.oracle()).to.equal(fund.oracle);
    expect(await pool.investorRegistry()).to.equal(await investors.getAddress());
    expect(await pool.kycRegistry()).to.equal(await kyc.getAddress());
    expect(await pool.cashToken()).to.equal(await tnd.getAddress());
    expect(await pool.taxVault()).to.equal(await taxVault.getAddress());
    expect(await pool.circuitBreaker()).to.equal(await cb.getAddress());

    // Shared infra linkage
    expect(await taxVault.isAuthorizedCaller(fund.pool)).to.equal(true);
    expect(await cb.isPoolRegistered(fund.pool)).to.equal(true);
  });
});

