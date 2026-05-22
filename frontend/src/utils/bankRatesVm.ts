import type { BankFxRateRow, BankRatesBoard } from '../services/bankRatesApi';

export type BankSortMode =
    | 'spread_asc'
    | 'spread_desc'
    | 'buy_desc'
    | 'sell_asc'
    | 'name_asc'
    | 'change_desc';

export type EnrichedBankRow = BankFxRateRow & {
    spread: number;
    spreadPct: number;
};

export type BankRatesKpis = {
    bestBuy: EnrichedBankRow | null;
    bestSell: EnrichedBankRow | null;
    narrowestSpread: EnrichedBankRow | null;
};

export function enrichBankRows(board: BankRatesBoard | undefined): EnrichedBankRow[] {
    if (!board?.rows?.length) return [];
    return board.rows.map((row) => {
        const buy = Number(row.buy);
        const sell = Number(row.sell);
        const spread = sell - buy;
        const mid = (buy + sell) / 2;
        const spreadPct = mid > 0 ? (spread / mid) * 100 : 0;
        return {
            ...row,
            spread,
            spreadPct,
        };
    });
}

export function sortBankRows(rows: EnrichedBankRow[], mode: BankSortMode): EnrichedBankRow[] {
    const copy = [...rows];
    switch (mode) {
        case 'spread_desc':
            return copy.sort((a, b) => b.spread - a.spread);
        case 'buy_desc':
            return copy.sort((a, b) => b.buy - a.buy);
        case 'sell_asc':
            return copy.sort((a, b) => a.sell - b.sell);
        case 'name_asc':
            return copy.sort((a, b) => a.bankName.localeCompare(b.bankName, 'tr'));
        case 'change_desc':
            return copy.sort((a, b) => (b.changePct ?? 0) - (a.changePct ?? 0));
        case 'spread_asc':
        default:
            return copy.sort((a, b) => a.spread - b.spread);
    }
}

export function computeKpis(rows: EnrichedBankRow[]): BankRatesKpis {
    if (!rows.length) {
        return {
            bestBuy: null,
            bestSell: null,
            narrowestSpread: null,
        };
    }
    let bestBuy = rows[0];
    let bestSell = rows[0];
    let narrowest = rows[0];
    for (const r of rows) {
        if (r.buy > bestBuy.buy) bestBuy = r;
        if (r.sell < bestSell.sell) bestSell = r;
        if (r.spread < narrowest.spread) narrowest = r;
    }
    return { bestBuy, bestSell, narrowestSpread: narrowest };
}

export function exportBankRatesCsv(rows: EnrichedBankRow[], currency: string): void {
    const header = [
        'Banka',
        'Kod',
        `${currency} Alis`,
        `${currency} Satis`,
        'Makas',
        'Makas %',
        'Degisim %',
        'Saat',
    ];
    const lines = rows.map((r) =>
        [
            r.bankName,
            r.bankCode,
            r.buy,
            r.sell,
            r.spread.toFixed(4),
            r.spreadPct.toFixed(2),
            r.changePct ?? '',
            r.quoteTime ?? '',
        ].join(';'),
    );
    const blob = new Blob(['\ufeff' + [header.join(';'), ...lines].join('\n')], {
        type: 'text/csv;charset=utf-8',
    });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `banka-kurlari-${currency.toLowerCase()}.csv`;
    a.click();
    URL.revokeObjectURL(url);
}

export const BANK_BRAND: Record<string, { abbr: string; color: string }> = {
    Akbank: { abbr: 'AK', color: '#e4002b' },
    Albaraka: { abbr: 'AL', color: '#006341' },
    Denizbank: { abbr: 'DN', color: '#0033a0' },
    Finansbank: { abbr: 'FI', color: '#6e2c91' },
    'Garanti BBVA': { abbr: 'GA', color: '#00a651' },
    Garanti: { abbr: 'GA', color: '#00a651' },
    Halkbank: { abbr: 'HA', color: '#0072bc' },
    HSBC: { abbr: 'HS', color: '#db0011' },
    'ING Bank': { abbr: 'IN', color: '#ff6200' },
    'İş Bankası': { abbr: 'İŞ', color: '#004899' },
    Kuveyttürk: { abbr: 'KV', color: '#006847' },
    Şekerbank: { abbr: 'ŞK', color: '#8dc63f' },
    TEB: { abbr: 'TE', color: '#00a0e3' },
    ICBC: { abbr: 'IC', color: '#c8102e' },
    Vakıfbank: { abbr: 'VK', color: '#f9b233' },
    'Yapı Kredi': { abbr: 'YP', color: '#004990' },
    'QNB Finansbank': { abbr: 'QNB', color: '#7b0041' },
    Ziraat: { abbr: 'ZR', color: '#c41230' },
};

export function bankBrand(bankName: string): { abbr: string; color: string } {
    const direct = BANK_BRAND[bankName];
    if (direct) return direct;
    for (const [key, val] of Object.entries(BANK_BRAND)) {
        if (bankName.includes(key) || key.includes(bankName)) return val;
    }
    return { abbr: bankName.slice(0, 2).toUpperCase(), color: '#64748b' };
}
