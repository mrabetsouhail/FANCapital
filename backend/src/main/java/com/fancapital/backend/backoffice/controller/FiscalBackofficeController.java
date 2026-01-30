package com.fancapital.backend.backoffice.controller;

import com.fancapital.backend.backoffice.model.FiscalDashboardDtos;
import com.fancapital.backend.backoffice.service.BackofficeAuthzService;
import com.fancapital.backend.backoffice.service.TaxVaultDashboardService;
import com.fancapital.backend.backoffice.service.TaxVaultWriteService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.math.BigInteger;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/backoffice/fiscal")
public class FiscalBackofficeController {
  private static final String UINT_STR_RX = "^[0-9]{1,78}$";

  private final TaxVaultDashboardService dashboardService;
  private final TaxVaultWriteService writeService;
  private final BackofficeAuthzService authz;

  public FiscalBackofficeController(TaxVaultDashboardService dashboardService, TaxVaultWriteService writeService, BackofficeAuthzService authz) {
    this.dashboardService = dashboardService;
    this.writeService = writeService;
    this.authz = authz;
  }

  @GetMapping("/summary")
  public FiscalDashboardDtos.FiscalDashboardResponse summary() {
    // read-only could be opened, but keep it admin-only for regulator/admin view
    authz.requireAdmin();
    return dashboardService.dashboard();
  }

  @PostMapping("/withdraw")
  public FiscalDashboardDtos.TxResponse withdraw(@Valid @RequestBody WithdrawBody body) {
    authz.requireAdmin();
    BigInteger amount = new BigInteger(body.amount());
    String txHash = writeService.withdrawToFisc(amount);
    return new FiscalDashboardDtos.TxResponse("submitted", txHash, "withdrawToFisc submitted");
  }

  public record WithdrawBody(@Pattern(regexp = UINT_STR_RX) String amount) {}
}

