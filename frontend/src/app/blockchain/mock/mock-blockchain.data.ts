import type { Fund } from '../models/fund.models';

// Simple in-memory mock data to unblock UI before Spring Boot exists.
export const MOCK_FUNDS: Fund[] = [
  {
    id: 0,
    name: 'CPEF Atlas',
    symbol: '$ATLAS$',
    token: '0x8e0BfED44D5B63812d0693FB248AfA1892dDc036',
    pool: '0x1D39e79c59b7705ec57F65aCb6420E43a3495f84',
    oracle: '0xA1Ee49A7156D264f4F6f886c03726b296A0A3dbD',
    createdAt: new Date().toISOString(),
  },
  {
    id: 1,
    name: 'CPEF Didon',
    symbol: '$DIDON$',
    token: '0x0Fa25a25af5f831d3d66A59CF9e5AdE1018bC9e3',
    pool: '0x40CFa95C804feaF1d0eEf82072e143F424CE0030',
    oracle: '0x66A17eC6720cF120bA9F6512B529D8D80E5cd1D7',
    createdAt: new Date().toISOString(),
  },
];

// VNI in 1e8 (TND per token)
export const MOCK_VNI_1E8: Record<string, string> = {
  [MOCK_FUNDS[0].token]: String(125_50000000), // 125.50
  [MOCK_FUNDS[1].token]: String(85_25000000), // 85.25
};

export const MOCK_VOL_BPS: Record<string, number> = {
  [MOCK_FUNDS[0].token]: 250,
  [MOCK_FUNDS[1].token]: 180,
};

