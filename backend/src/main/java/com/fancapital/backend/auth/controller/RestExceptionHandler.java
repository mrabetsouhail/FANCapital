package com.fancapital.backend.auth.controller;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class RestExceptionHandler {

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<?> badRequest(IllegalArgumentException e) {
    return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<?> illegalState(IllegalStateException e) {
    // Config/runtime issues (missing env vars, etc.)
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("message", e.getMessage()));
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<?> status(ResponseStatusException e) {
    HttpStatus code = HttpStatus.resolve(e.getStatusCode().value());
    if (code == null) code = HttpStatus.INTERNAL_SERVER_ERROR;
    String msg = e.getReason() != null ? e.getReason() : code.getReasonPhrase();
    return ResponseEntity.status(code).body(Map.of("message", msg));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<?> validation(MethodArgumentNotValidException e) {
    String msg = e.getBindingResult().getAllErrors().isEmpty()
        ? "Validation error"
        : e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
    return ResponseEntity.badRequest().body(Map.of("message", msg));
  }
}

