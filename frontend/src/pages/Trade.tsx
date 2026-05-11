import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import type { AxiosResponse } from 'axios';
import { Check, Loader2 } from 'lucide-react';
import { financeClient, marketClient } from '../api/client';
import { useTheme } from '../theme/ThemeContext';
import { useLanguage } from '../i18n/LanguageContext';
import { useSearchParams } from 'react-router-dom';
import {
    TEMPLATE_LABELS,
    getTradeSideAndDetail,
    getAssetClassOption,
    type AssetClass,
    type AssetType,
    type TemplateType,
    type TradeType,
} from '../constants/OrderConstants';
import { fetchSpotSymbolsByAssetClass } from '../services/marketDataService';
import './Trade.css';

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

type BalanceMe = { currentAmount: number };
type PortfolioRow = { symbol: string; type: string; quantity: number };

const orderFormV2Enabled = String(import.meta.env.VITE_ORDER_FORM_V2_ENABLED ?? 'true') === 'true';

function unwrapFinance<T>(res: AxiosResponse<T>): T {
    const body = res.data as unknown;
    if (body && typeof body === 'object' && 'data' in (body as object)) {
        return (body as { data: T }).data;
    }
    return body as T;
}

function tradePriceQuotedUsd(category: TradeCategory): boolean {
    return category === 'EQUITY' || category === 'CRYPTO';
}
const FUTURES_PATTERN = /^[A-Z0-9_]+\d{4}$/;
type TradeCategory = 'EQUITY' | 'CRYPTO' | 'FX' | 'METAL' | 'FUND';
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
];

const QUICK_PCTS = [0.25, 0.5, 0.75, 1] as const;

function buildHistPageItems(current: number, totalPages: number): (number | 'gap')[] {
    if (totalPages <= 1) return [];
    if (totalPages <= 9) {
        return Array.from({ length: totalPages }, (_, i) => i);
    }
    const set = new Set<number>();
    set.add(0);
    set.add(totalPages - 1);
    for (let d = -2; d <= 2; d++) {
        const p = current + d;
        if (p >= 0 && p < totalPages) set.add(p);
    }
    const sorted = [...set].sort((a, b) => a - b);
    const out: (number | 'gap')[] = [];
    for (let i = 0; i < sorted.length; i++) {
        if (i > 0 && sorted[i]! - sorted[i - 1]! > 1) out.push('gap');
        out.push(sorted[i]!);
    }
    return out;
}

