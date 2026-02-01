export const FeatureFlags = {
  walletLoginStorageKey: 'fcWalletLoginEnabled',
};

/**
 * Toggle display of "Se connecter avec un Wallet".
 *
 * - Default: enabled (demo-friendly)
 * - To hide (public audience): localStorage.setItem('fcWalletLoginEnabled', 'false')
 * - To show: localStorage.setItem('fcWalletLoginEnabled', 'true')
 */
export function isWalletLoginEnabled(): boolean {
  try {
    const raw = localStorage.getItem(FeatureFlags.walletLoginStorageKey);
    // Circuit fermé (Livre Blanc): hide wallet login by default (no MetaMask dependency)
    if (raw == null) return false;
    return String(raw).toLowerCase() === 'true';
  } catch {
    return false;
  }
}

