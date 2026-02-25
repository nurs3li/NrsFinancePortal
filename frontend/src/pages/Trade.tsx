import { useEffect, useState } from 'react';
import { financeClient } from '../api/client';
import { useTheme } from '../theme/ThemeContext';

type AssetType = 'CRYPTO' | 'FX' | 'FUND' | 'METAL' | string;
type TradeType = 'BUY' | 'SELL';

type TradeRequest = {
    assetType: AssetType;
    symbol: string;
    quantity: number;
    tradeType: TradeType;
};

type TradeResponse = {
    symbol: string;
    quantity: number;
    tryPrice: number;
    totalTry: number;
    balanceAfter: number;
    timestamp: string;
};

type TradeHistoryItem = {
    tradeId: number;
    tradeType: TradeType;
    assetType: AssetType;
    symbol: string;
    quantity: number;
    totalTry: number;
    balanceAfter: number;
    tradedAt: string;
};

type Page<T> = {
    content: T[];
    totalElements: number;
    number: number;
    size: number;
};

const ASSET_TYPES: AssetType[] = ['CRYPTO', 'FX', 'FUND', 'METAL'];

export function Trade() {
    const { tokens } = useTheme();

    const [assetType, setAssetType] = useState<AssetType>('CRYPTO');
    const [symbol, setSymbol] = useState('BTCUSDT');
    const [tradeType, setTradeType] = useState<TradeType>('BUY');
    const [quantity, setQuantity] = useState<string>('0.1');

    const [submitting, setSubmitting] = useState(false);
    const [submitError, setSubmitError] = useState<string | null>(null);
    const [lastTrade, setLastTrade] = useState<TradeResponse | null>(null);

    const [history, setHistory] = useState<Page<TradeHistoryItem> | null>(null);
    const [historyLoading, setHistoryLoading] = useState(true);
    const [historyError, setHistoryError] = useState<string | null>(null);
    const [historyPage, setHistoryPage] = useState(0);

    const pageSize = 10;

    const formatMoney = (v: number) =>
        '₺' + v.toLocaleString('tr-TR', { maximumFractionDigits: 2 });

    const loadHistory = () => {
        setHistoryLoading(true);
        setHistoryError(null);
        financeClient
            .get<Page<TradeHistoryItem>>('/api/trades/history', {
                params: { page: historyPage, size: pageSize },
            })
            .then((res) => {
                const raw = (res.data as any)?.data ?? res.data;
                setHistory(raw);
            })
            .catch((err) => {
                const msg = err.response?.data?.message ?? err.message ?? 'Hata';
                setHistoryError(msg);
            })
            .finally(() => setHistoryLoading(false));
    };

    useEffect(() => {
        loadHistory();
    }, [historyPage]);

    const handleSubmit = (e: React.FormEvent) => {
        e.preventDefault();
        const qty = Number(quantity);
        if (!symbol.trim() || isNaN(qty) || qty <= 0) {
            setSubmitError('Sembol ve miktar alanlarını kontrol edin.');
            return;
        }
        const payload: TradeRequest = {
            assetType,
            symbol: symbol.trim(),
            quantity: qty,
            tradeType,
        };
        setSubmitting(true);
        setSubmitError(null);
        financeClient
            .post<{ data: TradeResponse }>('/api/trades', payload)
            .then((res) => {
                const data = (res.data as any)?.data ?? res.data;
                setLastTrade(data);
                setHistoryPage(0);
                setTimeout(loadHistory, 200);
            })
            .catch((err) => {
                const msg =
                    err.response?.data?.errors?.error ??
                    err.response?.data?.message ??
                    err.message ??
                    'Emir gönderilirken hata oluştu.';
                setSubmitError(msg);
            })
            .finally(() => setSubmitting(false));
    };

    const pageStyle: React.CSSProperties = {
        padding: 24,
        background: tokens.bg,
        color: tokens.text,
        minHeight: '100%',
    };
    const titleStyle: React.CSSProperties = { fontSize: '1.75rem', fontWeight: 700, marginBottom: 8 };
    const mutedStyle: React.CSSProperties = { color: tokens.textMuted, fontSize: '0.875rem', marginBottom: 24 };
    const cardStyle: React.CSSProperties = {
        padding: 16,
        borderRadius: 12,
        background: tokens.bgCard,
        border: `1px solid ${tokens.border}`,
    };
    const inputStyle: React.CSSProperties = {
        marginTop: 4,
        width: '100%',
        padding: 8,
        borderRadius: 8,
        border: `1px solid ${tokens.border}`,
        background: tokens.inputBg,
        color: tokens.text,
        fontSize: '0.9375rem',
    };

    return (
        <div style={pageStyle}>
            <h1 style={titleStyle}>Alım &amp; Satım</h1>
            <p style={mutedStyle}>
                Demo hesabın üzerinden hızlıca trade yap, işlemlerini aşağıdaki listeden takip et.
            </p>

            <div
                style={{
                    display: 'grid',
                    gridTemplateColumns: 'minmax(0, 1.3fr) minmax(0, 2fr)',
                    gap: 24,
                    marginBottom: 32,
                }}
            >
                <div style={cardStyle}>
                    <h2 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: 16 }}>Emir Formu</h2>
                    <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                        <label style={{ fontSize: '0.875rem' }}>
                            Varlık Türü
                            <select
                                value={assetType}
                                onChange={(e) => setAssetType(e.target.value as AssetType)}
                                style={inputStyle}
                            >
                                {ASSET_TYPES.map((t) => (
                                    <option key={t} value={t}>{t}</option>
                                ))}
                            </select>
                        </label>
                        <label style={{ fontSize: '0.875rem' }}>
                            Sembol
                            <input
                                value={symbol}
                                onChange={(e) => setSymbol(e.target.value)}
                                placeholder="Örn: BTCUSDT, USDTRY"
                                style={inputStyle}
                            />
                        </label>
                        <label style={{ fontSize: '0.875rem' }}>
                            Yön
                            <div style={{ display: 'flex', gap: 8, marginTop: 4 }}>
                                <button
                                    type="button"
                                    onClick={() => setTradeType('BUY')}
                                    style={{
                                        flex: 1,
                                        padding: '8px 0',
                                        borderRadius: 8,
                                        border: tradeType === 'BUY' ? `2px solid ${tokens.success}` : `1px solid ${tokens.border}`,
                                        background: tradeType === 'BUY' ? 'rgba(34, 197, 94, 0.15)' : tokens.bgCard,
                                        color: tradeType === 'BUY' ? tokens.success : tokens.text,
                                        cursor: 'pointer',
                                        fontSize: '0.875rem',
                                        fontWeight: 500,
                                    }}
                                >
                                    Al (BUY)
                                </button>
                                <button
                                    type="button"
                                    onClick={() => setTradeType('SELL')}
                                    style={{
                                        flex: 1,
                                        padding: '8px 0',
                                        borderRadius: 8,
                                        border: tradeType === 'SELL' ? '2px solid #f97316' : `1px solid ${tokens.border}`,
                                        background: tradeType === 'SELL' ? 'rgba(249, 115, 22, 0.15)' : tokens.bgCard,
                                        color: tradeType === 'SELL' ? '#f97316' : tokens.text,
                                        cursor: 'pointer',
                                        fontSize: '0.875rem',
                                        fontWeight: 500,
                                    }}
                                >
                                    Sat (SELL)
                                </button>
                            </div>
                        </label>
                        <label style={{ fontSize: '0.875rem' }}>
                            Miktar
                            <input
                                type="number"
                                step="0.0001"
                                value={quantity}
                                onChange={(e) => setQuantity(e.target.value)}
                                style={inputStyle}
                            />
                        </label>
                        {submitError && (
                            <div style={{ color: tokens.error, fontSize: '0.8125rem' }}>{submitError}</div>
                        )}
                        <button
                            type="submit"
                            disabled={submitting}
                            style={{
                                marginTop: 8,
                                padding: '10px 0',
                                borderRadius: 8,
                                border: 'none',
                                background: tradeType === 'BUY'
                                    ? 'linear-gradient(90deg,#16a34a,#22c55e)'
                                    : 'linear-gradient(90deg,#f97316,#fb923c)',
                                color: '#fff',
                                fontWeight: 600,
                                fontSize: '0.9375rem',
                                cursor: submitting ? 'default' : 'pointer',
                            }}
                        >
                            {submitting ? 'Gönderiliyor...' : 'Emri Gönder'}
                        </button>
                    </form>
                    {lastTrade && (
                        <div
                            style={{
                                marginTop: 16,
                                padding: 12,
                                borderRadius: 8,
                                background: 'rgba(34, 197, 94, 0.15)',
                                border: `1px solid ${tokens.success}`,
                                color: tokens.success,
                                fontSize: '0.8125rem',
                            }}
                        >
                            <div>{lastTrade.symbol} için {lastTrade.quantity} adet işlem gerçekleşti.</div>
                            <div>Fiyat: {formatMoney(lastTrade.tryPrice)} · Toplam: {formatMoney(lastTrade.totalTry)}</div>
                            <div>İşlem sonrası bakiye: {formatMoney(lastTrade.balanceAfter)}</div>
                        </div>
                    )}
                </div>

                <div style={cardStyle}>
                    <h2 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: 16 }}>Geçmiş İşlemler</h2>
                    {historyError && (
                        <div style={{ color: tokens.error, marginBottom: 8, fontSize: '0.8125rem' }}>
                            {historyError}
                        </div>
                    )}
                    {historyLoading && <p style={mutedStyle}>Yükleniyor...</p>}
                    {history && history.content.length === 0 && !historyLoading && (
                        <p style={mutedStyle}>Henüz işlem yok.</p>
                    )}
                    {history && history.content.length > 0 && (
                        <>
                            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.875rem' }}>
                                <thead>
                                <tr style={{ borderBottom: `2px solid ${tokens.border}` }}>
                                    <th style={{ textAlign: 'left', padding: 10 }}>Tarih</th>
                                    <th style={{ textAlign: 'left', padding: 10 }}>Sembol</th>
                                    <th style={{ textAlign: 'left', padding: 10 }}>Tür</th>
                                    <th style={{ textAlign: 'right', padding: 10 }}>Miktar</th>
                                    <th style={{ textAlign: 'right', padding: 10 }}>Toplam (TRY)</th>
                                    <th style={{ textAlign: 'right', padding: 10 }}>Bakiye Sonrası</th>
                                </tr>
                                </thead>
                                <tbody>
                                {history.content.map((t) => {
                                    const tryPrice = t.quantity && t.quantity !== 0 ? t.totalTry / t.quantity : 0;
                                    return (
                                        <tr key={t.tradeId} style={{ borderBottom: `1px solid ${tokens.tableBorder}` }}>
                                            <td style={{ padding: 10 }}>{new Date(t.tradedAt).toLocaleString('tr-TR')}</td>
                                            <td style={{ padding: 10 }}>{t.symbol}</td>
                                            <td style={{ padding: 10 }}>{t.tradeType === 'BUY' ? 'AL' : 'SAT'} ({t.assetType})</td>
                                            <td style={{ padding: 10, textAlign: 'right' }}>{t.quantity.toLocaleString('tr-TR')}</td>
                                            <td style={{ padding: 10, textAlign: 'right' }}>
                                                {formatMoney(t.totalTry)}
                                                <div style={{ fontSize: '0.75rem', color: tokens.textMuted }}>
                                                    {formatMoney(tryPrice)} / birim
                                                </div>
                                            </td>
                                            <td style={{ padding: 10, textAlign: 'right' }}>{formatMoney(t.balanceAfter)}</td>
                                        </tr>
                                    );
                                })}
                                </tbody>
                            </table>
                            {history.totalElements > history.size && (
                                <div style={{ marginTop: 12, display: 'flex', alignItems: 'center', gap: 8, fontSize: '0.875rem' }}>
                                    <button
                                        type="button"
                                        disabled={historyPage === 0}
                                        onClick={() => setHistoryPage((p) => p - 1)}
                                        style={{
                                            padding: '6px 12px',
                                            borderRadius: 8,
                                            border: `1px solid ${tokens.border}`,
                                            background: tokens.bgCard,
                                            color: tokens.text,
                                            cursor: historyPage === 0 ? 'default' : 'pointer',
                                            opacity: historyPage === 0 ? 0.6 : 1,
                                        }}
                                    >
                                        Önceki
                                    </button>
                                    <span style={{ color: tokens.textMuted }}>
                                        Sayfa {history.number + 1} / {Math.ceil(history.totalElements / history.size)}
                                    </span>
                                    <button
                                        type="button"
                                        disabled={(history.number + 1) * history.size >= history.totalElements}
                                        onClick={() => setHistoryPage((p) => p + 1)}
                                        style={{
                                            padding: '6px 12px',
                                            borderRadius: 8,
                                            border: `1px solid ${tokens.border}`,
                                            background: tokens.bgCard,
                                            color: tokens.text,
                                            cursor: (history.number + 1) * history.size >= history.totalElements ? 'default' : 'pointer',
                                            opacity: (history.number + 1) * history.size >= history.totalElements ? 0.6 : 1,
                                        }}
                                    >
                                        Sonraki
                                    </button>
                                </div>
                            )}
                        </>
                    )}
                </div>
            </div>
        </div>
    );
}