export function Trade() {
    const { tokens } = useTheme();
    const { t, lang } = useLanguage();
    const locale = lang === 'en' ? 'en-US' : 'tr-TR';
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

    const [symbolPool, setSymbolPool] = useState<string[]>([]);
    const [symbolPoolLoading, setSymbolPoolLoading] = useState(false);
    const symbolPoolRequestIdRef = useRef(0);
    const [lastPrice, setLastPrice] = useState<number | null>(null);

    const [submitUi, setSubmitUi] = useState<'idle' | 'loading' | 'success'>('idle');
    const [submitError, setSubmitError] = useState<string | null>(null);
    const [restrictionToast, setRestrictionToast] = useState<string | null>(null);
    const [lastTrade, setLastTrade] = useState<TradeResponse | null>(null);

    const [history, setHistory] = useState<Page<TradeHistoryItem> | null>(null);
    const [historyLoading, setHistoryLoading] = useState(true);
    const [historyError, setHistoryError] = useState<string | null>(null);
    const [historyPage, setHistoryPage] = useState(0);
    const pageSize = 12;

    const [estimateFlash, setEstimateFlash] = useState<'up' | 'down' | null>(null);
    const estimateFlashRef = useRef<string>('');

    const formatTryDisplay = (v: number) =>
        `${v.toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} ₺`;
    const formatUsd = (v: number) => '$' + v.toLocaleString('tr-TR', { maximumFractionDigits: 4 });

    const templateLabelTr = useMemo(() => {
        switch (templateType) {
            case 'SPOT':
                return t('trade.templateSpot', 'Spot');
            case 'FUTURES':
                return t('trade.templateFutures', 'VİOP');
            case 'FIXED_INCOME':
                return t('trade.templateFixed', 'Tahvil / Bono');
            default:
                return TEMPLATE_LABELS[templateType];
        }
    }, [templateType, t]);

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

    const { data: balanceData } = useQuery({
        queryKey: ['balance', 'me', 'trade'],
        queryFn: () => financeClient.get<BalanceMe>('/api/balance/me').then((r) => unwrapFinance(r)),
        staleTime: 15_000,
        refetchInterval: 45_000,
    });
    const availableTry = Number(balanceData?.currentAmount ?? 0);

    const { data: portfolioRows } = useQuery({
        queryKey: ['portfolio', 'unified', 'trade'],
        queryFn: () => financeClient.get<PortfolioRow[]>('/api/portfolio/me/unified').then((r) => unwrapFinance(r) as PortfolioRow[]),
        staleTime: 20_000,
    });

    useEffect(() => {
        setTemplateType(selectedCategory.templateType);
        setAssetClass(selectedCategory.assetClass);
        setAssetType(selectedCategory.assetType);
        setQuantity(selectedCategory.quantityMode === 'INTEGER' ? '1' : '0.1');
        setLimitPrice('');
    }, [selectedCategory]);

    useEffect(() => {
        setSymbol('');
        setLastPrice(null);
    }, [category]);

    useEffect(() => {
        const kind = String(searchParams.get('kind') ?? '').toUpperCase();
        const symbolFromQs = searchParams.get('symbol');
        if (kind === 'STOCK') {
            setCategory('EQUITY');
        } else if (kind === 'BOND' || kind === 'FUTURES') {
            setCategory('CRYPTO');
        }
        if (symbolFromQs) {
            const normalized = symbolFromQs.toUpperCase();
            setSymbol(normalized);
            if (FUTURES_PATTERN.test(normalized)) {
                setCategory('CRYPTO');
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
            } else {
                nextPool = await fetchSpotSymbolsByAssetClass(assetClass);
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
    }, [assetClass, category]);

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

    const unitTry = useMemo(() => {
        if (lastPrice == null || !Number.isFinite(lastPrice)) return null;
        if (priceUsdQuoted) {
            if (usdTryRate == null) return null;
            return lastPrice * usdTryRate;
        }
        return lastPrice;
    }, [lastPrice, priceUsdQuoted, usdTryRate]);

    const estimatedTotalTry = useMemo(() => {
        const qty = Number(quantity);
        if (!Number.isFinite(qty) || qty <= 0 || unitTry == null || !Number.isFinite(unitTry)) return null;
        return qty * unitTry;
    }, [quantity, unitTry]);

    const estimatedTotalUsd = useMemo(() => {
        const qty = Number(quantity);
        if (!priceUsdQuoted || !Number.isFinite(qty) || qty <= 0 || lastPrice == null || !Number.isFinite(lastPrice)) {
            return null;
        }
        return qty * lastPrice;
    }, [quantity, lastPrice, priceUsdQuoted]);

    const maxBuyQty = useMemo(() => {
        if (tradeType !== 'BUY' || unitTry == null || unitTry <= 0 || availableTry <= 0) return 0;
        const raw = availableTry / unitTry;
        return selectedCategory.quantityMode === 'INTEGER' ? Math.floor(raw) : raw;
    }, [tradeType, unitTry, availableTry, selectedCategory.quantityMode]);

    const maxSellQty = useMemo(() => {
        if (tradeType !== 'SELL' || !portfolioRows?.length || !tradeSymbol) return 0;
        const rows = portfolioRows.filter((r) => r.symbol === tradeSymbol && String(r.type).toUpperCase() === assetType);
        return rows.reduce((s, r) => s + Number(r.quantity ?? 0), 0);
    }, [tradeType, portfolioRows, tradeSymbol, assetType]);

    const quickFillMax = tradeType === 'BUY' ? maxBuyQty : maxSellQty;

    const estimateFlashKey = `${lastPrice ?? ''}|${estimatedTotalTry ?? ''}`;
    useEffect(() => {
        if (!estimateFlashKey || estimateFlashKey === '|') {
            estimateFlashRef.current = estimateFlashKey;
            return;
        }
        const prev = estimateFlashRef.current;
        estimateFlashRef.current = estimateFlashKey;
        if (!prev || prev === estimateFlashKey) return;
        const prevTry = Number(prev.split('|')[1] ?? '');
        const nextTry = Number(estimateFlashKey.split('|')[1] ?? '');
        if (!Number.isFinite(prevTry) || !Number.isFinite(nextTry)) {
            setEstimateFlash(null);
            return;
        }
        if (nextTry === prevTry) {
            setEstimateFlash(null);
            return;
        }
        setEstimateFlash(nextTry > prevTry ? 'up' : 'down');
        const timer = window.setTimeout(() => setEstimateFlash(null), 750);
        return () => window.clearTimeout(timer);
    }, [estimateFlashKey]);

    const loadHistory = useCallback(() => {
        setHistoryLoading(true);
        setHistoryError(null);
        financeClient
            .get<Page<TradeHistoryItem>>('/api/trades/history', { params: { page: historyPage, size: pageSize } })
            .then((res) => setHistory((res.data as any)?.data ?? res.data))
            .catch((err) => setHistoryError(err.response?.data?.message ?? err.message ?? t('common.loadingFailed', 'Yüklenemedi')))
            .finally(() => setHistoryLoading(false));
    }, [historyPage, pageSize, t]);

    useEffect(() => {
        loadHistory();
    }, [loadHistory]);

    const applyQuickPct = (pct: number) => {
        const max = quickFillMax;
        if (!Number.isFinite(max) || max <= 0) return;
        const v = max * pct;
        const q =
            selectedCategory.quantityMode === 'INTEGER' ? Math.max(0, Math.floor(v)) : Math.max(0, Number(v.toFixed(6)));
        setQuantity(String(q));
    };

    const handleSubmit = (e: React.FormEvent) => {
        e.preventDefault();
        const qty = Number(quantity);
        if (!tradeSymbol.trim() || Number.isNaN(qty) || qty <= 0) {
            setSubmitError(t('trade.errorSymbolQty', 'Lütfen sembol seçin ve miktar girin.'));
            return;
        }
        if (selectedCategory.quantityMode === 'INTEGER' && !Number.isInteger(qty)) {
            setSubmitError(t('trade.errorIntegerQty', 'Bu varlık türünde miktar tam sayı olmalıdır.'));
            return;
        }
        if (orderFormV2Enabled) {
            const selectedClass = getAssetClassOption(assetClass);
            if (!selectedClass || selectedClass.template !== templateType) {
                setSubmitError(t('trade.errorTemplateMismatch', 'Şablon ve varlık türü uyumsuz.'));
                return;
            }
        }
        const attributes: Record<string, unknown> = { assetClass };
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
        setSubmitUi('loading');
        setSubmitError(null);
        setRestrictionToast(null);
        financeClient
            .post<{ data: TradeResponse }>('/api/trades', payload)
            .then((res) => {
                setLastTrade((res.data as any)?.data ?? res.data);
                setHistoryPage(0);
                setSubmitUi('success');
                window.setTimeout(() => setSubmitUi('idle'), 2200);
                window.setTimeout(loadHistory, 200);
            })
            .catch((err) => {
                setSubmitUi('idle');
                const msg =
                    err.response?.data?.errors?.error ??
                    err.response?.data?.message ??
                    err.message ??
                    t('trade.submitError', 'Emir gönderilirken hata oluştu.');
                setSubmitError(msg);
                if (String(msg).includes('MARKET_ORDER_RESTRICTION')) {
                    setRestrictionToast(
                        `${t('trade.marketRestriction', 'Kısıt')}: ${String(msg).replace('MARKET_ORDER_RESTRICTION:', '').trim()}`,
                    );
                }
            });
    };

    const glowVar =
        tradeType === 'BUY'
            ? 'rgba(57, 255, 20, 0.22)'
            : 'rgba(248, 113, 113, 0.28)';

    const histTotalPages = history ? Math.max(1, Math.ceil(history.totalElements / history.size)) : 1;
    const histPageItems = buildHistPageItems(history?.number ?? 0, histTotalPages);

    return (
        <div className="trade-terminal-root" style={{ background: tokens.bg, color: tokens.text }}>
            <div className="trade-terminal-inner">
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
                <h1 className="trade-terminal-title">{t('trade.title', 'Alım ve Satım')}</h1>
                <p className="trade-terminal-sub" style={{ color: tokens.textMuted }}>
                    {t('trade.subtitle', 'Enstrüman şablonu, varlık sınıfı ve sembol hiyerarşisi ile profesyonel emir akışı.')}
                </p>

                <div className="trade-terminal-grid">
                    <div
                        className="trade-card-premium trade-order-card"
                        style={{ '--trade-glow': glowVar } as React.CSSProperties}
                    >
                        <h2 className="trade-field-label" style={{ marginBottom: 4, letterSpacing: '0.08em' }}>
                            {t('trade.orderForm', 'Emir paneli')}
                        </h2>
                        <p className="trade-hint" style={{ marginBottom: 12 }}>
                            {templateLabelTr} {t('trade.templateLine', 'şablonuna uygun varlıklar listelenir.')}
                        </p>
                        <form onSubmit={handleSubmit} className="trade-order-form">
                            <div className="trade-side-toggle">
                                <button
                                    type="button"
                                    className={`trade-side-btn trade-side-btn--buy ${tradeType === 'BUY' ? 'trade-side-btn--active' : ''}`}
                                    onClick={() => setTradeType('BUY')}
                                >
                                    {t('trade.buy', 'AL')}
                                </button>
                                <button
                                    type="button"
                                    className={`trade-side-btn trade-side-btn--sell ${tradeType === 'SELL' ? 'trade-side-btn--active' : ''}`}
                                    onClick={() => setTradeType('SELL')}
                                >
                                    {t('trade.sell', 'SAT')}
                                </button>
                            </div>

                            <div>
                                <span className="trade-field-label">{t('trade.assetCategory', 'Varlık kategorisi')}</span>
                                <div className="trade-category-tabs" role="tablist">
                                    {TRADE_CATEGORIES.map((item) => (
                                        <button
                                            key={item.id}
                                            type="button"
                                            role="tab"
                                            aria-selected={category === item.id}
                                            className={`trade-category-tab ${category === item.id ? 'trade-category-tab--active' : ''}`}
                                            onClick={() => setCategory(item.id)}
                                        >
                                            {item.label}
                                        </button>
                                    ))}
                                </div>
                            </div>

                            <div>
                                <span className="trade-field-label">{t('trade.symbol', 'Sembol')}</span>
                                <p className="trade-hint" style={{ marginTop: 6 }}>
                                    {t('trade.symbolDropdownHint', 'Manuel sembol girişi kapalı; listeden seçim yapın.')}
                                </p>
                                {symbolPoolLoading ? (
                                    <div className="trade-input" style={{ color: tokens.textMuted }}>
                                        {t('common.loading', 'Yükleniyor...')}
                                    </div>
                                ) : symbolPool.length === 0 ? (
                                    <div className="trade-input" style={{ color: tokens.textMuted }}>
                                        {t('trade.noSymbolsForSelection', 'Bu seçim için sembol bulunamadı.')}
                                    </div>
                                ) : (
                                    <select
                                        className="trade-input"
                                        value={tradeSymbol}
                                        onChange={(e) => setSymbol(e.target.value)}
                                    >
                                        {symbolPool.map((s) => (
                                            <option key={s} value={s}>
                                                {s}
                                            </option>
                                        ))}
                                    </select>
                                )}
                            </div>

                            <label className="trade-field-label" style={{ display: 'block' }}>
                                <span>
                                    {t('trade.quantity', 'Miktar')} ({selectedCategory.quantityLabel})
                                </span>
                                <input
                                    type="number"
                                    className="trade-input"
                                    step={selectedCategory.quantityMode === 'INTEGER' ? '1' : '0.0001'}
                                    min={selectedCategory.quantityMode === 'INTEGER' ? '1' : '0.0001'}
                                    value={quantity}
                                    onChange={(e) => setQuantity(e.target.value)}
                                />
                                <div className="trade-quick-row">
                                    {QUICK_PCTS.map((pct) => (
                                        <button
                                            key={pct}
                                            type="button"
                                            className="trade-quick-btn"
                                            disabled={quickFillMax <= 0 || lastPrice == null}
                                            onClick={() => applyQuickPct(pct)}
                                        >
                                            %{Math.round(pct * 100)}
                                        </button>
                                    ))}
                                </div>
                                {tradeType === 'BUY' && maxBuyQty > 0 && (
                                    <p className="trade-hint" style={{ marginTop: 6 }}>
                                        {t('trade.quickFillBuyHint', 'Hızlı miktar: kullanılabilir bakiyeye göre maksimum alım.')}
                                    </p>
                                )}
                                {tradeType === 'SELL' && (
                                    <p className="trade-hint" style={{ marginTop: 6 }}>
                                        {maxSellQty > 0
                                            ? `${t('trade.quickFillSellHint', 'Hızlı miktar: portföydeki')} ${tradeSymbol} ${t('trade.quickFillSellHintTail', 'miktarına göre.')}`
                                            : t('trade.quickFillSellNoPos', 'Satış için bu sembolde kayıtlı pozisyon bulunamadı; miktarı elle girin.')}
                                    </p>
                                )}
                            </label>

                            {templateType === 'SPOT' ? (
                                <label className="trade-field-label" style={{ display: 'block' }}>
                                    {t('trade.limitPriceOptional', 'Limit fiyat (opsiyonel)')}
                                    <input
                                        type="number"
                                        className="trade-input"
                                        step="0.0001"
                                        value={limitPrice}
                                        onChange={(e) => setLimitPrice(e.target.value)}
                                    />
                                </label>
                            ) : null}

                            <p className="trade-hint">{t('trade.spotRoutingHint', 'Spot emirler piyasa kotasyonları üzerinden işlenir.')}</p>

                            {lastPrice != null && Number.isFinite(lastPrice) ? (
                                <div
                                    className={`trade-estimate-card ${
                                        estimateFlash === 'up'
                                            ? 'trade-estimate-card--flash-up'
                                            : estimateFlash === 'down'
                                              ? 'trade-estimate-card--flash-down'
                                              : ''
                                    }`}
                                >
                                    <div className="trade-estimate-label">{t('trade.priceCardTitle', 'Piyasa özeti')}</div>
                                    {priceUsdQuoted ? (
                                        <>
                                            <div className="trade-mono" style={{ fontSize: '1.05rem', fontWeight: 700 }}>
                                                {formatUsd(lastPrice)}
                                            </div>
                                            <div className="trade-mono" style={{ fontSize: '0.92rem', fontWeight: 600, marginTop: 4, opacity: 0.92 }}>
                                                {usdTryRate != null ? formatTryDisplay(lastPrice * usdTryRate) : t('trade.tryRateLoading', '₺ karşılığı yükleniyor…')}
                                            </div>
                                        </>
                                    ) : (
                                        <div className="trade-mono" style={{ fontSize: '1.05rem', fontWeight: 700 }}>
                                            {formatTryDisplay(lastPrice)}
                                        </div>
                                    )}
                                    <div style={{ fontSize: '0.72rem', fontWeight: 700, letterSpacing: '0.08em', color: 'rgba(184,193,204,0.9)', marginTop: 10, textTransform: 'uppercase' }}>
                                        {t('trade.estimatedTotal', 'Tahmini toplam')}
                                    </div>
                                    {priceUsdQuoted ? (
                                        <div className="trade-mono" style={{ fontSize: '0.95rem', fontWeight: 700, marginTop: 4 }}>
                                            {estimatedTotalUsd != null ? (
                                                <span>{formatUsd(estimatedTotalUsd)}</span>
                                            ) : (
                                                <span>—</span>
                                            )}
                                            <span style={{ margin: '0 8px', opacity: 0.5 }}>·</span>
                                            {estimatedTotalTry != null ? (
                                                <span>{formatTryDisplay(estimatedTotalTry)}</span>
                                            ) : (
                                                <span>{usdTryRate != null ? '—' : t('trade.waitingFx', 'Kur bekleniyor')}</span>
                                            )}
                                        </div>
                                    ) : (
                                        <div className="trade-mono" style={{ fontSize: '0.95rem', fontWeight: 700, marginTop: 4 }}>
                                            {estimatedTotalTry != null ? formatTryDisplay(estimatedTotalTry) : '—'}
                                        </div>
                                    )}
                                </div>
                            ) : null}

                            {submitError && <div style={{ color: tokens.error, fontSize: '0.8125rem' }}>{submitError}</div>}

                            <button
                                type="submit"
                                disabled={submitUi === 'loading' || submitUi === 'success' || symbolPool.length === 0}
                                className={`trade-submit-btn ${tradeType === 'BUY' ? 'trade-submit-btn--buy' : 'trade-submit-btn--sell'}`}
                            >
                                {submitUi === 'loading' ? (
                                    <>
                                        <Loader2 size={20} className="trade-spin" />
                                        {t('trade.processing', 'İşleniyor...')}
                                    </>
                                ) : submitUi === 'success' ? (
                                    <>
                                        <Check size={22} strokeWidth={2.5} />
                                        {t('trade.success', 'Tamamlandı')}
                                    </>
                                ) : (
                                    t('trade.submitOrder', 'Emri gönder')
                                )}
                            </button>
                        </form>

                        {lastTrade && (
                            <div
                                style={{
                                    marginTop: 14,
                                    padding: 12,
                                    borderRadius: 12,
                                    background: 'rgba(57, 255, 20, 0.1)',
                                    border: '1px solid rgba(57, 255, 20, 0.35)',
                                    fontSize: '0.8125rem',
                                }}
                            >
                                <div>
                                    {lastTrade.symbol} · {lastTrade.quantity} {t('trade.lastTradeUnits', 'birim işlem gerçekleşti.')}
                                </div>
                                <div className="trade-mono" style={{ marginTop: 6 }}>
                                    {t('trade.unitPrice', 'Birim')}: {formatTryDisplay(lastTrade.tryPrice)} · {t('trade.totalLabel', 'Toplam')}:{' '}
                                    {formatTryDisplay(lastTrade.totalTry)}
                                </div>
                                <div className="trade-mono" style={{ marginTop: 4 }}>
                                    {t('trade.balanceAfterExec', 'İşlem sonrası bakiye')}: {formatTryDisplay(lastTrade.balanceAfter)}
                                </div>
                            </div>
                        )}
                    </div>

                    <div className="trade-card-premium trade-history-col">
                        <h2 className="trade-history-head">{t('trade.history', 'İşlem geçmişi')}</h2>
                        {historyError && (
                            <div style={{ color: tokens.error, marginBottom: 8, fontSize: '0.8125rem' }}>{historyError}</div>
                        )}
                        {historyLoading && <p className="trade-hint">{t('common.loading', 'Yükleniyor...')}</p>}
                        {history && history.content.length === 0 && !historyLoading && (
                            <p className="trade-hint">{t('trade.historyEmpty', 'Henüz işlem yok.')}</p>
                        )}
                        {history && history.content.length > 0 && (
                            <>
                                <div className="trade-history-scroll">
                                    <table className="trade-hist-table">
                                        <thead>
                                            <tr>
                                                <th>{t('news.date', 'Tarih')}</th>
                                                <th>{t('trade.symbol', 'Sembol')}</th>
                                                <th>{t('trade.sideCol', 'Yön')}</th>
                                                <th className="trade-th-right">{t('trade.quantity', 'Miktar')}</th>
                                                <th className="trade-th-right">{t('trade.totalTry', 'Toplam')}</th>
                                                <th className="trade-th-right">{t('trade.balanceAfter', 'Bakiye (sonra)')}</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            {history.content.map((row) => {
                                                const { sideLabel, detail } = getTradeSideAndDetail(row.tradeType, row.symbol, row.assetType);
                                                const tryUnit = row.quantity && row.quantity !== 0 ? row.totalTry / row.quantity : 0;
                                                return (
                                                    <tr key={row.tradeId}>
                                                        <td>{new Date(row.tradedAt).toLocaleString(locale)}</td>
                                                        <td className="trade-mono">{row.symbol}</td>
                                                        <td>
                                                            <span className={`trade-pill ${row.tradeType === 'BUY' ? 'trade-pill--buy' : 'trade-pill--sell'}`}>
                                                                {sideLabel}
                                                            </span>
                                                            <span className="trade-tech-sub">{detail}</span>
                                                        </td>
                                                        <td className="trade-mono" style={{ textAlign: 'right' }}>
                                                            {row.quantity.toLocaleString('tr-TR', { maximumFractionDigits: 6 })}
                                                        </td>
                                                        <td style={{ textAlign: 'right' }}>
                                                            <div className="trade-mono">{formatTryDisplay(row.totalTry)}</div>
                                                            <div className="trade-tech-sub">{formatTryDisplay(tryUnit)} / {t('trade.perUnit', 'birim')}</div>
                                                        </td>
                                                        <td className="trade-mono" style={{ textAlign: 'right' }}>
                                                            {formatTryDisplay(row.balanceAfter)}
                                                        </td>
                                                    </tr>
                                                );
                                            })}
                                        </tbody>
                                    </table>
                                </div>
                                {histTotalPages > 1 && (
                                    <div className="trade-hist-pagination">
                                        <button
                                            type="button"
                                            className="trade-page-btn"
                                            disabled={history.number <= 0}
                                            onClick={() => setHistoryPage((p) => Math.max(0, p - 1))}
                                        >
                                            {t('trade.prevPage', 'Önceki')}
                                        </button>
                                        {histPageItems.map((item, idx) =>
                                            item === 'gap' ? (
                                                <span key={`g-${idx}`} className="trade-hint">
                                                    …
                                                </span>
                                            ) : (
                                                <button
                                                    key={item}
                                                    type="button"
                                                    className={`trade-page-btn ${item === history.number ? 'trade-page-btn--active' : ''}`}
                                                    onClick={() => setHistoryPage(item)}
                                                >
                                                    {item + 1}
                                                </button>
                                            ),
                                        )}
                                        <button
                                            type="button"
                                            className="trade-page-btn"
                                            disabled={history.number >= histTotalPages - 1}
                                            onClick={() => setHistoryPage((p) => p + 1)}
                                        >
                                            {t('trade.nextPage', 'Sonraki')}
                                        </button>
                                    </div>
                                )}
                            </>
                        )}
                    </div>
                </div>
            </div>
        </div>
    );
}
