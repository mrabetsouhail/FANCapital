package com.fancapital.backend.backoffice.audit.controller;

import com.fancapital.backend.backoffice.audit.model.AuditDtos;
import com.fancapital.backend.backoffice.audit.model.AuditLogEntry;
import com.fancapital.backend.backoffice.audit.model.AuditAlert;
import com.fancapital.backend.backoffice.audit.model.AuditCheckpoint;
import com.fancapital.backend.backoffice.audit.model.BusinessContextMapping;
import com.fancapital.backend.backoffice.audit.service.AuditLogService;
import com.fancapital.backend.backoffice.audit.service.AuditRegistryService;
import com.fancapital.backend.backoffice.audit.service.AuditReconciliationService;
import com.fancapital.backend.backoffice.audit.service.AuditProofService;
import com.fancapital.backend.backoffice.audit.service.BusinessContextService;
import com.fancapital.backend.backoffice.audit.repo.AuditAlertRepository;
import com.fancapital.backend.backoffice.service.BackofficeAuthzService;
import com.fancapital.backend.auth.repo.AppUserRepository;
import com.fancapital.backend.blockchain.service.BlockchainReadService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.data.domain.PageRequest;

@RestController
@RequestMapping("/api/backoffice/audit")
public class AuditRegistryController {
  private static final String UUID_RX = "^[0-9a-fA-F\\-]{10,60}$";

  private final BackofficeAuthzService authz;
  private final AuditRegistryService registry;
  private final AppUserRepository userRepo;
  private final BlockchainReadService chain;
  private final AuditLogService auditLog;
  private final AuditReconciliationService recon;
  private final AuditAlertRepository alerts;
  private final AuditProofService auditProof;
  private final BusinessContextService businessContext;

  public AuditRegistryController(
      BackofficeAuthzService authz,
      AuditRegistryService registry,
      AppUserRepository userRepo,
      BlockchainReadService chain,
      AuditLogService auditLog,
      AuditReconciliationService recon,
      AuditAlertRepository alerts,
      AuditProofService auditProof,
      BusinessContextService businessContext
  ) {
    this.authz = authz;
    this.registry = registry;
    this.userRepo = userRepo;
    this.chain = chain;
    this.auditLog = auditLog;
    this.recon = recon;
    this.alerts = alerts;
    this.auditProof = auditProof;
    this.businessContext = businessContext;
  }

  @GetMapping("/registry")
  public AuditDtos.AuditRegistryResponse registry(
      @RequestParam(name = "q", required = false) String q,
      @RequestParam(name = "atBlock", required = false) Long atBlock,
      HttpServletRequest req
  ) {
    authz.requireAuditRead();
    BigInteger block = atBlock == null ? null : BigInteger.valueOf(atBlock);
    var res = registry.registry(q, block);
    log(req, "VIEW_REGISTRY", null, "atBlock=" + (atBlock == null ? "latest" : atBlock));
    return res;
  }

  @GetMapping("/tx/history")
  public Object txHistory(
      @RequestParam @Pattern(regexp = UUID_RX) String userId,
      @RequestParam(required = false, defaultValue = "200") @Min(1) @Max(500) int limit,
      HttpServletRequest req
  ) {
    authz.requireAuditRead();
    // Re-use existing chain txHistory by wallet address stored for userId
    var u = registryUserWallet(userId);
    log(req, "VIEW_TX_HISTORY", userId, "limit=" + limit);
    return chain.txHistory(u, limit);
  }

  @GetMapping("/logs")
  public AuditDtos.AuditLogsResponse logs(
      @RequestParam(required = false, defaultValue = "200") @Min(1) @Max(500) int limit,
      HttpServletRequest req
  ) {
    authz.requireAuditExport();
    List<AuditDtos.AuditLogRow> items = auditLog.latest(limit).stream().map(this::toRow).toList();
    log(req, "VIEW_AUDIT_LOGS", null, "limit=" + limit);
    return new AuditDtos.AuditLogsResponse(items);
  }

