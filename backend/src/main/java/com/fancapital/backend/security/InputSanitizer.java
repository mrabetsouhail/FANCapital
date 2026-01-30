package com.fancapital.backend.security;

import java.text.Normalizer;

public final class InputSanitizer {
  private InputSanitizer() {}

  /**
   * Basic input normalization:
   * - NFKC normalize (reduces some confusables)
   * - remove control characters
   * - trim
   * - collapse internal whitespace
   */
  public static String clean(String s) {
    if (s == null) return null;
    String x = Normalizer.normalize(s, Normalizer.Form.NFKC);
    x = x.replaceAll("\\p{C}", ""); // control chars
    x = x.trim();
    x = x.replaceAll("\\s{2,}", " ");
    return x;
  }

  public static String lowerEmail(String email) {
    String x = clean(email);
    return x == null ? null : x.toLowerCase();
  }
}

