import { marketClient } from '../api/client';

export type BankFxRateRow = {
    bankCode: string;
    bankName: string;
    currency: string;
    buy: number;
    sell: number;
    changePct: number | null;
    quoteTime: string | null;
    trend: 'UP' | 'DOWN' | 'FLAT';
};

export type TcmbReference = {
    buy: number;
    sell: number;
};

export type BankRatesBoard = {
    source: string;
    currency: string;
    fetchedAt: string | null;
    stale: boolean;
    rows: BankFxRateRow[];
    tcmb: TcmbReference | null;
    attribution: string;
    notes: string[];
};

export async function fetchBankRatesBoard(currency: string, signal?: AbortSignal): Promise<BankRatesBoard> {
    const { data } = await marketClient.get<BankRatesBoard>('/api/market/bank-rates/board', {
        params: { currency },
        signal,
    });
    return (
        data ?? {
            source: 'DOVIZBORSA',
            currency,
            fetchedAt: null,
            stale: true,
            rows: [],
            tcmb: null,
            attribution: 'Kaynak: dovizborsa.com',
            notes: [],
        }
    );
}
