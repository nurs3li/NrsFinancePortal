import { useMemo, useState } from 'react';
import { Download } from 'lucide-react';
import type { EnrichedBankRow } from '../../utils/bankRatesVm';
import { exportBankRatesCsv } from '../../utils/bankRatesVm';
import { BankLogo } from './BankLogo';

type Props = {
    rows: EnrichedBankRow[];
    currency: string;
    locale: string;
    tokens: { bgCard: string; border: string; text: string; textMuted: string };
    t: (key: string, fallback?: string) => string;
};

const PAGE_SIZE = 10;

function fmt(n: number | null | undefined, locale: string) {
    if (n == null || !Number.isFinite(Number(n))) return '—';
    return Number(n).toLocaleString(locale, { minimumFractionDigits: 4, maximumFractionDigits: 4 });
}

export function BankRatesComparisonTable({ rows, currency, locale, tokens, t }: Props) {
    const [page, setPage] = useState(0);
    const totalPages = Math.max(1, Math.ceil(rows.length / PAGE_SIZE));
    const pageRows = useMemo(() => rows.slice(page * PAGE_SIZE, page * PAGE_SIZE + PAGE_SIZE), [rows, page]);

    return (
        <section className="br-table-panel" style={{ background: tokens.bgCard, borderColor: tokens.border }}>
            <div className="br-table-panel__head">
                <h2>
                    {t('bankRates.tableTitle', 'Banka Kur Karşılaştırması')} ({currency})
                </h2>
                <button
                    type="button"
                    className="br-btn br-btn--ghost"
                    onClick={() => exportBankRatesCsv(rows, currency)}
                    disabled={!rows.length}
                >
                    <Download size={16} />
                    {t('bankRates.exportExcel', 'Excel İndir')}
                </button>
            </div>
            <div className="br-table-wrap">
                <table className="br-table">
                    <thead>
                        <tr>
                            <th>#</th>
                            <th>{t('bankRates.colBank', 'Banka')}</th>
                            <th>{currency} {t('bankRates.colBuy', 'Alış')}</th>
                            <th>{currency} {t('bankRates.colSell', 'Satış')}</th>
                            <th>{t('bankRates.colSpreadTry', 'Makas (₺)')}</th>
                            <th>{t('bankRates.colSpreadPct', 'Makas (%)')}</th>
                            <th>{t('bankRates.colTime', 'Kaynak saati')}</th>
                        </tr>
                    </thead>
                    <tbody>
                        {pageRows.map((row, idx) => {
                            return (
                                <tr key={row.bankCode}>
                                    <td>{page * PAGE_SIZE + idx + 1}</td>
                                    <td>
                                        <div className="br-table-bank">
                                            <BankLogo bankName={row.bankName} size="sm" />
                                            {row.bankName}
                                        </div>
                                    </td>
                                    <td>{fmt(row.buy, locale)}</td>
                                    <td>{fmt(row.sell, locale)}</td>
                                    <td>{fmt(row.spread, locale)}</td>
                                    <td>{row.spreadPct.toFixed(2)}%</td>
                                    <td style={{ color: tokens.textMuted }}>{row.quoteTime ?? '—'}</td>
                                </tr>
                            );
                        })}
                    </tbody>
                </table>
            </div>
            {totalPages > 1 ? (
                <div className="br-table-pager">
                    <button
                        type="button"
                        className="br-btn br-btn--ghost"
                        disabled={page <= 0}
                        onClick={() => setPage((p) => Math.max(0, p - 1))}
                    >
                        ←
                    </button>
                    <span style={{ color: tokens.textMuted }}>
                        {page + 1} / {totalPages}
                    </span>
                    <button
                        type="button"
                        className="br-btn br-btn--ghost"
                        disabled={page >= totalPages - 1}
                        onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                    >
                        →
                    </button>
                </div>
            ) : null}
        </section>
    );
}
