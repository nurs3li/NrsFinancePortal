import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { jsPDF } from 'jspdf';
import autoTable from 'jspdf-autotable';
import { ArrowDownRight, ArrowUpRight, RotateCcw } from 'lucide-react';
import { financeClient } from '../api/client';
import { useTheme } from '../theme/ThemeContext';
import { useRefetchOnFocus } from '../hooks/useRefetchOnFocus';
import { usePolling } from '../hooks/usePolling';
import { useLanguage } from '../i18n/LanguageContext';
import { registerSimulationPdfFont, SIMULATION_PDF_FONT_FAMILY } from '../utils/simulationPdfFont';
import './Transactions.css';

type TransactionRow = {
    id: number;
    accountId: number;
    amount: number;
    balanceAfter: number;
    type: string;
    createdAt: string;
};

type SpringPage<T> = {
    content?: T[];
    last?: boolean;
};

type TypeFilter = 'ALL' | 'DEPOSIT' | 'WITHDRAW';

const FETCH_BATCH = 100;
const PAGE_SIZE = 11;

function unwrapPayload<T>(payload: unknown): T {
    if (payload && typeof payload === 'object' && 'data' in (payload as object)) {
        return (payload as { data: T }).data;
    }
    return payload as T;
}

function toISOStartOfDay(date: Date): string {
    const d = new Date(date);
    d.setHours(0, 0, 0, 0);
    return d.toISOString();
}

function toISOEndOfDay(date: Date): string {
    const d = new Date(date);
    d.setHours(23, 59, 59, 999);
    return d.toISOString();
}