  @GetMapping("/alerts")
  public AuditDtos.AuditAlertsResponse openAlerts(
      @RequestParam(required = false, defaultValue = "100") @Min(1) @Max(500) int limit,
      HttpServletRequest req
  ) {
    authz.requireAuditRead();
    List<AuditAlert> open = alerts.findOpen(PageRequest.of(0, limit));
    List<AuditDtos.AuditAlertRow> items = open.stream().map(this::toAlertRow).toList();
    log(req, "VIEW_ALERTS", null, "limit=" + limit);
    return new AuditDtos.AuditAlertsResponse(items);
  }

  @PostMapping("/reconcile")
  public AuditDtos.ReconcileResponse reconcile(HttpServletRequest req) {
    authz.requireAuditExport();
    String actorId = authz.currentUserId();
    String actorEmail = authz.currentUserEmail();
    var r = recon.reconcileOnce(actorId, actorEmail);
    log(req, "RECONCILE_TRIGGERED", null, "latestBlock=" + r.latestBlock());
    return new AuditDtos.ReconcileResponse(r.latestBlock(), r.tokensSynced(), r.transfersProcessed(), r.alertsCreated());
  }

  @GetMapping("/checkpoints")
  public List<AuditDtos.CheckpointResponse> checkpoints(
      @RequestParam(required = false) String tokenAddress,
      @RequestParam(required = false) Long beforeBlock,
      @RequestParam(required = false, defaultValue = "100") @Min(1) @Max(500) int limit,
      HttpServletRequest req
  ) {
    authz.requireAuditRead();
    log(req, "VIEW_CHECKPOINTS", null, "token=" + tokenAddress + ", beforeBlock=" + beforeBlock + ", limit=" + limit);
    
    if (tokenAddress != null && beforeBlock != null) {
      AuditCheckpoint cp = auditProof.getLatestCheckpointBefore(tokenAddress, beforeBlock);
      if (cp == null) return List.of();
      return List.of(toCheckpointResponse(cp));
    }
    
    // Liste des checkpoints avec filtres optionnels
    List<AuditCheckpoint> checkpoints = auditProof.listCheckpoints(tokenAddress, limit);
    return checkpoints.stream().map(this::toCheckpointResponse).toList();
  }

  @GetMapping("/checkpoints/verify")
  public AuditDtos.CheckpointVerifyResponse verifyCheckpoint(
      @RequestParam @Pattern(regexp = UUID_RX) String checkpointId,
      HttpServletRequest req
  ) {
    authz.requireAuditRead();
    log(req, "VERIFY_CHECKPOINT", null, "checkpointId=" + checkpointId);
    
    return auditProof.findCheckpointById(checkpointId)
        .map(checkpoint -> {
          boolean isValid = auditProof.verifyCheckpoint(checkpoint);
          String message = isValid 
              ? "Checkpoint is valid" 
              : "Checkpoint verification failed: proof hash mismatch";
          return new AuditDtos.CheckpointVerifyResponse(isValid, message);
        })
        .orElse(new AuditDtos.CheckpointVerifyResponse(false, "Checkpoint not found"));
  }

  @GetMapping("/business-context")
  public AuditDtos.BusinessContextResponse getBusinessContext(
      @RequestParam(required = false) String transactionHash,
      @RequestParam(required = false) String businessContextId,
      HttpServletRequest req
  ) {
    authz.requireAuditRead();
    log(req, "VIEW_BUSINESS_CONTEXT", null, "txHash=" + transactionHash + ", contextId=" + businessContextId);
    
    if (transactionHash != null) {
      return businessContext.findByTransactionHash(transactionHash)
          .map(this::toBusinessContextResponse)
          .orElse(null);
    }
    
    if (businessContextId != null) {
      return businessContext.findByBusinessContextId(businessContextId)
          .map(this::toBusinessContextResponse)
          .orElse(null);
    }
    
    throw new IllegalArgumentException("Either transactionHash or businessContextId must be provided");
  }

