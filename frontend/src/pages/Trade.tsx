import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import type { AxiosResponse } from 'axios';
import { financeClient, marketClient } from '../api/client';
import { useTheme } from '../theme/ThemeContext';
import { useLanguage } from '../i18n/LanguageContext';
import { useSearchParams } from 'react-router-dom';
import {
    TEMPLATE_THEME,
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

function unwrapFinance<T>(res: AxiosResponse<T>): T {
    const body = res.data as unknown;
    if (body && typeof body === 'object' && 'data' in (body as object)) {
        return (body as { data: T }).data;
    }
    return body as T;
}

/** Piyasa kotasyonu USD olan kategoriler (FX/altın/VIOP/tahvil TRY). */
function tradePriceQuotedUsd(category: TradeCategory): boolean {
    return category === 'EQUITY' || category === 'CRYPTO';
}
const ISIN_PATTERN = /^TR[A-Z0-9]{10}$/;
const FUTURES_PATTERN = /^[A-Z0-9_]+\d{4}$/;
type TradeCategory = 'EQUITY' | 'CRYPTO' | 'FX' | 'METAL' | 'FUND' | 'FUTURES' | 'BOND';
type QuantityMode = 'DECIMAL' | 'INTEGER';

const TRADE_CATEGORIES: {
    id: TradeCategory;
    label: string;
    templateType: TemplateType;
    assetClass: AssetClass;
    assetType: AssetType;
    quantityMode: QuantityMode;
    quantityLabel: string;
}[] = [
    { id: 'EQUITY', label: 'Hisse', templateType: 'SPOT', assetClass: 'SPOT_EQUITY', assetType: 'STOCK', quantityMode: 'INTEGER', quantityLabel: 'Adet / lot' },
    { id: 'CRYPTO', label: 'Kripto', templateType: 'SPOT', assetClass: 'SPOT_CRYPTO', assetType: 'CRYPTO', quantityMode: 'DECIMAL', quantityLabel: 'Coin' },
    { id: 'FX', label: 'Döviz', templateType: 'SPOT', assetClass: 'SPOT_FX', assetType: 'FX', quantityMode: 'DECIMAL', quantityLabel: 'Para birimi' },
    { id: 'METAL', label: 'Altın', templateType: 'SPOT', assetClass: 'SPOT_COMMODITY', assetType: 'METAL', quantityMode: 'DECIMAL', quantityLabel: 'Gram' },
    { id: 'FUND', label: 'Fon', templateType: 'SPOT', assetClass: 'SPOT_EQUITY', assetType: 'FUND', quantityMode: 'INTEGER', quantityLabel: 'Pay' },
    { id: 'FUTURES', label: 'VIOP', templateType: 'FUTURES', assetClass: 'FUTURES_INDEX', assetType: 'STOCK', quantityMode: 'INTEGER', quantityLabel: 'Kontrat' },
    { id: 'BOND', label: 'Tahvil', templateType: 'FIXED_INCOME', assetClass: 'BOND_GOV', assetType: 'FUND', quantityMode: 'INTEGER', quantityLabel: 'Nominal' },
];

export function Trade() {
    const { tokens } = useTheme();
    const { t } = useLanguage();
    const [searchParams] = useSearchParams();

    const [category, setCategory] = useState<TradeCategory>('CRYPTO');
    const selectedCategory = useMemo(
        () => TRADE_CATEGORIES.find((item) => item.id === category) ?? TRADE_CATEGORIES[1],
        [category],
    );
    const [templateType, setTemplateType] = useState<TemplateType>(selectedCategory.templateType);
    const [assetClass, setAssetClass] = useState<AssetClass>(selectedCategory.assetClass);
    const [assetType, setAssetType] = useState<AssetType>(selectedCategory.assetType);
    const [symbol, setSymbol] = useState('');

    const [tradeType, setTradeType] = useState<TradeType>('BUY');
    const [quantity, setQuantity] = useState<string>('0.1');
    const [limitPrice, setLimitPrice] = useState<string>('');
    const [contractMonth, setContractMonth] = useState<string>('Haz 2026');
    const [nominal, setNominal] = useState<string>('');
    const [priceMode, setPriceMode] = useState<'PRICE' | 'YIELD'>('PRICE');

    const [symbolPool, setSymbolPool] = useState<string[]>([]);
    const [symbolPoolLoading, setSymbolPoolLoading] = useState(false);
    const symbolPoolRequestIdRef = useRef(0);
    const [lastPrice, setLastPrice] = useState<number | null>(null);

    const [submitting, setSubmitting] = useState(false);
    const [submitError, setSubmitError] = useState<string | null>(null);
    const [restrictionToast, setRestrictionToast] = useState<string | null>(null);
    const [lastTrade, setLastTrade] = useState<TradeResponse | null>(null);

    const [history, setHistory] = useState<Page<TradeHistoryItem> | null>(null);
    const [historyLoading, setHistoryLoading] = useState(true);
    const [historyError, setHistoryError] = useState<string | null>(null);
    const [historyPage, setHistoryPage] = useState(0);
    const pageSize = 10;

    const formatTry = (v: number) => '₺' + v.toLocaleString('tr-TR', { maximumFractionDigits: 2 });
    const formatUsd = (v: number) => '$' + v.toLocaleString('tr-TR', { maximumFractionDigits: 4 });
    const formatMoney = formatTry;
    const templateTheme = TEMPLATE_THEME[templateType];

    const { data: usdTryPayload } = useQuery({
        queryKey: ['market', 'terminal', 'usd-try-rate', 'trade'],
        queryFn: () =>
            financeClient.get<{ rate: number; available: boolean }>('/api/market/terminal/usd-try-rate').then((r) => unwrapFinance(r)),
        staleTime: 15_000,
        refetchInterval: 25_000,
    });
    const usdTryRate =
        usdTryPayload?.available && Number.isFinite(Number(usdTryPayload.rate)) && Number(usdTryPayload.rate) > 0
            ? Number(usdTryPayload.rate)
            : null;

    useEffect(() => {
        setTemplateType(selectedCategory.templateType);
        setAssetClass(selectedCategory.assetClass);
        setAssetType(selectedCategory.assetType);
        setQuantity(selectedCategory.quantityMode === 'INTEGER' ? '1' : '0.1');
        setLimitPrice('');
        setNominal('');
    }, [selectedCategory]);

    /** Kategori değişince önceki kategorinin sembolü dropdown’da kalmaması için */
    useEffect(() => {
        setSymbol('');
        setLastPrice(null);
    }, [category]);

    useEffect(() => {
        const kind = String(searchParams.get('kind') ?? '').toUpperCase();
        const symbolFromQs = searchParams.get('symbol');
        if (kind === 'BOND') {
            setCategory('BOND');
        } else if (kind === 'FUTURES') {
            setCategory('FUTURES');
        } else if (kind === 'STOCK') {
            setCategory('EQUITY');
        }
        if (symbolFromQs) {
            const normalized = symbolFromQs.toUpperCase();
            setSymbol(normalized);
            if (ISIN_PATTERN.test(normalized)) {
                setCategory('BOND');
            } else if (FUTURES_PATTERN.test(normalized)) {
                setCategory('FUTURES');
                setAssetClass(classifyViopContract(normalized));
            }
        }
    }, [searchParams]);

    const loadSymbols = useCallback(async () => {
        const reqId = ++symbolPoolRequestIdRef.current;
        setSymbolPoolLoading(true);
        setSymbolPool([]);
        try {
            let nextPool: string[] = [];
            if (category === 'FUND') {
                const res = await marketClient.get<Record<string, unknown>>('/api/market/funds/latest');
                nextPool = Object.keys(res.data ?? {})
                    .map((s) => String(s ?? '').toUpperCase())
                    .filter(Boolean)
                    .sort((a, b) => a.localeCompare(b, 'tr-TR'));
            } else if (category === 'FUTURES') {
                const classes: AssetClass[] = ['FUTURES_INDEX', 'FUTURES_FX', 'FUTURES_COMMODITY', 'FUTURES_EQUITY'];
                const all = await Promise.all(classes.map((cls) => fetchViopSymbolsByClass(cls)));
                nextPool = Array.from(new Set(all.flat())).sort((a, b) => a.localeCompare(b, 'tr-TR'));
            } else if (templateType === 'SPOT') {
                nextPool = await fetchSpotSymbolsByAssetClass(assetClass);
            } else {
                nextPool = await fetchDebtSymbolsByClass(assetClass);
            }
            if (reqId === symbolPoolRequestIdRef.current) {
                setSymbolPool(nextPool);
            }
        } catch {
            if (reqId === symbolPoolRequestIdRef.current) {
                setSymbolPool([]);
            }
        } finally {
            if (reqId === symbolPoolRequestIdRef.current) {
                setSymbolPoolLoading(false);
            }
        }
    }, [templateType, assetClass, category]);

    useEffect(() => {
        if (!orderFormV2Enabled) return;
        void loadSymbols();
    }, [loadSymbols]);

    const tradeSymbol = useMemo(() => {
        if (!symbolPool.length) return '';
        if (symbol && symbolPool.includes(symbol)) return symbol;
        return symbolPool[0];
    }, [symbolPool, symbol]);

    useEffect(() => {
        if (category !== 'FUTURES' || !tradeSymbol) return;
        const futuresClass = classifyViopContract(tradeSymbol);
        setAssetClass(futuresClass);
        const option = getAssetClassOption(futuresClass);
        if (option) {
            setAssetType(option.backendAssetType);
        }
    }, [category, tradeSymbol]);

    useEffect(() => {
        if (!tradeSymbol) {
            setLastPrice(null);
            return;
        }
        const readPrice = (row: Record<string, unknown> | undefined): number | null => {
            if (!row) return null;
            const candidates = [
                Number(row.tryPrice),
                Number(row.lastPrice),
                Number(row.price),
                Number(row.closePrice),
                Number(row.buyPrice),
                Number(row.sellPrice),
                Number(row.dirtyPrice),
                Number(row.midPrice),
            ];
            const hit = candidates.find((v) => Number.isFinite(v) && v > 0);
            return typeof hit === 'number' ? hit : null;
        };

        const pickSpotMapRow = (
            raw: Record<string, Record<string, unknown>>,
            sym: string,
        ): Record<string, unknown> | undefined => {
            if (sym && raw[sym]) return raw[sym];
            if (category === 'METAL') {
                for (const k of ['XAU_TRY', 'ALTIN_TRY', sym]) {
                    if (k && raw[k]) return raw[k];
                }
                const first = Object.keys(raw)[0];
                return first ? raw[first] : undefined;
            }
            return undefined;
        };

        const fetchPrice = async () => {
            try {
                if (category === 'FUTURES') {
                    const res = await marketClient.get<Array<Record<string, unknown>>>('/api/market/viop/latest');
                    const row = (res.data ?? []).find((item) => String(item.contractCode ?? '').toUpperCase() === tradeSymbol);
                    setLastPrice(readPrice(row));
                    return;
                }
                if (category === 'BOND') {
                    const res = await marketClient.get<Array<Record<string, unknown>>>('/api/market/debt/latest');
                    const row = (res.data ?? []).find((item) => String(item.isin ?? '').toUpperCase() === tradeSymbol);
                    setLastPrice(readPrice(row));
                    return;
                }
                const endpoint =
                    category === 'EQUITY'
                        ? '/api/market/equity/latest'
                        : category === 'CRYPTO'
                            ? '/api/market/crypto/latest'
                            : category === 'FX'
                                ? '/api/market/doviz/latest'
                                : category === 'METAL'
                                    ? '/api/market/metals/latest'
                                    : '/api/market/funds/latest';
                const res = await marketClient.get<Record<string, Record<string, unknown>>>(endpoint);
                const raw = (res.data ?? {}) as Record<string, Record<string, unknown>>;
                const row = pickSpotMapRow(raw, tradeSymbol);
                setLastPrice(readPrice(row));
            } catch {
                setLastPrice(null);
            }
        };
        void fetchPrice();
    }, [tradeSymbol, category]);

    const priceUsdQuoted = tradePriceQuotedUsd(category);

    const estimatedTotalTry = useMemo(() => {
        const qty = Number(quantity);
        if (!Number.isFinite(qty) || qty <= 0 || lastPrice == null || !Number.isFinite(lastPrice)) return null;
        if (priceUsdQuoted) {
            if (usdTryRate == null) return null;
            return qty * lastPrice * usdTryRate;
        }
        return qty * lastPrice;
    }, [quantity, lastPrice, priceUsdQuoted, usdTryRate]);

    const estimatedTotalUsd = useMemo(() => {
        const qty = Number(quantity);
        if (!priceUsdQuoted || !Number.isFinite(qty) || qty <= 0 || lastPrice == null || !Number.isFinite(lastPrice)) {
            return null;
        }
        return qty * lastPrice;
    }, [quantity, lastPrice, priceUsdQuoted]);

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
        if (!tradeSymbol.trim() || Number.isNaN(qty) || qty <= 0) {
            setSubmitError('Lutfen sembol secin ve miktar girin.');
            return;
        }
        if (selectedCategory.quantityMode === 'INTEGER' && !Number.isInteger(qty)) {
            setSubmitError('Bu varlik turunde miktar tam sayi olmalidir.');
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
            symbol: tradeSymbol.trim(),
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
            <h1 style={titleStyle}>{t('trade.title', 'Alim ve Satim')}</h1>
            <p style={mutedStyle}>{t('trade.subtitle', 'Enstruman sablonu, varlik sinifi ve sembol hiyerarsisi ile profesyonel emir akisi.')}</p>

            <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1.3fr) minmax(0, 2fr)', gap: 24, marginBottom: 32 }}>
                <div style={{ ...cardStyle, border: `1px solid ${templateTheme.border}`, background: templateTheme.bg }}>
                    <h2 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: 6 }}>{t('trade.orderForm', 'Emir Formu')}</h2>
                    <div style={{ fontSize: '0.75rem', color: tokens.textMuted, marginBottom: 14 }}>{templateTheme.chip} templatine uygun varliklar listelenir.</div>
                    <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                        <label style={{ fontSize: '0.875rem' }}>
                            {t('trade.assetCategory', 'Varlik Kategorisi')}
                            <div
                                style={{
                                    marginTop: 6,
                                    display: 'grid',
                                    gridTemplateColumns: 'repeat(auto-fit, minmax(106px, 1fr))',
                                    gap: 8,
                                }}
                            >
                                {TRADE_CATEGORIES.map((item) => (
                                    <button
                                        key={item.id}
                                        type="button"
                                        onClick={() => setCategory(item.id)}
                                        style={{
                                            padding: '8px 10px',
                                            borderRadius: 10,
                                            border: category === item.id ? `2px solid ${tokens.success}` : `1px solid ${tokens.border}`,
                                            background: category === item.id ? 'rgba(34, 197, 94, 0.12)' : tokens.bgCard,
                                            color: category === item.id ? tokens.success : tokens.text,
                                            fontWeight: 600,
                                            cursor: 'pointer',
                                        }}
                                    >
                                        {item.label}
                                    </button>
                                ))}
                            </div>
                        </label>
                        <label style={{ fontSize: '0.875rem' }}>
                            {t('trade.symbol', 'Sembol')}
                            <div style={{ fontSize: '0.75rem', color: tokens.textMuted, marginTop: 4 }}>
                                Manuel sembol yazma kapali. Sisteminizdeki {selectedCategory.label} varliklari dropdown ile secilir.
                            </div>
                            {symbolPoolLoading ? (
                                <div style={{ ...inputStyle, color: tokens.textMuted }}>{t('common.loading', 'Yukleniyor...')}</div>
                            ) : symbolPool.length === 0 ? (
                                <div style={{ ...inputStyle, color: tokens.textMuted }}>{t('trade.noSymbolsForSelection', 'Bu secim icin sembol bulunamadi.')}</div>
                            ) : (
                                <select value={tradeSymbol} onChange={(e) => setSymbol(e.target.value)} style={inputStyle}>
                                    {symbolPool.map((s) => (
                                        <option key={s} value={s}>{s}</option>
                                    ))}
                                </select>
                            )}
                        </label>
                        <label style={{ fontSize: '0.875rem' }}>
                            {t('trade.side', 'Yon')}
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
                            {t('trade.quantity', 'Miktar')} ({selectedCategory.quantityLabel})
                            <input
                                type="number"
                                step={selectedCategory.quantityMode === 'INTEGER' ? '1' : '0.0001'}
                                min={selectedCategory.quantityMode === 'INTEGER' ? '1' : '0.0001'}
                                value={quantity}
                                onChange={(e) => setQuantity(e.target.value)}
                                style={inputStyle}
                            />
                        </label>
                        {templateType === 'SPOT' ? (
                            <label style={{ fontSize: '0.875rem' }}>
                                {t('trade.limitPriceOptional', 'Limit Fiyat (opsiyonel)')}
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
                        {lastPrice != null && Number.isFinite(lastPrice) ? (
                            <div style={{ borderRadius: 10, border: `1px solid ${tokens.border}`, background: tokens.bgCard, padding: 10 }}>
                                <div style={{ fontSize: '0.8rem', color: tokens.textMuted }}>Son fiyat</div>
                                {priceUsdQuoted ? (
                                    <>
                                        <div style={{ fontSize: '1rem', fontWeight: 700, marginTop: 2 }}>{formatUsd(lastPrice)}</div>
                                        <div style={{ fontSize: '0.9rem', fontWeight: 600, marginTop: 4, opacity: 0.9 }}>
                                            {usdTryRate != null
                                                ? formatTry(lastPrice * usdTryRate)
                                                : '₺ karşılığı yükleniyor…'}
                                        </div>
                                    </>
                                ) : (
                                    <div style={{ fontSize: '1rem', fontWeight: 700, marginTop: 2 }}>{formatTry(lastPrice)}</div>
                                )}
                                <div style={{ fontSize: '0.8rem', color: tokens.textMuted, marginTop: 6 }}>Tahmini toplam</div>
                                {priceUsdQuoted ? (
                                    <div style={{ fontSize: '0.9rem', fontWeight: 600, marginTop: 2 }}>
                                        {estimatedTotalUsd != null ? (
                                            <span>{formatUsd(estimatedTotalUsd)}</span>
                                        ) : (
                                            <span>—</span>
                                        )}
                                        <span style={{ margin: '0 6px', color: tokens.textMuted }}>·</span>
                                        {estimatedTotalTry != null ? (
                                            <span>{formatTry(estimatedTotalTry)}</span>
                                        ) : (
                                            <span>{usdTryRate != null ? '—' : '₺ için kur bekleniyor'}</span>
                                        )}
                                    </div>
                                ) : (
                                    <div style={{ fontSize: '0.9rem', fontWeight: 600, marginTop: 2 }}>
                                        {estimatedTotalTry != null ? formatTry(estimatedTotalTry) : '-'}
                                    </div>
                                )}
                            </div>
                        ) : null}
                        {submitError && <div style={{ color: tokens.error, fontSize: '0.8125rem' }}>{submitError}</div>}
                        <button type="submit" disabled={submitting || symbolPool.length === 0} style={{ marginTop: 8, padding: '10px 0', borderRadius: 8, border: 'none', background: tradeType === 'BUY' ? 'linear-gradient(90deg,#16a34a,#22c55e)' : 'linear-gradient(90deg,#f97316,#fb923c)', color: '#fff', fontWeight: 600, fontSize: '0.9375rem', cursor: submitting ? 'default' : 'pointer' }}>
                            {submitting ? t('common.submitting', 'Gonderiliyor...') : t('trade.submitOrder', 'Emri gonder')}
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
                    <h2 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: 16 }}>{t('trade.history', 'Gecmis Islemler')}</h2>
                    {historyError && <div style={{ color: tokens.error, marginBottom: 8, fontSize: '0.8125rem' }}>{historyError}</div>}
                    {historyLoading && <p style={mutedStyle}>{t('common.loading', 'Yukleniyor...')}</p>}
                    {history && history.content.length === 0 && !historyLoading && <p style={mutedStyle}>Henuz islem yok.</p>}
                    {history && history.content.length > 0 && (
                        <>
                            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.875rem' }}>
                                <thead>
                                <tr style={{ borderBottom: `2px solid ${tokens.border}` }}>
                                    <th style={{ textAlign: 'left', padding: 10 }}>Tarih</th>
                                    <th style={{ textAlign: 'left', padding: 10 }}>{t('trade.symbol', 'Sembol')}</th>
                                    <th style={{ textAlign: 'left', padding: 10 }}>Tur</th>
                                    <th style={{ textAlign: 'right', padding: 10 }}>{t('trade.quantity', 'Miktar')}</th>
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
                                        {t('news.prev', 'Önceki')}
                                    </button>
                                    <span style={{ color: tokens.textMuted }}>Sayfa {history.number + 1} / {Math.ceil(history.totalElements / history.size)}</span>
                                    <button type="button" disabled={(history.number + 1) * history.size >= history.totalElements} onClick={() => setHistoryPage((p) => p + 1)} style={{ padding: '6px 12px', borderRadius: 8, border: `1px solid ${tokens.border}`, background: tokens.bgCard, color: tokens.text, cursor: (history.number + 1) * history.size >= history.totalElements ? 'default' : 'pointer', opacity: (history.number + 1) * history.size >= history.totalElements ? 0.6 : 1 }}>
                                        {t('news.next', 'Sonraki')}
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
