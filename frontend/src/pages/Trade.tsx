import { useEffect, useState, useCallback } from 'react';
import { financeClient } from '../api/client';
import { useTheme } from '../theme/ThemeContext';
import { useSearchParams } from 'react-router-dom';

type AssetType = 'CRYPTO' | 'FX' | 'FUND' | 'METAL' | 'STOCK';
type TradeType = 'BUY' | 'SELL';
type TemplateType = 'SPOT' | 'FUTURES' | 'FIXED_INCOME';

type TradeRequest = {
    assetType: AssetType;
    symbol: string;
    quantity: number;
    tradeType: TradeType;
    templateType?: TemplateType;
    attributes?: Record<string, unknown>;
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

type MarketOverview = {
    doviz?: Record<string, { buyPrice?: number; sellPrice?: number; source?: string }>;
    metals?: Record<string, { buyPrice?: number; source?: string }>;
    crypto?: Record<string, { buyPrice?: number; source?: string }>;
    funds?: Record<string, { buyPrice?: number; source?: string }>;
    stocks?: Record<string, { buyPrice?: number; source?: string }>;
    timestamp?: string;
};

const ASSET_TYPES: { value: AssetType; label: string }[] = [
    { value: 'CRYPTO', label: 'Kripto' },
    { value: 'FX', label: 'Döviz' },
    { value: 'FUND', label: 'Fon (ETF)' },
    { value: 'METAL', label: 'Altın' },
    { value: 'STOCK', label: 'Hisse' },
];
const TEMPLATE_LABELS: Record<TemplateType, string> = {
    SPOT: 'Spot',
    FUTURES: 'VİOP',
    FIXED_INCOME: 'Tahvil/Bono',
};
const INSTRUMENT_CONFIG: Record<TemplateType, AssetType[]> = {
    SPOT: ['CRYPTO', 'FX', 'STOCK', 'METAL'],
    FUTURES: ['FX', 'STOCK', 'METAL'],
    FIXED_INCOME: ['FUND'],
};
const orderFormV2Enabled = String(import.meta.env.VITE_ORDER_FORM_V2_ENABLED ?? 'true') === 'true';
const ISIN_PATTERN = /^TR[A-Z0-9]{10}$/;
const FUTURES_PATTERN = /^[A-Z0-9_]+\d{4}$/;

function getOverviewKey(type: AssetType): keyof MarketOverview {
    switch (type) {
        case 'CRYPTO': return 'crypto';
        case 'FX': return 'doviz';
        case 'METAL': return 'metals';
        case 'FUND': return 'funds';
        case 'STOCK': return 'stocks';
        default: return 'crypto';
    }
}

export function Trade() {
    const { tokens } = useTheme();
    const [searchParams] = useSearchParams();

    const [assetType, setAssetType] = useState<AssetType>('CRYPTO');
    const [symbol, setSymbol] = useState('');
    const [templateType, setTemplateType] = useState<TemplateType>('SPOT');
    const [tradeType, setTradeType] = useState<TradeType>('BUY');
    const [quantity, setQuantity] = useState<string>('0.1');
    const [limitPrice, setLimitPrice] = useState<string>('');
    const [contractMonth, setContractMonth] = useState<string>('Haz 2026');
    const [nominal, setNominal] = useState<string>('');
    const [priceMode, setPriceMode] = useState<'PRICE' | 'YIELD'>('PRICE');

    const [overview, setOverview] = useState<MarketOverview | null>(null);
    const [overviewLoading, setOverviewLoading] = useState(true);

    const [submitting, setSubmitting] = useState(false);
    const [submitError, setSubmitError] = useState<string | null>(null);
    const [restrictionToast, setRestrictionToast] = useState<string | null>(null);
    const [lastTrade, setLastTrade] = useState<TradeResponse | null>(null);

    const [history, setHistory] = useState<Page<TradeHistoryItem> | null>(null);
    const [historyLoading, setHistoryLoading] = useState(true);
    const [historyError, setHistoryError] = useState<string | null>(null);
    const [historyPage, setHistoryPage] = useState(0);

    const pageSize = 10;

    const formatMoney = (v: number) =>
        '₺' + v.toLocaleString('tr-TR', { maximumFractionDigits: 2 });

    const loadOverview = useCallback(() => {
        setOverviewLoading(true);
        financeClient
            .get<MarketOverview>('/api/market/overview')
            .then((res) => {
                const raw = (res.data as { data?: MarketOverview })?.data ?? res.data;
                setOverview(raw ?? null);
            })
            .catch(() => setOverview(null))
            .finally(() => setOverviewLoading(false));
    }, []);

    useEffect(() => {
        loadOverview();
    }, [loadOverview]);
    useEffect(() => {
        const kind = String(searchParams.get('kind') ?? '').toUpperCase();
        const symbolFromQs = searchParams.get('symbol');
        if (kind === 'BOND') {
            setTemplateType('FIXED_INCOME');
        } else if (kind === 'FUTURES') {
            setTemplateType('FUTURES');
        } else if (kind === 'STOCK') {
            setTemplateType('SPOT');
        }
        if (symbolFromQs) {
            const normalized = symbolFromQs.toUpperCase();
            setSymbol(normalized);
            if (orderFormV2Enabled) {
                if (ISIN_PATTERN.test(normalized)) {
                    setTemplateType('FIXED_INCOME');
                    setAssetType('FUND');
                } else if (FUTURES_PATTERN.test(normalized)) {
                    setTemplateType('FUTURES');
                    setAssetType('FX');
                }
            }
        }
    }, [searchParams]);
    useEffect(() => {
        if (!orderFormV2Enabled) return;
        const allowed = INSTRUMENT_CONFIG[templateType];
        if (!allowed.includes(assetType)) {
            setAssetType(allowed[0]);
        }
    }, [templateType, assetType]);
    useEffect(() => {
        if (!orderFormV2Enabled || !symbol) return;
        const normalized = symbol.trim().toUpperCase();
        if (ISIN_PATTERN.test(normalized) && templateType !== 'FIXED_INCOME') {
            setTemplateType('FIXED_INCOME');
            setAssetType('FUND');
            return;
        }
        if (FUTURES_PATTERN.test(normalized) && templateType !== 'FUTURES') {
            setTemplateType('FUTURES');
            if (!INSTRUMENT_CONFIG.FUTURES.includes(assetType)) {
                setAssetType('FX');
            }
        }
    }, [symbol, templateType, assetType]);

    const symbolOptions = ((): string[] => {
        if (!overview) return [];
        const key = getOverviewKey(assetType);
        const map = overview[key];
        if (!map || typeof map !== 'object') return [];
        const base = Object.keys(map).filter((k) => map[k] != null);
        if (symbol && !base.includes(symbol)) {
            return [symbol, ...base];
        }
        return base;
    })();
    const selectableAssetTypes = orderFormV2Enabled
        ? ASSET_TYPES.filter((a) => INSTRUMENT_CONFIG[templateType].includes(a.value))
        : ASSET_TYPES;

    useEffect(() => {
        if (symbolOptions.length > 0 && !symbolOptions.includes(symbol)) {
            setSymbol(symbolOptions[0]);
        } else if (symbolOptions.length === 0) {
            setSymbol('');
        }
    }, [assetType, symbolOptions.join(',')]);

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
            setSubmitError('Lütfen sembol seçin ve miktar girin.');
            return;
        }
        if (orderFormV2Enabled) {
            const allowed = INSTRUMENT_CONFIG[templateType];
            if (!allowed.includes(assetType)) {
                setSubmitError('Market Order Restriction: Şablon ve varlık türü uyumsuz.');
                return;
            }
        }
        const attributes: Record<string, unknown> = {};
        if (templateType === 'FUTURES') {
            attributes.contractMonth = contractMonth;
            attributes.expiryDate = contractMonth;
        }
        if (templateType === 'FIXED_INCOME') {
            attributes.nominal = nominal ? Number(nominal) : null;
            attributes.priceMode = priceMode;
            attributes.yield = priceMode === 'YIELD' ? limitPrice : null;
        }
        if (templateType === 'SPOT' && limitPrice) {
            attributes.limitPrice = Number(limitPrice);
        }
        const payload: TradeRequest = {
            assetType,
            symbol: symbol.trim(),
            quantity: qty,
            tradeType,
            templateType: orderFormV2Enabled ? templateType : undefined,
            attributes: orderFormV2Enabled ? attributes : undefined,
        };
        setSubmitting(true);
        setSubmitError(null);
        setRestrictionToast(null);
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
                if (String(msg).includes('MARKET_ORDER_RESTRICTION')) {
                    setRestrictionToast(`Market Order Restriction: ${String(msg).replace('MARKET_ORDER_RESTRICTION:', '').trim()}`);
                }
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
        background: (tokens as { inputBg?: string }).inputBg ?? tokens.bgCard,
        color: tokens.text,
        fontSize: '0.9375rem',
    };

    return (
        <div style={pageStyle}>
            {restrictionToast ? (
                <div
                    style={{
                        marginBottom: 12,
                        borderRadius: 10,
                        border: `1px solid ${tokens.error}`,
                        background: 'rgba(239, 68, 68, 0.15)',
                        color: '#fecaca',
                        padding: '10px 12px',
                        fontSize: '0.85rem',
                    }}
                >
                    {restrictionToast}
                </div>
            ) : null}
            <h1 style={titleStyle}>Alım ve satım</h1>
            <p style={mutedStyle}>
                Varlık türüne göre sembol seçin; miktar girip emri gönderin.
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
                            Enstrüman Şablonu
                            <select
                                value={templateType}
                                onChange={(e) => setTemplateType(e.target.value as TemplateType)}
                                style={inputStyle}
                            >
                                {Object.keys(TEMPLATE_LABELS).map((key) => (
                                    <option key={key} value={key}>
                                        {TEMPLATE_LABELS[key as TemplateType]}
                                    </option>
                                ))}
                            </select>
                        </label>
                        <label style={{ fontSize: '0.875rem' }}>
                            Varlık Türü
                            <select
                                value={assetType}
                                onChange={(e) => setAssetType(e.target.value as AssetType)}
                                style={inputStyle}
                            >
                                {selectableAssetTypes.map((t) => (
                                    <option key={t.value} value={t.value}>{t.label}</option>
                                ))}
                            </select>
                        </label>
                        <label style={{ fontSize: '0.875rem' }}>
                            Sembol
                            {overviewLoading ? (
                                <div style={{ ...inputStyle, color: tokens.textMuted }}>Yükleniyor...</div>
                            ) : symbolOptions.length === 0 ? (
                                <div style={{ ...inputStyle, color: tokens.textMuted }}>Bu tür için sembol yok.</div>
                            ) : (
                                <select
                                    value={symbol}
                                    onChange={(e) => setSymbol(e.target.value)}
                                    style={inputStyle}
                                >
                                    {symbolOptions.map((s) => (
                                        <option key={s} value={s}>{s}</option>
                                    ))}
                                </select>
                            )}
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
                                    Al (AL)
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
                                    Sat (SAT)
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
                        {templateType === 'SPOT' ? (
                            <label style={{ fontSize: '0.875rem' }}>
                                Fiyat (opsiyonel)
                                <input
                                    type="number"
                                    step="0.0001"
                                    value={limitPrice}
                                    onChange={(e) => setLimitPrice(e.target.value)}
                                    style={inputStyle}
                                />
                            </label>
                        ) : null}
                        {templateType === 'FUTURES' ? (
                            <label style={{ fontSize: '0.875rem' }}>
                                Sözleşme Vadesi
                                <select value={contractMonth} onChange={(e) => setContractMonth(e.target.value)} style={inputStyle}>
                                    <option value="Haz 2026">Haz 2026</option>
                                    <option value="Eyl 2026">Eyl 2026</option>
                                    <option value="Ara 2026">Ara 2026</option>
                                    <option value="Mar 2027">Mar 2027</option>
                                </select>
                            </label>
                        ) : null}
                        {templateType === 'FIXED_INCOME' ? (
                            <>
                                <label style={{ fontSize: '0.875rem' }}>
                                    Nominal
                                    <input
                                        type="number"
                                        step="1"
                                        value={nominal}
                                        onChange={(e) => setNominal(e.target.value)}
                                        style={inputStyle}
                                    />
                                </label>
                                <label style={{ fontSize: '0.875rem' }}>
                                    Fiyat/Yield Modu
                                    <select value={priceMode} onChange={(e) => setPriceMode(e.target.value as 'PRICE' | 'YIELD')} style={inputStyle}>
                                        <option value="PRICE">Fiyat</option>
                                        <option value="YIELD">Yield</option>
                                    </select>
                                </label>
                            </>
                        ) : null}
                        {(templateType === 'FIXED_INCOME' || templateType === 'FUTURES') ? (
                            <div style={{ fontSize: '0.75rem', color: tokens.textMuted }}>
                                Şablon ve varlık eşleşmeleri iş kuralına göre filtrelenmiştir.
                            </div>
                        ) : null}
                        {submitError && (
                            <div style={{ color: tokens.error, fontSize: '0.8125rem' }}>{submitError}</div>
                        )}
                        <button
                            type="submit"
                            disabled={submitting || symbolOptions.length === 0}
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
                            {submitting ? 'Gönderiliyor...' : 'Emri gönder'}
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
                                    <th style={{ textAlign: 'right', padding: 10 }}>İşlem sonrası bakiye</th>
                                </tr>
                                </thead>
                                <tbody>
                                {history.content.map((t) => {
                                    const tryPrice = t.quantity && t.quantity !== 0 ? t.totalTry / t.quantity : 0;
                                    return (
                                        <tr key={t.tradeId} style={{ borderBottom: `1px solid ${tokens.tableBorder ?? tokens.border}` }}>
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