import { useCallback, useEffect, useMemo, useState } from 'react';
import { financeClient } from '../api/client';
import { useTheme } from '../theme/ThemeContext';
import { useSearchParams } from 'react-router-dom';
import {
    TEMPLATE_LABELS,
    TEMPLATE_THEME,
    assetClassesByTemplate,
    classifyViopContract,
    getAssetClassOption,
    professionalTradeLabel,
    type AssetClass,
    type AssetType,
    type TemplateType,
    type TradeType,
} from '../constants/OrderConstants';
import {
    fetchDebtSymbolsByClass,
    fetchSpotSymbolsByAssetClass,
    fetchViopSymbolsByClass,
} from '../services/marketDataService';

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

const orderFormV2Enabled = String(import.meta.env.VITE_ORDER_FORM_V2_ENABLED ?? 'true') === 'true';
const ISIN_PATTERN = /^TR[A-Z0-9]{10}$/;
const FUTURES_PATTERN = /^[A-Z0-9_]+\d{4}$/;

export function Trade() {
    const { tokens } = useTheme();
    const [searchParams] = useSearchParams();

    const [templateType, setTemplateType] = useState<TemplateType>('SPOT');
    const [assetClass, setAssetClass] = useState<AssetClass>('SPOT_CRYPTO');
    const [assetType, setAssetType] = useState<AssetType>('CRYPTO');
    const [symbol, setSymbol] = useState('');
    const [symbolQuery, setSymbolQuery] = useState('');

    const [tradeType, setTradeType] = useState<TradeType>('BUY');
    const [quantity, setQuantity] = useState<string>('0.1');
    const [limitPrice, setLimitPrice] = useState<string>('');
    const [contractMonth, setContractMonth] = useState<string>('Haz 2026');
    const [nominal, setNominal] = useState<string>('');
    const [priceMode, setPriceMode] = useState<'PRICE' | 'YIELD'>('PRICE');

    const [symbolPool, setSymbolPool] = useState<string[]>([]);
    const [symbolPoolLoading, setSymbolPoolLoading] = useState(false);

    const [submitting, setSubmitting] = useState(false);
    const [submitError, setSubmitError] = useState<string | null>(null);
    const [restrictionToast, setRestrictionToast] = useState<string | null>(null);
    const [lastTrade, setLastTrade] = useState<TradeResponse | null>(null);

    const [history, setHistory] = useState<Page<TradeHistoryItem> | null>(null);
    const [historyLoading, setHistoryLoading] = useState(true);
    const [historyError, setHistoryError] = useState<string | null>(null);
    const [historyPage, setHistoryPage] = useState(0);
    const pageSize = 10;

    const formatMoney = (v: number) => '₺' + v.toLocaleString('tr-TR', { maximumFractionDigits: 2 });
    const templateTheme = TEMPLATE_THEME[templateType];

    useEffect(() => {
        const classes = assetClassesByTemplate(templateType);
        if (!classes.some((x) => x.value === assetClass)) {
            setAssetClass(classes[0]?.value ?? 'SPOT_CRYPTO');
        }
    }, [templateType, assetClass]);

    useEffect(() => {
        const option = getAssetClassOption(assetClass);
        if (option && option.backendAssetType !== assetType) {
            setAssetType(option.backendAssetType);
        }
    }, [assetClass, assetType]);

    useEffect(() => {
        const kind = String(searchParams.get('kind') ?? '').toUpperCase();
        const symbolFromQs = searchParams.get('symbol');
        if (kind === 'BOND') {
            setTemplateType('FIXED_INCOME');
            setAssetClass('BOND_GOV');
        } else if (kind === 'FUTURES') {
            setTemplateType('FUTURES');
            setAssetClass('FUTURES_INDEX');
        } else if (kind === 'STOCK') {
            setTemplateType('SPOT');
            setAssetClass('SPOT_EQUITY');
        }
        if (symbolFromQs) {
            const normalized = symbolFromQs.toUpperCase();
            setSymbol(normalized);
            if (ISIN_PATTERN.test(normalized)) {
                setTemplateType('FIXED_INCOME');
                setAssetClass('BOND_GOV');
            } else if (FUTURES_PATTERN.test(normalized)) {
                setTemplateType('FUTURES');
                setAssetClass(classifyViopContract(normalized));
            }
        }
    }, [searchParams]);

    const loadSymbols = useCallback(async () => {
        setSymbolPoolLoading(true);
        try {
            if (templateType === 'SPOT') {
                setSymbolPool(await fetchSpotSymbolsByAssetClass(assetClass));
            } else if (templateType === 'FUTURES') {
                setSymbolPool(await fetchViopSymbolsByClass(assetClass));
            } else {
                setSymbolPool(await fetchDebtSymbolsByClass(assetClass));
            }
        } catch {
            setSymbolPool([]);
        } finally {
            setSymbolPoolLoading(false);
        }
    }, [templateType, assetClass]);

    useEffect(() => {
        if (!orderFormV2Enabled) return;
        void loadSymbols();
    }, [loadSymbols]);

    const symbolOptions = useMemo(() => {
        const query = symbolQuery.trim().toLowerCase();
        const base = query ? symbolPool.filter((s) => s.toLowerCase().includes(query)) : symbolPool;
        if (symbol && !base.includes(symbol)) return [symbol, ...base];
        return base;
    }, [symbolPool, symbolQuery, symbol]);

    useEffect(() => {
        if (!symbolOptions.length) {
            setSymbol('');
            return;
        }
        if (!symbolOptions.includes(symbol)) {
            setSymbol(symbolOptions[0]);
        }
    }, [symbolOptions.join('|'), symbol]);

    const selectableAssetClasses = orderFormV2Enabled
        ? assetClassesByTemplate(templateType)
        : assetClassesByTemplate('SPOT');

    const loadHistory = () => {
        setHistoryLoading(true);
        setHistoryError(null);
        financeClient
            .get<Page<TradeHistoryItem>>('/api/trades/history', { params: { page: historyPage, size: pageSize } })
            .then((res) => setHistory((res.data as any)?.data ?? res.data))
            .catch((err) => setHistoryError(err.response?.data?.message ?? err.message ?? 'Hata'))
            .finally(() => setHistoryLoading(false));
    };

    useEffect(() => {
        loadHistory();
    }, [historyPage]);

    const handleSubmit = (e: React.FormEvent) => {
        e.preventDefault();
        const qty = Number(quantity);
        if (!symbol.trim() || Number.isNaN(qty) || qty <= 0) {
            setSubmitError('Lutfen sembol secin ve miktar girin.');
            return;
        }
        if (orderFormV2Enabled) {
            const selectedClass = getAssetClassOption(assetClass);
            if (!selectedClass || selectedClass.template !== templateType) {
                setSubmitError('Market Order Restriction: Sablon ve varlik turu uyumsuz.');
                return;
            }
        }
        const attributes: Record<string, unknown> = { assetClass };
        if (templateType === 'FUTURES') {
            attributes.contractMonth = contractMonth;
            attributes.expiryDate = contractMonth;
        }
        if (templateType === 'FIXED_INCOME') {
            attributes.nominal = nominal ? Number(nominal) : null;
            attributes.priceMode = priceMode;
            if (priceMode === 'YIELD') {
                attributes.yield = limitPrice ? Number(limitPrice) : null;
            } else if (limitPrice) {
                attributes.limitPrice = Number(limitPrice);
            }
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
                setLastTrade((res.data as any)?.data ?? res.data);
                setHistoryPage(0);
                setTimeout(loadHistory, 200);
            })
            .catch((err) => {
                const msg =
                    err.response?.data?.errors?.error ??
                    err.response?.data?.message ??
                    err.message ??
                    'Emir gonderilirken hata olustu.';
                setSubmitError(msg);
                if (String(msg).includes('MARKET_ORDER_RESTRICTION')) {
                    setRestrictionToast(`Market Order Restriction: ${String(msg).replace('MARKET_ORDER_RESTRICTION:', '').trim()}`);
                }
            })
            .finally(() => setSubmitting(false));
    };

    const pageStyle: React.CSSProperties = { padding: 24, background: tokens.bg, color: tokens.text, minHeight: '100%' };
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
                <div style={{ marginBottom: 12, borderRadius: 10, border: `1px solid ${tokens.error}`, background: 'rgba(239, 68, 68, 0.15)', color: '#fecaca', padding: '10px 12px', fontSize: '0.85rem' }}>
                    {restrictionToast}
                </div>
            ) : null}
            <h1 style={titleStyle}>Alim ve Satim</h1>
            <p style={mutedStyle}>Enstruman sablonu, varlik sinifi ve sembol hiyerarsisi ile profesyonel emir akisi.</p>

            <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1.3fr) minmax(0, 2fr)', gap: 24, marginBottom: 32 }}>
                <div style={{ ...cardStyle, border: `1px solid ${templateTheme.border}`, background: templateTheme.bg }}>
                    <h2 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: 6 }}>Emir Formu</h2>
                    <div style={{ fontSize: '0.75rem', color: tokens.textMuted, marginBottom: 14 }}>{templateTheme.chip} templatine uygun varliklar listelenir.</div>
                    <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                        <label style={{ fontSize: '0.875rem' }}>
                            Enstruman Sablonu
                            <select value={templateType} onChange={(e) => setTemplateType(e.target.value as TemplateType)} style={inputStyle}>
                                {Object.keys(TEMPLATE_LABELS).map((key) => (
                                    <option key={key} value={key}>{TEMPLATE_LABELS[key as TemplateType]}</option>
                                ))}
                            </select>
                        </label>
                        <label style={{ fontSize: '0.875rem' }}>
                            Varlik Turu
                            <select value={assetClass} onChange={(e) => setAssetClass(e.target.value as AssetClass)} style={inputStyle}>
                                {selectableAssetClasses.map((t) => (
                                    <option key={t.value} value={t.value}>{t.label}</option>
                                ))}
                            </select>
                        </label>
                        <label style={{ fontSize: '0.875rem' }}>
                            Sembol Ara
                            <input value={symbolQuery} onChange={(e) => setSymbolQuery(e.target.value)} placeholder="Sembol yazin..." style={inputStyle} />
                        </label>
                        <label style={{ fontSize: '0.875rem' }}>
                            Sembol
                            {symbolPoolLoading ? (
                                <div style={{ ...inputStyle, color: tokens.textMuted }}>Yukleniyor...</div>
                            ) : symbolOptions.length === 0 ? (
                                <div style={{ ...inputStyle, color: tokens.textMuted }}>Bu secim icin sembol bulunamadi.</div>
                            ) : (
                                <select value={symbol} onChange={(e) => setSymbol(e.target.value)} style={inputStyle}>
                                    {symbolOptions.map((s) => (
                                        <option key={s} value={s}>{s}</option>
                                    ))}
                                </select>
                            )}
                        </label>
                        <label style={{ fontSize: '0.875rem' }}>
                            Yon
                            <div style={{ display: 'flex', gap: 8, marginTop: 4 }}>
                                <button type="button" onClick={() => setTradeType('BUY')} style={{ flex: 1, padding: '8px 0', borderRadius: 8, border: tradeType === 'BUY' ? `2px solid ${tokens.success}` : `1px solid ${tokens.border}`, background: tradeType === 'BUY' ? 'rgba(34, 197, 94, 0.15)' : tokens.bgCard, color: tradeType === 'BUY' ? tokens.success : tokens.text, cursor: 'pointer', fontSize: '0.875rem', fontWeight: 500 }}>
                                    Al (AL)
                                </button>
                                <button type="button" onClick={() => setTradeType('SELL')} style={{ flex: 1, padding: '8px 0', borderRadius: 8, border: tradeType === 'SELL' ? '2px solid #f97316' : `1px solid ${tokens.border}`, background: tradeType === 'SELL' ? 'rgba(249, 115, 22, 0.15)' : tokens.bgCard, color: tradeType === 'SELL' ? '#f97316' : tokens.text, cursor: 'pointer', fontSize: '0.875rem', fontWeight: 500 }}>
                                    Sat (SAT)
                                </button>
                            </div>
                        </label>
                        <label style={{ fontSize: '0.875rem' }}>
                            {templateType === 'FIXED_INCOME' ? 'Nominal Deger' : 'Miktar'}
                            <input type="number" step="0.0001" value={quantity} onChange={(e) => setQuantity(e.target.value)} style={inputStyle} />
                        </label>
                        {templateType === 'SPOT' ? (
                            <label style={{ fontSize: '0.875rem' }}>
                                Limit Fiyat (opsiyonel)
                                <input type="number" step="0.0001" value={limitPrice} onChange={(e) => setLimitPrice(e.target.value)} style={inputStyle} />
                            </label>
                        ) : null}
                        {templateType === 'FUTURES' ? (
                            <label style={{ fontSize: '0.875rem' }}>
                                Sozlesme Vadesi
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
                                    Fiyat/Getiri Modu
                                    <select value={priceMode} onChange={(e) => setPriceMode(e.target.value as 'PRICE' | 'YIELD')} style={inputStyle}>
                                        <option value="PRICE">Fiyat Uzerinden Emir</option>
                                        <option value="YIELD">Getiri Uzerinden Emir</option>
                                    </select>
                                </label>
                                <label style={{ fontSize: '0.875rem' }}>
                                    {priceMode === 'YIELD' ? 'Hedef Getiri (%)' : 'Birim Fiyat'}
                                    <input type="number" step="0.0001" value={limitPrice} onChange={(e) => setLimitPrice(e.target.value)} style={inputStyle} />
                                </label>
                                <label style={{ fontSize: '0.875rem' }}>
                                    Nominal Deger
                                    <input type="number" step="1" value={nominal} onChange={(e) => setNominal(e.target.value)} style={inputStyle} />
                                </label>
                            </>
                        ) : null}
                        <div style={{ fontSize: '0.75rem', color: tokens.textMuted }}>
                            Secilen varlik turu icin emirler {templateType === 'SPOT' ? 'BIST/Binance' : templateType === 'FUTURES' ? 'BIST VIOP' : 'BIST Borclanma'} uzerinden gercek zamanli islenmektedir.
                        </div>
                        {submitError && <div style={{ color: tokens.error, fontSize: '0.8125rem' }}>{submitError}</div>}
                        <button type="submit" disabled={submitting || symbolOptions.length === 0} style={{ marginTop: 8, padding: '10px 0', borderRadius: 8, border: 'none', background: tradeType === 'BUY' ? 'linear-gradient(90deg,#16a34a,#22c55e)' : 'linear-gradient(90deg,#f97316,#fb923c)', color: '#fff', fontWeight: 600, fontSize: '0.9375rem', cursor: submitting ? 'default' : 'pointer' }}>
                            {submitting ? 'Gonderiliyor...' : 'Emri gonder'}
                        </button>
                    </form>
                    {lastTrade && (
                        <div style={{ marginTop: 16, padding: 12, borderRadius: 8, background: 'rgba(34, 197, 94, 0.15)', border: `1px solid ${tokens.success}`, color: tokens.success, fontSize: '0.8125rem' }}>
                            <div>{lastTrade.symbol} icin {lastTrade.quantity} adet islem gerceklesti.</div>
                            <div>Fiyat: {formatMoney(lastTrade.tryPrice)} · Toplam: {formatMoney(lastTrade.totalTry)}</div>
                            <div>Islem sonrasi bakiye: {formatMoney(lastTrade.balanceAfter)}</div>
                        </div>
                    )}
                </div>

                <div style={cardStyle}>
                    <h2 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: 16 }}>Gecmis Islemler</h2>
                    {historyError && <div style={{ color: tokens.error, marginBottom: 8, fontSize: '0.8125rem' }}>{historyError}</div>}
                    {historyLoading && <p style={mutedStyle}>Yukleniyor...</p>}
                    {history && history.content.length === 0 && !historyLoading && <p style={mutedStyle}>Henuz islem yok.</p>}
                    {history && history.content.length > 0 && (
                        <>
                            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.875rem' }}>
                                <thead>
                                <tr style={{ borderBottom: `2px solid ${tokens.border}` }}>
                                    <th style={{ textAlign: 'left', padding: 10 }}>Tarih</th>
                                    <th style={{ textAlign: 'left', padding: 10 }}>Sembol</th>
                                    <th style={{ textAlign: 'left', padding: 10 }}>Tur</th>
                                    <th style={{ textAlign: 'right', padding: 10 }}>Miktar</th>
                                    <th style={{ textAlign: 'right', padding: 10 }}>Toplam (TRY)</th>
                                    <th style={{ textAlign: 'right', padding: 10 }}>Islem sonrasi bakiye</th>
                                </tr>
                                </thead>
                                <tbody>
                                {history.content.map((t) => {
                                    const tryPrice = t.quantity && t.quantity !== 0 ? t.totalTry / t.quantity : 0;
                                    return (
                                        <tr key={t.tradeId} style={{ borderBottom: `1px solid ${tokens.tableBorder ?? tokens.border}` }}>
                                            <td style={{ padding: 10 }}>{new Date(t.tradedAt).toLocaleString('tr-TR')}</td>
                                            <td style={{ padding: 10 }}>{t.symbol}</td>
                                            <td style={{ padding: 10 }}>{professionalTradeLabel(t.tradeType, t.symbol, t.assetType)}</td>
                                            <td style={{ padding: 10, textAlign: 'right' }}>{t.quantity.toLocaleString('tr-TR')}</td>
                                            <td style={{ padding: 10, textAlign: 'right' }}>
                                                {formatMoney(t.totalTry)}
                                                <div style={{ fontSize: '0.75rem', color: tokens.textMuted }}>{formatMoney(tryPrice)} / birim</div>
                                            </td>
                                            <td style={{ padding: 10, textAlign: 'right' }}>{formatMoney(t.balanceAfter)}</td>
                                        </tr>
                                    );
                                })}
                                </tbody>
                            </table>
                            {history.totalElements > history.size && (
                                <div style={{ marginTop: 12, display: 'flex', alignItems: 'center', gap: 8, fontSize: '0.875rem' }}>
                                    <button type="button" disabled={historyPage === 0} onClick={() => setHistoryPage((p) => p - 1)} style={{ padding: '6px 12px', borderRadius: 8, border: `1px solid ${tokens.border}`, background: tokens.bgCard, color: tokens.text, cursor: historyPage === 0 ? 'default' : 'pointer', opacity: historyPage === 0 ? 0.6 : 1 }}>
                                        Onceki
                                    </button>
                                    <span style={{ color: tokens.textMuted }}>Sayfa {history.number + 1} / {Math.ceil(history.totalElements / history.size)}</span>
                                    <button type="button" disabled={(history.number + 1) * history.size >= history.totalElements} onClick={() => setHistoryPage((p) => p + 1)} style={{ padding: '6px 12px', borderRadius: 8, border: `1px solid ${tokens.border}`, background: tokens.bgCard, color: tokens.text, cursor: (history.number + 1) * history.size >= history.totalElements ? 'default' : 'pointer', opacity: (history.number + 1) * history.size >= history.totalElements ? 0.6 : 1 }}>
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
