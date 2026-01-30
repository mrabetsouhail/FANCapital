import { HttpErrorResponse, HttpResponse, type HttpInterceptorFn } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { delay } from 'rxjs/operators';

import { MOCK_FUNDS, MOCK_VNI_1E8, MOCK_VOL_BPS } from './mock-blockchain.data';
import type { QuoteBuyResponse, QuoteSellResponse, TxResponse } from '../models/pricing.models';

const PRICE_SCALE = 1e8;
const BPS = 10_000;
const VAT_BPS = 1_900;

function json(res: unknown, ms = 150) {
  return of(new HttpResponse({ status: 200, body: res })).pipe(delay(ms));
}

function badRequest(message: string) {
  return throwError(
    () =>
      new HttpErrorResponse({
        status: 400,
        statusText: 'Bad Request',
        error: { message },
      })
  );
}

export const mockBlockchainInterceptor: HttpInterceptorFn = (req, next) => {
  // Mock is opt-in (useful when Spring Boot is not running).
  // Enable with: localStorage.setItem('mockBlockchainApi', 'true')
  // Disable with: localStorage.setItem('mockBlockchainApi', 'false') or removeItem(...)
  const enabled = (localStorage.getItem('mockBlockchainApi') ?? 'false') === 'true';
  if (!enabled) return next(req);

  // Only intercept relative API calls.
  if (!req.url.startsWith('/api/blockchain')) return next(req);

  // ---------- Funds ----------
  if (req.method === 'GET' && req.url === '/api/blockchain/funds') {
    return json({ funds: MOCK_FUNDS });
  }
  if (req.method === 'GET' && req.url.startsWith('/api/blockchain/funds/')) {
    const idStr = req.url.split('/').pop() ?? '';
    const id = Number(idStr);
    const f = MOCK_FUNDS.find((x) => x.id === id);
    if (!f) return badRequest('Unknown fund id');
    return json(f);
  }

  // ---------- Oracle ----------
  if (req.method === 'GET' && req.url === '/api/blockchain/oracle/vni') {
    const token = (req.params.get('token') ?? '').toLowerCase();
    const fund = MOCK_FUNDS.find((f) => f.token.toLowerCase() === token);
    if (!fund) return badRequest('Unknown token');
    const vni = MOCK_VNI_1E8[fund.token] ?? '0';
    return json({
      token: fund.token,
      vni,
      updatedAt: String(Math.floor(Date.now() / 1000)),
      volatilityBps: MOCK_VOL_BPS[fund.token] ?? 0,
    });
  }

  // ---------- Pool quotes ----------
  if (req.method === 'POST' && req.url === '/api/blockchain/pool/quote-buy') {
    const body = (req.body ?? {}) as any;
    const token = body.token as string;
    const tndIn = body.tndIn as string;
    const vni = Number(MOCK_VNI_1E8[token] ?? '0');
    if (!vni) return badRequest('VNI not set');

    // Simple approximation: spread 0.20%
    const priceClient = Math.floor((vni * (BPS + 20)) / BPS);
    const feeBase = Math.floor((Number(tndIn) * 100) / BPS); // 1%
    const vat = Math.floor((feeBase * VAT_BPS) / BPS);
    const totalFee = feeBase + vat;
    const netTnd = Number(tndIn) - totalFee;
    const minted = Math.floor((netTnd * PRICE_SCALE) / priceClient);

    const res: QuoteBuyResponse = {
      priceClient: String(priceClient),
      minted: String(minted),
      feeBase: String(feeBase),
      vat: String(vat),
      totalFee: String(totalFee),
    };
    return json(res);
  }

  if (req.method === 'POST' && req.url === '/api/blockchain/pool/quote-sell') {
    const body = (req.body ?? {}) as any;
    const token = body.token as string;
    const tokenAmount = body.tokenAmount as string;
    const vni = Number(MOCK_VNI_1E8[token] ?? '0');
    if (!vni) return badRequest('VNI not set');

    // Simple approximation: spread 0.20%
    const priceClient = Math.floor((vni * (BPS - 20)) / BPS);
    const grossTnd = Math.floor((Number(tokenAmount) * priceClient) / PRICE_SCALE);
    const feeBase = Math.floor((grossTnd * 100) / BPS); // 1%
    const vat = Math.floor((feeBase * VAT_BPS) / BPS);
    const totalFee = feeBase + vat;
    const tax = 0; // MVP mock: not computing PRM here
    const tndOut = grossTnd - totalFee - tax;

    const res: QuoteSellResponse = {
      priceClient: String(priceClient),
      tndOut: String(tndOut),
      feeBase: String(feeBase),
      vat: String(vat),
      totalFee: String(totalFee),
      tax: String(tax),
    };
    return json(res);
  }

  // ---------- Pool execution (mock) ----------
  if (req.method === 'POST' && (req.url === '/api/blockchain/pool/buy' || req.url === '/api/blockchain/pool/sell')) {
    const res: TxResponse = { status: 'submitted', txHash: '0xmocked', message: 'Mock mode (no backend yet)' };
    return json(res, 250);
  }

  // ---------- P2P execution (mock) ----------
  if (req.method === 'POST' && req.url === '/api/blockchain/p2p/settle') {
    const res: TxResponse = { status: 'submitted', txHash: '0xmocked', message: 'Mock mode (no backend yet)' };
    return json(res, 250);
  }

  return next(req);
};