function escapeCsvCell(v: string): string {
    if (/[;"\n]/.test(v)) return `"${v.replace(/"/g, '""')}"`;
    return v;
}

function formatTryAmount(value: number, locale: string): string {
    const sign = value > 0 ? '+' : value < 0 ? '−' : '';
    const abs = Math.abs(value);
    return `${sign}${abs.toLocaleString(locale, { minimumFractionDigits: 2, maximumFractionDigits: 2 })} ₺`;
}

function formatTryPlain(value: number, locale: string): string {
    return `${Number(value).toLocaleString(locale, { minimumFractionDigits: 2, maximumFractionDigits: 2 })} ₺`;
}

function signedDisplay(tx: TransactionRow): { signed: number; className: string } {
    const raw = Number(tx.amount);
    switch (tx.type) {
        case 'DEPOSIT':
            return { signed: raw, className: 'tx-amt--in' };
        case 'WITHDRAW':
            return { signed: -raw, className: 'tx-amt--out' };
        case 'REVERSAL':
            return { signed: raw, className: 'tx-amt--neutral' };
        default:
            return { signed: raw, className: 'tx-balance' };
    }
}

function filterRows(
    rows: TransactionRow[],
    typeFilter: TypeFilter,
    minAmount: number | null,
    maxAmount: number | null
): TransactionRow[] {
    return rows.filter((tx) => {
        if (typeFilter === 'DEPOSIT' && tx.type !== 'DEPOSIT') return false;
        if (typeFilter === 'WITHDRAW' && tx.type !== 'WITHDRAW') return false;
        const amt = Math.abs(Number(tx.amount));
        if (minAmount != null && amt < minAmount) return false;
        if (maxAmount != null && amt > maxAmount) return false;
        return true;
    });
}

function buildPageNumberItems(page: number, pageCount: number): (number | 'gap')[] {
    if (pageCount <= 1) return [];
    if (pageCount <= 9) {
        return Array.from({ length: pageCount }, (_, i) => i);
    }
    const set = new Set<number>();
    set.add(0);
    set.add(pageCount - 1);
    for (let d = -2; d <= 2; d++) {
        const p = page + d;
        if (p >= 0 && p < pageCount) set.add(p);
    }
    const sorted = [...set].sort((a, b) => a - b);
    const out: (number | 'gap')[] = [];
    for (let i = 0; i < sorted.length; i++) {
        if (i > 0 && sorted[i]! - sorted[i - 1]! > 1) out.push('gap');
        out.push(sorted[i]!);
    }
    return out;
}

export function Transactions() {
    const { tokens } = useTheme();
    const { t, lang } = useLanguage();
    const locale = lang === 'en' ? 'en-US' : 'tr-TR';

    const [allItems, setAllItems] = useState<TransactionRow[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const [page, setPage] = useState(0);
    const [startDate, setStartDate] = useState('');
    const [endDate, setEndDate] = useState('');
    const [typeFilter, setTypeFilter] = useState<TypeFilter>('ALL');
    const [minAmountStr, setMinAmountStr] = useState('');
    const [maxAmountStr, setMaxAmountStr] = useState('');

    const minAmount = useMemo(() => {
        const n = parseFloat(minAmountStr.replace(',', '.'));
        return minAmountStr.trim() === '' || Number.isNaN(n) ? null : n;
    }, [minAmountStr]);

    const maxAmount = useMemo(() => {
        const n = parseFloat(maxAmountStr.replace(',', '.'));
        return maxAmountStr.trim() === '' || Number.isNaN(n) ? null : n;
    }, [maxAmountStr]);

    const filteredItems = useMemo(
        () => filterRows(allItems, typeFilter, minAmount, maxAmount),
        [allItems, typeFilter, minAmount, maxAmount]
    );

    const pageCount = Math.max(1, Math.ceil(filteredItems.length / PAGE_SIZE));

    useEffect(() => {
        setPage((p) => Math.min(p, pageCount - 1));
    }, [pageCount, filteredItems.length]);

    const pageSlice = useMemo(() => {
        const start = page * PAGE_SIZE;
        return filteredItems.slice(start, start + PAGE_SIZE);
    }, [filteredItems, page]);

    const fetchAllTransactions = useCallback(async () => {
        setLoading(true);
        setError(null);
        const useRange = Boolean(startDate && endDate);
        const url = useRange ? '/api/transactions/me/range' : '/api/transactions/me';
        const collected: TransactionRow[] = [];
        let pageNum = 0;
        try {
            for (;;) {
                const params: Record<string, string | number> = { page: pageNum, size: FETCH_BATCH };
                if (useRange) {
                    params.start = toISOStartOfDay(new Date(startDate));
                    params.end = toISOEndOfDay(new Date(endDate));
                }
                const res = await financeClient.get(url, { params });
                const body = unwrapPayload<SpringPage<TransactionRow>>(res.data);
                const chunk = body.content ?? [];
                collected.push(...chunk);
                const isLast = body.last === true || chunk.length < FETCH_BATCH;
                if (isLast || chunk.length === 0) break;
                pageNum += 1;
            }
            setAllItems(collected);
        } catch (err: any) {
            setError(err.response?.data?.message ?? err.message ?? t('common.loadingFailed', 'Yüklenemedi'));
            setAllItems([]);
        } finally {
            setLoading(false);
        }
    }, [startDate, endDate, t]);

    useEffect(() => {
        void fetchAllTransactions();
    }, [fetchAllTransactions]);

    useRefetchOnFocus(fetchAllTransactions);
    usePolling(fetchAllTransactions, 60_000);

    useEffect(() => {
        setPage(0);
    }, [typeFilter, minAmountStr, maxAmountStr]);

    const typeLabel = useCallback(
        (txType: string) => {
            switch (txType) {
                case 'DEPOSIT':
                    return t('transactions.typeDeposit', 'Para Yatırma');
                case 'WITHDRAW':
                    return t('transactions.typeWithdraw', 'Para Çekme');
                case 'REVERSAL':
                    return t('transactions.typeReversal', 'İade / Düzeltme');
                default:
                    return txType;
            }
        },
        [t]
    );

    const handleFilterApply = () => {
        setPage(0);
        void fetchAllTransactions();
    };

    const handleClear = () => {
        setStartDate('');
        setEndDate('');
        setTypeFilter('ALL');
        setMinAmountStr('');
        setMaxAmountStr('');
        setPage(0);
    };

    const typeIcon = (txType: string): ReactNode => {
        const up = '#39ff14';
        const down = '#f87171';
        const neu = '#fbbf24';
        switch (txType) {
            case 'DEPOSIT':
                return <ArrowUpRight className="tx-type-icon" size={18} strokeWidth={2.25} color={up} aria-hidden />;
            case 'WITHDRAW':
                return <ArrowDownRight className="tx-type-icon" size={18} strokeWidth={2.25} color={down} aria-hidden />;
            case 'REVERSAL':
                return <RotateCcw className="tx-type-icon" size={17} strokeWidth={2.25} color={neu} aria-hidden />;
            default:
                return null;
        }
    };

    const exportCsv = () => {
        if (filteredItems.length === 0) return;
        const sep = ';';
        const header = [
            t('news.date', 'Tarih'),
            t('transactions.type', 'Tür'),
            t('transactions.amount', 'Tutar'),
            t('transactions.balanceAfter', 'Bakiye (sonra)'),
        ];
        const lines = filteredItems.map((tx) => {
            const { signed } = signedDisplay(tx);
            return [
                new Date(tx.createdAt).toLocaleString(locale),
                typeLabel(tx.type),
                formatTryAmount(signed, locale),
                formatTryPlain(Number(tx.balanceAfter), locale),
            ];
        });
        const csv =
            '\uFEFF' +
            [header, ...lines].map((row) => row.map((c) => escapeCsvCell(String(c))).join(sep)).join('\n');
        const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = `nrs-islem-gecmisi-${new Date().toISOString().slice(0, 10)}.csv`;
        a.click();
        URL.revokeObjectURL(a.href);
    };

    const exportPdf = async () => {
        if (filteredItems.length === 0) return;
        const doc = new jsPDF({ orientation: 'landscape', unit: 'mm', format: 'a4' });
        let tableFont = SIMULATION_PDF_FONT_FAMILY;
        try {
            await registerSimulationPdfFont(doc);
        } catch {
            tableFont = 'helvetica';
            doc.setFont('helvetica', 'normal');
        }
        doc.setFontSize(11);
        doc.text(t('nav.transactions', 'İşlem Geçmişi'), 10, 12);
        const head = [
            [
                t('news.date', 'Tarih'),
                t('transactions.type', 'Tür'),
                t('transactions.amount', 'Tutar'),
                t('transactions.balanceAfter', 'Bakiye (sonra)'),
            ],
        ];
        const body = filteredItems.map((tx) => {
            const { signed } = signedDisplay(tx);
            return [
                new Date(tx.createdAt).toLocaleString(locale),
                typeLabel(tx.type),
                formatTryAmount(signed, locale),
                formatTryPlain(Number(tx.balanceAfter), locale),
            ];
        });
        autoTable(doc, {
            startY: 16,
            head,
            body,
            styles: {
                font: tableFont,
                fontStyle: 'normal',
                fontSize: 8,
                cellPadding: 1.5,
                textColor: [25, 28, 35],
            },
            headStyles: {
                font: tableFont,
                fontStyle: 'normal',
                fillColor: [16, 22, 35],
                textColor: [235, 238, 245],
            },
            margin: { left: 10, right: 10 },
        });
        doc.save(`nrs-islem-gecmisi-${new Date().toISOString().slice(0, 10)}.pdf`);
    };

    const pageItems = buildPageNumberItems(page, pageCount);

    if (error) {
        return (
            <div className="tx-page" style={{ background: tokens.bg, color: tokens.text }}>
                <div className="tx-container">
                    <h1 className="tx-title">{t('nav.transactions', 'İşlem Geçmişi')}</h1>
                    <p style={{ color: tokens.error }}>
                        {t('news.errorPrefix', 'Hata')}: {error}
                    </p>
                </div>
            </div>
        );
    }

    return (
        <div className="tx-page" style={{ background: tokens.bg, color: tokens.text }}>
            <div className="tx-container">
                <h1 className="tx-title">{t('nav.transactions', 'İşlem Geçmişi')}</h1>
                <p className="tx-subtitle" style={{ color: tokens.textMuted }}>
                    {t('transactions.subtitle', 'Son işlemleriniz. Tarih aralığı seçerek filtreleyebilirsiniz.')}
                </p>

                <div className="tx-filters">
                    <div className="tx-filters-main">
                        <label className="tx-label">
                            {t('transactions.start', 'Başlangıç')}
                            <input
                                type="date"
                                className="tx-input"
                                value={startDate}
                                onChange={(e) => setStartDate(e.target.value)}
                            />
                        </label>
                        <label className="tx-label">
                            {t('transactions.end', 'Bitiş')}
                            <input
                                type="date"
                                className="tx-input"
                                value={endDate}
                                onChange={(e) => setEndDate(e.target.value)}
                            />
                        </label>

                        <div className="tx-label">
                            {t('transactions.filterType', 'Tür')}
                            <div className="tx-segment" role="group" aria-label={t('transactions.filterType', 'Tür')}>
                                {(
                                    [
                                        ['ALL', t('transactions.segmentAll', 'Hepsi')],
                                        ['DEPOSIT', t('transactions.segmentDeposit', 'Para Yatırma')],
                                        ['WITHDRAW', t('transactions.segmentWithdraw', 'Para Çekme')],
                                    ] as const
                                ).map(([val, label]) => (
                                    <button
                                        key={val}
                                        type="button"
                                        className={typeFilter === val ? 'tx-segment--active' : ''}
                                        onClick={() => setTypeFilter(val)}
                                    >
                                        {label}
                                    </button>
                                ))}
                            </div>
                        </div>

                        <label className="tx-label">
                            {t('transactions.minAmount', 'Min. tutar')}
                            <input
                                type="number"
                                inputMode="decimal"
                                step="0.01"
                                min={0}
                                className="tx-input"
                                style={{ width: 120 }}
                                value={minAmountStr}
                                onChange={(e) => setMinAmountStr(e.target.value)}
                                placeholder="0"
                            />
                        </label>
                        <label className="tx-label">
                            {t('transactions.maxAmount', 'Max. tutar')}
                            <input
                                type="number"
                                inputMode="decimal"
                                step="0.01"
                                min={0}
                                className="tx-input"
                                style={{ width: 120 }}
                                value={maxAmountStr}
                                onChange={(e) => setMaxAmountStr(e.target.value)}
                                placeholder="∞"
                            />
                        </label>

                        <button type="button" className="tx-btn tx-btn--primary" onClick={handleFilterApply}>
                            {t('common.filter', 'Filtrele')}
                        </button>
                        <button type="button" className="tx-btn tx-btn--ghost" onClick={handleClear}>
                            {t('transactions.clearFilters', 'Temizle')}
                        </button>
                    </div>

                    <div className="tx-filters-actions">
                        <button
                            type="button"
                            className="tx-btn tx-btn--export"
                            onClick={exportCsv}
                            disabled={loading || filteredItems.length === 0}
                        >
                            {t('transactions.exportCsv', 'CSV indir')}
                        </button>
                        <button
                            type="button"
                            className="tx-btn tx-btn--export"
                            onClick={() => void exportPdf()}
                            disabled={loading || filteredItems.length === 0}
                        >
                            {t('transactions.exportPdf', 'PDF al')}
                        </button>
                    </div>
                </div>

                <div className="tx-card-premium">
                    {loading ? (
                        <p className="tx-muted">{t('common.loading', 'Yükleniyor...')}</p>
                    ) : filteredItems.length === 0 ? (
                        <p className="tx-muted">{t('transactions.empty', 'Henüz işlem yok.')}</p>
                    ) : (
                        <>
                            <div className="tx-table-scroll">
                                <table className="tx-table">
                                    <thead>
                                        <tr>
                                            <th>{t('news.date', 'Tarih')}</th>
                                            <th>{t('transactions.type', 'Tür')}</th>
                                            <th className="tx-th-num">{t('transactions.amount', 'Tutar')}</th>
                                            <th className="tx-th-num">{t('transactions.balanceAfter', 'Bakiye (sonra)')}</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {pageSlice.map((tx) => {
                                            const { signed, className } = signedDisplay(tx);
                                            return (
                                                <tr key={tx.id} className="tx-row">
                                                    <td>{new Date(tx.createdAt).toLocaleString(locale)}</td>
                                                    <td>
                                                        <span className="tx-type-cell">
                                                            {typeIcon(tx.type)}
                                                            {typeLabel(tx.type)}
                                                        </span>
                                                    </td>
                                                    <td className={className}>{formatTryAmount(signed, locale)}</td>
                                                    <td className="tx-balance">{formatTryPlain(Number(tx.balanceAfter), locale)}</td>
                                                </tr>
                                            );
                                        })}
                                    </tbody>
                                </table>
                            </div>

                            {pageCount > 1 && (
                                <div className="tx-pagination">
                                    <button
                                        type="button"
                                        className="tx-page-btn"
                                        disabled={page <= 0}
                                        onClick={() => setPage((p) => Math.max(0, p - 1))}
                                    >
                                        {t('transactions.prevPage', 'Önceki')}
                                    </button>
                                    <div className="tx-pagination-pages">
                                        {pageItems.map((item, idx) =>
                                            item === 'gap' ? (
                                                <span key={`g-${idx}`} className="tx-muted">
                                                    …
                                                </span>
                                            ) : (
                                                <button
                                                    key={item}
                                                    type="button"
                                                    className={`tx-page-btn ${item === page ? 'tx-page-btn--active' : ''}`}
                                                    onClick={() => setPage(item)}
                                                >
                                                    {item + 1}
                                                </button>
                                            )
                                        )}
                                    </div>
                                    <button
                                        type="button"
                                        className="tx-page-btn"
                                        disabled={page >= pageCount - 1}
                                        onClick={() => setPage((p) => Math.min(pageCount - 1, p + 1))}
                                    >
                                        {t('transactions.nextPage', 'Sonraki')}
                                    </button>
                                    <span className="tx-muted">
                                        {filteredItems.length} {t('transactions.records', 'kayıt')}
                                    </span>
                                </div>
                            )}
                        </>
                    )}
                </div>
            </div>
        </div>
    );
}
