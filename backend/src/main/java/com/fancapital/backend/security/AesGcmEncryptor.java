package com.fancapital.backend.security;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Minimal AES-GCM encryptor for storing WaaS private keys at rest (dev/MVP).
 *
 * Format: base64(iv || ciphertext), where iv is 12 bytes.
 */
public final class AesGcmEncryptor {
  private static final int IV_LEN = 12;
  private static final int TAG_BITS = 128;

  private final SecureRandom rng = new SecureRandom();
  private final byte[] key;

  public AesGcmEncryptor(byte[] key32) {
    if (key32 == null || key32.length != 32) throw new IllegalArgumentException("AES key must be 32 bytes");
    this.key = key32;
  }

  public String encryptToB64(String plaintext) {
    try {
      byte[] iv = new byte[IV_LEN];
      rng.nextBytes(iv);
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
      byte[] ct = c.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

      byte[] out = new byte[IV_LEN + ct.length];
      System.arraycopy(iv, 0, out, 0, IV_LEN);
      System.arraycopy(ct, 0, out, IV_LEN, ct.length);
      return Base64.getEncoder().encodeToString(out);
    } catch (Exception e) {
      throw new IllegalStateException("encrypt failed", e);
    }
  }

  public String decryptFromB64(String b64) {
    try {
      byte[] in = Base64.getDecoder().decode(b64);
      if (in.length <= IV_LEN) throw new IllegalArgumentException("bad ciphertext");
      byte[] iv = new byte[IV_LEN];
      byte[] ct = new byte[in.length - IV_LEN];
      System.arraycopy(in, 0, iv, 0, IV_LEN);
      System.arraycopy(in, IV_LEN, ct, 0, ct.length);

      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
      byte[] pt = c.doFinal(ct);
      return new String(pt, StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalStateException("decrypt failed", e);
    }
  }
}

