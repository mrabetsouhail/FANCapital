package com.fancapital.backend.backoffice.controller;

import com.fancapital.backend.backoffice.service.BackofficeAuthzService;
import com.fancapital.backend.blockchain.service.CompartmentsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrôleur backoffice pour l'Architecture des Compartiments (La Matrice).
 * A, B, C, D : Réserve, Sas, Revenus, Fonds de Garantie.
 */
@RestController
@RequestMapping("/api/backoffice/compartments")
public class CompartmentsBackofficeController {
  private final CompartmentsService compartmentsService;
  private final BackofficeAuthzService authz;

  public CompartmentsBackofficeController(CompartmentsService compartmentsService, BackofficeAuthzService authz) {
    this.compartmentsService = compartmentsService;
    this.authz = authz;
  }

  @GetMapping
  public ResponseEntity<CompartmentsService.MatriceInfo> getCompartments() {
    authz.requireAuditRead();
    return compartmentsService.getMatrice()
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }
}