  private AuditDtos.CheckpointResponse toCheckpointResponse(AuditCheckpoint cp) {
    return new AuditDtos.CheckpointResponse(
        cp.getId(),
        cp.getCreatedAt() != null ? cp.getCreatedAt().toString() : null,
        cp.getBlockNumber(),
        cp.getBlockHash(),
        cp.getTokenAddress(),
        cp.getTotalSupply1e8(),
        cp.getProofHash(),
        cp.getPreviousCheckpointHash(),
        cp.getMetadata()
    );
  }

  private AuditDtos.BusinessContextResponse toBusinessContextResponse(BusinessContextMapping m) {
    return new AuditDtos.BusinessContextResponse(
        m.getId(),
        m.getCreatedAt() != null ? m.getCreatedAt().toString() : null,
        m.getTransactionHash(),
        m.getBusinessContextId(),
        m.getContractAddress(),
        m.getOperationType(),
        m.getDescription(),
        m.getAccountingDocumentId()
    );
  }

  @GetMapping("/export/csv")
  public ResponseEntity<byte[]> exportCsv(
      @RequestParam(name = "atBlock", required = false) Long atBlock,
      HttpServletRequest req
  ) {
    authz.requireAuditExport();
    BigInteger block = atBlock == null ? null : BigInteger.valueOf(atBlock);
    var res = registry.registry(null, block);

    StringBuilder sb = new StringBuilder();
    sb.append("generatedAtSec,atBlock,userId,type,email,resident,idValue,displayName,walletAddress,atlasBalanceToken1e8,didonBalanceToken1e8\n");
    for (var r : res.rows()) {
      sb.append(res.generatedAtSec()).append(",");
      sb.append(res.atBlockNumber() == null ? "" : res.atBlockNumber()).append(",");
      sb.append(csv(r.userId())).append(",");
      sb.append(csv(r.type())).append(",");
      sb.append(csv(r.email())).append(",");
      sb.append(r.resident() == null ? "" : r.resident()).append(",");
      sb.append(csv(r.cinOrPassportOrFiscalId())).append(",");
      sb.append(csv(r.fullNameOrCompany())).append(",");
      sb.append(csv(r.walletAddress())).append(",");
      sb.append(csv(r.atlasBalanceToken1e8())).append(",");
      sb.append(csv(r.didonBalanceToken1e8())).append("\n");
    }
    byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
    String hash = sha256Hex(bytes);

    log(req, "EXPORT_CSV", null, "sha256=" + hash);
    HttpHeaders h = new HttpHeaders();
    h.setContentType(MediaType.parseMediaType("text/csv; charset=utf-8"));
    h.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"fancapital_audit_registry.csv\"");
    h.set("X-Report-SHA256", hash);
    return ResponseEntity.ok().headers(h).body(bytes);
  }

  @GetMapping("/export/pdf")
  public ResponseEntity<byte[]> exportPdf(
      @RequestParam(name = "atBlock", required = false) Long atBlock,
      HttpServletRequest req
  ) {
    authz.requireAuditExport();
    BigInteger block = atBlock == null ? null : BigInteger.valueOf(atBlock);
    var res = registry.registry(null, block);
    byte[] bytes = renderPdf(res);
    String hash = sha256Hex(bytes);

    log(req, "EXPORT_PDF", null, "sha256=" + hash);
    HttpHeaders h = new HttpHeaders();
    h.setContentType(MediaType.APPLICATION_PDF);
    h.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"fancapital_audit_registry.pdf\"");
    h.set("X-Report-SHA256", hash);
    return ResponseEntity.ok().headers(h).body(bytes);
  }

  private static byte[] renderPdf(AuditDtos.AuditRegistryResponse res) {
    try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      float margin = 48f;
      float leading = 12f;

      PDPage page = new PDPage(PDRectangle.A4);
      doc.addPage(page);
      PDPageContentStream cs = new PDPageContentStream(doc, page);

      float y = page.getMediaBox().getHeight() - margin;

      // Header
      y = writeLine(cs, margin, y, 14, true, "FAN-Capital — Export PDF Scellé (Audit)");
      y = writeLine(cs, margin, y - 6, 10, false, "generatedAt: " + Instant.ofEpochSecond(res.generatedAtSec()));
      y = writeLine(cs, margin, y, 10, false, "atBlock: " + (res.atBlockNumber() == null ? "latest" : res.atBlockNumber()));
      y = writeLine(cs, margin, y - 6, 9, true, "userId | idValue | wallet | atlas(1e8) | didon(1e8)");

      // Rows
      for (var r : res.rows()) {
        String line =
            safe(r.userId()) + " | " +
            safe(r.cinOrPassportOrFiscalId()) + " | " +
            safe(r.walletAddress()) + " | " +
            safe(r.atlasBalanceToken1e8()) + " | " +
            safe(r.didonBalanceToken1e8());
        if (line.length() > 170) line = line.substring(0, 167) + "...";

        if (y - leading < margin) {
          cs.close();
          page = new PDPage(PDRectangle.A4);
          doc.addPage(page);
          cs = new PDPageContentStream(doc, page);
          y = page.getMediaBox().getHeight() - margin;
        }
        y = writeLine(cs, margin, y, 8, false, line);
      }

      cs.close();
      doc.save(out);
      return out.toByteArray();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to generate PDF: " + e.getMessage(), e);
    }
  }

  private static float writeLine(PDPageContentStream cs, float x, float y, int fontSize, boolean bold, String text) throws java.io.IOException {
    cs.beginText();
    var fontName = bold ? Standard14Fonts.FontName.HELVETICA_BOLD : Standard14Fonts.FontName.HELVETICA;
    cs.setFont(new PDType1Font(fontName), fontSize);
    cs.newLineAtOffset(x, y);
    cs.showText(text == null ? "" : text);
    cs.endText();
    return y - 12f;
  }

  private AuditDtos.AuditLogRow toRow(AuditLogEntry e) {
    return new AuditDtos.AuditLogRow(
        e.getId(),
        e.getCreatedAt() != null ? e.getCreatedAt().toString() : null,
        e.getAction(),
        e.getActorEmail(),
        e.getActorUserId(),
        e.getTargetUserId(),
        e.getIp(),
        e.getUserAgent(),
        e.getPreviousHash(),
        e.getEntryHash(),
        e.getDetails()
    );
  }

  private AuditDtos.AuditAlertRow toAlertRow(AuditAlert a) {
    return new AuditDtos.AuditAlertRow(
        a.getId(),
        a.getCreatedAt() != null ? a.getCreatedAt().toString() : null,
        a.getSeverity(),
        a.getKind(),
        a.getUserId(),
        a.getWalletAddress(),
        a.getTokenAddress(),
        a.getExpectedBalance1e8(),
        a.getOnchainBalance1e8(),
        a.getCheckedAtBlock(),
        a.getDetails(),
        a.getResolvedAt() != null ? a.getResolvedAt().toString() : null
    );
  }

  private void log(HttpServletRequest req, String action, String targetUserId, String details) {
    String actorId = authz.currentUserId();
    String actorEmail = authz.currentUserEmail();
    String ip = req.getRemoteAddr();
    String ua = req.getHeader("User-Agent");
    auditLog.append(action, actorId, actorEmail, targetUserId, ip, ua, details);
  }

  private String registryUserWallet(String userId) {
    // We only need walletAddress; do not expose private keys.
    var u = registryUser(userId);
    String w = u.getWalletAddress();
    if (w == null || w.isBlank()) throw new IllegalArgumentException("User has no walletAddress");
    return w;
  }

  private com.fancapital.backend.auth.model.AppUser registryUser(String userId) {
    return userRepo.findById(userId).orElseThrow(() -> new IllegalArgumentException("Unknown user"));
  }

  private static String csv(String v) {
    if (v == null) return "";
    String s = v.replace("\"", "\"\"");
    return "\"" + s + "\"";
  }

  private static String safe(String v) {
    return v == null ? "" : v;
  }

  private static String sha256Hex(byte[] bytes) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] d = md.digest(bytes);
      return "0x" + HexFormat.of().formatHex(d);
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}

