import {
    useCallback,
    useEffect,
    useLayoutEffect,
    useMemo,
    useRef,
    useState,
    type CSSProperties,
    type MouseEvent,
    type ReactElement,
} from 'react';
import { createPortal } from 'react-dom';
import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import type { AxiosResponse } from 'axios';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { financeClient, marketClient } from '../api/client';
import {
    getBistBatchHistory,
    getBistCandles,
    getBistLatest,
    bistLatestPrice,
    bistPickClose,
} from '../services/bistEquityApi';
import type { LatestPriceRow, MarketDashboard } from '../components/market/marketTypes';
import { approxPctByCalendarSpan, approxPctByDays, approxPctBySpan } from '../components/market/heatmapApproxPct';
import {
    CHART_RANGE_SEQUENCE as TERMINAL_CHART_RANGE_SEQUENCE,
    type ChartRangeId,
    RANGE_TO_DAYS,
    snapMarketHistoryDays,
} from '../components/market/heatmapRange';
import { heatmapSectorLabel } from '../components/market/heatmapSectorLabels';
import { useTheme } from '../theme/ThemeContext';
import { useLanguage } from '../i18n/LanguageContext';
import { formatAssetLabel, getDynamicLogoUrl } from '../lib/assetBranding';
import { AssetLogo } from '../components/AssetLogo';
import { MarketFinvizTreemap, type TreemapTile } from '../components/market/MarketFinvizTreemap';
import { MarketTerminalChart } from '../components/market/MarketTerminalChart';
import { BondTerminalChart } from '../components/market/BondTerminalChart';
import { ViopTerminalChart } from '../components/market/ViopTerminalChart';
import { SpotTerminalChart } from '../components/market/SpotTerminalChart';
import { FxEffectiveRatesComparisonSection } from '../components/market/FxEffectiveRatesComparisonSection';
import { FxEffectiveRatesModal } from '../components/market/FxEffectiveRatesModal';
import { MarketCategoryScrollTabs } from '../components/market/MarketCategoryScrollTabs';
import { ChartIndicatorToggles } from '../components/market/ChartIndicatorToggles';
import { MarketCompareLwChart } from '../components/market/MarketCompareLwChart';
import { MarketMacroInfoCard } from '../components/market/MarketMacroInfoCard';
import { MarketPurchasingPowerSummaryLive } from '../components/market/MarketPurchasingPowerSummaryLive';
import { MarketPurchasingPowerCompareChart } from '../components/market/MarketPurchasingPowerCompareChart';
import { MarketPreciousMetalSummaryCard } from '../components/market/MarketPreciousMetalSummaryCard';
import { MarketPreciousMetalCompareChart } from '../components/market/MarketPreciousMetalCompareChart';
import { usePurchasingPowerData } from '../hooks/usePurchasingPowerData';
import { usePreciousMetalComparisonData } from '../hooks/usePreciousMetalComparisonData';
import {
    fetchMarketTerminalList,
    MARKET_LIST_PAGE_SIZE,
    type FundSubmarket,
} from '../services/marketTerminalListApi';
import {
    fetchTefasFundHistory,
    mapTerminalSortToTefas,
    tefasMonthsForChartRange,
} from '../services/tefasFundApi';
import {
    buildTefasTreemapTiles,
    tefasHeatmapSortForRange,
    tefasReturnPctForChartRange,
} from '../utils/tefasHeatmap';
import { enrichBistInstrumentHorizons } from '../utils/bistInstrumentHorizons';
import { terminalListItemToVm } from '../utils/marketTerminalListVm';
import { istanbulTodayYmd } from '../components/simulation/simDates';
import { clampPpAnchorYmd, firstYmdFromCandles, lastYmdFromCandles } from '../utils/marketPurchasingPower';
import { resolvePurchasingPowerUnitLabel } from '../utils/purchasingPowerUnitLabel';
import {
    ChartCandlestick,
    ChartLine,
    ChevronDown,
    GitCompare,
    Info,
    Maximize2,
    Bell,
    Star,
    TrendingUp,
    X,
} from 'lucide-react';
import { PriceAlertModal } from '../components/priceAlert/PriceAlertModal';
import { marketCategoryToPriceAlertAsset } from '../utils/priceAlertAsset';
import type { PriceAlertAssetType } from '../types/priceAlert';
import { extractMaturityDate, formatBondDisplayName, getRemainingDays } from '../utils/bondFormatter';
import { bondIsinDisplay, bondIsinLabel, bondListSubtitle, formatBondCouponRate } from '../utils/marketBondUi';
import { bondTypeFromInstrument } from '../components/viopBond/viopBondMarket';
import { bondTypeLabel } from '../components/viopBond/bondPositionLabels';
import { debtHasStructuredYieldData, pickStructuredYieldDecimal } from '../utils/bondYieldSemantics';
import { cryptoMeta, etfMeta, fxMeta, getBondMeta, instrumentMeta } from '../utils/instrumentMeta';
import { isViopWhitelisted, viopCategoryFor, type ViopCategory } from '../constants/ViopWhitelist';
import {
    defaultMetalSymbolForSubmarket,
    getPreciousMetalDisplayMeta,
    isPreciousMetalAllowlisted,
    isUsdPerOunceMetalSymbol,
    type MetalsSubmarket,
    preciousMetalSymbolsForSubmarket,
    symbolMatchesMetalsSubmarket,
    PRECIOUS_USD_OZ_DESCRIPTION,
} from '../constants/preciousMetalsUsd';
import './MarketTerminal.css';

type MarketInstrument = {
    symbol: string;
    /** Liste / karşılaştırma etiketi; API veya whitelist’ten (VİOP snapshot displayName vb.) */
    displayName?: string | null;
    /** Okunabilir ad (API `name` / `instrumentName` ile uyumlu fallback zinciri) */
    name?: string | null;
    instrumentName?: string | null;
    category: MarketCategory;
    price: number;
    /** ABD / BIST ayrımı (backend `marketRegion`) */
    marketRegion?: 'US' | 'TR' | string;
    exchange?: string;
    sector?: string;
    source?: string;
    delayMinutes?: number;
    marketCap?: number | null;
    /**
     * Tablonun "Toplam donem degisimi" ana metrigi. Sparkline penceresinden hesaplandigi
     * icin pratikte ~14 gunluk degisimi temsil eder; siralama/Hero panel/asagi yukari
     * piyasaler bu deger uzerinden caliṣir.
     */
    changePercent: number;
    /**
     * Heatmap (FINVIZ tarzi) tile'inin 1-gunluk yuzdesi. Sol tablo "1G" chip'i ile heatmap
     * paneli birebir ayni rakami gostersin diye ayri tutuyoruz. Live tick geldiginde live
     * tick'in changePercent'i (genelde 24h) ile override edilir; yoksa heatmap tile degerini
     * dondurur. Yoksa fallback olarak `changePercent` ile aynidir.
     */
    dailyChangePercent?: number;
    trend: 'UP' | 'DOWN';
    high?: number;
    low?: number;
    volume?: number | null;
    /** İlk bulunan: API `currency` | `priceCurrency` | `quoteCurrency` */
    currency?: string;
    /** BIST günlük satırı `dataQuality` (HISTORICAL / PARTIAL vb.) */
    dataQuality?: string;
    metrics?: {
        basis?: number;
        yield?: number;
    };
    /** Terminal listesi spark kapanışları (BIST horizon hesabı) */
    sparkline?: number[];
    /** TEFAS / BIST terminal listesi — hero / VM eşlemesi */
    pctDay?: number | null;
    pctWeek?: number | null;
    pctMonth?: number | null;
    pctYear?: number | null;
    fundSubmarket?: FundSubmarket;
    fundRiskLevel?: number;
    fundReturn3m?: number;
    fundReturn6m?: number;
    fundReturn3y?: number;
    fundReturn5y?: number;
    listSubtitle?: string;
};
type InstrumentType = 'STOCK' | 'BOND' | 'FUTURES';
type InstrumentVm = MarketInstrument & {
    type: InstrumentType;
    displayName: string;
    /** Kıymetli madenler tablosu: alt satır (örn. XAU/USD). */
    listSubtitle?: string;
    sparkline: number[];
    /** Piyasa listesi: günlük / haftalık / aylık / yıllık yaklaşık % (spark veya VIOP sözleşme alanları). */
    pctDay?: number | null;
    pctWeek?: number | null;
    pctMonth?: number | null;
    pctYear?: number | null;
    maturityDate?: string;
    daysToMaturity?: number;
    couponRate?: number;
    couponFrequencyLabel?: string;
    yieldToMaturity?: number;
    contractMonth?: string;
    expiryDate?: string;
    marginRequirement?: number;
    longShort?: 'LONG' | 'SHORT' | 'NÖTR';
    fundSubmarket?: FundSubmarket;
    fundRiskLevel?: number;
    fundReturn3m?: number;
    fundReturn6m?: number;
    fundReturn3y?: number;
    fundReturn5y?: number;
};

/** Piyasa seçici tablosu: Fiyat ve horizon % sütunları için sıralama anahtarı */
type PickerListSortKey = 'price' | 'pctDay' | 'pctWeek' | 'pctMonth' | 'pctYear' | 'fundReturn3m' | 'fundReturn6m';

type IndicatorPoint = { t: string; value: number };
type IndicatorsResponse = {
    close: IndicatorPoint[];
    ma: Record<string, IndicatorPoint[]>;
};
type CandlePoint = { t: string; o: number; h: number; l: number; c: number; v: number };
type MarketHistoryPoint = {
    buyPrice?: number;
    sellPrice?: number;
    timestamp?: string;
    asOf?: string;
};
type BatchHistoryResponse = { series: Record<string, CandlePoint[]> };
type ViopSnapshot = {
    contractCode: string;
    expiryDate?: string;
    contractMonth?: string;
    price: number;
    basis: number;
    /** Teorik spot; `basis` 0 gelince baz = fiyat − spot ile türetilir (backend ViopSnapshotResponse) */
    theoreticalSpot?: number | null;
    /** API bazen null/omit; 0 geldiğinde basis doluysa client tarafında fiyat oranı kullanılır */
    annualizedBasisPct?: number | null;
    marginRequirement?: number;
    longShortIndicator?: 'LONG' | 'SHORT' | 'NEUTRAL';
    openInterest?: number;
    asOf?: string;
    /** CSV import sonrası backend rollup (liste zaman aralığı); yoksa null */
    listPctChange1d?: number | null;
    listPctChange7d?: number | null;
    listPctChange30d?: number | null;
    listPctChange365d?: number | null;
    seqMovePct?: number | null;
    seqMoveTrend?: string | null;
    /** /viop/latest: son 14 takvim günü gün başına kapanışlara göre % */
    listPctChange14d?: number | null;
    /** /viop/latest: gün başına son pozitif kapanışlar (sparkline) */
    sparklineCloses?: number[] | null;
};
type DebtSnapshot = {
    isin: string;
    dirtyPrice: number;
    yieldPct: number;
    maturityDate?: string;
    daysToMaturity?: number;
    couponRate?: number;
    /** Yapılandırılmış yield — yalnızca API gönderirse doldurulur (vade–getiri eğrisi için). */
    yieldToMaturity?: number | null;
    simpleYield?: number | null;
    compoundYield?: number | null;
    asOf?: string;
    source?: string;
    quality?: 'EXACT' | 'FALLBACK' | 'STALE' | string;
    synthetic?: boolean;
    hasStructuredYieldData?: boolean;
    bondDataCategory?: string;
    dirtyPriceUnit?: string;
    yieldFieldRepresentsYtm?: boolean;
    couponFrequencyPerYear?: number | null;
    couponFrequencyLabel?: string | null;
    couponFrequencySource?: string | null;
};
type ViopContract = {
    contractCode: string;
    underlying: string;
    expiry: string;
    type: string;
    listPctChange1d?: number | null;
    listPctChange7d?: number | null;
    listPctChange30d?: number | null;
    listPctChange365d?: number | null;
    seqMovePct?: number | null;
    seqMoveTrend?: string | null;
};
/** `/api/market/viop/contracts/{code}/snapshot` — İş Yatırım snapshot (envelope dışı gövde) */
type ViopContractSnapshotApi = {
    contractCode: string;
    underlying: string;
    displayName: string;
    contractName: string;
    maturityMonth: number;
    maturityYear: number;
    assetClass?: string;
    segment?: string;
    sourceLabel: string;
    delayMinutes: number;
    updateDate: string;
    bid: number | null;
    ask: number | null;
    last: number | null;
    open: number | null;
    /** Backend `openPrice` (İş Yatırım `open` alanı) */
    openPrice?: number | null;
    high: number | null;
    low: number | null;
    dayClose: number | null;
    changeAmount: number | null;
    changePercent: number | null;
    quantity: number | null;
    volume: number | null;
    settlement: number | null;
    preSettlement: number | null;
    limitUp: number | null;
    limitDown: number | null;
    initialMargin: number | null;
    priceStep: number | null;
    weekLow: number | null;
    weekHigh: number | null;
    monthLow: number | null;
    monthHigh: number | null;
    /** Referans kapanışlar — liste Hafta/Ay/Yıl % için (İş Yatırım snapshot). */
    weekClose?: number | null;
    monthClose?: number | null;
    yearClose?: number | null;
    prevYearClose?: number | null;
    dataQuality: string | null;
};
type ViopHistoryPointApi = {
    contractCode: string;
    time: string;
    price: number;
    source: string;
    periodMinutes: number;
};
type ViopHistoryApi = {
    contractCode: string;
    chartType: string;
    dataQuality?: string;
    points: ViopHistoryPointApi[];
};
type DebtInstrument = {
    isin: string;
    name: string;
    issuer: string;
    maturityDate: string;
    couponFrequencyPerYear?: number | null;
    couponFrequencyLabel?: string | null;
    couponFrequencySource?: string | null;
};
type MarketCategory = 'EQUITY' | 'CRYPTO' | 'FX' | 'METALS' | 'FUNDS' | 'FUTURES' | 'BOND';
type CompareRow = { time: string; values: Record<string, number> };
type LiveTick = { category: MarketCategory; symbol: string; price: number; changePercent: number; volume: number };
type LivePayload = { ts: string; ticks: LiveTick[] };

/**
 * Chart penceresi -> backend gun parametresi.
 *
 * `1D` icin backend'den 3 gun veri istiyoruz: chart en az 2 nokta gerektirir ve `days=1`
 * cogu zaman tek nokta donduruyordu (hafta sonu / tatil / market kapaniss). Kullanici icin
 * "1D" anlam olarak "son gunluk degisim/yon" demek; arka tarafta 2-3 islem gunu cekersek
 * "onceki gune gore degisim" cizgisi her zaman gorunur olur ve "yeterli veri yok"
 * mesajiyla karsilasilmaz.
 */
function toFiniteNumber(v: unknown): number | null {
    if (v == null) return null;
    if (typeof v === 'number' && Number.isFinite(v)) return v;
    if (typeof v === 'string') {
        const n = parseFloat(v.replace(',', '.'));
        return Number.isFinite(n) ? n : null;
    }
    const n = Number(v);
    return Number.isFinite(n) ? n : null;
}

/** VIOP liste % / trend: DB’de son iki snapshot’a göre (takvim günü değil). */
function viopSeqChangeFromDb(
    snap: ViopSnapshot | undefined,
    contract: ViopContract | undefined
): { pct: number; trend: 'UP' | 'DOWN' } | null {
    const n = toFiniteNumber(snap?.seqMovePct ?? contract?.seqMovePct);
    if (n == null) return null;
    const tr = String(snap?.seqMoveTrend ?? contract?.seqMoveTrend ?? '')
        .trim()
        .toUpperCase();
    let trend: 'UP' | 'DOWN' = n >= 0 ? 'UP' : 'DOWN';
    if (tr === 'DOWN') trend = 'DOWN';
    else if (tr === 'UP') trend = 'UP';
    else if (tr === 'NEUTRAL') trend = 'UP';
    return { pct: n, trend };
}

/** Terminal satırı: önce son 14 gün (gün başına kapanış) %, yoksa rollup/seq/baz. */
function viopTerminalListChange(
    snap: ViopSnapshot | undefined,
    contract: ViopContract | undefined
): { pct: number; trend: 'UP' | 'DOWN' } | null {
    const p14 = toFiniteNumber(snap?.listPctChange14d);
    if (p14 != null) {
        return { pct: p14, trend: p14 >= 0 ? 'UP' : 'DOWN' };
    }
    for (const v of [
        snap?.listPctChange7d,
        snap?.listPctChange30d,
        contract?.listPctChange7d,
        contract?.listPctChange30d,
    ]) {
        const n = toFiniteNumber(v);
        if (n != null) return { pct: n, trend: n >= 0 ? 'UP' : 'DOWN' };
    }
    const seq = viopSeqChangeFromDb(snap, contract);
    if (seq != null) return seq;
    return null;
}
const CATEGORY_LABELS: { id: MarketCategory; label: string }[] = [
    { id: 'EQUITY', label: 'Hisse' },
    { id: 'CRYPTO', label: 'Kripto' },
    { id: 'FX', label: 'Döviz' },
    { id: 'METALS', label: 'Kıymetli madenler' },
    { id: 'FUNDS', label: 'Fonlar' },
    { id: 'FUTURES', label: 'VİOP' },
    { id: 'BOND', label: 'Tahvil' },
];

const STARRED_MAX_FALLBACK = 12;

function heatAssetClassForCategory(category: MarketCategory): string {
    if (category === 'EQUITY') return 'STOCK';
    if (category === 'CRYPTO') return 'CRYPTO';
    if (category === 'FX') return 'FX';
    if (category === 'METALS') return 'METAL';
    if (category === 'FUNDS') return 'FUND';
    return '';
}

function marketKindForCategory(category: MarketCategory): 'EQUITY' | 'CRYPTO' | 'FX' | 'METALS' | 'FUNDS' {
    if (category === 'EQUITY') return 'EQUITY';
    if (category === 'CRYPTO') return 'CRYPTO';
    if (category === 'FX') return 'FX';
    if (category === 'METALS') return 'METALS';
    if (category === 'FUNDS') return 'FUNDS';
    return 'FX';
}

type DisplayCurrency = 'USD' | 'TRY';

function normalizeApiCurrencyCode(raw: string | undefined): DisplayCurrency | null {
    if (!raw || typeof raw !== 'string') return null;
    const u = raw.trim().toUpperCase();
    if (u === 'USD' || u === 'USDT') return 'USD';
    if (u === 'TRY' || u === 'TL' || u === 'TRL') return 'TRY';
    return null;
}

function resolveDisplayCurrency(
    ins: Pick<MarketInstrument, 'category' | 'currency' | 'marketRegion' | 'exchange' | 'symbol'>
): DisplayCurrency {
    if (isUsdPerOunceMetalSymbol(ins.symbol)) return 'USD';
    const fromApi = normalizeApiCurrencyCode(ins.currency);
    if (fromApi) return fromApi;
    if (ins.marketRegion === 'TR' || String(ins.exchange ?? '').toUpperCase() === 'BIST') return 'TRY';
    if (ins.category === 'EQUITY' || ins.category === 'CRYPTO') return 'USD';
    /** Yabancı ETF / fon kotasyonları genelde USD; API `currency` göndermezse ₺ yanlış gösterilmesin. */
    if (ins.category === 'FUNDS') return 'USD';
    return 'TRY';
}

function currencyGlyph(c: DisplayCurrency): string {
    return c === 'USD' ? '$' : '₺';
}

function normalizeMetaKey(symbol: string | undefined): string {
    return String(symbol ?? '')
        .trim()
        .toUpperCase()
        .replace(/[^A-Z0-9]/g, '');
}

function bistSymbolBadgeStyle(symbol: string): { background: string; color: string } {
    let h = 0;
    const s = symbol.trim().toUpperCase();
    for (let i = 0; i < s.length; i += 1) h = (h * 31 + s.charCodeAt(i)) >>> 0;
    const hue = h % 360;
    return { background: `hsl(${hue} 46% 36%)`, color: 'hsl(210 20% 98%)' };
}

type UsdTryRatePayload = { rate: number; available: boolean };

function effectiveUsdTryRate(payload: UsdTryRatePayload | undefined): number | null {
    if (!payload?.available) return null;
    const n = Number(payload.rate);
    return Number.isFinite(n) && n > 0 ? n : null;
}

/** USD kotasyonlu satırlar: toggle açıkken backend USDTRY kuru ile TL; diğerleri aynı kalır. */
function terminalPriceDisplay(
    ins: Pick<MarketInstrument, 'category' | 'currency' | 'price' | 'marketRegion' | 'exchange' | 'symbol'>,
    opts: { showUsdInTry: boolean; usdTryRate: number | null }
): { title: string; glyph: string; amount: number; suffix?: string } {
    if (isUsdPerOunceMetalSymbol(ins.symbol)) {
        return { title: 'USD/ons', glyph: '$', amount: ins.price, suffix: ' / ons' };
    }
    const base = resolveDisplayCurrency(ins);
    if (opts.showUsdInTry && base === 'USD' && opts.usdTryRate != null) {
        return { title: 'TRY', glyph: '₺', amount: ins.price * opts.usdTryRate };
    }
    return { title: base, glyph: currencyGlyph(base), amount: ins.price };
}

function findHeatmapTile(dashboard: MarketDashboard, symbol: string, assetClass: string) {
    const key = normalizeSymbolKey(symbol);
    return dashboard.heatmapTiles.find((t) => normalizeSymbolKey(t.symbol) === key && t.assetClass === assetClass);
}

function sparklineClosesFor(dashboard: MarketDashboard | undefined, symbol: string, assetClass: string): number[] {
    if (!dashboard) return [];
    const key = normalizeSymbolKey(symbol);
    const exact = dashboard.sparklines.find((s) => normalizeSymbolKey(s.symbol) === key && s.assetClass === assetClass);
    if (exact?.closes?.length) return exact.closes;
    const loose = dashboard.sparklines.find((s) => normalizeSymbolKey(s.symbol) === key);
    return loose?.closes ?? [];
}

/** Kıymetli madenler: yanlış varlık sınıfından sparkline çekilmesini engeller (yalnızca eşleşen assetClass). */
function sparklineClosesForStrict(dashboard: MarketDashboard | undefined, symbol: string, assetClass: string): number[] {
    if (!dashboard) return [];
    const key = normalizeSymbolKey(symbol);
    const exact = dashboard.sparklines.find((s) => normalizeSymbolKey(s.symbol) === key && s.assetClass === assetClass);
    return exact?.closes?.filter((x) => Number.isFinite(x) && x > 0) ?? [];
}

function resolveListVolume(
    dashboard: MarketDashboard,
    symbol: string,
    assetClass: string,
    price: number,
    tile: ReturnType<typeof findHeatmapTile>
): number | null {
    const w = tile?.layoutWeight;
    if (w != null && Number.isFinite(Number(w)) && Number(w) > 0) {
        return Number(w);
    }
    const volRow = dashboard.volatility.find(
        (v) => normalizeSymbolKey(v.symbol) === normalizeSymbolKey(symbol) && v.assetClass === assetClass
    );
    const dv = volRow?.dailyVolatility;
    if (dv != null && Number.isFinite(Number(dv)) && Number(dv) > 0 && price > 0) {
        return Number(dv) * price * 100_000;
    }
    if (price > 0) {
        return Math.sqrt(Math.max(price, 1e-6));
    }
    return null;
}

function pickPrice(row: LatestPriceRow): number {
    if (!row || row.status === 'NO_DATA') return 0;
    return Number(row.buyPrice ?? row.buy ?? row.price ?? row.sellPrice ?? row.sell ?? 0);
}

function computeChangePct(closes: number[]): number {
    if (!Array.isArray(closes) || closes.length < 2) return 0;
    const first = Number(closes[0] ?? 0);
    const last = Number(closes[closes.length - 1] ?? 0);
    if (!first) return 0;
    return ((last - first) / first) * 100;
}

function resolveChangePercent(closes: number[], fallback?: number): number {
    const computed = computeChangePct(closes);
    if (Number.isFinite(computed) && Math.abs(computed) > 1e-6) {
        return computed;
    }
    for (const days of [14, 7, 1] as const) {
        const a = approxPctByDays(closes, days);
        if (a != null && Number.isFinite(a) && Math.abs(a) > 1e-6) {
            return a;
        }
    }
    if (fallback != null && Number.isFinite(fallback) && Math.abs(fallback) > 1e-6) {
        return fallback;
    }
    return 0;
}

function buildFallbackTrendSparkline(price: number, changePercent: number, points = 14): number[] {
    const safePrice = Number.isFinite(price) && price > 0 ? price : 1;
    const ratio = 1 + Number(changePercent ?? 0) / 100;
    const start = ratio > 0 ? safePrice / ratio : safePrice * 0.99;
    return Array.from({ length: points }, (_, i) => {
        const t = i / Math.max(points - 1, 1);
        return start + (safePrice - start) * t;
    });
}

function normalizeTrendSparkline(values: number[], price: number, changePercent: number, points = 14): number[] {
    const clean = (values ?? []).map((v) => Number(v)).filter((v) => Number.isFinite(v) && v > 0);
    if (clean.length >= 2) {
        /*
         * Gercek veri ne kadar varsa o kadar nokta dondur -- eskiden 14'e kadar `slice[0]` ile
         * padding yapiliyordu, bu da grafigin basini yapay bir yatay cizgi haline getiriyordu.
         * 7G/14G yuzde hesabini da (last - first) padded ilk degere baglayip "her donemde ayni
         * yuzde" bug'ina sebep oluyordu. Render tarafi (`row.sparkline.slice(-N)`) zaten yetersiz
         * veride dizinin tamamini cizdigi icin padding gereksiz.
         */
        return clean.slice(-points);
    }
    return buildFallbackTrendSparkline(price, changePercent, points);
}

function historyRowsToSyntheticCandles(rows: MarketHistoryPoint[]): CandlePoint[] {
    const sorted = [...(rows ?? [])]
        .filter((row) => row && (row.timestamp || row.asOf))
        .sort((a, b) => new Date(a.timestamp ?? a.asOf ?? 0).getTime() - new Date(b.timestamp ?? b.asOf ?? 0).getTime());
    if (!sorted.length) return [];
    return sorted.map((row, idx) => {
        const buy = Number(row.buyPrice ?? Number.NaN);
        const sell = Number(row.sellPrice ?? Number.NaN);
        const closeCandidates = [buy, sell].filter((x) => Number.isFinite(x) && x > 0);
        const close = closeCandidates.length
            ? closeCandidates.reduce((acc, x) => acc + x, 0) / closeCandidates.length
            : 0;
        const prevClose = idx > 0 ? Number(sorted[idx - 1].buyPrice ?? sorted[idx - 1].sellPrice ?? close) : close;
        const safePrev = Number.isFinite(prevClose) && prevClose > 0 ? prevClose : close;
        return {
            t: row.timestamp ?? row.asOf ?? new Date().toISOString(),
            o: safePrev,
            h: Math.max(safePrev, close),
            l: Math.min(safePrev, close),
            c: close,
            v: 0,
        };
    });
}

/** Grafik serisi gerçekten değişmediyse gereksiz parent re-render / fitContent tetiklenmesin. */
function chartPriceSeriesSignature(points: { time: string; price?: number; close?: number }[]): string {
    if (!points.length) return 'empty';
    const first = points[0]!;
    const last = points[points.length - 1]!;
    const lastVal = Number(last.price ?? last.close ?? 0);
    return `${points.length}|${first.time}|${last.time}|${lastVal.toFixed(6)}`;
}

function movingAverage(candles: { time: string; close: number }[], window: number): { time: string; value: number }[] {
    if (!candles.length || window <= 1) {
        return candles.map((c) => ({ time: c.time, value: c.close }));
    }
    const out: { time: string; value: number }[] = [];
    let sum = 0;
    for (let i = 0; i < candles.length; i += 1) {
        sum += candles[i].close;
        if (i >= window) {
            sum -= candles[i - window].close;
        }
        if (i >= window - 1) {
            out.push({
                time: candles[i].time,
                value: Number((sum / window).toFixed(6)),
            });
        }
    }
    return out;
}

function parseViopContractLabel(contractCode: string): string {
    const m = contractCode.match(/^([A-Z0-9_]+?)(\d{2})(\d{2})$/);
    if (!m) return contractCode;
    const [, rawUnderlying, mm, yy] = m;
    const monthMap: Record<string, string> = {
        '01': 'Oca',
        '02': 'Şub',
        '03': 'Mar',
        '04': 'Nis',
        '05': 'May',
        '06': 'Haz',
        '07': 'Tem',
        '08': 'Ağu',
        '09': 'Eyl',
        '10': 'Eki',
        '11': 'Kas',
        '12': 'Ara',
    };
    const underlyingMap: Record<string, string> = {
        XU030: 'BIST30',
        XLBNK: 'Banka',
        USDTRY: 'USD/TRY',
        EURTRY: 'EUR/TRY',
        XAUTRYM: 'Altın/TRY',
        XAUUSD: 'Altın/USD',
        ALTIN: 'Altın',
        GARAN: 'Garanti BBVA',
        THYAO: 'Türk Hava Yolları',
        ASELS: 'ASELSAN',
        AKBNK: 'Akbank',
        SISE: 'Şişecam',
        EREGL: 'Ereğli Demir Çelik',
    };
    const underlying = underlyingMap[rawUnderlying] ?? rawUnderlying;
    const month = monthMap[mm] ?? mm;
    return `${underlying} Vadeli (${month} 20${yy})`;
}

/** VİOP kategori chip'i için kısa etiket + (eski inline kullanımlar için) renk paleti. */
const VIOP_CATEGORY_CHIP: Record<ViopCategory, { label: string; bg: string; color: string }> = {
    FX: { label: 'FX', bg: 'rgba(56, 189, 248, 0.18)', color: '#7dd3fc' },
    INDEX: { label: 'IDX', bg: 'rgba(245, 158, 11, 0.18)', color: '#fbbf24' },
    COMMODITY: { label: 'GOLD', bg: 'rgba(234, 179, 8, 0.22)', color: '#facc15' },
    EQUITY: { label: 'EQ', bg: 'rgba(34, 197, 94, 0.18)', color: '#86efac' },
};

function viopCatBadgeClass(cat: ViopCategory): string {
    switch (cat) {
        case 'FX':
            return 'viop-cat-badge viop-cat-badge--fx';
        case 'INDEX':
            return 'viop-cat-badge viop-cat-badge--index';
        case 'COMMODITY':
            return 'viop-cat-badge viop-cat-badge--gold';
        case 'EQUITY':
            return 'viop-cat-badge viop-cat-badge--eq';
        default:
            return 'viop-cat-badge';
    }
}

function viopAssetClassTitle(
    cat: ViopCategory | null,
    tr: (key: string, fallback?: string) => string,
): string {
    switch (cat) {
        case 'INDEX':
            return tr('market.viop.cat.index', 'Endeks Vadeli');
        case 'FX':
            return tr('market.viop.cat.fx', 'Döviz Vadeli');
        case 'COMMODITY':
            return tr('market.viop.cat.commodity', 'Altın Vadeli');
        case 'EQUITY':
            return tr('market.viop.cat.equity', 'Pay Vadeli');
        default:
            return tr('market.viop.cat.default', 'VİOP');
    }
}

function viopPriceDecimals(symbol: string): number {
    const c = viopCategoryFor(symbol);
    if (c === 'FX') return 3;
    if (c === 'INDEX') return 2;
    if (c === 'COMMODITY') return 2;
    if (c === 'EQUITY') return 2;
    return 2;
}

function fmtViopMktSide(
    n: number | null | undefined,
    maximumFractionDigits: number,
    locale: string,
): string | null {
    if (n == null || !Number.isFinite(Number(n))) return null;
    const v = Number(n);
    if (v <= 0) return null;
    return v.toLocaleString(locale, { maximumFractionDigits, minimumFractionDigits: 0 });
}

function fmtViopBidAskLine(
    bid: number | null | undefined,
    ask: number | null | undefined,
    maximumFractionDigits: number,
    locale: string,
): string {
    const b = fmtViopMktSide(bid, maximumFractionDigits, locale);
    const a = fmtViopMktSide(ask, maximumFractionDigits, locale);
    if (b && a) return `${b} / ${a}`;
    if (b) return `${b} / —`;
    if (a) return `— / ${a}`;
    return '—';
}

function viopDataQualityHint(
    s: ViopContractSnapshotApi | undefined,
    tr: (key: string, fallback?: string) => string,
): string | null {
    if (!s?.dataQuality) return null;
    const q = String(s.dataQuality).toUpperCase();
    if (q.includes('STALE')) return 'STALE';
    if (q.includes('DELAY')) return tr('market.dataQuality.delay', 'GECİKME');
    if (q.includes('FALLBACK') || q.includes('LOW')) return tr('market.dataQuality.estimated', 'TAHMİNİ');
    return null;
}

function buildRsi(points: { time: string; close: number }[], period = 14): { time: string; value: number }[] {
    if (points.length <= period) return [];
    const out: { time: string; value: number }[] = [];
    let gains = 0;
    let losses = 0;

    for (let i = 1; i <= period; i += 1) {
        const diff = points[i].close - points[i - 1].close;
        if (diff >= 0) gains += diff;
        else losses -= diff;
    }
    let avgGain = gains / period;
    let avgLoss = losses / period;
    const firstRs = avgLoss === 0 ? 100 : 100 - 100 / (1 + avgGain / avgLoss);
    out.push({ time: points[period].time, value: Number(firstRs.toFixed(2)) });

    for (let i = period + 1; i < points.length; i += 1) {
        const diff = points[i].close - points[i - 1].close;
        const gain = diff > 0 ? diff : 0;
        const loss = diff < 0 ? -diff : 0;
        avgGain = (avgGain * (period - 1) + gain) / period;
        avgLoss = (avgLoss * (period - 1) + loss) / period;
        const rs = avgLoss === 0 ? 100 : 100 - 100 / (1 + avgGain / avgLoss);
        out.push({ time: points[i].time, value: Number(rs.toFixed(2)) });
    }
    return out;
}

function normalizeSymbolKey(symbol: string): string {
    return String(symbol ?? '')
        .trim()
        .replace(/\s+/g, '')
        .toUpperCase();
}

/** VİOP liste (`AKBNK0726`) ile kontrat kodu (`F_AKBNK0726`) eşlemesi. */
function viopSymbolKeysEquivalent(a: string, b: string): boolean {
    const ka = normalizeSymbolKey(a);
    const kb = normalizeSymbolKey(b);
    if (!ka || !kb) return false;
    if (ka === kb) return true;
    const strip = (s: string) => (s.startsWith('F_') ? s.slice(2) : s);
    return strip(ka) === strip(kb);
}

function resolveViopContractSymbol(
    symbol: string,
    contractBySymbol: Record<string, ViopContract>,
): string {
    const k = normalizeSymbolKey(symbol);
    if (!k) return '';
    const hit = contractBySymbol[k];
    return hit ? normalizeSymbolKey(hit.contractCode) : k;
}

/** VIOP geçmiş/snapshot için geçerli kontrat kodu; bilinmeyen liste satırlarında null. */
function resolveViopHistoryContract(
    symbol: string,
    contractBySymbol: Record<string, ViopContract>,
    snapshotBySymbol: Record<string, ViopContractSnapshotApi>,
): string | null {
    const k = normalizeSymbolKey(symbol);
    if (!k) return null;
    const resolved = normalizeSymbolKey(resolveViopContractSymbol(symbol, contractBySymbol));
    if (resolved && contractBySymbol[resolved]) {
        return resolved;
    }
    if (snapshotBySymbol[k]) {
        return k;
    }
    return null;
}

function findInstrumentVmBySymbol(
    vms: { symbol: string; category: MarketCategory }[],
    symbol: string,
    category: MarketCategory,
): (typeof vms)[number] | undefined {
    if (!symbol) return undefined;
    return vms.find((x) => {
        if (x.category !== category) return false;
        if (category === 'FUTURES') return viopSymbolKeysEquivalent(x.symbol, symbol);
        return normalizeSymbolKey(x.symbol) === normalizeSymbolKey(symbol);
    });
}

function formatEuropeIstanbulDateOnly(date: Date): string {
    return date.toLocaleDateString('en-CA', { timeZone: 'Europe/Istanbul' });
}

/** İş Yatırım USD/ons serisi için `from`/`to` (yyyy-MM-dd); gram altın için `days`. */
function metalsHistoryQueryParams(rawSymbol: string, days: number): Record<string, string | number> {
    const sym = normalizeSymbolKey(rawSymbol);
    const apiSym = sym === 'ALTIN_TRY' ? 'XAU_TRY' : sym;
    if (apiSym === 'XAU_TRY') {
        return { symbol: apiSym, days: snapMarketHistoryDays(days) };
    }
    if (isUsdPerOunceMetalSymbol(apiSym)) {
        const now = new Date();
        const to = formatEuropeIstanbulDateOnly(now);
        const fromMs = now.getTime() - Math.max(1, days) * 86_400_000;
        const from = formatEuropeIstanbulDateOnly(new Date(fromMs));
        return { symbol: apiSym, from, to };
    }
    return { symbol: apiSym, days };
}

/** `LocalDateTime` query paramları için İstanbul duvar saati (backend `Europe/Istanbul`). */
function formatEuropeIstanbulLocalIso(date: Date): string {
    const parts = new Intl.DateTimeFormat('en-CA', {
        timeZone: 'Europe/Istanbul',
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        hourCycle: 'h23',
    }).formatToParts(date);
    const g = (t: Intl.DateTimeFormatPartTypes) => parts.find((p) => p.type === t)?.value ?? '00';
    return `${g('year')}-${g('month')}-${g('day')}T${g('hour')}:${g('minute')}:${g('second')}`;
}

function snapshotToLegacyViopSnapshot(s: ViopContractSnapshotApi): ViopSnapshot {
    const last = Number(s.last ?? 0);
    const settle = Number(s.settlement ?? s.preSettlement ?? last);
    const basis = last - (Number.isFinite(settle) ? settle : last);
    return {
        contractCode: s.contractCode,
        price: last,
        basis,
        theoreticalSpot: Number.isFinite(settle) ? settle : last,
        asOf: s.updateDate,
        openInterest: s.quantity != null ? Number(s.quantity) : 0,
        expiryDate: `${s.maturityYear}-${String(s.maturityMonth).padStart(2, '0')}-28`,
        contractMonth: s.displayName,
        annualizedBasisPct: null,
        marginRequirement: s.initialMargin != null ? Number(s.initialMargin) : undefined,
        longShortIndicator: basis >= 0 ? 'LONG' : 'SHORT',
        listPctChange14d: s.changePercent != null ? Number(s.changePercent) : null,
        sparklineCloses: null,
    };
}

/** Raporlanan baz veya (fiyat − teorik spot); API sadece spot doldurup bazı 0 bırakabiliyor */
function impliedViopBasis(v: Pick<ViopSnapshot, 'basis' | 'theoreticalSpot'>, price: number): number {
    const reported = Number(v?.basis ?? 0);
    if (Number.isFinite(reported) && Math.abs(reported) > 1e-9) {
        return reported;
    }
    const p = Number(price);
    const spotRaw = v?.theoreticalSpot;
    const spot = spotRaw === null || spotRaw === undefined ? Number.NaN : Number(spotRaw);
    if (p > 0 && Number.isFinite(spot) && Math.abs(spot) > 1e-9) {
        return p - spot;
    }
    return Number.isFinite(reported) ? reported : 0;
}

/**
 * Taşıma %: API’deki annualized anlamlıysa onu kullan; değilse (örtük baz / fiyat) * 100.
 */
function effectiveViopCarryPercent(v: ViopSnapshot, price: number): number {
    const p = Number(price);
    if (!(p > 0)) return Number.NaN;
    const impliedBasis = impliedViopBasis(v, p);
    const raw = v?.annualizedBasisPct;
    const ann = raw === null || raw === undefined ? Number.NaN : Number(raw);
    if (Number.isFinite(ann) && Math.abs(ann) > 1e-9) {
        return ann;
    }
    if (Number.isFinite(impliedBasis) && Math.abs(impliedBasis) > 1e-9) {
        return (impliedBasis / p) * 100;
    }
    if (Number.isFinite(ann)) return ann;
    return Number.NaN;
}

/**
 * /viop/latest gerçekten boş döndüğünde fallback için kontrat başına history isteği atıyoruz.
 * 96 çok agresifti (200+ kontratın yarısı 6 paralel kanaldan saniyelerce frontend'i bloke ediyordu);
 * orta seviye olarak küçük tutuyoruz — gerçek mod sadece boş latest senaryosunda devrede.
 */
type EquitySubmarket = 'US' | 'BIST';

function rowIsTefasFundVm(row: Pick<InstrumentVm, 'category' | 'exchange' | 'fundSubmarket'>): boolean {
    return (
        row.category === 'FUNDS' &&
        (row.fundSubmarket === 'TR' || String(row.exchange ?? '').toUpperCase() === 'TEFAS')
    );
}

function tefasRiskLevelDisplay(risk: number | undefined | null): string {
    if (risk == null || !Number.isFinite(risk)) return '—';
    const n = Math.min(7, Math.max(1, Math.round(risk)));
    return `${n}/7`;
}

/** Grafik aralığı: dahili `1D` vb. — Türkçe kısa etiket (UI). */
/** TEFAS pay değeri grafiği: seçilen aralığa göre son N günlük günlük mumlar (1G ≈ 2 iş günü). */
function sliceCandlesToChartRange<T extends { time: string }>(candles: T[], range: ChartRangeId): T[] {
    if (candles.length < 2) return candles;
    const sorted = [...candles].sort((a, b) => String(a.time).localeCompare(String(b.time)));
    if (range === '1D') {
        return sorted.slice(-2);
    }
    const calDays = RANGE_TO_DAYS[range] ?? 30;
    const cutoff = new Date();
    cutoff.setDate(cutoff.getDate() - calDays);
    const ymd = cutoff.toISOString().slice(0, 10);
    const filtered = sorted.filter((c) => String(c.time).slice(0, 10) >= ymd);
    return filtered.length >= 2 ? filtered : sorted.slice(-Math.min(calDays + 2, sorted.length));
}

function chartRangeUiShortLabel(r: ChartRangeId): string {
    const m: Record<ChartRangeId, string> = {
        '1D': '1G',
        '1W': '1H',
        '1M': '1A',
        '3M': '3A',
        '6M': '6A',
        '1Y': '1Y',
        '2Y': '2Y',
    };
    return m[r];
}

function istCalendarYmd(d: Date): string {
    const parts = new Intl.DateTimeFormat('en-CA', {
        timeZone: 'Europe/Istanbul',
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
    }).formatToParts(d);
    const g = (t: Intl.DateTimeFormatPartTypes) => parts.find((p) => p.type === t)?.value ?? '00';
    return `${g('year')}-${g('month')}-${g('day')}`;
}

function bistCalendarRange(daysBack: number): { from: string; to: string } {
    const to = new Date();
    const from = new Date(to.getTime() - Math.max(1, daysBack) * 86_400_000);
    return { from: istCalendarYmd(from), to: istCalendarYmd(to) };
}

function getInstrumentDisplayName(row: {
    symbol: string;
    displayName?: string | null;
    name?: string | null;
    instrumentName?: string | null;
}): string {
    for (const v of [row.displayName, row.name, row.instrumentName]) {
        if (typeof v === 'string' && v.trim().length > 0) return v.trim();
    }
    return row.symbol;
}

function canShowComparisonChart(
    activeCategory: MarketCategory,
    marketType: string | null,
    equitySubmarket: EquitySubmarket
): boolean {
    if (activeCategory === 'EQUITY' && equitySubmarket === 'BIST') return true;
    return Boolean(marketType) || activeCategory === 'FUTURES';
}

function rowIsBistEquityVm(row: Pick<InstrumentVm, 'category' | 'marketRegion' | 'exchange'>): boolean {
    return (
        row.category === 'EQUITY' &&
        row.marketRegion === 'TR' &&
        String(row.exchange ?? '').toUpperCase() === 'BIST'
    );
}

/** Piyasa listesinden grafik karşılaştırması: aynı ana kategori / alt pazar (BIST) / VİOP türü */
function canPickerCompareRow(
    row: InstrumentVm,
    selected: InstrumentVm | null,
    activeCategory: MarketCategory,
    equitySubmarket: EquitySubmarket,
    metalsSubmarket: MetalsSubmarket,
): boolean {
    if (!selected?.symbol || row.symbol === selected.symbol) return false;
    if (row.category !== activeCategory || selected.category !== activeCategory) return false;
    if (activeCategory === 'EQUITY') {
        if (rowIsBistEquityVm(row) !== (equitySubmarket === 'BIST')) return false;
    }
    if (activeCategory === 'METALS') {
        if (
            !symbolMatchesMetalsSubmarket(row.symbol, metalsSubmarket) ||
            !symbolMatchesMetalsSubmarket(selected.symbol, metalsSubmarket)
        ) {
            return false;
        }
    }
    if (activeCategory === 'FUTURES') {
        const cr = viopCategoryFor(row.symbol);
        const cs = viopCategoryFor(selected.symbol);
        if (!cr || !cs || cr !== cs) return false;
    }
    return true;
}

/**
 * Sol listedeki sparkline trendinin kac gunluk pencereden hesaplanacagini belirler.
 * BOND (tahvil) icin 7 gun: kullanici tahvili kisa-vadeli yon icin izliyor ve degisimler
 * yavas oldugundan 14 gun cok pasif kaliyordu. Diger kategoriler 14 gun (default).
 */
const SPARK_DAYS_BY_CATEGORY: Partial<Record<MarketCategory, number>> = {
    BOND: 7,
};
const DEFAULT_SPARK_DAYS = 14;
function sparkDaysFor(category: MarketCategory): number {
    return SPARK_DAYS_BY_CATEGORY[category] ?? DEFAULT_SPARK_DAYS;
}

const TREND_SELECTABLE_CATEGORIES: ReadonlySet<MarketCategory> = new Set<MarketCategory>([
    'EQUITY',
    'CRYPTO',
    'FX',
    'METALS',
    'FUNDS',
]);

/**
 * Mini spark çizgisi için slice uzunluğu (ek API yok).
 * Spot kategoriler: üstteki grafik aralığı ile aynı `RANGE_TO_DAYS` penceresi.
 * Tahvil / VİOP: uzun aralıkta SVG okunabilirliği için kademeli kısa pencere.
 */
function sparkSliceDaysForInstrumentRow(category: MarketCategory, chartRange: ChartRangeId): number {
    if (TREND_SELECTABLE_CATEGORIES.has(category)) {
        return Math.max(2, RANGE_TO_DAYS[chartRange]);
    }
    switch (chartRange) {
        case '1D':
            return 2;
        case '1W':
            return 7;
        case '1M':
            return 14;
        case '3M':
            return 45;
        case '6M':
            return 90;
        case '1Y':
            return 60;
        case '2Y':
            return 120;
        default:
            return sparkDaysFor(category);
    }
}

/** Terminal grafiğiyle aynı seri: sıralı mumların ilk→son kapanış % (1A/6A/2Y seçimi). */
function pctChangeFromTerminalCandles(candleList: { time: string; close: number }[]): number | null {
    if (!candleList || candleList.length < 2) return null;
    const sorted = [...candleList].sort((a, b) => new Date(a.time).getTime() - new Date(b.time).getTime());
    const first = Number(sorted[0]?.close);
    const last = Number(sorted[sorted.length - 1]?.close);
    if (!(Number.isFinite(first) && first > 0 && Number.isFinite(last))) return null;
    return ((last - first) / first) * 100;
}

/** Açılır piyasa listesi: Gün / Hafta / Ay / Yıl yüzde hücreleri (spark veya backend sayıları). */
function finiteHorizonPct(v: unknown): number | null {
    if (v == null) return null;
    const n = typeof v === 'number' ? v : Number(v);
    if (!Number.isFinite(n)) return null;
    return n;
}

/** VIOP: son fiyatın haftalık/aylık/yıllık referans kapanışa göre %’si (snapshot alanları). */
function viopPctVersusReference(last: number, referenceClose: unknown): number | null {
    const ref = referenceClose == null ? Number.NaN : Number(referenceClose);
    if (!Number.isFinite(last) || last <= 0) return null;
    if (!Number.isFinite(ref) || ref <= 0) return null;
    return ((last - ref) / ref) * 100;
}

type InstrumentHorizonPcts = {
    pctDay: number | null;
    pctWeek: number | null;
    pctMonth: number | null;
    pctYear: number | null;
};

/**
 * Trend grafiği / `effectiveChangePercent` ile aynı: downsample spark’ta takvim günü penceresini
 * dizi indeksine `approxPctByCalendarSpan` ile çevirir. (Eski `approxPctBySpan(..., 30|252)` son N
 * örnek alırdı; 1A/1Y ile grafik chip’i çelişirdi.)
 */
function spotSparkHorizonPct(
    spark: readonly number[],
    category: MarketCategory,
    chartRange: '1W' | '1M' | '1Y',
): number | null {
    if (!TREND_SELECTABLE_CATEGORIES.has(category)) return null;
    const calDays = RANGE_TO_DAYS[chartRange];
    const assumedCalendarSpan =
        category === 'EQUITY' || category === 'METALS' ? RANGE_TO_DAYS['2Y'] : 90;
    const mapped = approxPctByCalendarSpan(spark, calDays, assumedCalendarSpan);
    if (mapped != null && Math.abs(mapped) > 1e-6) return mapped;
    const win = Math.min(calDays, Math.max(2, spark.length));
    const slice = spark.slice(-win);
    if (slice.length >= 2) {
        const first = Number(slice[0]);
        const last = Number(slice[slice.length - 1]);
        if (Number.isFinite(first) && first > 0 && Number.isFinite(last)) {
            return ((last - first) / first) * 100;
        }
    }
    return approxPctBySpan(spark, calDays);
}

function horizonPctsFromSparkCloses(
    closes: readonly number[],
    dayOverride: number | null,
    category?: MarketCategory,
): InstrumentHorizonPcts {
    const spark = closes.map((x) => Number(x)).filter((x) => Number.isFinite(x) && x > 0);
    if (spark.length < 2) {
        return { pctDay: dayOverride, pctWeek: null, pctMonth: null, pctYear: null };
    }
    if (category != null && TREND_SELECTABLE_CATEGORIES.has(category)) {
        return {
            pctDay: dayOverride ?? approxPctBySpan(spark, 1),
            pctWeek: spotSparkHorizonPct(spark, category, '1W'),
            pctMonth: spotSparkHorizonPct(spark, category, '1M'),
            pctYear: spotSparkHorizonPct(spark, category, '1Y'),
        };
    }
    return {
        pctDay: dayOverride ?? approxPctBySpan(spark, 1),
        pctWeek: approxPctBySpan(spark, 7),
        pctMonth: approxPctBySpan(spark, 30),
        pctYear: approxPctBySpan(spark, 252),
    };
}

function formatHorizonPct(p: number | null | undefined): string {
    if (p == null || !Number.isFinite(p)) return '—';
    return `${p >= 0 ? '+' : ''}${p.toFixed(2)}%`;
}

function horizonPctClassName(p: number | null | undefined): string {
    if (p == null || !Number.isFinite(p)) return 'terminal-horizon-pct';
    return `terminal-horizon-pct ${p >= 0 ? 'terminal-pct-pos' : 'terminal-pct-neg'}`;
}

/**
 * "1G" sütunu / `effectiveChangePercent(..., '1D')` için günlük % kaynağı.
 * Hisse: backend heatmap tile gerçek 1 günlük (FINHUB) — tile öncelikli.
 * Fon / döviz / kripto / altın: tile.changePercent uzun pencere (dashboard ~14–30g);
 * kısa hareket için sparkline son iki nokta (`approxPctByDays(..., 1)`) öncelikli.
 */
function dailyChangePercentForListedAsset(
    category: MarketCategory,
    tileChange: number | null | undefined,
    spark: number[],
    resolvedChangePercent: number,
): number {
    const spark1 = approxPctByDays(spark, 1);
    if (category === 'EQUITY') {
        const t = tileChange != null ? Number(tileChange) : Number.NaN;
        if (Number.isFinite(t) && Math.abs(t) > 1e-6) return t;
        if (spark1 != null && Math.abs(spark1) > 1e-6) return spark1;
        return resolvedChangePercent;
    }
    if (category === 'METALS') {
        if (spark1 != null && Math.abs(spark1) > 1e-6) return spark1;
        const t = tileChange != null ? Number(tileChange) : Number.NaN;
        if (Number.isFinite(t) && Math.abs(t) > 1e-6) return t;
        return resolvedChangePercent;
    }
    if (spark1 != null && Math.abs(spark1) > 1e-6) return spark1;
    const t = tileChange != null ? Number(tileChange) : Number.NaN;
    if (Number.isFinite(t) && Math.abs(t) > 1e-6) return t;
    return resolvedChangePercent;
}

/**
 * % kolonunda gosterilecek "donem degisim" yuzdesini hesaplar.
 *
 * Kurallar:
 *  - BOND / FUTURES: row.changePercent — bu kategorilerde grafik aralığı ayrı mantıkta.
 *  - Spot (TREND_SELECTABLE): üstteki grafik zaman aralığı `chartRange` ile aynı pencere
 *    (`RANGE_TO_DAYS`): 1G günlük % önceliği; diğer aralıklarda downsample spark için
 *    `approxPctByCalendarSpan` (takvim günü oranı), sonra kısa seri yedekleri.
 */
function effectiveChangePercent(
    row: {
        sparkline: number[];
        changePercent: number;
        dailyChangePercent?: number;
        category: MarketCategory;
        fundSubmarket?: FundSubmarket;
        exchange?: string;
        pctDay?: number | null;
        pctWeek?: number | null;
        pctMonth?: number | null;
        pctYear?: number | null;
        fundReturn3m?: number;
        fundReturn6m?: number;
    },
    chartRange: ChartRangeId,
): number {
    if (row.category === 'FUNDS' && rowIsTefasFundVm(row)) {
        return tefasReturnPctForChartRange(row, chartRange);
    }
    if (!TREND_SELECTABLE_CATEGORIES.has(row.category)) {
        return row.changePercent;
    }
    if (chartRange === '1D') {
        const daily = row.dailyChangePercent;
        if (daily != null && Number.isFinite(daily) && Math.abs(daily) > 1e-6) {
            return daily;
        }
        const a1 = approxPctByDays(row.sparkline, 1);
        if (a1 != null && Math.abs(a1) > 1e-6) return a1;
        return row.changePercent;
    }
    const calDays = RANGE_TO_DAYS[chartRange];
    const assumedCalendarSpan =
        row.category === 'EQUITY' ? RANGE_TO_DAYS['2Y'] : 90;
    const mapped = approxPctByCalendarSpan(row.sparkline, calDays, assumedCalendarSpan);
    if (mapped != null && Math.abs(mapped) > 1e-6) {
        return mapped;
    }
    const win = Math.min(calDays, Math.max(2, row.sparkline.length));
    const slice = row.sparkline.slice(-win);
    if (slice.length >= 2) {
        const first = Number(slice[0]);
        const last = Number(slice[slice.length - 1]);
        if (Number.isFinite(first) && first > 0 && Number.isFinite(last)) {
            return ((last - first) / first) * 100;
        }
    }
    const approx = approxPctBySpan(row.sparkline, calDays);
    if (approx != null && Math.abs(approx) > 1e-6) {
        return approx;
    }
    return row.changePercent;
}

/** Treemap `assetClass` → sol tablo / trend chip ile aynı `MarketCategory`. */
function heatmapAssetClassToCategory(assetClass: string): MarketCategory {
    switch (assetClass) {
        case 'STOCK':
        case 'BIST':
            return 'EQUITY';
        case 'CRYPTO':
            return 'CRYPTO';
        case 'FX':
            return 'FX';
        case 'METAL':
            return 'METALS';
        case 'FUND':
            return 'FUNDS';
        default:
            return 'EQUITY';
    }
}

/**
 * Sağ panel ısı haritası: üstteki grafik zaman aralığı ile aynı % mantığı
 * (`effectiveChangePercent` + dashboard sparkline).
 * @param sparkOverride BIST gibi dashboard spark’ı olmayan satırlar için harici kapanış dizisi.
 */
function enrichTreemapTileForChartRange(
    tile: TreemapTile,
    dashboard: MarketDashboard | undefined,
    chartRange: ChartRangeId,
    sparkOverride?: number[],
): TreemapTile {
    const closes =
        sparkOverride != null && sparkOverride.length > 0
            ? sparkOverride
            : dashboard != null
              ? sparklineClosesFor(dashboard, tile.symbol, tile.assetClass)
              : [];
    if ((sparkOverride == null || sparkOverride.length === 0) && !dashboard) return tile;
    const category = heatmapAssetClassToCategory(tile.assetClass);
    if (!TREND_SELECTABLE_CATEGORIES.has(category)) {
        return tile;
    }
    const changePercent = resolveChangePercent(closes, tile.changePercent);
    const dailyChangePercent = dailyChangePercentForListedAsset(
        category,
        tile.changePercent,
        closes,
        changePercent,
    );
    const row = {
        sparkline: closes,
        changePercent,
        dailyChangePercent,
        category,
    };
    const pct = effectiveChangePercent(row, chartRange);
    return { ...tile, changePercent: pct };
}

function toContractMonth(label: string): string {
    const m = label.match(/\((.+)\)/);
    return m?.[1] ?? '—';
}

function formatDateTr(value: string | Date | null | undefined): string {
    if (!value) return '—';
    const d = value instanceof Date ? value : new Date(value);
    if (Number.isNaN(d.getTime())) return '—';
    return d.toLocaleDateString('tr-TR');
}

function unwrapData<T>(res: AxiosResponse<T>): T {
    const body = res.data as unknown;
    if (body && typeof body === 'object' && 'data' in (body as object)) {
        return (body as { data: T }).data;
    }
    return body as T;
}

type StarredAssetsApiResponse = {
    maxItems: number;
    selected: { marketType: string; symbol: string; position: number }[];
    resolved: { marketType: string; symbol: string; position: number; defaultFilled: boolean }[];
};

/** Dashboard /api/me/starred-assets ile uyumlu marketType + symbol */
function rowToStarredApiKey(activeCategory: MarketCategory, symbol: string): { marketType: string; symbol: string } | null {
    const sym = normalizeSymbolKey(symbol);
    if (!sym) return null;
    if (activeCategory === 'EQUITY') return { marketType: 'EQUITY', symbol: sym };
    if (activeCategory === 'CRYPTO') return { marketType: 'CRYPTO', symbol: sym };
    if (activeCategory === 'FX') return { marketType: 'FX', symbol: sym };
    if (activeCategory === 'METALS') {
        if (sym === 'ALTIN_TRY') return { marketType: 'METALS', symbol: 'XAU_TRY' };
        return { marketType: 'METALS', symbol: sym };
    }
    if (activeCategory === 'FUNDS') return { marketType: 'FUNDS', symbol: sym };
    return null;
}

function isStarredResolved(data: StarredAssetsApiResponse | undefined, mt: string, sym: string): boolean {
    if (!data?.resolved?.length) return false;
    return data.resolved.some((r) => r.marketType === mt && r.symbol === sym);
}

export function Market() {
    const { theme, tokens } = useTheme();
    const { t, lang } = useLanguage();
    const numberLocale = lang === 'en' ? 'en-US' : 'tr-TR';
    const navigate = useNavigate();
    const [searchParams, setSearchParams] = useSearchParams();
    const [activeCategory, setActiveCategory] = useState<MarketCategory>('EQUITY');
    const [showUsdInTry, setShowUsdInTry] = useState(false);
    const [selectedSymbol, setSelectedSymbol] = useState<string>('');
    const [searchTerm, setSearchTerm] = useState('');
    const [isDetailPanelOpen, setIsDetailPanelOpen] = useState(false);
    const [liveOverrides, setLiveOverrides] = useState<Record<string, LiveTick>>({});
    const [range, setRange] = useState<ChartRangeId>('1M');
    const [equitySubmarket, setEquitySubmarket] = useState<EquitySubmarket>('US');
    const [fundSubmarket, setFundSubmarket] = useState<FundSubmarket>('TR');
    const [metalsSubmarket, setMetalsSubmarket] = useState<MetalsSubmarket>('GRAM');
    const isTefasFundsView = activeCategory === 'FUNDS' && fundSubmarket === 'TR';
    const isBondInstrumentsView = activeCategory === 'BOND';
    /** Hisse / kripto / FX / metaller / fonlar (ısı haritası + makro kartlar). */
    const isSpotMarketCategory =
        activeCategory === 'EQUITY' ||
        activeCategory === 'CRYPTO' ||
        activeCategory === 'FX' ||
        activeCategory === 'METALS' ||
        activeCategory === 'FUNDS';
    /** Hisse / kripto / FX / metaller / fonlar + VİOP: sol sütunda liste (+ spot’ta makro). Tahvil ayrı grid. */
    const isSpotTerminalLayout = isSpotMarketCategory || activeCategory === 'FUTURES';
    const isBondTerminalLayout = isBondInstrumentsView;
    const showMarketHeatmap = isSpotMarketCategory;
    /** Açılır piyasa listesinde gezilen kategori / alt pazar; grafik `activeCategory` ile ayrılır — chip’e basınca hero değişmez. */
    const [pickerCategory, setPickerCategory] = useState<MarketCategory>('EQUITY');
    const [pickerEquitySubmarket, setPickerEquitySubmarket] = useState<EquitySubmarket>('US');
    const [pickerFundSubmarket, setPickerFundSubmarket] = useState<FundSubmarket>('TR');
    const [pickerMetalsSubmarket, setPickerMetalsSubmarket] = useState<MetalsSubmarket>('GRAM');
    const isTefasFundsPicker = pickerCategory === 'FUNDS' && pickerFundSubmarket === 'TR';
    const [bistRange, setBistRange] = useState<ChartRangeId>('1M');
    const trendChartRange: ChartRangeId =
        activeCategory === 'EQUITY' && equitySubmarket === 'BIST' ? bistRange : range;
    const [showMa, setShowMa] = useState(true);
    const [showRsi, setShowRsi] = useState(false);
    const [bondChartMode, setBondChartMode] = useState<'DUAL' | 'CANDLE'>('DUAL');
    const [viopChartMode, setViopChartMode] = useState<'LINE' | 'CANDLE'>('LINE');
    const [spotChartMode, setSpotChartMode] = useState<'ANALYSIS' | 'CANDLE'>('ANALYSIS');
    /** Karşılaştırma grafiği: zaman aralığının başlangıç günü (crosshair ile değişmez). */
    const [ppChartAnchor, setPpChartAnchor] = useState('');
    const ppAnchorBoundsRef = useRef({ first: '', last: '', today: '' });
    const [priceFlash, setPriceFlash] = useState<Record<string, 'up' | 'down'>>({});
    const prevPricesRef = useRef<Record<string, number>>({});
    const prevTerminalCategoryRef = useRef<MarketCategory>(activeCategory);

    /** Detaylı ısı haritası / dış link: ?type=METALS&symbol=…&metalsSubmarket=GRAM|OUNCE */
    useEffect(() => {
        const type = searchParams.get('type')?.trim().toUpperCase();
        const symbol = searchParams.get('symbol')?.trim();
        const sub = searchParams.get('metalsSubmarket')?.trim().toUpperCase();
        if (type === 'METALS' && (sub === 'GRAM' || sub === 'OUNCE')) {
            const metalsSub = sub as MetalsSubmarket;
            setActiveCategory('METALS');
            setPickerCategory('METALS');
            setMetalsSubmarket(metalsSub);
            setPickerMetalsSubmarket(metalsSub);
        } else if (
            type === 'EQUITY' ||
            type === 'CRYPTO' ||
            type === 'FX' ||
            type === 'METALS' ||
            type === 'FUNDS' ||
            type === 'FUTURES' ||
            type === 'BOND'
        ) {
            setActiveCategory(type as MarketCategory);
            setPickerCategory(type as MarketCategory);
        }
        if (symbol) {
            setSelectedSymbol(symbol);
        }
        if (type || symbol || sub) {
            const next = new URLSearchParams(searchParams);
            next.delete('type');
            next.delete('symbol');
            next.delete('metalsSubmarket');
            next.delete('days');
            setSearchParams(next, { replace: true });
        }
    }, [searchParams, setSearchParams]);

    /** Faiz paneli CTA → Piyasalar Tahvil sekmesi (sessionStorage, tek seferlik). */
    useEffect(() => {
        try {
            const pref = sessionStorage.getItem('nrs.market.prefCategory');
            if (
                pref === 'BOND' ||
                pref === 'FUTURES' ||
                pref === 'EQUITY' ||
                pref === 'CRYPTO' ||
                pref === 'FX' ||
                pref === 'METALS' ||
                pref === 'FUNDS'
            ) {
                setActiveCategory(pref);
                setPickerCategory(pref);
                sessionStorage.removeItem('nrs.market.prefCategory');
            }
        } catch {
            /* ignore */
        }
    }, []);

    const [compareSymbols, setCompareSymbols] = useState<string[]>([]);
    const [compareRows, setCompareRows] = useState<CompareRow[]>([]);
    const [loadingCompare, setLoadingCompare] = useState(false);
    /** Piyasa listesi: spot terminal düzeninde sol kartta sabit; diğer düzenlerde popover. */
    const [instrumentListOpen, setInstrumentListOpen] = useState(false);
    const terminalHeroRef = useRef<HTMLDivElement | null>(null);
    const leftMarketPanelRef = useRef<HTMLDivElement | null>(null);
    const [instrumentListPopoverBox, setInstrumentListPopoverBox] = useState<{
        top: number;
        left: number;
        width: number;
    } | null>(null);
    /** Sol liste: arama sonrası hızlı filtre (Tümü / yükselen / düşen / hacim veya VİOP türü). */
    const [marketListQuickFilter, setMarketListQuickFilter] = useState<string>('ALL');
    /** Piyasa listesi tam ekrana yakın büyük görünüm (bulanık arka plan). */
    const [marketListExpanded, setMarketListExpanded] = useState(false);
    /** Piyasa listesi: Fiyat / Gün / Hafta / Ay / Yıl başlık tıklaması ile sıralama */
    const [pickerTableSort, setPickerTableSort] = useState<{ key: PickerListSortKey; dir: 'asc' | 'desc' } | null>(
        null,
    );
    const [marketListPage, setMarketListPage] = useState(0);
    const [priceAlertTarget, setPriceAlertTarget] = useState<{
        assetType: PriceAlertAssetType;
        symbol: string;
        displayName?: string;
        referencePrice?: number | null;
        priceCurrency?: string | null;
    } | null>(null);
    const [effectiveRatesOpen, setEffectiveRatesOpen] = useState(false);

    const togglePickerListSort = useCallback((key: PickerListSortKey) => {
        setPickerTableSort((prev) => {
            if (prev?.key === key) {
                return { key, dir: prev.dir === 'asc' ? 'desc' : 'asc' };
            }
            return { key, dir: 'desc' };
        });
    }, []);

    useEffect(() => {
        if (!marketListExpanded) return;
        const prevOverflow = document.body.style.overflow;
        document.body.style.overflow = 'hidden';
        return () => {
            document.body.style.overflow = prevOverflow;
        };
    }, [marketListExpanded]);

    useEffect(() => {
        if (!marketListExpanded) return;
        const onKey = (e: KeyboardEvent) => {
            if (e.key === 'Escape') setMarketListExpanded(false);
        };
        window.addEventListener('keydown', onKey);
        return () => window.removeEventListener('keydown', onKey);
    }, [marketListExpanded]);

    const queryClient = useQueryClient();
    const { data: starredAssets } = useQuery({
        queryKey: ['me', 'starred-assets'],
        queryFn: () => financeClient.get<StarredAssetsApiResponse>('/api/me/starred-assets').then((r) => unwrapData(r)),
    });

    const starMutation = useMutation({
        mutationFn: (nextSelected: { marketType: string; symbol: string }[]) =>
            financeClient.put('/api/me/starred-assets', {
                selected: nextSelected.map((s) => ({ marketType: s.marketType, symbol: s.symbol })),
            }),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['me', 'starred-assets'] });
        },
        onError: (err: unknown) => {
            const msg = err instanceof Error ? err.message : 'Yıldız güncellenemedi.';
            window.alert(msg);
        },
    });

    const handleStarToggle = useCallback(
        (e: MouseEvent<HTMLButtonElement>, rowSymbol: string, rowCategory: MarketCategory) => {
            e.stopPropagation();
            const key = rowToStarredApiKey(rowCategory, rowSymbol);
            if (!key) return;
            const pair = `${key.marketType}:${key.symbol}`;
            const sel = starredAssets?.selected ?? [];
            const maxItems = starredAssets?.maxItems ?? STARRED_MAX_FALLBACK;
            const exists = sel.some((s) => `${s.marketType}:${s.symbol}` === pair);
            let next: { marketType: string; symbol: string }[];
            if (exists) {
                next = sel.filter((s) => `${s.marketType}:${s.symbol}` !== pair);
            } else {
                if (sel.length >= maxItems) {
                    window.alert(`En fazla ${maxItems} varlık yıldızlanabilir.`);
                    return;
                }
                // Yeni yıldızlanan varlığı en üste al: dashboard'da kullanıcı seçimi önce görünsün.
                next = [{ marketType: key.marketType, symbol: key.symbol }, ...sel];
            }
            starMutation.mutate(next);
        },
        [starredAssets, starMutation]
    );

    const applyPickerCategory = useCallback(
        (id: MarketCategory) => {
            setPickerCategory(id);
            setActiveCategory(id);
            setMarketListQuickFilter('ALL');
            setMarketListPage(0);
        },
        [],
    );

    const handleInstrumentSelectorClick = useCallback(() => {
        if (isSpotTerminalLayout) {
            setPickerCategory(activeCategory);
            setPickerEquitySubmarket(equitySubmarket);
            setPickerFundSubmarket(fundSubmarket);
            setPickerMetalsSubmarket(metalsSubmarket);
            leftMarketPanelRef.current?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
            return;
        }
        setInstrumentListOpen((wasOpen) => {
            if (!wasOpen) {
                setPickerCategory(activeCategory);
                setPickerEquitySubmarket(equitySubmarket);
                setPickerFundSubmarket(fundSubmarket);
                setPickerMetalsSubmarket(metalsSubmarket);
            }
            return !wasOpen;
        });
    }, [activeCategory, equitySubmarket, fundSubmarket, metalsSubmarket, isSpotTerminalLayout]);

    useEffect(() => {
        setInstrumentListOpen(false);
        setMarketListQuickFilter('ALL');
    }, [activeCategory, equitySubmarket, fundSubmarket, metalsSubmarket]);

    /** Spot terminal: sol liste sekmeleri grafikle aynı kategori/alt pazarı göstersin. */
    useEffect(() => {
        if (!isSpotTerminalLayout) return;
        setPickerCategory(activeCategory);
        setPickerEquitySubmarket(equitySubmarket);
        setPickerFundSubmarket(fundSubmarket);
        setPickerMetalsSubmarket(metalsSubmarket);
    }, [isSpotTerminalLayout, activeCategory, equitySubmarket, fundSubmarket, metalsSubmarket]);

    useEffect(() => {
        if (activeCategory !== 'FUNDS') return;
        setCompareSymbols([]);
        setCompareRows([]);
    }, [fundSubmarket, activeCategory]);

    useEffect(() => {
        if (activeCategory !== 'BOND') return;
        setCompareSymbols([]);
        setCompareRows([]);
    }, [activeCategory]);

    useEffect(() => {
        if (!instrumentListOpen) return;
        const onKey = (e: KeyboardEvent) => {
            if (e.key === 'Escape') setInstrumentListOpen(false);
        };
        window.addEventListener('keydown', onKey);
        return () => window.removeEventListener('keydown', onKey);
    }, [instrumentListOpen]);

    useLayoutEffect(() => {
        if (!instrumentListOpen) {
            setInstrumentListPopoverBox(null);
            return;
        }
        const sync = () => {
            const host = terminalHeroRef.current;
            if (!host) return;
            const r = host.getBoundingClientRect();
            const width = Math.min(720, Math.max(460, Math.round(r.width * 0.88)));
            const left = Math.max(12, Math.min(r.left, window.innerWidth - width - 12));
            setInstrumentListPopoverBox({ top: r.bottom + 6, left, width });
        };
        sync();
        window.addEventListener('resize', sync);
        window.addEventListener('scroll', sync, true);
        return () => {
            window.removeEventListener('resize', sync);
            window.removeEventListener('scroll', sync, true);
        };
    }, [instrumentListOpen]);

    /** Grafik BIST veya piyasa listesinde Türk Hisseleri — spot sol kartta liste sürekli açık. */
    const needBistTerminalData = activeCategory === 'EQUITY' && equitySubmarket === 'BIST';

    /** VİOP: grafik VİOP modunda; snapshot yalnızca seçili + liste sayfası. */
    const needViopTerminalData = activeCategory === 'FUTURES';

    /** Tahvil: DİBS/bono ISIN listesi ve grafik. */
    const needDebtTerminalData = isBondInstrumentsView;
    const needDebtCatalog = needDebtTerminalData;

    const pickerMatchesChart =
        pickerCategory === activeCategory &&
        (activeCategory !== 'EQUITY' || pickerEquitySubmarket === equitySubmarket) &&
        (activeCategory !== 'FUNDS' || pickerFundSubmarket === fundSubmarket) &&
        (activeCategory !== 'METALS' || pickerMetalsSubmarket === metalsSubmarket);

    /** Grafik ve batch istekleri seçili `range` ile aynı kalır (VİOP/tahvil 1G dahil). */
    const dataRangeForChart: ChartRangeId = range;

    const {
        data: dashboard,
        isLoading: loadingDashboard,
        error: dashboardError,
    } = useQuery({
        queryKey: ['market', 'dashboard', 'terminal'],
        queryFn: () =>
            financeClient.get<MarketDashboard>('/api/market/dashboard').then((r) => unwrapData(r)),
        enabled: activeCategory !== 'FUTURES' && activeCategory !== 'BOND' && !isTefasFundsView,
        staleTime: 30_000,
        refetchInterval: activeCategory === 'FUTURES' || activeCategory === 'BOND' ? false : 12_000,
        refetchOnWindowFocus: false,
    });

    const {
        data: bistLatest = [],
        isLoading: loadingBistLatest,
    } = useQuery({
        queryKey: ['market', 'bist', 'latest', 'terminal'],
        queryFn: ({ signal }) => getBistLatest(signal),
        enabled: needBistTerminalData,
        staleTime: 30_000,
        refetchInterval: needBistTerminalData ? 60_000 : false,
        refetchOnWindowFocus: needBistTerminalData,
    });

    const terminalListSortKey = pickerTableSort?.key ?? null;
    const terminalListSortDir = pickerTableSort?.dir ?? 'desc';

    const {
        data: terminalListPageData,
        isLoading: loadingTerminalList,
        error: terminalListError,
    } = useQuery({
        queryKey: [
            'market',
            'terminal',
            'list',
            pickerCategory,
            pickerEquitySubmarket,
            pickerFundSubmarket,
            pickerMetalsSubmarket,
            marketListPage,
            marketListQuickFilter,
            searchTerm,
            terminalListSortKey,
            terminalListSortDir,
        ],
        queryFn: ({ signal }) =>
            fetchMarketTerminalList(
                {
                    category: pickerCategory,
                    equitySubmarket: pickerCategory === 'EQUITY' ? pickerEquitySubmarket : undefined,
                    fundSubmarket: pickerCategory === 'FUNDS' ? pickerFundSubmarket : undefined,
                    page: marketListPage,
                    size:
                        pickerCategory === 'FUNDS' && pickerFundSubmarket === 'TR'
                            ? 12
                            : MARKET_LIST_PAGE_SIZE,
                    filter: marketListQuickFilter,
                    sort:
                        pickerCategory === 'FUNDS' && pickerFundSubmarket === 'TR'
                            ? mapTerminalSortToTefas(terminalListSortKey) ?? undefined
                            : terminalListSortKey,
                    dir: terminalListSortDir,
                    search: searchTerm,
                },
                signal,
            ),
        // Alt pazar (TR/US fon) değişince önceki ETF listesini gösterme — TEFAS boş dönse bile EEM/SPY kalmasın.
        placeholderData: (previousData, previousQuery) => {
            if (!previousData || !previousQuery?.queryKey) return undefined;
            const pk = previousQuery.queryKey as readonly unknown[];
            if (pk[3] !== pickerCategory) return undefined;
            if (pickerCategory === 'EQUITY' && pk[4] !== pickerEquitySubmarket) return undefined;
            if (pickerCategory === 'FUNDS' && pk[5] !== pickerFundSubmarket) return undefined;
            if (pickerCategory === 'METALS' && pk[6] !== pickerMetalsSubmarket) return undefined;
            return previousData;
        },
        staleTime:
            pickerCategory === 'FUNDS' && pickerFundSubmarket === 'TR'
                ? 120_000
                : pickerCategory === 'BOND' || pickerCategory === 'FUTURES'
                  ? 30_000
                  : 8_000,
    });

    const TEFAS_HEATMAP_PAGE_SIZE = 100;

    const { data: tefasHeatmapPage } = useQuery({
        queryKey: ['market', 'tefas', 'heatmap', range, fundSubmarket],
        queryFn: ({ signal }) =>
            fetchMarketTerminalList(
                {
                    category: 'FUNDS',
                    fundSubmarket: 'TR',
                    page: 0,
                    size: TEFAS_HEATMAP_PAGE_SIZE,
                    filter: 'ALL',
                    sort: tefasHeatmapSortForRange(range),
                    dir: 'desc',
                },
                signal,
            ),
        enabled: isTefasFundsView,
        staleTime: 120_000,
        refetchInterval: 180_000,
    });

    const pickerTerminalListPage = useMemo(() => {
        if (!terminalListPageData) return terminalListPageData;
        if (pickerCategory !== 'METALS') return terminalListPageData;
        const filtered = terminalListPageData.items.filter((row) =>
            symbolMatchesMetalsSubmarket(row.symbol, pickerMetalsSubmarket),
        );
        const size = terminalListPageData.size || MARKET_LIST_PAGE_SIZE;
        const total = filtered.length;
        const totalPages = Math.max(1, Math.ceil(total / size));
        const page = Math.min(marketListPage, totalPages - 1);
        const from = page * size;
        return {
            ...terminalListPageData,
            items: filtered.slice(from, from + size),
            page,
            totalElements: total,
            totalPages,
            hasNext: page < totalPages - 1,
            hasPrevious: page > 0,
        };
    }, [terminalListPageData, pickerCategory, pickerMetalsSubmarket, marketListPage]);

    /** Dashboard beklenmeden ilk satırdan sembol seç — grafik istekleri hemen başlasın. */
    const bootstrapSymbolFromList = useMemo(() => {
        if (!pickerMatchesChart) return '';
        const first = pickerTerminalListPage?.items?.[0]?.symbol;
        return first ? normalizeSymbolKey(first) : '';
    }, [pickerMatchesChart, pickerTerminalListPage]);

    useEffect(() => {
        if (activeCategory === 'FUTURES' || activeCategory === 'BOND') return;
        if (!pickerMatchesChart || !bootstrapSymbolFromList) return;
        setSelectedSymbol((prev) => prev || bootstrapSymbolFromList);
    }, [activeCategory, pickerMatchesChart, bootstrapSymbolFromList]);

    useEffect(() => {
        setMarketListPage(0);
    }, [
        pickerCategory,
        pickerEquitySubmarket,
        pickerFundSubmarket,
        pickerMetalsSubmarket,
        marketListQuickFilter,
        searchTerm,
        terminalListSortKey,
        terminalListSortDir,
    ]);

    const bistSparkSymbolsCsv = useMemo(() => {
        if (pickerCategory === 'EQUITY' && pickerEquitySubmarket === 'BIST') {
            const fromPage = (terminalListPageData?.items ?? [])
                .map((r) => normalizeSymbolKey(r.symbol))
                .filter(Boolean)
                .join(',');
            if (fromPage) return fromPage;
        }
        if (!bistLatest.length) return '';
        return bistLatest
            .slice(0, 22)
            .map((r) => normalizeSymbolKey(String(r.symbol ?? '')))
            .filter(Boolean)
            .join(',');
    }, [pickerCategory, pickerEquitySubmarket, terminalListPageData, bistLatest]);

    const bistSparkRange = useMemo(() => bistCalendarRange(400), []);

    const { data: bistBatchSpark } = useQuery({
        queryKey: ['market', 'bist', 'batch-spark', bistSparkRange.from, bistSparkRange.to, bistSparkSymbolsCsv],
        queryFn: ({ signal }) => {
            const syms = bistSparkSymbolsCsv.split(',').map((s) => s.trim()).filter(Boolean);
            return getBistBatchHistory(syms, bistSparkRange.from, bistSparkRange.to, signal);
        },
        enabled:
            (needBistTerminalData ||
                (pickerCategory === 'EQUITY' && pickerEquitySubmarket === 'BIST')) &&
            bistSparkSymbolsCsv.length > 0,
        staleTime: 120_000,
    });

    const bistSparkClosesBySymbol = useMemo(() => {
        const m: Record<string, number[]> = {};
        const by = bistBatchSpark?.historiesBySymbol;
        if (!by) return m;
        Object.entries(by).forEach(([sym, rows]) => {
            const key = normalizeSymbolKey(sym);
            const closes = [...(rows ?? [])]
                .sort((a, b) => String(a.date ?? '').localeCompare(String(b.date ?? '')))
                .map((r) => bistPickClose(r))
                .filter((x) => Number.isFinite(x) && x > 0);
            if (closes.length >= 2) m[key] = closes;
        });
        return m;
    }, [bistBatchSpark]);

    const bistMainChartDays = useMemo(() => RANGE_TO_DAYS[bistRange], [bistRange]);
    const bistMainHistoryRange = useMemo(() => bistCalendarRange(bistMainChartDays), [bistMainChartDays]);

    const { data: bistCandles = [], isLoading: loadingBistCandles } = useQuery({
        queryKey: [
            'market',
            'bist',
            'candles',
            'terminal',
            selectedSymbol,
            bistMainHistoryRange.from,
            bistMainHistoryRange.to,
        ],
        enabled: activeCategory === 'EQUITY' && equitySubmarket === 'BIST' && Boolean(selectedSymbol),
        queryFn: ({ signal }) =>
            getBistCandles(selectedSymbol, bistMainHistoryRange.from, bistMainHistoryRange.to, signal),
        staleTime: 60_000,
        refetchInterval: 90_000,
        refetchOnWindowFocus: true,
    });

    const bistTreemapTiles = useMemo((): TreemapTile[] => {
        const fromDash = (dashboard?.heatmapTiles ?? []).filter((t) => t.assetClass === 'BIST' && t.symbol);
        if (fromDash.length > 0 && dashboard) {
            return fromDash.map((tile) => enrichTreemapTileForChartRange(tile, dashboard, bistRange));
        }
        if (!bistLatest.length) return [];
        const out: TreemapTile[] = [];
        for (const r of bistLatest) {
            const sym = normalizeSymbolKey(String(r.symbol ?? ''));
            if (!sym) continue;
            const pctRaw = r.changePercent;
            const pct =
                typeof pctRaw === 'number' && Number.isFinite(pctRaw)
                    ? pctRaw
                    : parseFloat(String(pctRaw ?? '0').replace(',', '.')) || 0;
            const mcRaw = r.marketCapTry;
            const mc =
                typeof mcRaw === 'number' && Number.isFinite(mcRaw)
                    ? mcRaw
                    : parseFloat(String(mcRaw ?? '0').replace(',', '.')) || 0;
            const w = mc > 0 ? mc : Math.abs(pct) + 1;
            const display =
                typeof (r as { displayName?: string }).displayName === 'string'
                    ? String((r as { displayName?: string }).displayName).trim()
                    : '';
            out.push({
                sector: 'BIST_EQUITY',
                industry: display.length > 0 ? display : null,
                symbol: sym,
                assetClass: 'BIST',
                changePercent: pct,
                layoutWeight: Math.max(1, w),
                mode: 'BIST_DAILY',
            });
        }
        return out.map((tile) => {
            const spark = bistSparkClosesBySymbol[normalizeSymbolKey(tile.symbol)];
            return enrichTreemapTileForChartRange(
                tile,
                dashboard,
                bistRange,
                spark != null && spark.length >= 2 ? spark : undefined,
            );
        });
    }, [bistLatest, dashboard, bistRange, bistSparkClosesBySymbol]);

    const chartTfLabel = useMemo(() => {
        if (activeCategory === 'EQUITY' && equitySubmarket === 'BIST') return chartRangeUiShortLabel(bistRange);
        return chartRangeUiShortLabel(range);
    }, [activeCategory, equitySubmarket, bistRange, range]);

    const usdTryRateQueryEnabled =
        showUsdInTry &&
        isSpotMarketCategory &&
        ((activeCategory === 'EQUITY' && equitySubmarket !== 'BIST') ||
            activeCategory === 'CRYPTO' ||
            (activeCategory === 'FUNDS' && fundSubmarket === 'US') ||
            activeCategory === 'METALS');
    const { data: usdTryRatePayload } = useQuery({
        queryKey: ['market', 'terminal', 'usd-try-rate'],
        queryFn: () =>
            financeClient.get<UsdTryRatePayload>('/api/market/terminal/usd-try-rate').then((r) => unwrapData(r)),
        enabled: usdTryRateQueryEnabled,
        staleTime: 15_000,
        refetchInterval: usdTryRateQueryEnabled ? 25_000 : false,
    });
    const usdTryRate = usdTryRateQueryEnabled ? effectiveUsdTryRate(usdTryRatePayload) : null;

    /** Orta kolon (grafik + karşılaştırma) yüksekliği — sol/sağ paneller buna göre uzar/kısalır; taşan liste içeride kayar */
    const centerStackRef = useRef<HTMLDivElement | null>(null);
    const [sideRailPx, setSideRailPx] = useState<number | null>(null);
    const sideRailRafRef = useRef<number | null>(null);
    const lastSideRailPxRef = useRef<number | null>(null);
    useLayoutEffect(() => {
        if (loadingDashboard && !(activeCategory === 'EQUITY' && equitySubmarket === 'BIST')) {
            setSideRailPx(null);
            lastSideRailPxRef.current = null;
            return;
        }
        const el = centerStackRef.current;
        if (!el || typeof ResizeObserver === 'undefined') {
            setSideRailPx(null);
            lastSideRailPxRef.current = null;
            return;
        }
        const sync = () => {
            const h = Math.round(el.getBoundingClientRect().height);
            const next = h > 0 ? h : null;
            const prev = lastSideRailPxRef.current;
            // 1-3px oynama/scrollbar jitter'ında state güncelleyip layout döngüsüne girmesin.
            if (prev != null && next != null && Math.abs(prev - next) < 16) return;
            lastSideRailPxRef.current = next;
            if (sideRailRafRef.current != null) cancelAnimationFrame(sideRailRafRef.current);
            sideRailRafRef.current = requestAnimationFrame(() => {
                setSideRailPx(next);
                sideRailRafRef.current = null;
            });
        };
        const ro = new ResizeObserver(sync);
        ro.observe(el);
        sync();
        return () => {
            ro.disconnect();
            if (sideRailRafRef.current != null) {
                cancelAnimationFrame(sideRailRafRef.current);
                sideRailRafRef.current = null;
            }
        };
    }, [loadingDashboard, activeCategory, equitySubmarket, loadingBistLatest]);

    const [threeColRailSync, setThreeColRailSync] = useState(
        () => typeof window !== 'undefined' && window.matchMedia('(min-width: 981px)').matches
    );
    useEffect(() => {
        if (typeof window === 'undefined') return;
        const mq = window.matchMedia('(min-width: 981px)');
        const apply = () => setThreeColRailSync(mq.matches);
        apply();
        mq.addEventListener('change', apply);
        return () => mq.removeEventListener('change', apply);
    }, []);

    const enableSideRailSync = threeColRailSync;
    const sideRailBoxStyle: CSSProperties | undefined =
        enableSideRailSync && sideRailPx != null && sideRailPx > 0
            ? {
                  height: sideRailPx,
                  maxHeight: sideRailPx,
                  minHeight: 0,
                  overflow: 'hidden',
                  boxSizing: 'border-box',
              }
            : undefined;
    const rightPanelStyle: CSSProperties | undefined = sideRailBoxStyle;

    // VIOP: kontrat listesi + kontrat başına snapshot (`/api/market/viop/contracts/{code}/snapshot`).
    const { data: viopContracts = [] } = useQuery({
        queryKey: ['market', 'viop', 'contracts', 'terminal'],
        queryFn: ({ signal }) =>
            marketClient.get<ViopContract[]>('/api/market/viop/contracts', { signal }).then((r) => r.data),
        enabled: needViopTerminalData,
        select: (rows) => (rows ?? []).filter((c) => isViopWhitelisted(c?.contractCode)),
        refetchInterval: false,
        refetchOnWindowFocus: false,
        refetchOnReconnect: false,
        staleTime: Infinity,
    });
    const viopSnapshotSymbols = useMemo(() => {
        if (!needViopTerminalData) return [] as string[];
        const keys = new Set<string>();
        const add = (raw?: string) => {
            const k = normalizeSymbolKey(raw ?? '');
            if (k && isViopWhitelisted(k)) keys.add(k);
        };
        add(selectedSymbol);
        if (pickerCategory === 'FUTURES') {
            viopContracts.forEach((c) => add(c.contractCode));
            (terminalListPageData?.items ?? []).forEach((r) => add(r.symbol));
        }
        return [...keys];
    }, [needViopTerminalData, selectedSymbol, pickerCategory, terminalListPageData, viopContracts]);

    const viopSnapshotQueries = useQueries({
        queries: viopSnapshotSymbols.map((sym) => ({
            queryKey: ['market', 'viop', 'snapshot', sym],
            queryFn: ({ signal }: { signal: AbortSignal }) =>
                marketClient
                    .get<ViopContractSnapshotApi>(`/api/market/viop/contracts/${encodeURIComponent(sym)}/snapshot`, {
                        signal,
                    })
                    .then((r) => r.data),
            enabled: needViopTerminalData && Boolean(sym),
            staleTime: 45_000,
            refetchInterval:
                activeCategory === 'FUTURES' && sym === normalizeSymbolKey(selectedSymbol) ? 60_000 : false,
            refetchOnWindowFocus:
                activeCategory === 'FUTURES' && sym === normalizeSymbolKey(selectedSymbol),
        })),
    });
    const viopSnapshotBySymbol = useMemo(() => {
        const m: Record<string, ViopContractSnapshotApi> = {};
        viopSnapshotQueries.forEach((q, i) => {
            const sym = normalizeSymbolKey(viopSnapshotSymbols[i]);
            const row = q.data;
            if (sym && row) {
                m[sym] = row;
                const alt = sym.startsWith('F_') ? sym.slice(2) : `F_${sym}`;
                m[alt] = row;
            }
        });
        return m;
    }, [viopSnapshotQueries, viopSnapshotSymbols]);
    const viopContractBySymbol = useMemo(() => {
        const m: Record<string, ViopContract> = {};
        viopContracts.forEach((c) => {
            const k = normalizeSymbolKey(c.contractCode);
            if (!k) return;
            m[k] = c;
            const alt = k.startsWith('F_') ? k.slice(2) : `F_${k}`;
            m[alt] = c;
        });
        return m;
    }, [viopContracts]);

    const { data: debtLatest = [] } = useQuery({
        queryKey: ['market', 'debt', 'latest', 'terminal'],
        queryFn: ({ signal }) =>
            marketClient.get<DebtSnapshot[]>('/api/market/debt/latest', { signal }).then((r) => r.data),
        enabled: needDebtTerminalData,
        refetchInterval: needDebtTerminalData ? 60_000 : false,
    });
    const { data: debtCatalog = [] } = useQuery({
        queryKey: ['market', 'debt', 'catalog', 'terminal'],
        queryFn: ({ signal }) =>
            marketClient.get<DebtInstrument[]>('/api/market/debt/catalog', { signal }).then((r) => r.data),
        enabled: needDebtCatalog,
        staleTime: 120_000,
        refetchInterval: needDebtCatalog ? 120_000 : false,
    });

    const fxSpreadMap = useMemo(() => {
        const out: Record<string, number> = {};
        if (!dashboard) return out;
        Object.entries(dashboard.latest.doviz ?? {}).forEach(([symbol, row]) => {
            const buy = Number(row.buyPrice ?? row.buy ?? row.price ?? 0);
            const sell = Number(row.sellPrice ?? row.sell ?? row.price ?? buy);
            out[symbol] = Math.max(0, sell - buy);
        });
        return out;
    }, [dashboard]);

    const debtNameMap = useMemo(() => {
        const m: Record<string, string> = {};
        debtCatalog.forEach((x) => {
            m[normalizeSymbolKey(x.isin)] = x.name;
        });
        return m;
    }, [debtCatalog]);
    const debtMetaMap = useMemo(() => {
        const m: Record<string, DebtInstrument> = {};
        debtCatalog.forEach((x) => {
            m[normalizeSymbolKey(x.isin)] = x;
        });
        return m;
    }, [debtCatalog]);
    const debtLatestMap = useMemo(() => {
        const m: Record<string, DebtSnapshot> = {};
        debtLatest.forEach((x) => {
            m[normalizeSymbolKey(x.isin)] = x;
        });
        return m;
    }, [debtLatest]);
    const viopLatestMap = useMemo(() => {
        const m: Record<string, ViopSnapshot> = {};
        const seen = new Set<string>();
        Object.values(viopSnapshotBySymbol).forEach((snap) => {
            if (!snap?.contractCode) return;
            const key = normalizeSymbolKey(snap.contractCode);
            if (!key || seen.has(key)) return;
            seen.add(key);
            m[key] = snapshotToLegacyViopSnapshot(snap);
        });
        return m;
    }, [viopSnapshotBySymbol]);
    const volatilityByKey = useMemo(() => {
        const m: Record<string, number> = {};
        (dashboard?.volatility ?? []).forEach((v) => {
            m[`${v.assetClass}:${normalizeSymbolKey(v.symbol)}`] = v.dailyVolatility;
        });
        return m;
    }, [dashboard]);

    const tefasHistoryMonths = useMemo(() => tefasMonthsForChartRange(range), [range]);

    const {
        data: tefasHistory = [],
        isLoading: loadingTefasHistory,
        isFetching: fetchingTefasHistory,
    } = useQuery({
        queryKey: ['market', 'tefas', 'history', selectedSymbol, tefasHistoryMonths],
        queryFn: ({ signal }) =>
            fetchTefasFundHistory(normalizeSymbolKey(selectedSymbol), tefasHistoryMonths, signal),
        enabled: isTefasFundsView && Boolean(selectedSymbol),
        staleTime: 120_000,
    });

    const buildInstruments = useCallback(
        (
            cat: MarketCategory,
            eqSub: EquitySubmarket,
            fundSub: FundSubmarket,
            metalsSub: MetalsSubmarket,
        ): MarketInstrument[] => {
        if (cat === 'FUTURES') {
            if (pickerMatchesChart && (terminalListPageData?.items?.length ?? 0) > 0) {
                return terminalListPageData!.items
                    .filter((row) => row.category === 'FUTURES')
                    .map((row) => ({
                        symbol: normalizeSymbolKey(row.symbol),
                        category: 'FUTURES' as const,
                        displayName: row.displayName ?? undefined,
                        price: row.price,
                        changePercent: row.changePercent,
                        dailyChangePercent: row.dailyChangePercent ?? row.changePercent,
                        trend: row.trend,
                        volume: row.volume ?? null,
                    }));
            }
            const out: MarketInstrument[] = [];
            for (const sym of viopSnapshotSymbols) {
                const symbol = normalizeSymbolKey(sym);
                const snap = viopSnapshotBySymbol[symbol];
                const priceN = Number(snap?.last ?? 0);
                if (!(priceN > 0) || !snap) continue;
                const pctRaw = snap.changePercent;
                const pct =
                    pctRaw != null && Number.isFinite(Number(pctRaw))
                        ? Number(pctRaw)
                        : viopTerminalListChange(snapshotToLegacyViopSnapshot(snap), viopContractBySymbol[symbol])
                              ?.pct ?? 0;
                const trendDir = pct >= 0 ? ('UP' as const) : ('DOWN' as const);
                const legacy = snapshotToLegacyViopSnapshot(snap);
                out.push({
                    symbol,
                    category: 'FUTURES',
                    displayName: snap.displayName?.trim() || snap.contractName?.trim() || undefined,
                    name: snap.contractName?.trim() || undefined,
                    price: priceN,
                    changePercent: Number.isFinite(pct) ? pct : 0,
                    trend: trendDir,
                    metrics: {
                        basis: impliedViopBasis(legacy, priceN),
                        yield: effectiveViopCarryPercent(legacy, priceN),
                    },
                    volume: snap.volume != null ? Number(snap.volume) : Number(snap.quantity ?? 0),
                });
            }
            return out.sort((a, b) => Math.abs(b.changePercent) - Math.abs(a.changePercent));
        }
        if (cat === 'FUNDS' && fundSub === 'TR') {
            if (pickerMatchesChart && (terminalListPageData?.items?.length ?? 0) > 0) {
                return terminalListPageData!.items
                    .filter((row) => row.category === 'FUNDS' && row.fundSubmarket === 'TR')
                    .map((row) => {
                        const vm = terminalListItemToVm(row);
                        return {
                            symbol: normalizeSymbolKey(vm.symbol),
                            category: 'FUNDS' as const,
                            displayName: vm.displayName,
                            price: vm.price,
                            changePercent: vm.changePercent,
                            dailyChangePercent: vm.dailyChangePercent ?? vm.pctDay ?? vm.changePercent,
                            trend: vm.trend,
                            currency: vm.currency ?? 'TRY',
                            marketRegion: vm.marketRegion ?? 'TR',
                            exchange: vm.exchange ?? 'TEFAS',
                            sector: vm.sector ?? undefined,
                            pctDay: vm.pctDay,
                            pctWeek: vm.pctWeek,
                            pctMonth: vm.pctMonth,
                            pctYear: vm.pctYear,
                            fundSubmarket: 'TR' as const,
                            fundRiskLevel: vm.fundRiskLevel,
                            fundReturn3m: vm.fundReturn3m,
                            fundReturn6m: vm.fundReturn6m,
                            fundReturn3y: vm.fundReturn3y,
                            fundReturn5y: vm.fundReturn5y,
                            listSubtitle: vm.listSubtitle,
                        };
                    });
            }
            return [];
        }
        if (cat === 'BOND') {
            if (pickerMatchesChart && (terminalListPageData?.items?.length ?? 0) > 0) {
                return terminalListPageData!.items
                    .filter((row) => row.category === 'BOND')
                    .map((row) => ({
                        symbol: normalizeSymbolKey(row.symbol),
                        category: 'BOND' as const,
                        displayName: row.displayName ?? row.symbol,
                        price: row.price,
                        changePercent: row.changePercent,
                        dailyChangePercent: row.dailyChangePercent ?? row.changePercent,
                        trend: row.trend,
                        volume: row.volume ?? null,
                    }));
            }
            const merged = new Map<string, DebtSnapshot>();
            debtLatest.forEach((d) => {
                const key = normalizeSymbolKey(d?.isin);
                if (!key) return;
                const prev = merged.get(key);
                if (!prev) {
                    merged.set(key, d);
                    return;
                }
                const prevTs = new Date(prev.asOf ?? 0).getTime();
                const nextTs = new Date(d.asOf ?? 0).getTime();
                if (nextTs >= prevTs) merged.set(key, d);
            });
            return [...merged.values()]
                .map((d) => {
                    const symbol = normalizeSymbolKey(d.isin);
                    const current = Number(d.dirtyPrice ?? 0);
                    return {
                        symbol,
                        category: 'BOND' as const,
                        price: current,
                        changePercent: 0,
                        trend: 'UP' as const,
                        metrics: undefined,
                        volume: d.synthetic || current <= 0 ? null : current * 100,
                    };
                })
                .sort((a, b) => b.price - a.price);
        }
        if (cat === 'EQUITY' && eqSub === 'BIST') {
            if (pickerMatchesChart && (terminalListPageData?.items?.length ?? 0) > 0) {
                return terminalListPageData!.items
                    .filter((row) => row.category === 'EQUITY' && row.equitySubmarket === 'BIST')
                    .map((row) => {
                        const vm = terminalListItemToVm(row);
                        const enriched = enrichBistInstrumentHorizons({
                            symbol: normalizeSymbolKey(vm.symbol),
                            sparkline: vm.sparkline,
                            pctDay: vm.pctDay,
                            pctWeek: vm.pctWeek,
                            pctMonth: vm.pctMonth,
                            pctYear: vm.pctYear,
                            changePercent: vm.changePercent,
                            dailyChangePercent: vm.dailyChangePercent,
                        });
                        return {
                            symbol: normalizeSymbolKey(vm.symbol),
                            category: 'EQUITY' as const,
                            displayName: vm.displayName,
                            name: vm.displayName,
                            price: vm.price,
                            changePercent: vm.changePercent,
                            dailyChangePercent: vm.dailyChangePercent ?? enriched.pctDay ?? vm.changePercent,
                            trend: vm.trend,
                            volume: vm.volume ?? null,
                            currency: vm.currency ?? 'TRY',
                            marketRegion: vm.marketRegion ?? 'TR',
                            exchange: vm.exchange ?? 'BIST',
                            sector: vm.sector ?? undefined,
                            source: vm.source ?? undefined,
                            pctDay: enriched.pctDay,
                            pctWeek: enriched.pctWeek,
                            pctMonth: enriched.pctMonth,
                            pctYear: enriched.pctYear,
                            sparkline: enriched.sparkline,
                        } as MarketInstrument;
                    });
            }
            const out: MarketInstrument[] = [];
            for (const r of bistLatest) {
                const symbol = normalizeSymbolKey(String(r.symbol ?? ''));
                if (!symbol) continue;
                const price = bistLatestPrice(r);
                if (!Number.isFinite(price) || price <= 0) continue;
                const pctRaw = r.changePercent;
                const changePercent =
                    typeof pctRaw === 'number' && Number.isFinite(pctRaw)
                        ? pctRaw
                        : parseFloat(String(pctRaw ?? '0').replace(',', '.')) || 0;
                const volRaw = r.volume;
                const volume =
                    typeof volRaw === 'number' && Number.isFinite(volRaw)
                        ? volRaw
                        : parseFloat(String(volRaw ?? '0').replace(',', '.')) || null;
                const dq = r.dataQuality != null ? String(r.dataQuality) : undefined;
                const displayName = (r.displayName && String(r.displayName).trim()) || undefined;
                const sector = (r.sector && String(r.sector).trim()) || undefined;
                out.push({
                    symbol,
                    category: 'EQUITY',
                    displayName,
                    name: displayName,
                    price,
                    changePercent,
                    dailyChangePercent: changePercent,
                    trend: changePercent >= 0 ? 'UP' : 'DOWN',
                    volume,
                    currency: 'TRY',
                    marketRegion: 'TR',
                    exchange: 'BIST',
                    sector,
                    source: r.source != null ? String(r.source) : undefined,
                    ...(dq ? { dataQuality: dq } : {}),
                });
            }
            return out.sort((a, b) => Math.abs(b.changePercent) - Math.abs(a.changePercent));
        }
        if (!dashboard) return [];
        const latestMap =
            cat === 'EQUITY'
                ? dashboard.latest.stocks
                : cat === 'CRYPTO'
                ? dashboard.latest.crypto
                : cat === 'FX'
                ? dashboard.latest.doviz
                : cat === 'METALS'
                ? dashboard.latest.metals
                : dashboard.latest.funds;
        const assetClass = heatAssetClassForCategory(cat);
        if (!assetClass) return [];
        const unique = new Map<string, MarketInstrument>();

        if (cat === 'METALS') {
            const lm = latestMap as Record<string, LatestPriceRow | undefined>;
            for (const rawSymbol of preciousMetalSymbolsForSubmarket(metalsSub)) {
                const row = lm[rawSymbol];
                if (!row || row.status === 'NO_DATA') continue;
                const symbol = normalizeSymbolKey(rawSymbol);
                if (!symbol) continue;
                const spark = sparklineClosesForStrict(dashboard, symbol, assetClass);
                const tile = findHeatmapTile(dashboard, symbol, assetClass);
                const price = pickPrice(row);
                if (!Number.isFinite(price) || price <= 0) continue;
                const volume = resolveListVolume(dashboard, symbol, assetClass, price, tile);
                const tileChange = tile?.changePercent;
                const changePercent = resolveChangePercent(spark, tileChange);
                const dailyChangePercent = dailyChangePercentForListedAsset(
                    cat,
                    tileChange,
                    spark,
                    changePercent,
                );
                const r = row as Record<string, unknown>;
                const apiCurrency = [r.currency, r.priceCurrency, r.quoteCurrency].find(
                    (x): x is string => typeof x === 'string' && x.trim().length > 0
                );
                const apiName = typeof r.name === 'string' && r.name.trim().length > 0 ? r.name.trim() : undefined;
                const apiRegion =
                    typeof r.marketRegion === 'string' && r.marketRegion.trim().length > 0
                        ? r.marketRegion.trim()
                        : undefined;
                const apiExchange =
                    typeof r.exchange === 'string' && r.exchange.trim().length > 0 ? r.exchange.trim() : undefined;
                const apiSector =
                    typeof r.sector === 'string' && r.sector.trim().length > 0 ? r.sector.trim() : undefined;
                const apiSource =
                    typeof r.source === 'string' && r.source.trim().length > 0 ? r.source.trim() : undefined;
                const delayRaw = r.delayMinutes;
                const apiDelay =
                    typeof delayRaw === 'number' && Number.isFinite(delayRaw)
                        ? delayRaw
                        : typeof delayRaw === 'string' && /^\d+$/.test(delayRaw.trim())
                          ? Number(delayRaw.trim())
                          : undefined;
                const meta = getPreciousMetalDisplayMeta(symbol);
                const mergedSource =
                    isUsdPerOunceMetalSymbol(symbol) && !apiSource?.trim() ? meta?.sourceLabel : apiSource;
                unique.set(symbol, {
                    symbol,
                    category: cat,
                    price,
                    changePercent,
                    dailyChangePercent,
                    trend: changePercent >= 0 ? 'UP' : 'DOWN',
                    volume,
                    ...(meta?.displayName ? { displayName: meta.displayName } : {}),
                    ...(apiCurrency ? { currency: apiCurrency.trim() } : {}),
                    ...(isUsdPerOunceMetalSymbol(symbol) && !apiCurrency ? { currency: 'USD' } : {}),
                    ...(apiName ? { name: apiName } : {}),
                    ...(apiRegion ? { marketRegion: apiRegion } : {}),
                    ...(apiExchange ? { exchange: apiExchange } : {}),
                    ...(apiSector ? { sector: apiSector } : {}),
                    ...(mergedSource ? { source: String(mergedSource).trim() } : {}),
                    ...(apiDelay != null ? { delayMinutes: apiDelay } : {}),
                });
            }
            return [...unique.values()];
        }

        Object.entries(latestMap ?? {})
            .filter(([, row]) => row && row.status !== 'NO_DATA')
            .forEach(([rawSymbol, row]) => {
                const symbol = normalizeSymbolKey(rawSymbol);
                if (!symbol) return;
                const spark = sparklineClosesFor(dashboard, symbol, assetClass);
                const tile = findHeatmapTile(dashboard, symbol, assetClass);
                const price = pickPrice(row);
                // Piyasa listesinde veri üretmeyen/0 fiyatlı satırları (örn. EEM-) gizle.
                if (!Number.isFinite(price) || price <= 0) return;
                const volume = resolveListVolume(dashboard, symbol, assetClass, price, tile);
                const tileChange = tile?.changePercent;
                const changePercent = resolveChangePercent(spark, tileChange);
                const dailyChangePercent = dailyChangePercentForListedAsset(
                    cat,
                    tileChange,
                    spark,
                    changePercent,
                );
                const r = row as Record<string, unknown>;
                const apiCurrency = [r.currency, r.priceCurrency, r.quoteCurrency].find(
                    (x): x is string => typeof x === 'string' && x.trim().length > 0
                );
                const apiName = typeof r.name === 'string' && r.name.trim().length > 0 ? r.name.trim() : undefined;
                const apiRegion =
                    typeof r.marketRegion === 'string' && r.marketRegion.trim().length > 0
                        ? r.marketRegion.trim()
                        : undefined;
                const apiExchange =
                    typeof r.exchange === 'string' && r.exchange.trim().length > 0 ? r.exchange.trim() : undefined;
                const apiSector =
                    typeof r.sector === 'string' && r.sector.trim().length > 0 ? r.sector.trim() : undefined;
                const apiSource =
                    typeof r.source === 'string' && r.source.trim().length > 0 ? r.source.trim() : undefined;
                const delayRaw = r.delayMinutes;
                const apiDelay =
                    typeof delayRaw === 'number' && Number.isFinite(delayRaw)
                        ? delayRaw
                        : typeof delayRaw === 'string' && /^\d+$/.test(delayRaw.trim())
                          ? Number(delayRaw.trim())
                          : undefined;
                unique.set(symbol, {
                    symbol,
                    category: cat,
                    price,
                    changePercent,
                    dailyChangePercent,
                    trend: changePercent >= 0 ? 'UP' : 'DOWN',
                    volume,
                    metrics: cat === 'FX' ? { basis: fxSpreadMap[symbol] ?? 0 } : undefined,
                    ...(apiCurrency ? { currency: apiCurrency.trim() } : {}),
                    ...(apiName ? { name: apiName } : {}),
                    ...(apiRegion ? { marketRegion: apiRegion } : {}),
                    ...(apiExchange ? { exchange: apiExchange } : {}),
                    ...(apiSector ? { sector: apiSector } : {}),
                    ...(apiSource ? { source: apiSource } : {}),
                    ...(apiDelay != null ? { delayMinutes: apiDelay } : {}),
                });
            });
        return [...unique.values()].sort((a, b) => Math.abs(b.changePercent) - Math.abs(a.changePercent));
        },
        [
            dashboard,
            viopSnapshotBySymbol,
            viopContractBySymbol,
            viopSnapshotSymbols,
            terminalListPageData,
            pickerMatchesChart,
            debtLatest,
            fxSpreadMap,
            bistLatest,
        ]
    );

    const instruments = useMemo(
        () => buildInstruments(activeCategory, equitySubmarket, fundSubmarket, metalsSubmarket),
        [buildInstruments, activeCategory, equitySubmarket, fundSubmarket, metalsSubmarket]
    );

    useEffect(() => {
        if (activeCategory !== 'METALS') return;
        if (symbolMatchesMetalsSubmarket(selectedSymbol, metalsSubmarket)) return;
        setSelectedSymbol(defaultMetalSymbolForSubmarket(metalsSubmarket));
        setCompareSymbols([]);
    }, [activeCategory, metalsSubmarket, selectedSymbol]);
    useEffect(() => {
        if (activeCategory === 'FUTURES' || activeCategory === 'BOND') {
            const stillExists = instruments.some((i) =>
                activeCategory === 'FUTURES'
                    ? viopSymbolKeysEquivalent(i.symbol, selectedSymbol)
                    : normalizeSymbolKey(i.symbol) === normalizeSymbolKey(selectedSymbol),
            );
            const pickDefault = () => {
                if (activeCategory === 'FUTURES') {
                    return (
                        instruments.find(
                            (i) =>
                                resolveViopHistoryContract(
                                    i.symbol,
                                    viopContractBySymbol,
                                    viopSnapshotBySymbol,
                                ) != null,
                        ) ?? instruments[0]
                    );
                }
                return instruments[0];
            };
            if (pickerMatchesChart && bootstrapSymbolFromList) {
                if (!selectedSymbol || !stillExists) {
                    const next = bootstrapSymbolFromList;
                    setSelectedSymbol((prev) =>
                        normalizeSymbolKey(prev) === normalizeSymbolKey(next) ? prev : next,
                    );
                }
                return;
            }
            if (!instruments.length) return;
            if (!selectedSymbol || !stillExists) {
                const next = pickDefault().symbol;
                setSelectedSymbol((prev) =>
                    normalizeSymbolKey(prev) === normalizeSymbolKey(next) ? prev : next,
                );
            }
            return;
        }
        if (!instruments.length) return;
        const stillExists = instruments.some((i) => i.symbol === selectedSymbol);
        if (!selectedSymbol || !stillExists) {
            const next = instruments[0].symbol;
            setSelectedSymbol((prev) => (prev === next ? prev : next));
        }
    }, [instruments, selectedSymbol, activeCategory, pickerMatchesChart, bootstrapSymbolFromList, viopContractBySymbol, viopSnapshotBySymbol]);

    useEffect(() => {
        setCompareSymbols([]);
        setCompareRows([]);
        setSearchTerm('');
        setIsDetailPanelOpen(false);
        setShowUsdInTry(false);
        setSelectedSymbol('');
    }, [activeCategory]);

    useEffect(() => {
        if (activeCategory !== 'EQUITY') return;
        setCompareSymbols([]);
        setCompareRows([]);
    }, [equitySubmarket, activeCategory]);

    useEffect(() => {
        if (activeCategory !== 'EQUITY' || equitySubmarket !== 'BIST') return;
        if (!bistLatest.length) return;
        const keys = new Set(bistLatest.map((r) => normalizeSymbolKey(String(r.symbol ?? ''))));
        const sel = normalizeSymbolKey(selectedSymbol);
        if (sel && keys.has(sel)) return;
        const preferred = keys.has('THYAO') ? 'THYAO' : String(bistLatest[0].symbol ?? '');
        setSelectedSymbol(normalizeSymbolKey(preferred));
    }, [activeCategory, equitySubmarket, bistLatest, selectedSymbol]);

    useEffect(() => {
        if (activeCategory !== 'FUTURES') return;
        setCompareSymbols((prev) => {
            const valid = prev.filter((s) =>
                instruments.some((i) => viopSymbolKeysEquivalent(i.symbol, s)),
            );
            if (valid.length === prev.length && valid.every((s, i) => s === prev[i])) return prev;
            return valid;
        });
    }, [activeCategory, instruments]);

    useEffect(() => {
        if (!isDetailPanelOpen) return;
        const onKeyDown = (event: KeyboardEvent) => {
            if (event.key === 'Escape') setIsDetailPanelOpen(false);
        };
        window.addEventListener('keydown', onKeyDown);
        return () => window.removeEventListener('keydown', onKeyDown);
    }, [isDetailPanelOpen]);

    useEffect(() => {
        if (activeCategory === 'FUTURES') {
            setPriceFlash({});
            return;
        }
        if (!instruments.length) return;
        const nextFlash: Record<string, 'up' | 'down'> = {};
        for (const row of instruments) {
            const prev = prevPricesRef.current[row.symbol];
            if (prev != null && prev !== row.price) {
                nextFlash[row.symbol] = row.price > prev ? 'up' : 'down';
            }
            prevPricesRef.current[row.symbol] = row.price;
        }
        if (Object.keys(nextFlash).length > 0) {
            setPriceFlash(nextFlash);
            const timer = setTimeout(() => setPriceFlash({}), 260);
            return () => clearTimeout(timer);
        }
    }, [instruments, activeCategory]);

    useEffect(() => {
        if (activeCategory === 'METALS' && prevTerminalCategoryRef.current !== 'METALS') {
            setRange('2Y');
        }
        prevTerminalCategoryRef.current = activeCategory;
    }, [activeCategory]);

    const rawChartDays =
        activeCategory === 'EQUITY' && equitySubmarket === 'BIST'
            ? bistMainChartDays
            : RANGE_TO_DAYS[dataRangeForChart];
    const days = snapMarketHistoryDays(rawChartDays);
    /** Tahvil grafiği: DB’de tarih filtreli sorgu; 2Y için üst sınır performans. */
    const bondHistoryDays =
        activeCategory === 'BOND' ? Math.min(RANGE_TO_DAYS[dataRangeForChart], 400) : days;
    const marketType =
        activeCategory === 'EQUITY'
            ? 'EQUITY'
            : activeCategory === 'CRYPTO'
            ? 'CRYPTO'
            : activeCategory === 'FX'
            ? 'FX'
            : activeCategory === 'METALS'
            ? 'METALS'
            : activeCategory === 'FUNDS'
            ? 'FUNDS'
            : null;

    const metalsApiSymbol =
        activeCategory === 'METALS'
            ? normalizeSymbolKey(selectedSymbol) === 'ALTIN_TRY'
                ? 'XAU_TRY'
                : normalizeSymbolKey(selectedSymbol) || selectedSymbol
            : selectedSymbol;

    const selectedSymbolReady = useMemo(() => {
        if (!selectedSymbol) return false;
        if (activeCategory === 'FUTURES') {
            return resolveViopHistoryContract(selectedSymbol, viopContractBySymbol, viopSnapshotBySymbol) != null;
        }
        if (!instruments.length) return false;
        return instruments.some(
            (i) => normalizeSymbolKey(i.symbol) === normalizeSymbolKey(selectedSymbol),
        );
    }, [selectedSymbol, activeCategory, instruments, viopContractBySymbol, viopSnapshotBySymbol]);

    const viopHistoryContract = useMemo(
        () =>
            activeCategory === 'FUTURES' && selectedSymbol
                ? resolveViopHistoryContract(selectedSymbol, viopContractBySymbol, viopSnapshotBySymbol)
                : null,
        [activeCategory, selectedSymbol, viopContractBySymbol, viopSnapshotBySymbol],
    );

    const chartIndicatorsEnabled =
        selectedSymbolReady &&
        Boolean(selectedSymbol && marketType) &&
        !(activeCategory === 'EQUITY' && equitySubmarket === 'BIST') &&
        !(activeCategory === 'FUNDS' && fundSubmarket === 'TR') &&
        (showMa || showRsi || spotChartMode === 'CANDLE');

    const macroSidebarQueriesEnabled = Boolean(selectedSymbol);

    /** Hisse / döviz / kripto / altın: yalnızca 1G için saatlik; 1H = son 7 takvim günü günlük mum (1A ile aynı bucket). */
    const terminalHourlyRange =
        ((activeCategory === 'EQUITY' && equitySubmarket !== 'BIST') ||
            activeCategory === 'FX' ||
            activeCategory === 'CRYPTO' ||
            activeCategory === 'METALS') &&
        range === '1D';

    const { data: indicatorData, isLoading: loadingIndicators } = useQuery({
        queryKey: ['market', 'indicators', 'terminal', activeCategory, selectedSymbol, days, terminalHourlyRange ? `hourly:${range}` : 'daily'],
        enabled: chartIndicatorsEnabled,
        queryFn: ({ signal }) =>
            marketClient
                .get<IndicatorsResponse>('/api/market/indicators', {
                    params: {
                        type: marketType,
                        symbol: activeCategory === 'METALS' ? metalsApiSymbol : selectedSymbol,
                        days,
                        ma: '7,21',
                        ...(terminalHourlyRange ? { bucket: 'hourly' } : {}),
                    },
                    signal,
                })
                .then((r) => r.data),
        refetchInterval: chartIndicatorsEnabled ? 120_000 : false,
        refetchOnWindowFocus: false,
    });

    const { data: batchData, isLoading: loadingCandles } = useQuery({
        queryKey: ['market', 'candles', 'terminal', activeCategory, selectedSymbol, days, terminalHourlyRange ? `hourly:${range}` : 'daily'],
        enabled:
            selectedSymbolReady &&
            Boolean(selectedSymbol && marketType) &&
            !(activeCategory === 'EQUITY' && equitySubmarket === 'BIST') &&
            !(activeCategory === 'FUNDS' && fundSubmarket === 'TR'),
        queryFn: async ({ signal }) => {
            if (activeCategory === 'METALS' && !terminalHourlyRange) {
                const symbolCandidates = [selectedSymbol, selectedSymbol === 'ALTIN_TRY' ? 'XAU_TRY' : 'ALTIN_TRY']
                    .map((s) => normalizeSymbolKey(s))
                    .filter(Boolean);
                for (const symbol of symbolCandidates) {
                    try {
                        const rows = await marketClient
                            .get<MarketHistoryPoint[]>('/api/market/metals/history', {
                                params: metalsHistoryQueryParams(symbol, days),
                                signal,
                            })
                            .then((r) => r.data);
                        const candlesFromHistory = historyRowsToSyntheticCandles(rows ?? []);
                        if (candlesFromHistory.length > 0) {
                            return {
                                series: {
                                    [selectedSymbol]: candlesFromHistory,
                                },
                            } satisfies BatchHistoryResponse;
                        }
                    } catch {
                        // try next alias
                    }
                }
            }
            const batchSymbol = activeCategory === 'METALS' ? metalsApiSymbol : selectedSymbol;
            const batch = await marketClient
                .get<BatchHistoryResponse>('/api/market/history/batch', {
                    params: {
                        type: marketType,
                        symbols: batchSymbol,
                        days,
                        ...(terminalHourlyRange ? { bucket: 'hourly' } : {}),
                    },
                    signal,
                })
                .then((r) => r.data);
            if (activeCategory === 'METALS' && batchSymbol !== selectedSymbol) {
                const sMetal = batch?.series?.[batchSymbol] ?? [];
                if (sMetal.length > 0) {
                    return {
                        series: {
                            ...(batch?.series ?? {}),
                            [selectedSymbol]: sMetal,
                        },
                    } satisfies BatchHistoryResponse;
                }
            }
            const existing = batch?.series?.[selectedSymbol] ?? batch?.series?.[batchSymbol] ?? [];
            const shouldTryAltHistory =
                Boolean(selectedSymbol) && (activeCategory === 'METALS' || activeCategory === 'FUNDS');
            if (!shouldTryAltHistory) {
                return batch;
            }
            const historyPath = activeCategory === 'METALS' ? '/api/market/metals/history' : '/api/market/funds/history';
            const historySymbol =
                activeCategory === 'METALS'
                    ? (selectedSymbol === 'ALTIN_TRY' ? 'XAU_TRY' : selectedSymbol)
                    : selectedSymbol;
            try {
                const rows = await marketClient
                    .get<MarketHistoryPoint[]>(historyPath, {
                        params: metalsHistoryQueryParams(historySymbol, days),
                        signal,
                    })
                    .then((r) => r.data);
                const fallbackCandles = historyRowsToSyntheticCandles(rows ?? []);
                const existingFlat =
                    existing.length > 0 &&
                    existing.every((c) => Number(c.o) === Number(c.h) && Number(c.h) === Number(c.l) && Number(c.l) === Number(c.c));
                const shouldReplaceWithFallback =
                    fallbackCandles.length > 0 &&
                    (
                        fallbackCandles.length > existing.length ||
                        existing.length < 2 ||
                        (activeCategory === 'METALS' && existingFlat)
                    );
                if (!shouldReplaceWithFallback) {
                    return batch;
                }
                return {
                    series: {
                        ...(batch?.series ?? {}),
                        [selectedSymbol]: fallbackCandles,
                    },
                } satisfies BatchHistoryResponse;
            } catch {
                return batch;
            }
        },
        // Günlük ingest / stale-tail onarımı sonrası grafik güncellensin (BIST ayrı sorgu kullanır).
        refetchInterval:
            selectedSymbolReady &&
            Boolean(selectedSymbol && marketType) &&
            !(activeCategory === 'EQUITY' && equitySubmarket === 'BIST')
                ? 90_000
                : false,
        refetchOnWindowFocus:
            selectedSymbolReady &&
            Boolean(selectedSymbol && marketType) &&
            !(activeCategory === 'EQUITY' && equitySubmarket === 'BIST'),
        staleTime: 90_000,
        placeholderData: (prev) => prev,
    });

    const { data: viopHistoryApi, isLoading: loadingViopHistory } = useQuery({
        queryKey: ['market', 'viop-history-api', viopHistoryContract, days],
        enabled: activeCategory === 'FUTURES' && Boolean(viopHistoryContract),
        queryFn: async ({ signal }) => {
            const now = new Date();
            const fromMs = now.getTime() - Math.max(1, days) * 86_400_000;
            const from = formatEuropeIstanbulLocalIso(new Date(fromMs));
            const to = formatEuropeIstanbulLocalIso(now);
            const sym = viopHistoryContract ?? '';
            const { data } = await marketClient.get<ViopHistoryApi>(
                `/api/market/viop/contracts/${encodeURIComponent(sym)}/history`,
                { params: { from, to, period: 60 }, signal }
            );
            return data;
        },
        refetchInterval: activeCategory === 'FUTURES' && Boolean(viopHistoryContract) ? 60_000 : false,
        refetchOnWindowFocus: activeCategory === 'FUTURES' && Boolean(viopHistoryContract),
        staleTime: 45_000,
    });
    const { data: debtHistory = [], isLoading: loadingDebtHistory } = useQuery({
        queryKey: ['market', 'debt-history', selectedSymbol, bondHistoryDays],
        enabled: isBondInstrumentsView && Boolean(selectedSymbol),
        queryFn: ({ signal }) =>
            marketClient
                .get<DebtSnapshot[]>('/api/market/debt/history', {
                    params: { isin: selectedSymbol, days: bondHistoryDays },
                    signal,
                })
                .then((r) => r.data),
        refetchInterval: isBondInstrumentsView && Boolean(selectedSymbol) ? 60_000 : false,
        refetchOnWindowFocus: isBondInstrumentsView && Boolean(selectedSymbol),
        staleTime: 90_000,
        placeholderData: (prev) => prev,
    });

    const candles = useMemo(() => {
        if (activeCategory === 'FUTURES') {
            const pts = viopHistoryApi?.points ?? [];
            const sorted = [...pts]
                .filter((p) => Number(p.price) > 0)
                .sort((a, b) => new Date(a.time).getTime() - new Date(b.time).getTime());
            return sorted.map((p, idx) => {
                const close = Number(p.price);
                const prev = idx > 0 ? Number(sorted[idx - 1]?.price ?? close) : close;
                return {
                    time: p.time,
                    open: prev,
                    high: Math.max(prev, close),
                    low: Math.min(prev, close),
                    close,
                    volume: 0,
                };
            });
        }
        if (isBondInstrumentsView) {
            const sorted = [...debtHistory].sort((a, b) => new Date(a.asOf ?? 0).getTime() - new Date(b.asOf ?? 0).getTime());
            const raw = sorted.map((x, idx) => {
                const close = Number(x.dirtyPrice ?? 0);
                const prev = idx > 0 ? Number(sorted[idx - 1]?.dirtyPrice ?? close) : close;
                return {
                    time: x.asOf ?? new Date().toISOString(),
                    open: prev,
                    high: Math.max(prev, close),
                    low: Math.min(prev, close),
                    close,
                    volume: close > 0 ? close * 100 : 0,
                };
            });
            return raw;
        }
        if (activeCategory === 'EQUITY' && equitySubmarket === 'BIST') {
            const sorted = [...bistCandles].sort((a, b) => String(a.date ?? '').localeCompare(String(b.date ?? '')));
            return sorted.map((row) => {
                const close = bistPickClose(row);
                const o = Number(row.open);
                const open = Number.isFinite(o) && o > 0 ? o : close;
                const hRaw = Number(row.high);
                const lRaw = Number(row.low);
                const high = Number.isFinite(hRaw) && hRaw > 0 ? hRaw : Math.max(open, close);
                const low = Number.isFinite(lRaw) && lRaw > 0 ? lRaw : Math.min(open, close);
                const vRaw = Number(row.volume);
                const volume = Number.isFinite(vRaw) ? vRaw : 0;
                const d = String(row.date ?? '').slice(0, 10);
                const time = d.length === 10 ? `${d}T12:00:00` : new Date().toISOString();
                return { time, open, high, low, close, volume };
            });
        }
        if (activeCategory === 'FUNDS' && fundSubmarket === 'TR') {
            const mapped = [...tefasHistory]
                .filter((r) => r.date && Number.isFinite(Number(r.price)) && Number(r.price) > 0)
                .sort((a, b) => String(a.date).localeCompare(String(b.date)))
                .map((row, idx, arr) => {
                    const close = Number(row.price);
                    const prevClose = idx > 0 ? Number(arr[idx - 1]?.price ?? close) : close;
                    const open = Number.isFinite(prevClose) && prevClose > 0 ? prevClose : close;
                    const d = String(row.date ?? '').slice(0, 10);
                    const time = d.length === 10 ? `${d}T12:00:00` : new Date().toISOString();
                    return {
                        time,
                        open,
                        high: Math.max(open, close),
                        low: Math.min(open, close),
                        close,
                        volume: 0,
                    };
                });
            return sliceCandlesToChartRange(mapped, dataRangeForChart);
        }
        const series = batchData?.series?.[selectedSymbol] ?? [];
        if (series.length > 0) {
        if (activeCategory === 'FUNDS') {
                const sorted = [...series].sort((a, b) => new Date(a.t).getTime() - new Date(b.t).getTime());
                return sorted.map((x, idx) => {
                    const close = Number(x.c ?? x.o ?? x.h ?? x.l ?? 0);
                    const prevClose = idx > 0 ? Number(sorted[idx - 1]?.c ?? close) : Number(x.o ?? close);
                    const open = Number.isFinite(prevClose) && prevClose > 0 ? prevClose : close;
                    const high = Number(x.h ?? Math.max(open, close));
                    const low = Number(x.l ?? Math.min(open, close));
                    return {
                        time: x.t,
                        open,
                        high: Number.isFinite(high) && high > 0 ? high : Math.max(open, close),
                        low: Number.isFinite(low) && low > 0 ? low : Math.min(open, close),
                        close,
                        volume: Number(x.v ?? 0),
                    };
                });
            }
            return series.map((x) => ({
                time: x.t,
                open: Number(x.o),
                high: Number(x.h),
                low: Number(x.l),
                close: Number(x.c),
                volume: Number(x.v ?? 0),
            }));
        }
        const closes = indicatorData?.close ?? [];
        return closes.map((p, idx) => {
            const close = Number(p.value);
            const prev = idx > 0 ? Number(closes[idx - 1]?.value ?? close) : close;
            return { time: p.t, open: prev, high: Math.max(prev, close), low: Math.min(prev, close), close, volume: 0 };
        });
    }, [
        activeCategory,
        equitySubmarket,
        fundSubmarket,
        viopHistoryApi,
        debtHistory,
        batchData,
        indicatorData,
        selectedSymbol,
        terminalHourlyRange,
        days,
        bistCandles,
        tefasHistory,
        dataRangeForChart,
    ]);
    const bondDualPoints = useMemo(() => {
        if (!isBondInstrumentsView) return [];
        const sorted = [...debtHistory].sort((a, b) => new Date(a.asOf ?? 0).getTime() - new Date(b.asOf ?? 0).getTime());
        const mapped = sorted
            .map((x) => ({
                time: x.asOf ?? new Date().toISOString(),
                price: Number(x.dirtyPrice ?? 0),
                yieldPct: 0,
                volume: Number(x.dirtyPrice ?? 0) > 0 ? Number(x.dirtyPrice ?? 0) * 100 : 0,
            }))
            .filter((x) => Number.isFinite(x.price) && x.price > 0);
        const maxChartPoints = 360;
        if (mapped.length <= maxChartPoints) return mapped;
        const step = Math.ceil(mapped.length / maxChartPoints);
        const out = [];
        for (let i = 0; i < mapped.length; i += step) out.push(mapped[i]);
        const last = mapped[mapped.length - 1];
        if (out[out.length - 1] !== last) out.push(last);
        return out;
    }, [isBondInstrumentsView, debtHistory]);
    const viopLinePoints = useMemo(() => {
        if (activeCategory !== 'FUTURES') return [];
        const pts = viopHistoryApi?.points ?? [];
        const byTs = new Map<number, ViopHistoryPointApi>();
        pts.forEach((p) => {
            const priceN = Number(p.price ?? 0);
            if (!(priceN > 0)) return;
            const ts = new Date(p.time).getTime();
            if (!Number.isFinite(ts) || ts <= 0) return;
            const existing = byTs.get(ts);
            if (!existing || new Date(existing.time).getTime() <= ts) {
                byTs.set(ts, p);
            }
        });
        return [...byTs.entries()]
            .sort((a, b) => a[0] - b[0])
            .map(([, p]) => ({
                time: p.time,
                price: Number(p.price),
                basis: 0,
                annualizedBasisPct: 0,
                openInterest: 0,
            }));
    }, [activeCategory, viopHistoryApi]);

    const bondChartSeriesSig = useMemo(
        () => (isBondInstrumentsView ? chartPriceSeriesSignature(bondDualPoints) : ''),
        [isBondInstrumentsView, bondDualPoints],
    );
    const viopChartSeriesSig = useMemo(
        () => (activeCategory === 'FUTURES' ? chartPriceSeriesSignature(viopLinePoints) : ''),
        [activeCategory, viopLinePoints],
    );
    const bondChartMa7 = useMemo(() => {
        if (!isBondInstrumentsView || !bondDualPoints.length) return [];
        return movingAverage(
            bondDualPoints.map((p) => ({ time: p.time, close: p.price })),
            7,
        );
    }, [isBondInstrumentsView, bondChartSeriesSig, bondDualPoints]);
    const bondChartMa21 = useMemo(() => {
        if (!isBondInstrumentsView || !bondDualPoints.length) return [];
        return movingAverage(
            bondDualPoints.map((p) => ({ time: p.time, close: p.price })),
            21,
        );
    }, [isBondInstrumentsView, bondChartSeriesSig, bondDualPoints]);
    const viopChartMa7 = useMemo(() => {
        if (activeCategory !== 'FUTURES' || !viopLinePoints.length) return [];
        return movingAverage(
            viopLinePoints.map((p) => ({ time: p.time, close: p.price })),
            7,
        );
    }, [activeCategory, viopChartSeriesSig, viopLinePoints]);
    const viopChartMa21 = useMemo(() => {
        if (activeCategory !== 'FUTURES' || !viopLinePoints.length) return [];
        return movingAverage(
            viopLinePoints.map((p) => ({ time: p.time, close: p.price })),
            21,
        );
    }, [activeCategory, viopChartSeriesSig, viopLinePoints]);

    const ma7 = useMemo(() => {
        if (indicatorData?.ma?.['7']?.length) {
            return indicatorData.ma['7'].map((p) => ({ time: p.t, value: Number(p.value) }));
        }
        return movingAverage(candles.map((c) => ({ time: c.time, close: c.close })), 7);
    }, [indicatorData, candles]);
    const ma21 = useMemo(() => {
        if (indicatorData?.ma?.['21']?.length) {
            return indicatorData.ma['21'].map((p) => ({ time: p.t, value: Number(p.value) }));
        }
        return movingAverage(candles.map((c) => ({ time: c.time, close: c.close })), 21);
    }, [indicatorData, candles]);

    const rsi14 = useMemo(
        () => buildRsi(candles.map((c) => ({ time: c.time, close: c.close }))),
        [candles]
    );

    const mapInstrumentsToVms = useCallback(
        (list: MarketInstrument[]): InstrumentVm[] =>
        list.map((ins) => {
            const symbolKey = normalizeSymbolKey(ins.symbol);
            const live = ins.category === 'FUTURES' ? null : liveOverrides[`${ins.category}:${symbolKey}`];
            const livePrice = live && live.price > 0 ? live.price : ins.price;
            const liveChange =
                live && Number.isFinite(Number(live.changePercent)) && Math.abs(Number(live.changePercent)) > 1e-9
                    ? Number(live.changePercent)
                    : ins.changePercent;
            /*
             * Live tick `changePercent` cogu varlikta broker/24s piyasa farki; bunu
             * `dailyChangePercent` uzerine yazmak 1G sutununu dashboard spark tabanli
             * ısı haritasından koparir (orn. GBPTRY liste -0.98% vs harita +0.37%).
             * EQUITY: tile zaten gercek 1G (FINHUB) — canli gunluk guncelleme mantikli.
             * FX / Kripto / Altın / Fon: 1G = spark son iki nokta; heatmap ile ayni kaynak.
             */
            const liveDailyChange =
                ins.category === 'EQUITY' &&
                live &&
                Number.isFinite(Number(live.changePercent)) &&
                Math.abs(Number(live.changePercent)) > 1e-9
                    ? Number(live.changePercent)
                    : ins.dailyChangePercent ?? ins.changePercent;
            const baseVolume = ins.volume != null && Number.isFinite(Number(ins.volume)) ? Number(ins.volume) : null;
            const liveVolume =
                live && Number.isFinite(Number(live.volume)) && Number(live.volume) > 0
                    ? Number(live.volume)
                    : baseVolume;
            if (ins.category === 'BOND') {
                const debtMeta = debtMetaMap[symbolKey];
                const latestDebt = debtLatestMap[symbolKey];
                const fromIsin = extractMaturityDate(symbolKey);
                const fromApi =
                    latestDebt?.maturityDate ?? debtMeta?.maturityDate ?? (ins as InstrumentVm).maturityDate;
                const remainingFromIsin = getRemainingDays(symbolKey);
                const remainingDays =
                    latestDebt?.daysToMaturity ??
                    (Number.isFinite(remainingFromIsin) ? remainingFromIsin : undefined);
                const sparkFromList = ((ins as InstrumentVm).sparkline ?? [])
                    .map((p) => Number(p))
                    .filter((p) => Number.isFinite(p) && p > 0);
                const sparkFromHistory =
                    symbolKey === normalizeSymbolKey(selectedSymbol)
                        ? [...debtHistory]
                              .sort((a, b) => new Date(a.asOf ?? 0).getTime() - new Date(b.asOf ?? 0).getTime())
                              .map((row) => Number(row?.dirtyPrice))
                              .filter((p) => Number.isFinite(p) && p > 0)
                        : [];
                const sparkBase = sparkFromHistory.length >= 2 ? sparkFromHistory : sparkFromList;
                const hzFromSpark = horizonPctsFromSparkCloses(sparkBase, finiteHorizonPct(liveChange), 'BOND');
                const vmIns = ins as InstrumentVm;
                const hzBond: InstrumentHorizonPcts = {
                    pctDay: finiteHorizonPct(vmIns.pctDay) ?? hzFromSpark.pctDay,
                    pctWeek: finiteHorizonPct(vmIns.pctWeek) ?? hzFromSpark.pctWeek,
                    pctMonth: finiteHorizonPct(vmIns.pctMonth) ?? hzFromSpark.pctMonth,
                    pctYear: finiteHorizonPct(vmIns.pctYear) ?? hzFromSpark.pctYear,
                };
                const sparkPoints = Math.min(Math.max(sparkBase.length, 2), 90);
                return {
                    ...ins,
                    price: livePrice,
                    changePercent: liveChange,
                    volume: liveVolume,
                    type: 'BOND',
                    displayName: formatBondDisplayName(symbolKey),
                    sparkline: normalizeTrendSparkline(sparkBase, livePrice, liveChange, sparkPoints),
                    maturityDate: fromApi ?? (fromIsin ? fromIsin.toISOString() : undefined),
                    daysToMaturity: remainingDays,
                    couponRate:
                        latestDebt?.couponRate != null && Number.isFinite(Number(latestDebt.couponRate))
                            ? Number(latestDebt.couponRate)
                            : undefined,
                    couponFrequencyLabel:
                        latestDebt?.couponFrequencyLabel?.trim() ||
                        debtMeta?.couponFrequencyLabel?.trim() ||
                        undefined,
                    yieldToMaturity: pickStructuredYieldDecimal(latestDebt),
                    longShort: 'NÖTR',
                    ...hzBond,
                };
            }
            if (ins.category === 'FUTURES') {
                const contractLabel = parseViopContractLabel(symbolKey);
                const latestViop = viopLatestMap[symbolKey];
                const snap = viopSnapshotBySymbol[symbolKey];
                const viopSpark = latestViop?.sparklineCloses;
                const sparkFromDb = Array.isArray(viopSpark)
                    ? viopSpark.map((x) => Number(x)).filter((x) => Number.isFinite(x) && x > 0)
                    : [];
                const basis = Number(ins.metrics?.basis ?? 0);
                const readable = getInstrumentDisplayName({
                    symbol: symbolKey,
                    displayName: snap?.displayName ?? ins.displayName,
                    name: snap?.contractName ?? ins.name,
                    instrumentName: ins.instrumentName,
                });
                const displayName = readable !== symbolKey ? readable : contractLabel;
                const ctr = viopContractBySymbol[symbolKey];
                const lastPx = Number(snap?.last ?? 0);
                const hzFut: InstrumentHorizonPcts = {
                    pctDay:
                        finiteHorizonPct(snap?.changePercent) ??
                        finiteHorizonPct(ctr?.listPctChange1d) ??
                        approxPctBySpan(sparkFromDb, 1),
                    pctWeek:
                        finiteHorizonPct(ctr?.listPctChange7d) ??
                        viopPctVersusReference(lastPx, snap?.weekClose) ??
                        approxPctBySpan(sparkFromDb, 7),
                    pctMonth:
                        finiteHorizonPct(ctr?.listPctChange30d) ??
                        viopPctVersusReference(lastPx, snap?.monthClose) ??
                        approxPctBySpan(sparkFromDb, 30),
                    pctYear:
                        finiteHorizonPct(ctr?.listPctChange365d) ??
                        viopPctVersusReference(lastPx, snap?.yearClose) ??
                        viopPctVersusReference(lastPx, snap?.prevYearClose) ??
                        approxPctBySpan(sparkFromDb, 365),
                };
                return {
                    ...ins,
                    price: livePrice,
                    changePercent: liveChange,
                    volume: liveVolume,
                    type: 'FUTURES',
                    displayName,
                    sparkline: normalizeTrendSparkline(sparkFromDb, livePrice, liveChange),
                    contractMonth: latestViop?.contractMonth ?? toContractMonth(contractLabel),
                    expiryDate: latestViop?.expiryDate,
                    marginRequirement: Number(latestViop?.marginRequirement ?? (ins.price * 0.12).toFixed(2)),
                    longShort:
                        latestViop?.longShortIndicator === 'LONG'
                            ? 'LONG'
                            : latestViop?.longShortIndicator === 'SHORT'
                            ? 'SHORT'
                            : basis > 0
                            ? 'LONG'
                            : basis < 0
                            ? 'SHORT'
                            : 'NÖTR',
                    ...hzFut,
                };
            }
            if (ins.category === 'EQUITY' && ins.marketRegion === 'TR' && String(ins.exchange ?? '').toUpperCase() === 'BIST') {
                const sparkLocal =
                    (bistSparkClosesBySymbol[symbolKey]?.length ?? 0) >= 2
                        ? bistSparkClosesBySymbol[symbolKey]
                        : ((ins as InstrumentVm).sparkline ?? []);
                const displayName = getInstrumentDisplayName({
                    symbol: symbolKey,
                    displayName: ins.displayName,
                    name: ins.name,
                    instrumentName: ins.instrumentName,
                });
                const vmIns = ins as InstrumentVm;
                const enriched = enrichBistInstrumentHorizons(
                    {
                        symbol: symbolKey,
                        sparkline: sparkLocal,
                        pctDay: vmIns.pctDay,
                        pctWeek: vmIns.pctWeek,
                        pctMonth: vmIns.pctMonth,
                        pctYear: vmIns.pctYear,
                        changePercent: liveChange,
                        dailyChangePercent: ins.dailyChangePercent ?? liveChange,
                    },
                    sparkLocal,
                );
                return {
                    ...ins,
                    price: livePrice,
                    changePercent: liveChange,
                    dailyChangePercent: ins.dailyChangePercent ?? enriched.pctDay ?? liveChange,
                    volume: liveVolume,
                    type: 'STOCK',
                    displayName,
                    sparkline: normalizeTrendSparkline(enriched.sparkline, livePrice, liveChange),
                    longShort: ins.trend === 'UP' ? 'LONG' : 'SHORT',
                    pctDay: enriched.pctDay,
                    pctWeek: enriched.pctWeek,
                    pctMonth: enriched.pctMonth,
                    pctYear: enriched.pctYear,
                };
            }
            if (ins.category === 'FUNDS' && (ins as InstrumentVm).fundSubmarket === 'TR') {
                const vmFund = ins as InstrumentVm;
                const displayName =
                    (vmFund.displayName && String(vmFund.displayName).trim()) ||
                    (ins.name && String(ins.name).trim()) ||
                    symbolKey;
                return {
                    ...ins,
                    price: livePrice,
                    changePercent: liveChange,
                    dailyChangePercent: vmFund.dailyChangePercent ?? vmFund.pctDay ?? liveChange,
                    type: 'STOCK',
                    displayName,
                    sparkline: [],
                    pctDay: finiteHorizonPct(vmFund.pctDay),
                    pctWeek: finiteHorizonPct(vmFund.pctWeek),
                    pctMonth: finiteHorizonPct(vmFund.pctMonth),
                    pctYear: finiteHorizonPct(vmFund.pctYear),
                    fundReturn3m: vmFund.fundReturn3m,
                    fundReturn6m: vmFund.fundReturn6m,
                    fundReturn3y: vmFund.fundReturn3y,
                    fundReturn5y: vmFund.fundReturn5y,
                    longShort: ins.trend === 'UP' ? 'LONG' : 'SHORT',
                };
            }
            if (ins.category === 'METALS' && isPreciousMetalAllowlisted(symbolKey)) {
                const meta = getPreciousMetalDisplayMeta(symbolKey);
                const spark =
                    dashboard && heatAssetClassForCategory('METALS')
                        ? sparklineClosesForStrict(dashboard, symbolKey, 'METAL')
                        : [];
                const hzDash = horizonPctsFromSparkCloses(spark, liveDailyChange, 'METALS');
                const sparkMaxPoints = 90;
                const displayName = meta?.displayName ?? ins.displayName ?? formatAssetLabel(symbolKey, 'METALS');
                return {
                    ...ins,
                    price: livePrice,
                    changePercent: liveChange,
                    dailyChangePercent: liveDailyChange,
                    volume: liveVolume,
                    type: 'STOCK',
                    displayName,
                    listSubtitle: meta?.subtitle,
                    sparkline: normalizeTrendSparkline(spark, livePrice, liveChange, sparkMaxPoints),
                    longShort: ins.trend === 'UP' ? 'LONG' : 'SHORT',
                    ...hzDash,
                };
            }
            const ac = heatAssetClassForCategory(ins.category);
            const spark = dashboard && ac ? sparklineClosesFor(dashboard, symbolKey, ac) : [];
            const hzDash = horizonPctsFromSparkCloses(spark, liveDailyChange, ins.category);
            const sparkMaxPoints = ins.category === 'EQUITY' ? 320 : 90;
            return {
                ...ins,
                price: livePrice,
                changePercent: liveChange,
                dailyChangePercent: liveDailyChange,
                volume: liveVolume,
                type: 'STOCK',
                displayName: formatAssetLabel(symbolKey, marketKindForCategory(ins.category)),
                sparkline: normalizeTrendSparkline(spark, livePrice, liveChange, sparkMaxPoints),
                longShort: ins.trend === 'UP' ? 'LONG' : 'SHORT',
                ...hzDash,
            };
        }),
        [
            dashboard,
            debtMetaMap,
            debtLatestMap,
            debtHistory,
            selectedSymbol,
            viopLatestMap,
            viopSnapshotBySymbol,
            debtNameMap,
            liveOverrides,
            bistSparkClosesBySymbol,
            viopContractBySymbol,
        ]
    );

    const instrumentVms = useMemo(() => mapInstrumentsToVms(instruments), [mapInstrumentsToVms, instruments]);
    const selectedBondStructuredYield = useMemo(() => {
        if (!isBondInstrumentsView || !selectedSymbol) return false;
        return debtHasStructuredYieldData(debtLatestMap[normalizeSymbolKey(selectedSymbol)]);
    }, [activeCategory, selectedSymbol, debtLatestMap]);
    const pickerMarketListQuickChips = useMemo(() => {
        if (pickerCategory === 'FUTURES') {
            return [
                { id: 'ALL', label: t('market.filterAll', 'Tümü') },
                { id: 'VIOP_FX', label: t('market.filterViopFx', 'Döviz') },
                { id: 'VIOP_INDEX', label: t('market.filterViopIndex', 'Endeks') },
                { id: 'VIOP_COMMODITY', label: t('market.filterViopCommodity', 'Emtia') },
                { id: 'VIOP_EQUITY', label: t('market.filterViopEquity', 'Pay') },
            ];
        }
        return [
            { id: 'ALL', label: t('market.filterAll', 'Tümü') },
            { id: 'UP', label: t('market.filterGainers', 'Yükselenler') },
            { id: 'DOWN', label: t('market.filterLosers', 'Düşenler') },
        ];
    }, [pickerCategory, t]);

    useEffect(() => {
        const allowed =
            pickerCategory === 'FUTURES'
                ? new Set(['ALL', 'VIOP_FX', 'VIOP_INDEX', 'VIOP_COMMODITY', 'VIOP_EQUITY'])
                : new Set(['ALL', 'UP', 'DOWN']);
        if (!allowed.has(marketListQuickFilter)) {
            setMarketListQuickFilter('ALL');
        }
    }, [pickerCategory, marketListQuickFilter]);

    const pickerDisplayedInstruments = useMemo(() => {
        if (pickerCategory === 'BOND') {
            if ((terminalListPageData?.items?.length ?? 0) > 0) {
                return (terminalListPageData!.items ?? []).map(
                    (row) => terminalListItemToVm(row) as InstrumentVm,
                );
            }
            const dibInstruments = buildInstruments(
                'BOND',
                pickerEquitySubmarket,
                pickerFundSubmarket,
                pickerMetalsSubmarket,
            );
            return mapInstrumentsToVms(dibInstruments);
        }
        if (pickerCategory === 'FUTURES') {
            if ((terminalListPageData?.items?.length ?? 0) > 0) {
                return (terminalListPageData!.items ?? []).map(
                    (row) => terminalListItemToVm(row) as InstrumentVm,
                );
            }
            const viopInstruments = buildInstruments(
                'FUTURES',
                pickerEquitySubmarket,
                pickerFundSubmarket,
                pickerMetalsSubmarket,
            );
            return mapInstrumentsToVms(viopInstruments);
        }
        let rows = (pickerTerminalListPage?.items ?? []).map((row) => terminalListItemToVm(row) as InstrumentVm);
        if (pickerCategory === 'EQUITY' && pickerEquitySubmarket === 'BIST') {
            rows = rows.map((row) =>
                enrichBistInstrumentHorizons(
                    row,
                    bistSparkClosesBySymbol[normalizeSymbolKey(row.symbol)],
                ) as InstrumentVm,
            );
        }
        if (pickerCategory === 'FUNDS' && pickerFundSubmarket === 'TR' && marketListQuickFilter !== 'ALL') {
            rows = rows.filter((row) => {
                const pct = finiteHorizonPct(row.pctDay) ?? finiteHorizonPct(row.dailyChangePercent) ?? 0;
                return marketListQuickFilter === 'UP' ? pct > 0 : pct < 0;
            });
        }
        return rows;
    }, [
        pickerTerminalListPage,
        pickerCategory,
        buildInstruments,
        mapInstrumentsToVms,
        pickerEquitySubmarket,
        pickerFundSubmarket,
        pickerMetalsSubmarket,
        marketListQuickFilter,
        bistSparkClosesBySymbol,
    ]);
    const marketListTotalPages = pickerTerminalListPage?.totalPages ?? 0;
    const marketListTotalElements = pickerTerminalListPage?.totalElements ?? 0;
    const selectedInstrumentVm = useMemo(() => {
        const fromPicker =
            findInstrumentVmBySymbol(pickerDisplayedInstruments, selectedSymbol, activeCategory) ??
            pickerDisplayedInstruments[0] ??
            null;
        const fromInstruments =
            findInstrumentVmBySymbol(instrumentVms, selectedSymbol, activeCategory) ??
            (activeCategory === 'FUTURES' || activeCategory === 'BOND' ? null : instrumentVms[0] ?? null);
        if (activeCategory === 'FUTURES' || activeCategory === 'BOND') {
            return (fromPicker ?? fromInstruments) as InstrumentVm | null;
        }
        return (fromInstruments ?? fromPicker) as InstrumentVm | null;
    }, [instrumentVms, selectedSymbol, pickerDisplayedInstruments, activeCategory]);

    const chartComparisonAvailable = useMemo(
        () =>
            canShowComparisonChart(activeCategory, marketType, equitySubmarket) &&
            activeCategory !== 'BOND',
        [activeCategory, marketType, equitySubmarket],
    );

    const heroCompareExtras = useMemo(() => {
        if (!selectedSymbol || !chartComparisonAvailable) return [] as string[];
        const sk = normalizeSymbolKey(selectedSymbol);
        return compareSymbols.filter((s) => normalizeSymbolKey(s) !== sk);
    }, [compareSymbols, selectedSymbol, chartComparisonAvailable]);

    const handlePickerCompareClick = useCallback(
        (e: MouseEvent, row: InstrumentVm) => {
            e.stopPropagation();
            e.preventDefault();
            if (!chartComparisonAvailable || !selectedInstrumentVm) return;
            if (!canPickerCompareRow(row, selectedInstrumentVm, activeCategory, equitySubmarket, metalsSubmarket))
                return;
            const selKey = normalizeSymbolKey(selectedInstrumentVm.symbol);
            const symKey = normalizeSymbolKey(row.symbol);
            if (symKey === selKey) return;

            setCompareSymbols((prev) => {
                const keys = prev.map(normalizeSymbolKey);
                if (keys.includes(symKey)) {
                    const next = prev.filter((s) => normalizeSymbolKey(s) !== symKey);
                    return next.length <= 1 ? [] : next;
                }
                if (prev.length >= 4) return prev;
                const withAnchor =
                    prev.length === 0 || !keys.includes(selKey)
                        ? [selectedInstrumentVm.symbol, ...prev.filter((s) => normalizeSymbolKey(s) !== selKey)]
                        : [...prev];
                const merged = [...withAnchor];
                if (!merged.map(normalizeSymbolKey).includes(symKey)) merged.push(row.symbol);
                const seen = new Set<string>();
                const out: string[] = [];
                const push = (raw: string) => {
                    const k = normalizeSymbolKey(raw);
                    if (!k || seen.has(k) || out.length >= 4) return;
                    seen.add(k);
                    out.push(raw);
                };
                push(selectedInstrumentVm.symbol);
                for (const s of merged) {
                    if (normalizeSymbolKey(s) !== selKey) push(s);
                }
                return out;
            });
        },
        [chartComparisonAvailable, selectedInstrumentVm, activeCategory, equitySubmarket, metalsSubmarket],
    );

    const compareSymbolChipStrip =
        chartComparisonAvailable && heroCompareExtras.length > 0 ? (
            <div
                className="terminal-hero-compare-strip"
                role="list"
                aria-label={t('market.compareSelectedSymbols', 'Karşılaştırmaya eklenen semboller')}
            >
                {heroCompareExtras.map((sym) => (
                    <button
                        key={sym}
                        type="button"
                        className="terminal-hero-compare-chip"
                        role="listitem"
                        title={t('market.compareChipRemove', 'Karşılaştırmadan çıkar')}
                        onClick={(ev) => {
                            ev.preventDefault();
                            ev.stopPropagation();
                            setCompareSymbols((prev) => {
                                const next = prev.filter((s) => normalizeSymbolKey(s) !== normalizeSymbolKey(sym));
                                return next.length <= 1 ? [] : next;
                            });
                        }}
                    >
                        <span className="terminal-hero-compare-chip__sym">{sym}</span>
                        <span className="terminal-hero-compare-chip__x" aria-hidden>
                            ×
                        </span>
                    </button>
                ))}
            </div>
        ) : null;

    const tableWrapRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        setPickerTableSort(null);
    }, [pickerCategory, pickerEquitySubmarket, pickerMetalsSubmarket, searchTerm]);
    const sparklinePath = useCallback((values: number[]) => {
        if (!values.length) return '';
        const min = Math.min(...values);
        const max = Math.max(...values);
        const range = Math.max(max - min, 1e-6);
        const topPad = 2;
        const bottomPad = 2;
        const chartHeight = 24 - topPad - bottomPad;
        return values
            .map((v, i) => {
                const x = (i / Math.max(values.length - 1, 1)) * 100;
                const y = topPad + (1 - (v - min) / range) * chartHeight;
                return `${x},${y}`;
            })
            .join(' ');
    }, []);

    const hero = useMemo(() => {
        // Üst hero % / ok: TREND (1G/7G/14G) seciminde tablo + ısı haritası ile aynı
        // `effectiveChangePercent`; yalnızca uzun-pencere `changePercent` kalsa hero ile
        // liste/heatmap ayrışırdı (FX canlı 24s vs spark 1G).
        // Tahvil / VİOP: seçilen zaman dilimindeki terminal mumları (candles) ilk→son kapanış
        // — hisse ile aynı grafik-tabanlı mantık; aksi halde canlı liste %’si sabit kalırdı.
        const current = selectedInstrumentVm;
        if (!current) return null;
        const highs = candles.map((c) => c.high);
        const lows = candles.map((c) => c.low);
        const useChartPct =
            (TREND_SELECTABLE_CATEGORIES.has(current.category) ||
                current.category === 'BOND' ||
                current.category === 'FUTURES') &&
            candles.length >= 2;
        const fromChart = useChartPct ? pctChangeFromTerminalCandles(candles) : null;
        const headlineChange =
            fromChart != null && Number.isFinite(fromChart)
                ? fromChart
                : TREND_SELECTABLE_CATEGORIES.has(current.category)
                  ? effectiveChangePercent(current, trendChartRange)
                  : current.changePercent;
        return {
            ...current,
            changePercent: headlineChange,
            trend: (headlineChange >= 0 ? 'UP' : 'DOWN') as 'UP' | 'DOWN',
            high: highs.length ? Math.max(...highs) : current.price,
            low: lows.length ? Math.min(...lows) : current.price,
        };
    }, [selectedInstrumentVm, candles, trendChartRange, activeCategory]);
    const heroScaled = useMemo(() => {
        if (!hero) return null;
        const pd = terminalPriceDisplay(hero, { showUsdInTry, usdTryRate });
        const isUsdConv =
            !isUsdPerOunceMetalSymbol(hero.symbol) &&
            resolveDisplayCurrency(hero) === 'USD' &&
            showUsdInTry &&
            usdTryRate != null;
        const mult = isUsdConv ? usdTryRate : 1;
        return { ...pd, high: hero.high * mult, low: hero.low * mult };
    }, [hero, showUsdInTry, usdTryRate]);

    const macroCompareEnabled =
        isSpotMarketCategory && Boolean(selectedSymbol) && spotChartMode === 'ANALYSIS';

    const macroAssetInTry = useMemo(() => {
        const ins = selectedInstrumentVm ?? hero;
        if (!ins) return true;
        if (isUsdPerOunceMetalSymbol(ins.symbol)) return false;
        return resolveDisplayCurrency(ins) === 'TRY';
    }, [selectedInstrumentVm, hero]);

    const ppChartResetKey = `${selectedSymbol ?? ''}|${activeCategory}|${trendChartRange}`;

    useEffect(() => {
        const today = istanbulTodayYmd();
        const first = firstYmdFromCandles(candles);
        const last = lastYmdFromCandles(candles);
        ppAnchorBoundsRef.current = { first, last, today };
    }, [candles]);

    useEffect(() => {
        setPpChartAnchor('');
    }, [selectedSymbol, activeCategory]);

    useEffect(() => {
        if (!macroCompareEnabled) return;
        const { first, last, today } = ppAnchorBoundsRef.current;
        if (!first) return;
        setPpChartAnchor(clampPpAnchorYmd(first, first, last, today));
    }, [macroCompareEnabled, ppChartResetKey]);

    useEffect(() => {
        if (!macroCompareEnabled || ppChartAnchor) return;
        const { first, last, today } = ppAnchorBoundsRef.current;
        if (first) setPpChartAnchor(clampPpAnchorYmd(first, first, last, today));
    }, [macroCompareEnabled, ppChartAnchor, candles.length]);

    const isMetalsPurchasingPower = macroCompareEnabled && activeCategory === 'METALS';

    const ppUnitLabel = useMemo(
        () =>
            resolvePurchasingPowerUnitLabel(
                {
                    category: activeCategory,
                    equitySubmarket,
                    fundSubmarket,
                    metalsSubmarket,
                },
                t,
            ),
        [activeCategory, equitySubmarket, fundSubmarket, metalsSubmarket, t],
    );

    const purchasingPowerChartData = usePurchasingPowerData(
        macroCompareEnabled && !isMetalsPurchasingPower,
        trendChartRange,
        candles,
        ppChartAnchor,
        macroAssetInTry,
    );

    const preciousMetalComparisonData = usePreciousMetalComparisonData(
        isMetalsPurchasingPower,
        selectedSymbol ?? '',
        trendChartRange,
        candles,
        ppChartAnchor,
    );

    const onAnalysisCrosshairDate = useCallback((d: string) => {
        const { first, last, today } = ppAnchorBoundsRef.current;
        if (!d) {
            if (first) setPpChartAnchor(clampPpAnchorYmd(first, first, last, today));
            return;
        }
        setPpChartAnchor(clampPpAnchorYmd(d, first, last, today));
    }, []);

    const drawerInstrumentPrice = useMemo(
        () =>
            selectedInstrumentVm
                ? terminalPriceDisplay(selectedInstrumentVm, { showUsdInTry, usdTryRate })
                : null,
        [selectedInstrumentVm, showUsdInTry, usdTryRate]
    );
    const selectedInstrumentMeta = useMemo(() => {
        if (!selectedInstrumentVm) return null;
        const key = normalizeMetaKey(selectedInstrumentVm.symbol);

        if (selectedInstrumentVm.category === 'EQUITY') {
            const isBist =
                selectedInstrumentVm.marketRegion === 'TR' &&
                String(selectedInstrumentVm.exchange ?? '').toUpperCase() === 'BIST';
            if (isBist) {
                return {
                    title: t('market.drawerStockTitle', 'Hisse bilgisi'),
                    rows: [
                        ['Sembol', selectedInstrumentVm.symbol],
                        ['Şirket', selectedInstrumentVm.displayName],
                        ['Sektör', selectedInstrumentVm.sector ?? '—'],
                        ['Borsa', 'BIST'],
                    ],
                    description: t('stocks.bistFootnote', 'BIST günlük kapanış verisi (İş Yatırım HisseTekil).'),
                };
            }
            const meta = instrumentMeta[key];
            return meta
                ? {
                      title: 'Hisse Bilgisi',
                      rows: [
                          ['Şirket', meta.name],
                          ['Sektör', meta.sector],
                          ['Borsa', meta.exchange],
                          ['Ülke', meta.country ?? '—'],
                      ],
                      description: meta.description,
                  }
                : null;
        }
        if (selectedInstrumentVm.category === 'CRYPTO') {
            const meta = cryptoMeta[key];
            return meta
                ? {
                      title: 'Kripto Bilgisi',
                      rows: [
                          ['Ad', meta.name],
                          ['Sembol', meta.symbol],
                          ['Kategori', meta.category],
                      ],
                      description: meta.description,
                  }
                : null;
        }
        if (selectedInstrumentVm.category === 'FX') {
            const meta = fxMeta[key];
            return meta
                ? {
                      title: 'Döviz Paritesi',
                      rows: [
                          ['Baz Varlık', `${meta.baseName} (${meta.baseCountry})`],
                          ['Karşılık', `${meta.quoteName} (${meta.quoteCountry})`],
                      ],
                      description: meta.description,
                  }
                : null;
        }
        if (selectedInstrumentVm.category === 'METALS') {
            const symKey = normalizeSymbolKey(selectedInstrumentVm.symbol);
            const pm = getPreciousMetalDisplayMeta(symKey);
            if (pm) {
                const rows: [string, string][] = [
                    ['Ürün', pm.displayName],
                    ['Kotasyon', pm.subtitle],
                    ['Birim', pm.unitLabel],
                ];
                if (pm.sourceLabel) rows.push(['Kaynak', pm.sourceLabel]);
                if (pm.delayLabel) rows.push(['Veri', pm.delayLabel]);
                const descUsd = isUsdPerOunceMetalSymbol(symKey) ? PRECIOUS_USD_OZ_DESCRIPTION[symKey] : undefined;
                return {
                    title: 'Kıymetli maden',
                    rows,
                    description:
                        descUsd ??
                        'TL bazlı yaklaşık gram altın (CoinGecko). USD/ons kotasyonları ayrı sembollerdedir.',
                };
            }
            return {
                title: 'Kıymetli maden',
                rows: [['Ürün', selectedInstrumentVm.displayName ?? symKey]],
                description: 'Kıymetli maden kotasyonu.',
            };
        }
        if (selectedInstrumentVm.category === 'FUNDS') {
            if (rowIsTefasFundVm(selectedInstrumentVm)) {
                return {
                    title: t('funds.tefasInfoTitle', 'TEFAS Fon Bilgisi'),
                    rows: [
                        ['Kod', selectedInstrumentVm.symbol],
                        ['Fon', selectedInstrumentVm.displayName],
                        ['Tür', selectedInstrumentVm.listSubtitle ?? selectedInstrumentVm.sector ?? '—'],
                        [
                            t('funds.riskLevel', 'Risk'),
                            tefasRiskLevelDisplay(selectedInstrumentVm.fundRiskLevel),
                        ],
                        ['Kaynak', 'TEFAS'],
                    ],
                    description: t(
                        'funds.tefasFootnote',
                        'Getiri ve fiyat verileri TEFAS resmi API üzerinden alınır; geçmiş fiyatlar fon pay değeridir.',
                    ),
                };
            }
            const meta = etfMeta[key];
            return meta
                ? {
                      title: 'Fon / ETF Bilgisi',
                      rows: [
                          ['Fon', meta.name],
                          ['Kategori', meta.category],
                          ['Sağlayıcı', meta.provider],
                      ],
                      description: meta.description,
                  }
                : null;
        }
        if (selectedInstrumentVm.category === 'BOND') {
            const days = selectedInstrumentVm.daysToMaturity ?? getRemainingDays(selectedInstrumentVm.symbol);
            const meta = getBondMeta(Number.isFinite(days) ? days : 365);
            const sym = selectedInstrumentVm.symbol;
            const symKey = normalizeSymbolKey(sym);
            const debtMeta = debtMetaMap[symKey];
            const typeTr = bondTypeLabel(
                bondTypeFromInstrument(
                    sym,
                    debtMeta?.name ?? debtNameMap[symKey] ?? selectedInstrumentVm.displayName,
                    debtMeta?.issuer,
                ),
                t,
            );
            return {
                title: 'Tahvil Bilgisi',
                rows: [
                    ['İhraççı', meta.issuer],
                    ['Tür', typeTr],
                    ['Kategori', meta.category],
                    ['Risk', meta.risk],
                ],
                description: meta.description,
            };
        }
        return null;
    }, [selectedInstrumentVm, debtNameMap, debtMetaMap, t]);
    const futuresHeaderMetrics = useMemo(() => {
        if (activeCategory !== 'FUTURES' || !selectedSymbol) return null;
        const key = normalizeSymbolKey(selectedSymbol);
        const snap = viopSnapshotBySymbol[key];
        const latest = viopLatestMap[key];
        const priceN = Number(snap?.last ?? latest?.price ?? 0);
        if (!(priceN > 0)) return null;
        const legacy = latest ?? (snap ? snapshotToLegacyViopSnapshot(snap) : null);
        const displayBasis = legacy ? impliedViopBasis(legacy, priceN) : 0;
        const carry = legacy ? effectiveViopCarryPercent(legacy, priceN) : 0;
        return {
            bid: snap?.bid != null ? Number(snap.bid) : null,
            ask: snap?.ask != null ? Number(snap.ask) : null,
            changeAmount: snap?.changeAmount != null ? Number(snap.changeAmount) : null,
            changePercent: snap?.changePercent != null ? Number(snap.changePercent) : null,
            volume: snap?.volume != null ? Number(snap.volume) : null,
            quantity: snap?.quantity != null ? Number(snap.quantity) : null,
            settlement: snap?.settlement != null ? Number(snap.settlement) : null,
            preSettlement: snap?.preSettlement != null ? Number(snap.preSettlement) : null,
            initialMargin: snap?.initialMargin != null ? Number(snap.initialMargin) : null,
            limitUp: snap?.limitUp != null ? Number(snap.limitUp) : null,
            limitDown: snap?.limitDown != null ? Number(snap.limitDown) : null,
            open: snap?.open != null ? Number(snap.open) : snap?.openPrice != null ? Number(snap.openPrice) : null,
            high: snap?.high != null ? Number(snap.high) : null,
            low: snap?.low != null ? Number(snap.low) : null,
            sourceLabel: snap?.sourceLabel ?? '—',
            delayMinutes: snap?.delayMinutes ?? null,
            basis: displayBasis,
            carry: Number.isFinite(carry) ? carry : 0,
        };
    }, [activeCategory, selectedSymbol, viopSnapshotBySymbol, viopLatestMap]);

    const markers = useMemo(() => [], []);

    const heatmapSectorDisplay = useCallback((sectorKey: string) => heatmapSectorLabel(sectorKey, t), [t]);

    const insightsTreemapTiles = useMemo(() => {
        if (!dashboard) return [];
        return (dashboard.heatmapTiles ?? [])
            .filter((tile) => {
                if (activeCategory === 'EQUITY') return tile.assetClass === 'STOCK';
                if (activeCategory === 'CRYPTO') return tile.assetClass === 'CRYPTO';
                if (activeCategory === 'FX') return tile.assetClass === 'FX';
                if (activeCategory === 'METALS') {
                    return (
                        tile.assetClass === 'METAL' &&
                        symbolMatchesMetalsSubmarket(tile.symbol, metalsSubmarket)
                    );
                }
                if (activeCategory === 'FUNDS') return tile.assetClass === 'FUND';
                return false;
            })
            .map((tile) => enrichTreemapTileForChartRange(tile, dashboard, range));
    }, [dashboard, activeCategory, metalsSubmarket, range]);

    const tefasTreemapTiles = useMemo(() => {
        if (!isTefasFundsView) return [];
        const items = tefasHeatmapPage?.items ?? [];
        return buildTefasTreemapTiles(items, range);
    }, [isTefasFundsView, tefasHeatmapPage?.items, range]);

    const insightsTreemapTilesEffective = useMemo(() => {
        if (isTefasFundsView) return tefasTreemapTiles;
        if (activeCategory === 'EQUITY' && equitySubmarket === 'BIST') return bistTreemapTiles;
        return insightsTreemapTiles;
    }, [
        isTefasFundsView,
        tefasTreemapTiles,
        activeCategory,
        equitySubmarket,
        bistTreemapTiles,
        insightsTreemapTiles,
    ]);

    const futuresTopMovers = useMemo(
        () => [...instruments].sort((a, b) => Math.abs(b.changePercent) - Math.abs(a.changePercent)).slice(0, 5),
        [instruments]
    );
    const futuresVolumeLeaders = useMemo(() => {
        if (activeCategory !== 'FUTURES') return [] as { symbol: string; volume: number }[];
        return instruments
            .map((ins) => {
                const snap = viopSnapshotBySymbol[normalizeSymbolKey(ins.symbol)];
                const vol = snap?.volume != null ? Number(snap.volume) : 0;
                return { symbol: ins.symbol, volume: vol };
            })
            .filter((x) => x.volume > 0)
            .sort((a, b) => b.volume - a.volume)
            .slice(0, 5);
    }, [activeCategory, instruments, viopSnapshotBySymbol]);

    const futuresTopPositive = useMemo(() => {
        if (activeCategory !== 'FUTURES') return [];
        return [...instruments].sort((a, b) => b.changePercent - a.changePercent).slice(0, 4);
    }, [activeCategory, instruments]);

    const futuresTopNegative = useMemo(() => {
        if (activeCategory !== 'FUTURES') return [];
        return [...instruments].sort((a, b) => a.changePercent - b.changePercent).slice(0, 4);
    }, [activeCategory, instruments]);

    const futuresBreadthLabel = useMemo(() => {
        if (activeCategory !== 'FUTURES' || instruments.length === 0) return null;
        const up = instruments.filter((i) => i.changePercent > 0.05).length;
        const down = instruments.filter((i) => i.changePercent < -0.05).length;
        const flat = instruments.length - up - down;
        if (up > down && up >= flat) return t('market.directionUp', 'Yukarı');
        if (down > up && down >= flat) return t('market.directionDown', 'Aşağı');
        return t('market.directionMixed', 'Karışık');
    }, [activeCategory, instruments, t]);

    const bondTopMovers = useMemo(
        () => [...instruments].sort((a, b) => Math.abs(b.changePercent) - Math.abs(a.changePercent)).slice(0, 5),
        [instruments],
    );
    const bondBreadthLabel = useMemo(() => {
        if (activeCategory !== 'BOND' || instruments.length === 0) return null;
        const up = instruments.filter((i) => i.changePercent > 0.05).length;
        const down = instruments.filter((i) => i.changePercent < -0.05).length;
        const flat = instruments.length - up - down;
        if (up > down && up >= flat) return t('market.directionUp', 'Yukarı');
        if (down > up && down >= flat) return t('market.directionDown', 'Aşağı');
        return t('market.directionMixed', 'Karışık');
    }, [activeCategory, instruments, t]);

    const bondContractSummary = useMemo(() => {
        if (activeCategory !== 'BOND' || !selectedSymbol) return null;
        const vm = instrumentVms.find((x) => normalizeSymbolKey(x.symbol) === normalizeSymbolKey(selectedSymbol));
        const snap = debtLatestMap[normalizeSymbolKey(selectedSymbol)];
        return {
            name: vm?.displayName ?? debtNameMap[selectedSymbol] ?? selectedSymbol,
            maturityDate: snap?.maturityDate ?? vm?.maturityDate ?? null,
            daysToMaturity: snap?.daysToMaturity ?? vm?.daysToMaturity ?? null,
            couponRate: snap?.couponRate ?? vm?.couponRate ?? null,
            dirtyPrice: snap?.dirtyPrice ?? vm?.price ?? null,
            currency: 'TRY',
        };
    }, [activeCategory, selectedSymbol, instrumentVms, debtLatestMap, debtNameMap]);

    const viopContractSummary = useMemo(() => {
        if (activeCategory !== 'FUTURES' || !selectedSymbol) return null;
        const key = normalizeSymbolKey(selectedSymbol);
        const snap = viopSnapshotBySymbol[key];
        const contract = viopContractBySymbol[key];
        let daysToExpiry: number | null = null;
        if (contract?.expiry) {
            const exp = new Date(`${String(contract.expiry).slice(0, 10)}T12:00:00`);
            if (!Number.isNaN(exp.getTime())) {
                daysToExpiry = Math.max(0, Math.ceil((exp.getTime() - Date.now()) / 86_400_000));
            }
        }
        return {
            underlying: snap?.underlying ?? contract?.underlying ?? '—',
            maturity:
                snap?.maturityMonth && snap?.maturityYear
                    ? `${snap.maturityMonth}/${snap.maturityYear}`
                    : contract?.expiry
                      ? String(contract.expiry).slice(0, 7)
                      : '—',
            daysToExpiry,
            settlement: snap?.settlement ?? null,
            preSettlement: snap?.preSettlement ?? null,
            initialMargin: snap?.initialMargin ?? null,
        };
    }, [activeCategory, selectedSymbol, viopSnapshotBySymbol, viopContractBySymbol]);

    const viopDelayMinutesDisplay = useMemo(() => {
        if (activeCategory !== 'FUTURES') return null;
        for (const c of viopContracts) {
            const s = viopSnapshotBySymbol[normalizeSymbolKey(c.contractCode)];
            if (s?.delayMinutes != null && Number.isFinite(Number(s.delayMinutes))) {
                return Number(s.delayMinutes);
            }
        }
        return null;
    }, [activeCategory, viopContracts, viopSnapshotBySymbol]);

    const cryptoDominance = useMemo(() => {
        if (activeCategory !== 'CRYPTO' || !dashboard) return [] as { symbol: string; dominancePct: number }[];
        const cryptoTiles = dashboard.heatmapTiles.filter((t) => t.assetClass === 'CRYPTO');
        const total = cryptoTiles.reduce((acc, t) => acc + Number(t.layoutWeight ?? 0), 0);
        if (total <= 0) return [] as { symbol: string; dominancePct: number }[];
        return cryptoTiles
            .map((t) => ({
                symbol: t.symbol,
                dominancePct: (Number(t.layoutWeight ?? 0) / total) * 100,
            }))
            .sort((a, b) => b.dominancePct - a.dominancePct)
            .slice(0, 5);
    }, [activeCategory, dashboard]);

    const lineWidthBySymbol = useMemo(() => {
        const out: Record<string, number> = {};
        const slice = compareSymbols.slice(0, 4);
        const vols = slice.map((s) => {
            const vm = instrumentVms.find((x) => x.symbol === s);
            if (vm && TREND_SELECTABLE_CATEGORIES.has(vm.category)) {
                return Math.abs(effectiveChangePercent(vm, trendChartRange));
            }
            return Math.abs(instruments.find((x) => x.symbol === s)?.changePercent ?? 0);
        });
        const max = Math.max(...vols, 0.0001);
        for (const sym of slice) {
            const vm = instrumentVms.find((x) => x.symbol === sym);
            const v =
                vm && TREND_SELECTABLE_CATEGORIES.has(vm.category)
                    ? Math.abs(effectiveChangePercent(vm, trendChartRange))
                    : Math.abs(instruments.find((x) => x.symbol === sym)?.changePercent ?? 0);
            out[sym] = 2 + Math.min(2.5, (v / max) * 2.5);
        }
        return out;
    }, [compareSymbols, instruments, instrumentVms, trendChartRange]);

    const loadCompare = useCallback(() => {
        const bistCompare =
            activeCategory === 'EQUITY' && equitySubmarket === 'BIST' && compareSymbols.length >= 2;
        if (
            compareSymbols.length < 2 ||
            (activeCategory !== 'FUTURES' && !marketType && !bistCompare)
        ) {
            setCompareRows([]);
            return;
        }
        setLoadingCompare(true);
        const symbols = compareSymbols.slice(0, 4);
        const request =
            activeCategory === 'FUTURES'
                ? (() => {
                      const now = new Date();
                      const fromMs = now.getTime() - Math.max(1, days) * 86_400_000;
                      const from = formatEuropeIstanbulLocalIso(new Date(fromMs));
                      const to = formatEuropeIstanbulLocalIso(now);
                      return Promise.all(
                          symbols.map((sym) => {
                              const contract = resolveViopHistoryContract(
                                  sym,
                                  viopContractBySymbol,
                                  viopSnapshotBySymbol,
                              );
                              if (!contract) {
                                  return Promise.resolve([sym, null] as const);
                              }
                              return marketClient
                                  .get<ViopHistoryApi>(
                                      `/api/market/viop/contracts/${encodeURIComponent(contract)}/history`,
                                      {
                                          params: { from, to, period: 60 },
                                      },
                                  )
                                  .then((res) => [sym, res.data] as const)
                                  .catch(() => [sym, null] as const);
                          }),
                      ).then((rows) => {
                          const series: Record<string, CandlePoint[]> = {};
                          rows.forEach(([sym, hist]) => {
                              if (!hist) return;
                              series[sym] = (hist?.points ?? []).map((p) => ({
                                  t: p.time,
                                  o: Number(p.price),
                                  h: Number(p.price),
                                  l: Number(p.price),
                                  c: Number(p.price),
                                  v: 0,
                              }));
                          });
                          return { series } satisfies BatchHistoryResponse;
                      });
                  })()
                : bistCompare
                  ? getBistBatchHistory(symbols, bistMainHistoryRange.from, bistMainHistoryRange.to).then((resp) => {
                        const series: Record<string, CandlePoint[]> = {};
                        const hist = resp.historiesBySymbol ?? {};
                        symbols.forEach((sym) => {
                            const rowsRaw = hist[sym] ?? hist[normalizeSymbolKey(sym)] ?? [];
                            const sorted = [...rowsRaw].sort((a, b) =>
                                String(a.date ?? '').localeCompare(String(b.date ?? ''))
                            );
                            series[sym] = sorted.map((row) => {
                                const c = bistPickClose(row);
                                const d = String(row.date ?? '').slice(0, 10);
                                return {
                                    t: `${d}T12:00:00`,
                                    o: c,
                                    h: c,
                                    l: c,
                                    c,
                                    v: 0,
                                };
                            });
                        });
                        return { series } satisfies BatchHistoryResponse;
                    })
                  : marketClient
                        .get<BatchHistoryResponse>('/api/market/history/batch', {
                            params: {
                                type: marketType,
                                symbols: symbols.join(','),
                                days,
                                ...((activeCategory === 'EQUITY' ||
                                    activeCategory === 'FX' ||
                                    activeCategory === 'CRYPTO' ||
                                    activeCategory === 'METALS') &&
                                range === '1D'
                                    ? { bucket: 'hourly' }
                                    : {}),
                            },
                        })
                        .then((res) => res.data);

        request
            .then((batch) => {
                const byDate: Record<string, Record<string, number>> = {};
                Object.entries(batch.series ?? {}).forEach(([sym, candlesBySym]) => {
                    candlesBySym.forEach((c) => {
                        const date = new Date(c.t).toISOString().slice(0, 10);
                        if (!byDate[date]) byDate[date] = {};
                        byDate[date][sym] = Number(c.c);
                    });
                });
                const dates = Object.keys(byDate).sort();
                if (!dates.length) {
                    setCompareRows([]);
                    return;
                }
                const first: Record<string, number> = {};
                symbols.forEach((sym) => {
                    const d = dates.find((dt) => byDate[dt][sym] != null);
                    if (d != null) first[sym] = byDate[d][sym];
                });
                const rows = dates.map((date) => {
                    const values: Record<string, number> = {};
                    symbols.forEach((sym) => {
                        const v = byDate[date][sym];
                        const base = first[sym];
                        values[sym] = base && v != null ? Math.round((v / base) * 1000) / 10 : Number.NaN;
                    });
                    return { time: date, values };
                });
                setCompareRows(rows);
            })
            .catch(() => setCompareRows([]))
            .finally(() => setLoadingCompare(false));
    }, [
        compareSymbols,
        days,
        marketType,
        activeCategory,
        range,
        equitySubmarket,
        bistMainHistoryRange.from,
        bistMainHistoryRange.to,
        viopContractBySymbol,
        viopSnapshotBySymbol,
    ]);

    useEffect(() => {
        if (compareSymbols.length >= 2) {
            loadCompare();
        } else {
            setCompareRows([]);
        }
    }, [compareSymbols, loadCompare]);

    useEffect(() => {
        if (activeCategory === 'FUTURES') {
            setLiveOverrides({});
            return;
        }
        const base = (import.meta.env.VITE_MARKET_API_URL || 'http://localhost:8083').replace(/^http/i, 'ws');
        const wsUrl = `${base}/ws/market`;
        let socket: WebSocket | null = null;
        let isCancelled = false;
        let connectTimer: ReturnType<typeof setTimeout> | null = null;
        const connect = () => {
            if (isCancelled) return;
            socket = new WebSocket(wsUrl);
            socket.onmessage = (event) => {
                try {
                    const payload = JSON.parse(String(event.data)) as LivePayload;
                    const next: Record<string, LiveTick> = {};
                    (payload?.ticks ?? []).forEach((t) => {
                        const cat = t.category as MarketCategory;
                        next[`${cat}:${normalizeSymbolKey(t.symbol)}`] = {
                            category: cat,
                            symbol: normalizeSymbolKey(t.symbol),
                            price: Number(t.price ?? 0),
                            changePercent: Number(t.changePercent ?? 0),
                            volume: Number(t.volume ?? 0),
                        };
                    });
                    if (Object.keys(next).length) {
                        setLiveOverrides((prev) => ({ ...prev, ...next }));
                    }
                } catch {
                    // swallow malformed WS payloads, polling remains fallback
                }
            };
            socket.onclose = () => {
                if (!isCancelled) {
                    window.setTimeout(connect, 1500);
                }
            };
        };
        connectTimer = setTimeout(connect, 1500);
        return () => {
            isCancelled = true;
            if (connectTimer) clearTimeout(connectTimer);
            socket?.close();
        };
    }, [activeCategory]);

    const errMsg = dashboardError instanceof Error ? dashboardError.message : '';
    /** Sayfa hemen açılır; dashboard/treemap arka planda dolar. */
    const showDashboardBlockingSpinner = false;
    const categoryLabel = useCallback(
        (id: MarketCategory) => {
            const key =
                id === 'EQUITY'
                    ? 'category.equity'
                    : id === 'CRYPTO'
                    ? 'category.crypto'
                    : id === 'FX'
                    ? 'category.fx'
                    : id === 'METALS'
                    ? 'category.metals'
                    : id === 'FUNDS'
                    ? 'category.funds'
                    : id === 'FUTURES'
                    ? 'category.futures'
                    : 'category.bond';
            const fallback = CATEGORY_LABELS.find((c) => c.id === id)?.label ?? id;
            return t(key, fallback);
        },
        [t]
    );
    const marketListCategoryTabs = useMemo(
        () => CATEGORY_LABELS.map((c) => ({ id: c.id, label: categoryLabel(c.id) })),
        [categoryLabel]
    );
    const fxQuoteScrollTabs = useMemo(
        () => [
            {
                id: 'USD' as const,
                label: t('market.showUsd', 'USD göster'),
                icon: <span className="terminal-fx-quote-tab__glyph" aria-hidden>$</span>,
            },
            {
                id: 'TRY' as const,
                label: t('market.convertTry', 'TL’ye çevir'),
                icon: <span className="terminal-fx-quote-tab__glyph" aria-hidden>₺</span>,
            },
        ],
        [t]
    );
    const chartRangeScrollTabs = useMemo(
        () => TERMINAL_CHART_RANGE_SEQUENCE.map((r) => ({ id: r, label: chartRangeUiShortLabel(r) })),
        []
    );
    const chartIndicatorRsiAvailable = activeCategory !== 'BOND' && activeCategory !== 'FUTURES';

    const spotChartModeTabs = useMemo(
        () => [
            {
                id: 'ANALYSIS' as const,
                label: t('market.marketAnalysis', 'Piyasa Analiz'),
                icon: <ChartLine size={17} strokeWidth={2} aria-hidden />,
            },
            {
                id: 'CANDLE' as const,
                label: t('market.detailedCandle', 'Detaylı Mum'),
                icon: <ChartCandlestick size={17} strokeWidth={2} aria-hidden />,
            },
        ],
        [t]
    );
    const bondChartModeTabs = useMemo(
        () => [
            {
                id: 'DUAL' as const,
                label: t('market.bondAnalysis', 'Tahvil Analiz'),
                icon: <ChartLine size={17} strokeWidth={2} aria-hidden />,
            },
            {
                id: 'CANDLE' as const,
                label: t('market.detailedCandle', 'Detaylı Mum'),
                icon: <ChartCandlestick size={17} strokeWidth={2} aria-hidden />,
            },
        ],
        [t]
    );
    const viopChartModeTabs = useMemo(
        () => [
            {
                id: 'LINE' as const,
                label: t('market.viopAnalysis', 'VİOP Analiz'),
                icon: <ChartLine size={17} strokeWidth={2} aria-hidden />,
            },
            {
                id: 'CANDLE' as const,
                label: t('market.detailedCandle', 'Detaylı Mum'),
                icon: <ChartCandlestick size={17} strokeWidth={2} aria-hidden />,
            },
        ],
        [t]
    );
    const spotTerminalTitle = useMemo(() => {
        if (activeCategory === 'EQUITY' && equitySubmarket === 'BIST') {
            return t('stocks.bistAnalysisTitle', 'Türk Hisse Piyasa Analizi');
        }
        if (isTefasFundsView) {
            return t('funds.tefasAnalysisTitle', 'Türk Fonları — TEFAS');
        }
        return `${categoryLabel(activeCategory)} ${t('market.marketAnalysis', 'Piyasa Analiz')}`;
    }, [activeCategory, equitySubmarket, isTefasFundsView, categoryLabel, t]);
    const spotTerminalSubtitle = useMemo(() => {
        if (activeCategory === 'EQUITY' && equitySubmarket === 'BIST') {
            return t('stocks.bistAnalysisSubtitle', 'BIST günlük fiyat verileri ve düzeltilmiş kapanış grafiği');
        }
        if (isTefasFundsView) {
            return t(
                'funds.tefasAnalysisSubtitle',
                'TEFAS fon getirileri ve pay değeri geçmişi — resmi dağıtım platformu verisi',
            );
        }
        return undefined;
    }, [activeCategory, equitySubmarket, isTefasFundsView, t]);
    const viopTerminalTitle = useMemo(() => {
        if (activeCategory !== 'FUTURES' || !selectedSymbol) {
            return t('market.viopAnalysisTitle', 'VIOP Piyasa Analiz');
        }
        const label = parseViopContractLabel(selectedSymbol);
        return label !== selectedSymbol ? `${label} — ${t('market.viopAnalysisTitle', 'VIOP Analiz')}` : selectedSymbol;
    }, [activeCategory, selectedSymbol, t]);
    const viopTerminalSubtitle = useMemo(
        () =>
            t(
                'market.viopAnalysisSubtitle',
                'Vadeli kontrat fiyat serisi. VIOP kaldıraçlı piyasa olduğu için fiyat hareketleri teminat etkisi yaratabilir.',
            ),
        [t],
    );
    const terminalVars = useMemo(
        () =>
            ({
                '--terminal-bg': tokens.bg,
                '--terminal-bg-accent': theme === 'dark' ? 'rgba(37, 99, 235, 0.16)' : 'rgba(59, 130, 246, 0.1)',
                '--terminal-card-bg': tokens.bgCard,
                '--terminal-border': tokens.border,
                '--terminal-text': tokens.text,
                '--terminal-muted': tokens.textMuted,
                '--terminal-text-muted': tokens.textMuted,
                '--terminal-accent': tokens.accent,
                '--terminal-btn-bg': tokens.inputBg,
                '--terminal-btn-active-bg': theme === 'dark' ? 'rgba(8, 47, 73, 0.8)' : 'rgba(29, 78, 216, 0.14)',
                '--terminal-btn-active-text': tokens.text,
                '--terminal-table-head-bg': theme === 'dark' ? 'rgba(15, 23, 42, 0.98)' : '#f8fafc',
                '--terminal-hover-bg': theme === 'dark' ? 'rgba(30, 41, 59, 0.72)' : 'rgba(148, 163, 184, 0.16)',
                '--terminal-active-row-bg': theme === 'dark' ? 'rgba(30, 58, 138, 0.28)' : 'rgba(59, 130, 246, 0.14)',
                '--terminal-pos': theme === 'dark' ? '#4ade80' : '#15803d',
                '--terminal-neg': theme === 'dark' ? '#f87171' : '#b91c1c',
            }) as CSSProperties,
        [theme, tokens]
    );

    /**
     * Chart bileşenlerinin `tokens` prop'u için stable referans. Aksi halde her renderda yeni
     * obje literal'i oluşur, chart'ların useEffect'i (deps: tokens) sürekli tetiklenir, chart
     * destroy+create sarmalı oluşur — sayfa titrer ve ana thread tükenince diğer sayfalara
     * geçiş tıkanır. Tema değiştiğinde yeni referans oluşmalı, bu yüzden token alanlarına bağlı.
     */
    const chartTokens = useMemo(
        () => ({
            bg: tokens.bg,
            bgCard: tokens.bgCard,
            border: tokens.border,
            text: tokens.text,
            textMuted: tokens.textMuted,
        }),
        [tokens.bg, tokens.bgCard, tokens.border, tokens.text, tokens.textMuted]
    );

    const instrumentListSheetStyle: CSSProperties | undefined = useMemo(() => {
        if (instrumentListOpen && instrumentListPopoverBox) {
            return {
                position: 'fixed',
                top: instrumentListPopoverBox.top,
                left: instrumentListPopoverBox.left,
                width: instrumentListPopoverBox.width,
                maxHeight: 'min(62vh, 480px)',
                minHeight: 0,
                zIndex: 200,
                display: 'flex',
                flexDirection: 'column',
                boxSizing: 'border-box',
                padding: '10px 12px',
                borderRadius: 10,
                border: `1px solid ${tokens.border}`,
                backgroundColor: tokens.bgCard,
                boxShadow: '0 12px 36px rgba(2, 6, 23, 0.28)',
                overflow: 'hidden',
            };
        }
        // Tahvil: sol sütunda sabit liste (hisse/VİOP gibi); yalnızca popover kapalıyken gizleme.
        if (isSpotTerminalLayout || isBondTerminalLayout) {
            return undefined;
        }
        return { display: 'none' };
    }, [
        instrumentListOpen,
        instrumentListPopoverBox,
        isSpotTerminalLayout,
        isBondTerminalLayout,
        tokens.border,
        tokens.bgCard,
    ]);

    const renderComparisonCard = (cardClass: string) => {
        if (!canShowComparisonChart(activeCategory, marketType, equitySubmarket) || activeCategory === 'BOND') {
            return null;
        }
        const chartH = cardClass === 'terminal-right-comparison' ? 220 : 250;
        return (
            <div className={`terminal-card ${cardClass}`}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8, marginBottom: 8 }}>
                    <strong style={{ fontSize: 14 }}>{t('market.comparisonChart', 'Karşılaştırma Grafiği (Baz 100)')}</strong>
                    <div style={{ fontSize: 12, color: 'var(--terminal-muted)' }}>
                        {activeCategory === 'FUTURES'
                            ? t(
                                  'market.compareFromListHintViop',
                                  'Karşılaştırma için piyasa listesindeki karşılaştırma ikonunu kullanın.',
                              )
                            : t('market.compareFromListHint', 'Karşılaştırma sembollerini piyasa listesindeki sütundan ekleyin veya çıkarın.')}
                    </div>
                </div>
                {compareSymbolChipStrip ? (
                    <div className="terminal-comparison-card__chip-row">{compareSymbolChipStrip}</div>
                ) : null}
                <div style={{ marginTop: 4, position: 'relative', minHeight: chartH }}>
                    {loadingCompare ? (
                        <div
                            style={{
                                position: 'absolute',
                                inset: 0,
                                zIndex: 2,
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                borderRadius: 6,
                                fontSize: 13,
                                color: tokens.textMuted,
                                background:
                                    theme === 'dark' ? 'rgba(15, 23, 42, 0.58)' : 'rgba(248, 250, 252, 0.72)',
                                pointerEvents: 'none',
                            }}
                        >
                            {t('market.compareLoading', 'Karşılaştırma yükleniyor...')}
                        </div>
                    ) : null}
                    <MarketCompareLwChart
                        rows={compareRows}
                        symbols={compareSymbols.slice(0, 4)}
                        colors={['#38bdf8', '#22c55e', '#eab308', '#f87171']}
                        lineWidthBySymbol={lineWidthBySymbol}
                        timeframeLabel={chartTfLabel}
                        tokens={chartTokens}
                        height={chartH}
                    />
                </div>
                {!loadingCompare && compareSymbols.length < 2 ? (
                    <div style={{ fontSize: 11, color: tokens.textMuted, marginTop: 8 }}>
                        {activeCategory === 'FUTURES'
                            ? t(
                                  'market.compareFromListHintViop',
                                  'Karşılaştırma için piyasa listesindeki karşılaştırma ikonunu kullanın.',
                              )
                            : t(
                                  'market.compareNeedTwoFromList',
                                  'En az iki sembol seçildiğinde çizgiler burada görünür. Piyasa listesindeki karşılaştırma sütununu kullanın.',
                              )}
                    </div>
                ) : null}
                {!loadingCompare && compareSymbols.length >= 2 && compareRows.length === 0 ? (
                    <div style={{ fontSize: 11, color: tokens.textMuted, marginTop: 8 }}>
                        {t(
                            'market.compareInsufficientHistory',
                            'Karşılaştırma için yeterli geçmiş veri yok. Zaman aralığını genişletmeyi veya başka sembolleri deneyin.'
                        )}
                    </div>
                ) : null}
            </div>
        );
    };

    function renderMarketListSubtitleKicker(): ReactElement | null {
        if (pickerCategory === 'FUTURES') {
            return (
                <div
                    style={{
                        fontSize: 11,
                        color: tokens.textMuted,
                        lineHeight: 1.25,
                        letterSpacing: 0.2,
                    }}
                >
                    {t('market.viopListKicker', 'VİOP vadeli işlemler')} (
                    {marketListTotalElements > 0 ? marketListTotalElements : viopContracts.length}{' '}
                    {t('market.viopContractCount', 'kontrat')})
                </div>
            );
        }
        if (pickerCategory === 'BOND') {
            return (
                <div
                    style={{
                        fontSize: 11,
                        color: tokens.textMuted,
                        lineHeight: 1.25,
                        letterSpacing: 0.2,
                    }}
                >
                    {t(
                        'market.bondListKicker',
                        'Gösterilen % değerleri dönemsel fiyat değişimidir; tahvil faizi (yield) değildir.',
                    )}
                </div>
            );
        }
        return null;
    }

    function renderMarketListTableSection(): ReactElement {
        return (
            <div className="terminal-market-list-body">
                            <MarketCategoryScrollTabs
                                categories={marketListCategoryTabs}
                                value={pickerCategory}
                                onChange={(id) => applyPickerCategory(id)}
                                ariaLabel={t('market.categorySelectAria', 'Piyasa kategorisi')}
                            />
                            {pickerCategory === 'EQUITY' ? (
                                <div
                                    className="terminal-equity-submarket"
                                    role="group"
                                    aria-label={t('stocks.submarketGroup', 'Hisse alt pazarı')}
                                >
                                    <button
                                        type="button"
                                        className={`terminal-submarket-chip ${pickerEquitySubmarket === 'US' ? 'is-active' : ''}`}
                                        onClick={() => {
                                            setPickerEquitySubmarket('US');
                                            setMarketListQuickFilter('ALL');
                                        }}
                                        title={t('stocks.nasdaqNyse', 'NASDAQ / NYSE')}
                                    >
                                        {t('stocks.usStocks', 'ABD Hisseleri')}
                                    </button>
                                    <button
                                        type="button"
                                        className={`terminal-submarket-chip ${pickerEquitySubmarket === 'BIST' ? 'is-active' : ''}`}
                                        onClick={() => {
                                            setPickerEquitySubmarket('BIST');
                                            setMarketListQuickFilter('ALL');
                                        }}
                                        title={t('stocks.bist', 'Borsa İstanbul')}
                                    >
                                        {t('stocks.turkishStocks', 'Türk Hisseleri')}
                                    </button>
                                </div>
                            ) : null}
                            {pickerCategory === 'FUNDS' ? (
                                <div
                                    className="terminal-equity-submarket"
                                    role="group"
                                    aria-label={t('funds.submarketGroup', 'Fon alt pazarı')}
                                >
                                    <button
                                        type="button"
                                        className={`terminal-submarket-chip ${pickerFundSubmarket === 'TR' ? 'is-active' : ''}`}
                                        onClick={() => {
                                            setPickerFundSubmarket('TR');
                                            setMarketListQuickFilter('ALL');
                                        }}
                                        title={t('funds.tefasSource', 'TEFAS')}
                                    >
                                        {t('funds.turkishFunds', 'Türk Fonları')}
                                    </button>
                                    <button
                                        type="button"
                                        className={`terminal-submarket-chip ${pickerFundSubmarket === 'US' ? 'is-active' : ''}`}
                                        onClick={() => {
                                            setPickerFundSubmarket('US');
                                            setMarketListQuickFilter('ALL');
                                        }}
                                        title={t('funds.usEtfs', 'ABD ETF')}
                                    >
                                        {t('funds.usFunds', 'Amerika Fonları')}
                                    </button>
                                </div>
                            ) : null}
                            {pickerCategory === 'METALS' ? (
                                <div
                                    className="terminal-equity-submarket"
                                    role="group"
                                    aria-label={t('metals.submarketGroup', 'Kıymetli maden alt pazarı')}
                                >
                                    <button
                                        type="button"
                                        className={`terminal-submarket-chip ${pickerMetalsSubmarket === 'GRAM' ? 'is-active' : ''}`}
                                        onClick={() => {
                                            setPickerMetalsSubmarket('GRAM');
                                            setMetalsSubmarket('GRAM');
                                            setPickerCategory('METALS');
                                            setActiveCategory('METALS');
                                            setMarketListQuickFilter('ALL');
                                            setMarketListPage(0);
                                            setSelectedSymbol(defaultMetalSymbolForSubmarket('GRAM'));
                                            setCompareSymbols([]);
                                        }}
                                        title={t('metals.gramGold', 'Gram altın')}
                                    >
                                        {t('metals.gramGold', 'Gram altın')}
                                    </button>
                                    <button
                                        type="button"
                                        className={`terminal-submarket-chip ${pickerMetalsSubmarket === 'OUNCE' ? 'is-active' : ''}`}
                                        onClick={() => {
                                            setPickerMetalsSubmarket('OUNCE');
                                            setMetalsSubmarket('OUNCE');
                                            setPickerCategory('METALS');
                                            setActiveCategory('METALS');
                                            setMarketListQuickFilter('ALL');
                                            setMarketListPage(0);
                                            setSelectedSymbol(defaultMetalSymbolForSubmarket('OUNCE'));
                                            setCompareSymbols([]);
                                        }}
                                        title={t('metals.usdOunce', 'Ons')}
                                    >
                                        {t('metals.usdOunce', 'Ons')}
                                    </button>
                                </div>
                            ) : null}
                            <div className="terminal-list-quick-filters">
                                <div className="terminal-list-quick-filters__chips">
                                    <MarketCategoryScrollTabs
                                        className="terminal-category-scroll-shell--quick-filters"
                                        categories={pickerMarketListQuickChips}
                                        value={marketListQuickFilter}
                                        onChange={setMarketListQuickFilter}
                                        ariaLabel={t('market.quickFiltersAria', 'Hızlı filtre')}
                                    />
                                </div>
                                {pickerCategory === 'FX' ? (
                                    <button
                                        type="button"
                                        className="terminal-btn terminal-fx-effective-rates-btn"
                                        onClick={() => setEffectiveRatesOpen(true)}
                                    >
                                        <span className="terminal-fx-effective-rates-btn__full">
                                            {t('market.fxEffectiveModal.openBtn', 'TCMB Efektif Kurlar')}
                                        </span>
                                        <span className="terminal-fx-effective-rates-btn__short">
                                            {t('market.fxEffectiveModal.openBtnShort', 'Efektif Kurlar')}
                                        </span>
                                    </button>
                                ) : null}
                            </div>
                            <input
                                className="terminal-search"
                                placeholder={t('market.searchPlaceholder', 'Sembol / enstrüman ara')}
                                value={searchTerm}
                                onChange={(e) => setSearchTerm(e.target.value)}
                            />
                            <div className="terminal-market-list-scroll-host">
                            <div
                                ref={tableWrapRef}
                                className="terminal-table-wrap"
                                role="region"
                                aria-label={t('market.marketList', 'Piyasa Listesi')}
                            >
                                <table
                                    className={`terminal-data-table terminal-data-table--picker-horizons ${
                                        pickerCategory === 'FUTURES' ? 'terminal-data-table--viop' : ''
                                    }${isTefasFundsPicker ? ' terminal-data-table--tefas' : ''}`}
                                >
                                    <thead>
                                        <tr>
                                            <th className="terminal-picker-star-head" aria-label={t('market.favoritesColumn', 'Favoriler')}>
                                                <span aria-hidden>★</span>
                                            </th>
                                            <th
                                                className="terminal-picker-cmp-head"
                                                aria-label={t('market.compareWithSelected', 'Karşılaştırma')}
                                                title={t('market.compareWithSelectedHint', 'Seçili enstrümanla grafikte karşılaştır')}
                                            >
                                                <GitCompare size={13} strokeWidth={2.2} aria-hidden className="terminal-picker-cmp-head__ic" />
                                            </th>
                                            <th>{t('market.instrument', 'Enstrüman')}</th>
                                            {isTefasFundsPicker ? (
                                                <>
                                                    <th scope="col">{t('funds.fundType', 'Fon türü')}</th>
                                                    <th scope="col">{t('funds.riskLevel', 'Risk')}</th>
                                                </>
                                            ) : null}
                                            <th
                                                scope="col"
                                                aria-sort={
                                                    pickerTableSort?.key === 'price'
                                                        ? pickerTableSort.dir === 'asc'
                                                            ? 'ascending'
                                                            : 'descending'
                                                        : 'none'
                                                }
                                            >
                                                <button
                                                    type="button"
                                                    className="terminal-sort-th"
                                                    onClick={() => togglePickerListSort('price')}
                                                    title={
                                                        pickerCategory === 'BOND'
                                                            ? t('market.bondSortMarketPrice', 'Piyasa fiyatına göre sırala')
                                                            : t('market.sortByPrice', 'Fiyata göre sırala')
                                                    }
                                                >
                                                    {isTefasFundsPicker
                                                        ? t('funds.navPrice', 'Pay değeri')
                                                        : t('market.price', 'Fiyat')}
                                                    {pickerTableSort?.key === 'price'
                                                        ? pickerTableSort.dir === 'asc'
                                                            ? ' \u2191'
                                                            : ' \u2193'
                                                        : ''}
                                                </button>
                                            </th>
                                            <th
                                                className="terminal-horizon-pct-head"
                                                scope="col"
                                                title={
                                                    isTefasFundsPicker
                                                        ? t('funds.return1dHint', 'Son iş günü pay değeri değişimi (TEFAS)')
                                                        : pickerCategory === 'BOND'
                                                        ? t('market.bondHorizonDayHint', '1 günlük fiyat değişimi (faiz/yield değil)')
                                                        : t('market.horizonDayHint', 'Günlük değişim: canlı veya spark son noktaları')
                                                }
                                                aria-sort={
                                                    pickerTableSort?.key === 'pctDay'
                                                        ? pickerTableSort.dir === 'asc'
                                                            ? 'ascending'
                                                            : 'descending'
                                                        : 'none'
                                                }
                                            >
                                                <button
                                                    type="button"
                                                    className="terminal-sort-th terminal-sort-th--pct"
                                                    onClick={() => togglePickerListSort('pctDay')}
                                                >
                                                    {isTefasFundsPicker
                                                        ? t('market.horizonDay', 'Gün')
                                                        : pickerCategory === 'BOND'
                                                        ? t('market.bondHorizon1g', '1G')
                                                        : t('market.horizonDay', 'Gün')}
                                                    {pickerTableSort?.key === 'pctDay'
                                                        ? pickerTableSort.dir === 'asc'
                                                            ? ' \u2191'
                                                            : ' \u2193'
                                                        : ''}
                                                </button>
                                            </th>
                                            <th
                                                className="terminal-horizon-pct-head"
                                                scope="col"
                                                title={
                                                    isTefasFundsPicker
                                                        ? t('funds.return1wHint', 'Son 7 gün pay değeri değişimi (TEFAS)')
                                                        : pickerCategory === 'BOND'
                                                        ? t('market.bondHorizonWeekHint', '1 haftalık fiyat değişimi')
                                                        : t('market.horizonWeekHint', 'Son ~7 kapanış')
                                                }
                                                aria-sort={
                                                    pickerTableSort?.key === 'pctWeek'
                                                        ? pickerTableSort.dir === 'asc'
                                                            ? 'ascending'
                                                            : 'descending'
                                                        : 'none'
                                                }
                                            >
                                                <button
                                                    type="button"
                                                    className="terminal-sort-th terminal-sort-th--pct"
                                                    onClick={() => togglePickerListSort('pctWeek')}
                                                >
                                                    {isTefasFundsPicker
                                                        ? t('market.horizonWeek', 'Hafta')
                                                        : pickerCategory === 'BOND'
                                                        ? t('market.bondHorizon1h', '1H')
                                                        : t('market.horizonWeek', 'Hafta')}
                                                    {pickerTableSort?.key === 'pctWeek'
                                                        ? pickerTableSort.dir === 'asc'
                                                            ? ' \u2191'
                                                            : ' \u2193'
                                                        : ''}
                                                </button>
                                            </th>
                                            <th
                                                className="terminal-horizon-pct-head"
                                                scope="col"
                                                title={
                                                    isTefasFundsPicker
                                                        ? t('funds.return1mHint', 'Son 1 ay getirisi (TEFAS)')
                                                        : pickerCategory === 'BOND'
                                                        ? t('market.bondHorizonMonthHint', '1 aylık fiyat değişimi')
                                                        : t('market.horizonMonthHint', 'Son ~30 kapanış')
                                                }
                                                aria-sort={
                                                    pickerTableSort?.key === 'pctMonth'
                                                        ? pickerTableSort.dir === 'asc'
                                                            ? 'ascending'
                                                            : 'descending'
                                                        : 'none'
                                                }
                                            >
                                                <button
                                                    type="button"
                                                    className="terminal-sort-th terminal-sort-th--pct"
                                                    onClick={() => togglePickerListSort('pctMonth')}
                                                >
                                                    {isTefasFundsPicker
                                                        ? t('funds.return1m', '1A')
                                                        : pickerCategory === 'BOND'
                                                        ? t('market.bondHorizon1a', '1A')
                                                        : t('market.horizonMonth', 'Ay')}
                                                    {pickerTableSort?.key === 'pctMonth'
                                                        ? pickerTableSort.dir === 'asc'
                                                            ? ' \u2191'
                                                            : ' \u2193'
                                                        : ''}
                                                </button>
                                            </th>
                                            {isTefasFundsPicker ? (
                                                <>
                                                    <th
                                                        className="terminal-horizon-pct-head"
                                                        scope="col"
                                                        title={t('funds.return3mHint', 'Son 3 ay getirisi (TEFAS)')}
                                                        aria-sort={
                                                            pickerTableSort?.key === 'fundReturn3m'
                                                                ? pickerTableSort.dir === 'asc'
                                                                    ? 'ascending'
                                                                    : 'descending'
                                                                : 'none'
                                                        }
                                                    >
                                                        <button
                                                            type="button"
                                                            className="terminal-sort-th terminal-sort-th--pct"
                                                            onClick={() => togglePickerListSort('fundReturn3m')}
                                                        >
                                                            {t('funds.return3m', '3A')}
                                                            {pickerTableSort?.key === 'fundReturn3m'
                                                                ? pickerTableSort.dir === 'asc'
                                                                    ? ' \u2191'
                                                                    : ' \u2193'
                                                                : ''}
                                                        </button>
                                                    </th>
                                                    <th
                                                        className="terminal-horizon-pct-head"
                                                        scope="col"
                                                        title={t('funds.return6mHint', 'Son 6 ay getirisi (TEFAS)')}
                                                        aria-sort={
                                                            pickerTableSort?.key === 'fundReturn6m'
                                                                ? pickerTableSort.dir === 'asc'
                                                                    ? 'ascending'
                                                                    : 'descending'
                                                                : 'none'
                                                        }
                                                    >
                                                        <button
                                                            type="button"
                                                            className="terminal-sort-th terminal-sort-th--pct"
                                                            onClick={() => togglePickerListSort('fundReturn6m')}
                                                        >
                                                            {t('funds.return6m', '6A')}
                                                            {pickerTableSort?.key === 'fundReturn6m'
                                                                ? pickerTableSort.dir === 'asc'
                                                                    ? ' \u2191'
                                                                    : ' \u2193'
                                                                : ''}
                                                        </button>
                                                    </th>
                                                </>
                                            ) : null}
                                            <th
                                                className="terminal-horizon-pct-head"
                                                scope="col"
                                                title={
                                                    isTefasFundsPicker
                                                        ? t('funds.return1yHint', 'Son 1 yıl getirisi (TEFAS)')
                                                        : pickerCategory === 'BOND'
                                                        ? t('market.bondHorizonYearHint', '1 yıllık fiyat değişimi')
                                                        : t('market.horizonYearHint', 'Son ~252 kapanış (mevcut seri kısaysa seri uzunluğu)')
                                                }
                                                aria-sort={
                                                    pickerTableSort?.key === 'pctYear'
                                                        ? pickerTableSort.dir === 'asc'
                                                            ? 'ascending'
                                                            : 'descending'
                                                        : 'none'
                                                }
                                            >
                                                <button
                                                    type="button"
                                                    className="terminal-sort-th terminal-sort-th--pct"
                                                    onClick={() => togglePickerListSort('pctYear')}
                                                >
                                                    {isTefasFundsPicker
                                                        ? t('funds.return1y', '1Y')
                                                        : pickerCategory === 'BOND'
                                                        ? t('market.bondHorizon1y', '1Y')
                                                        : t('market.horizonYear', 'Yıl')}
                                                    {pickerTableSort?.key === 'pctYear'
                                                        ? pickerTableSort.dir === 'asc'
                                                            ? ' \u2191'
                                                            : ' \u2193'
                                                        : ''}
                                                </button>
                                            </th>
                                            {isTefasFundsPicker ? (
                                                <>
                                                    <th className="terminal-horizon-pct-head" scope="col" title={t('funds.returnYtdHint', 'Yıl başından bugüne')}>
                                                        {t('funds.returnYtd', 'YBB')}
                                                    </th>
                                                    <th className="terminal-horizon-pct-head" scope="col">
                                                        {t('funds.return3y', '3Y')}
                                                    </th>
                                                    <th className="terminal-horizon-pct-head" scope="col">
                                                        {t('funds.return5y', '5Y')}
                                                    </th>
                                                </>
                                            ) : null}
                                            {!isTefasFundsPicker ? (
                                            <th
                                                title={
                                                    pickerCategory === 'BOND'
                                                        ? t(
                                                              'market.bondTrendColHint',
                                                              'Seçili grafik aralığıyla uyumlu mini fiyat serisi (fiyat performansı; tahvil faizi değildir).',
                                                          )
                                                        : TREND_SELECTABLE_CATEGORIES.has(pickerCategory)
                                                          ? `Trend ve mini çizgi: üstteki grafik aralığı (${chartRangeUiShortLabel(range)}) ile aynı veri penceresi`
                                                          : pickerCategory === 'FUTURES'
                                                            ? `Mini trend: seçili grafik aralığı (${chartRangeUiShortLabel(range)}) ile uyumlu veri penceresi`
                                                            : `Trend: ${chartRangeUiShortLabel(range)}`
                                                }
                                            >
                                                {pickerCategory === 'BOND'
                                                    ? t('market.bondTrendCol', '1A Fiyat Trendi')
                                                    : `${t('market.trend', 'Trend')} (${chartRangeUiShortLabel(range)})`}
                                            </th>
                                            ) : null}
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {loadingTerminalList ? (
                                            <tr>
                                                <td
                                                    colSpan={isTefasFundsPicker ? 15 : 9}
                                                    className="terminal-chart-empty"
                                                    style={{ padding: 16, textAlign: 'left' }}
                                                >
                                                    {isTefasFundsPicker
                                                        ? t(
                                                              'funds.tefasListLoading',
                                                              'Türk fonları yükleniyor…',
                                                          )
                                                        : t('market.listLoading', 'Liste yükleniyor…')}
                                                </td>
                                            </tr>
                                        ) : pickerDisplayedInstruments.length === 0 ? (
                                            <tr>
                                                <td
                                                    colSpan={isTefasFundsPicker ? 15 : 9}
                                                    className="terminal-chart-empty"
                                                    style={{ padding: 16, textAlign: 'left' }}
                                                >
                                                    {terminalListError
                                                        ? String(
                                                              terminalListError instanceof Error
                                                                  ? terminalListError.message
                                                                  : t('market.listError', 'Liste alınamadı.'),
                                                          )
                                                        : searchTerm.trim().length > 0
                                                          ? t('market.listEmpty', 'Bu arama için sonuç yok.')
                                                          : marketListQuickFilter !== 'ALL'
                                                            ? t('market.quickFilterEmpty', 'Bu filtre için sonuç yok.')
                                                            : pickerCategory === 'EQUITY' && pickerEquitySubmarket === 'BIST'
                                                              ? t(
                                                                    'stocks.bistListEmpty',
                                                                    'BIST için gösterilecek sembol bulunamadı (fiyat verisi yok).',
                                                                )
                                                              : pickerCategory === 'FUNDS' && pickerFundSubmarket === 'TR'
                                                                ? t(
                                                                      'funds.tefasListEmpty',
                                                                      'TEFAS fon listesi boş veya eşleşen sonuç yok.',
                                                                  )
                                                                : t('market.listEmptyCategory', 'Bu kategoride gösterilecek varlık yok.')}
                                                </td>
                                            </tr>
                                        ) : (
                                            pickerDisplayedInstruments.map((row) => {
                                            const sk = rowToStarredApiKey(row.category, row.symbol);
                                            const starFilled = sk
                                                ? isStarredResolved(starredAssets, sk.marketType, sk.symbol)
                                                : false;
                                            const rowPriceDisp = terminalPriceDisplay(row, { showUsdInTry, usdTryRate });
                                            const bondDays = row.category === 'BOND' ? getRemainingDays(row.symbol) : NaN;
                                            const daysValue = row.category === 'BOND'
                                                ? (row.daysToMaturity ?? (Number.isFinite(bondDays) ? bondDays : undefined))
                                                : undefined;
                                            const bondTooltip =
                                                row.category === 'BOND'
                                                    ? `ISIN: ${row.symbol}\nVade: ${formatDateTr(row.maturityDate ?? extractMaturityDate(row.symbol))}\nKalan Gün: ${
                                                          daysValue != null ? daysValue : '—'
                                                      }\nPiyasa fiyatı: ${rowPriceDisp.glyph} ${rowPriceDisp.amount.toLocaleString(numberLocale, {
                                                          maximumFractionDigits: 4,
                                                      })}\nNot: Dönemsel % değerleri fiyat performansıdır; tahvil faizi (yield) değildir.`
                                                    : undefined;
                                            const rowIsBistEquity =
                                                rowIsBistEquityVm(row) ||
                                                (pickerCategory === 'EQUITY' && pickerEquitySubmarket === 'BIST');
                                            const rowIsTefas = rowIsTefasFundVm(row);
                                            const rowMatchesChartSelection =
                                                (row.category === 'FUTURES'
                                                    ? viopSymbolKeysEquivalent(selectedSymbol, row.symbol)
                                                    : normalizeSymbolKey(selectedSymbol) ===
                                                      normalizeSymbolKey(row.symbol)) &&
                                                activeCategory === row.category &&
                                                (row.category !== 'EQUITY' ||
                                                    equitySubmarket === (rowIsBistEquity ? 'BIST' : 'US')) &&
                                                (row.category !== 'FUNDS' ||
                                                    fundSubmarket === (rowIsTefas ? 'TR' : 'US')) &&
                                                (row.category !== 'METALS' ||
                                                    metalsSubmarket ===
                                                        (symbolMatchesMetalsSubmarket(row.symbol, 'GRAM')
                                                            ? 'GRAM'
                                                            : 'OUNCE'));
                                            const rowKey = normalizeSymbolKey(row.symbol);
                                            const rowInCompareSet = compareSymbols.some((s) => normalizeSymbolKey(s) === rowKey);
                                            const comparePickerFull = compareSymbols.length >= 4 && !rowInCompareSet;
                                            return (
                                            <tr
                                                key={`${row.category}:${row.symbol}`}
                                                className={`${rowMatchesChartSelection ? 'active' : ''} ${
                                                    priceFlash[row.symbol] === 'up'
                                                        ? 'terminal-flash-up'
                                                        : priceFlash[row.symbol] === 'down'
                                                        ? 'terminal-flash-down'
                                                        : ''
                                                }`}
                                                onClick={() => {
                                                    setActiveCategory(row.category);
                                                    setPickerCategory(row.category);
                                                    if (row.category === 'EQUITY') {
                                                        setEquitySubmarket(rowIsBistEquity ? 'BIST' : 'US');
                                                        setPickerEquitySubmarket(rowIsBistEquity ? 'BIST' : 'US');
                                                    }
                                                    if (row.category === 'FUNDS') {
                                                        const sub: FundSubmarket = rowIsTefas ? 'TR' : 'US';
                                                        setFundSubmarket(sub);
                                                        setPickerFundSubmarket(sub);
                                                    }
                                                    if (row.category === 'METALS') {
                                                        const sub: MetalsSubmarket = symbolMatchesMetalsSubmarket(
                                                            row.symbol,
                                                            'GRAM',
                                                        )
                                                            ? 'GRAM'
                                                            : 'OUNCE';
                                                        setMetalsSubmarket(sub);
                                                        setPickerMetalsSubmarket(sub);
                                                    }
                                                    const nextSymbol =
                                                        row.category === 'FUTURES'
                                                            ? resolveViopContractSymbol(
                                                                  row.symbol,
                                                                  viopContractBySymbol,
                                                              )
                                                            : row.symbol;
                                                    setSelectedSymbol(nextSymbol);
                                                    setIsDetailPanelOpen(true);
                                                    setInstrumentListOpen(false);
                                                }}
                                                title={bondTooltip}
                                            >
                                                <td className="terminal-picker-star-cell" onClick={(e) => e.stopPropagation()}>
                                                    {sk ? (
                                                        <span style={{ display: 'inline-flex', gap: 4, alignItems: 'center' }}>
                                                        <button
                                                            type="button"
                                                            className="terminal-star-btn"
                                                            aria-label={starFilled ? 'Yıldızı kaldır' : 'Dashboard’da göster'}
                                                            disabled={starMutation.isPending}
                                                            onClick={(e) => handleStarToggle(e, row.symbol, row.category)}
                                                        >
                                                            <Star
                                                                size={14}
                                                                fill={starFilled ? tokens.accent : 'transparent'}
                                                                color={starFilled ? tokens.accent : tokens.textMuted}
                                                            />
                                                        </button>
                                                        <button
                                                            type="button"
                                                            className="terminal-star-btn"
                                                            aria-label={t('priceAlert.title', 'Alarm Kur')}
                                                            title={t('priceAlert.title', 'Alarm Kur')}
                                                            onClick={(e) => {
                                                                e.stopPropagation();
                                                                const pa = marketCategoryToPriceAlertAsset(
                                                                    row.category,
                                                                    row.symbol,
                                                                    {
                                                                        marketRegion: row.marketRegion,
                                                                        exchange: row.exchange,
                                                                    },
                                                                );
                                                                if (pa) {
                                                                    setPriceAlertTarget({
                                                                        ...pa,
                                                                        displayName: row.displayName ?? row.symbol,
                                                                        referencePrice:
                                                                            Number.isFinite(row.price) && row.price > 0
                                                                                ? row.price
                                                                                : null,
                                                                        priceCurrency: row.currency ?? 'TRY',
                                                                    });
                                                                }
                                                            }}
                                                        >
                                                            <Bell size={14} color={tokens.textMuted} />
                                                        </button>
                                                        </span>
                                                    ) : (
                                                        <span style={{ color: tokens.textMuted, fontSize: 11 }}>—</span>
                                                    )}
                                                </td>
                                                <td className="terminal-picker-cmp-cell" onClick={(e) => e.stopPropagation()}>
                                                    <button
                                                        type="button"
                                                        className={`terminal-picker-compare-btn${rowInCompareSet ? ' is-active' : ''}`}
                                                        disabled={
                                                            !chartComparisonAvailable ||
                                                            !selectedInstrumentVm ||
                                                            comparePickerFull ||
                                                            !canPickerCompareRow(
                                                                row,
                                                                selectedInstrumentVm,
                                                                activeCategory,
                                                                equitySubmarket,
                                                                metalsSubmarket,
                                                            )
                                                        }
                                                        title={
                                                            !chartComparisonAvailable
                                                                ? t('market.compareUnavailable', 'Bu görünümde karşılaştırma yok.')
                                                                : !selectedInstrumentVm
                                                                  ? t('market.compareNeedSelection', 'Önce bir enstrüman seçin.')
                                                                  : !canPickerCompareRow(
                                                                        row,
                                                                        selectedInstrumentVm,
                                                                        activeCategory,
                                                                        equitySubmarket,
                                                                        metalsSubmarket,
                                                                    )
                                                                    ? t(
                                                                          'market.compareIncompatible',
                                                                          'Aynı pazar ve kategorideki enstrümanlar karşılaştırılabilir.'
                                                                      )
                                                                    : `${selectedInstrumentVm.symbol} · ${row.symbol} — ${t(
                                                                          'market.pickerCompareShort',
                                                                          'Grafikte karşılaştır'
                                                                      )}`
                                                        }
                                                        aria-label={t('market.pickerCompareAria', 'Karşılaştır')}
                                                        onClick={(e) => handlePickerCompareClick(e, row)}
                                                    >
                                                        <GitCompare size={13} strokeWidth={2.2} aria-hidden />
                                                    </button>
                                                </td>
                                                <td className="terminal-instrument-cell">
                                                    {row.category === 'FUTURES' ? (
                                                        <div className="viop-instrument-stack">
                                                            <div className="viop-instrument-stack__badge">
                                                                {(() => {
                                                                    const cat = viopCategoryFor(row.symbol);
                                                                    if (!cat) return null;
                                                                    return (
                                                                        <span
                                                                            className={viopCatBadgeClass(cat)}
                                                                            title={
                                                                                cat === 'FX'
                                                                                    ? 'Döviz Vadeli'
                                                                                    : cat === 'INDEX'
                                                                                      ? 'Endeks Vadeli'
                                                                                      : cat === 'COMMODITY'
                                                                                        ? 'Altın Vadeli'
                                                                                        : 'Pay Vadeli'
                                                                            }
                                                                        >
                                                                            {VIOP_CATEGORY_CHIP[cat].label}
                                                                        </span>
                                                                    );
                                                                })()}
                                                            </div>
                                                            <div className="viop-instrument-stack__main">
                                                                <div className="viop-contract-code" title={row.symbol}>
                                                                    {row.symbol}
                                                                </div>
                                                                <div className="viop-contract-sub" title={row.displayName}>
                                                                    {row.displayName}
                                                                </div>
                                                                {(() => {
                                                                    const snap = viopSnapshotBySymbol[normalizeSymbolKey(row.symbol)];
                                                                    const hint = viopDataQualityHint(snap, t);
                                                                    return hint ? (
                                                                        <div style={{ marginTop: 2 }}>
                                                                            <span className="viop-stale-pill">{hint}</span>
                                                                        </div>
                                                                    ) : null;
                                                                })()}
                                                            </div>
                                                        </div>
                                                    ) : rowIsTefas ? (
                                                        <div className="tefas-fund-instrument">
                                                            <span className="tefas-fund-code" title={row.symbol}>
                                                                {row.symbol}
                                                            </span>
                                                            <div className="tefas-fund-instrument__main">
                                                                <span className="tefas-fund-instrument__dot" aria-hidden />
                                                                <span className="tefas-fund-instrument__name" title={row.displayName}>
                                                                    {row.displayName}
                                                                </span>
                                                            </div>
                                                        </div>
                                                    ) : (
                                                        <>
                                                            <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap', rowGap: 4 }}>
                                                                {row.category === 'EQUITY' && rowIsBistEquity ? (
                                                                    <span
                                                                        className="bist-symbol-badge"
                                                                        style={bistSymbolBadgeStyle(row.symbol)}
                                                                        title={row.symbol}
                                                                        aria-hidden
                                                                    >
                                                                        {row.symbol.slice(0, 2)}
                                                                    </span>
                                                                ) : (
                                                                    <AssetLogo
                                                                        src={
                                                                            row.category === 'BOND'
                                                                                ? null
                                                                                : getDynamicLogoUrl(
                                                                                      row.symbol,
                                                                                      marketKindForCategory(row.category),
                                                                                      {
                                                                                          equitySubmarket:
                                                                                              row.category === 'EQUITY'
                                                                                                  ? rowIsBistEquity
                                                                                                      ? 'BIST'
                                                                                                      : 'US'
                                                                                                  : undefined,
                                                                                      },
                                                                                  )
                                                                        }
                                                                        alt={`${row.symbol} logo`}
                                                                        fallbackIcon={TrendingUp}
                                                                        fallbackColor={tokens.textMuted}
                                                                        size={20}
                                                                    />
                                                                )}
                                                                <div style={{ fontWeight: 700, minWidth: 0, lineHeight: 1.2, wordBreak: 'break-word' }}>
                                                                    {row.category === 'BOND'
                                                                        ? row.symbol
                                                                        : row.category === 'METALS'
                                                                          ? row.displayName
                                                                          : row.symbol}
                                                                    {row.category === 'EQUITY' &&
                                                                    String(row.exchange ?? '').toUpperCase() === 'BIST' &&
                                                                    row.dataQuality &&
                                                                    String(row.dataQuality).toUpperCase() === 'PARTIAL' ? (
                                                                        <span className="bist-dq-pill" title={t('stocks.partialDataHint', 'Kapanış verisi eksik olabilir.')}>
                                                                            {t('stocks.partialData', 'Kısmi veri')}
                                                                        </span>
                                                                    ) : null}
                                                                </div>
                                                            </div>
                                                            {row.category === 'BOND' ? (
                                                                <>
                                                                    <div style={{ fontSize: 11, color: tokens.textMuted, lineHeight: 1.25, marginTop: 2, wordBreak: 'break-word' }}>
                                                                        {bondListSubtitle(row.symbol, {
                                                                                  displayName:
                                                                                      debtNameMap[normalizeSymbolKey(row.symbol)] ??
                                                                                      row.displayName,
                                                                                  issuer:
                                                                                      debtMetaMap[normalizeSymbolKey(row.symbol)]
                                                                                          ?.issuer,
                                                                                  maturityDate: row.maturityDate,
                                                                                  daysToMaturity:
                                                                                      daysValue ?? row.daysToMaturity,
                                                                                  formatDate: formatDateTr,
                                                                                  t,
                                                                              })}
                                                                    </div>
                                                                </>
                                                            ) : (
                                                                <div style={{ fontSize: 11, color: tokens.textMuted, lineHeight: 1.25, marginTop: 2, wordBreak: 'break-word' }}>
                                                                    {row.category === 'METALS' ? row.listSubtitle ?? row.symbol : row.displayName}
                                                                </div>
                                                            )}
                                                        </>
                                                    )}
                                                </td>
                                                {rowIsTefas ? (
                                                    <>
                                                        <td className="tefas-fund-type-cell" title={row.listSubtitle ?? row.sector ?? ''}>
                                                            <span className="tefas-fund-type">{row.listSubtitle ?? row.sector ?? '—'}</span>
                                                        </td>
                                                        <td className="tefas-fund-risk-cell">
                                                            <div className="tefas-risk">
                                                                <span className="tefas-risk__label">
                                                                    {tefasRiskLevelDisplay(row.fundRiskLevel)}
                                                                </span>
                                                                <span
                                                                    className="tefas-risk__bar"
                                                                    style={{
                                                                        width: `${row.fundRiskLevel != null && Number.isFinite(row.fundRiskLevel) ? (Math.min(7, Math.max(1, Math.round(row.fundRiskLevel)) / 7) * 100) : 0}%`,
                                                                    }}
                                                                    aria-hidden
                                                                />
                                                            </div>
                                                        </td>
                                                    </>
                                                ) : null}
                                                <td className="terminal-picker-price-cell" title={rowPriceDisp.title}>
                                                    <div className="terminal-picker-price-stack">
                                                        <div className="terminal-picker-price-stack__main">
                                                            <span style={{ opacity: 0.8 }}>{rowPriceDisp.glyph}</span>{' '}
                                                            {rowPriceDisp.amount.toLocaleString(numberLocale, {
                                                                maximumFractionDigits:
                                                                    row.category === 'FUTURES' ? viopPriceDecimals(row.symbol) : 4,
                                                            })}
                                                            {rowPriceDisp.suffix ?? ''}
                                                        </div>
                                                        <div
                                                            className={`terminal-picker-price-stack__day ${horizonPctClassName(row.pctDay)}`}
                                                            title={t('market.horizonDay', 'Gün')}
                                                        >
                                                            {formatHorizonPct(row.pctDay)}
                                                        </div>
                                                    </div>
                                                </td>
                                                <td className={horizonPctClassName(row.pctDay)} title={t('market.horizonDay', 'Gün')}>
                                                    {formatHorizonPct(row.pctDay)}
                                                </td>
                                                <td className={horizonPctClassName(row.pctWeek)} title={t('market.horizonWeek', 'Hafta')}>
                                                    {formatHorizonPct(row.pctWeek)}
                                                </td>
                                                <td className={horizonPctClassName(row.pctMonth)} title={rowIsTefas ? t('funds.return1m', '1A') : t('market.horizonMonth', 'Ay')}>
                                                    {formatHorizonPct(row.pctMonth)}
                                                </td>
                                                {rowIsTefas ? (
                                                    <>
                                                        <td className={horizonPctClassName(row.fundReturn3m)} title={t('funds.return3m', '3A')}>
                                                            {formatHorizonPct(row.fundReturn3m)}
                                                        </td>
                                                        <td className={horizonPctClassName(row.fundReturn6m)} title={t('funds.return6m', '6A')}>
                                                            {formatHorizonPct(row.fundReturn6m)}
                                                        </td>
                                                    </>
                                                ) : null}
                                                <td className={horizonPctClassName(row.pctYear)} title={rowIsTefas ? t('funds.return1y', '1Y') : t('market.horizonYear', 'Yıl')}>
                                                    {formatHorizonPct(row.pctYear)}
                                                </td>
                                                {rowIsTefas ? (
                                                    <>
                                                        <td className={horizonPctClassName(row.changePercent)} title={t('funds.returnYtd', 'YBB')}>
                                                            {formatHorizonPct(row.changePercent)}
                                                        </td>
                                                        <td className={horizonPctClassName(row.fundReturn3y)} title={t('funds.return3y', '3Y')}>
                                                            {formatHorizonPct(row.fundReturn3y)}
                                                        </td>
                                                        <td className={horizonPctClassName(row.fundReturn5y)} title={t('funds.return5y', '5Y')}>
                                                            {formatHorizonPct(row.fundReturn5y)}
                                                        </td>
                                                    </>
                                                ) : null}
                                                {!rowIsTefas ? (
                                                <td>
                                                    {(() => {
                                                        // Sparkline yon-rengi ve uzunlugu, secili periyoda gore (BOND/FUTURES
                                                        // dahil) hesaplanir. Tek slice + iki indeks okumasi, virtual list ile
                                                        // birlestiginde maliyet pratikte ihmal edilebilir.
                                                        const sparkDays = sparkSliceDaysForInstrumentRow(row.category, trendChartRange);
                                                        const slice = row.sparkline.slice(-sparkDays);
                                                        const isBistRow =
                                                            row.category === 'EQUITY' &&
                                                            row.marketRegion === 'TR' &&
                                                            String(row.exchange ?? '').toUpperCase() === 'BIST';
                                                        if (isBistRow && slice.length < 2) {
                                                            return (
                                                                <div
                                                                    className="bist-spark-skeleton"
                                                                    style={{
                                                                        height: 24,
                                                                        borderRadius: 4,
                                                                        background: 'var(--app-surface-soft-bg)',
                                                                    }}
                                                                    title={t('stocks.sparkLoading', 'Mini grafik yükleniyor')}
                                                                />
                                                            );
                                                        }
                                                        const effPct = effectiveChangePercent(row, trendChartRange);
                                                        // Renk: % kolonu ile aynı işaret (spark eğimi eski veride yukarı, canlı % aşağı kalabiliyordu).
                                                        const sparkUp = TREND_SELECTABLE_CATEGORIES.has(row.category)
                                                            ? effPct >= 0
                                                            : slice.length >= 2
                                                              ? slice[slice.length - 1] >= slice[0]
                                                              : effPct >= 0;
                                                        const sparkLabelUi = chartRangeUiShortLabel(trendChartRange);
                                                        return (
                                                            <svg
                                                                className="inline-spark"
                                                                viewBox="0 0 100 24"
                                                                preserveAspectRatio="none"
                                                                aria-hidden="true"
                                                            >
                                                                <title>{`Trend (${sparkLabelUi}): son ${sparkDays} nokta`}</title>
                                                                <polyline
                                                                    points={sparklinePath(slice)}
                                                                    fill="none"
                                                                    stroke={sparkUp ? 'var(--terminal-pos, #15803d)' : 'var(--terminal-neg, #b91c1c)'}
                                                                    strokeWidth="2"
                                                                />
                                                            </svg>
                                                        );
                                                    })()}
                                                </td>
                                                ) : null}
                                            </tr>
                                            );
                                        })
                                        )}
                                    </tbody>
                                </table>
                            </div>
                            {marketListTotalElements > 0 ? (
                                <div className="terminal-market-list-pagination" aria-live="polite">
                                    <span className="terminal-market-list-pagination__count">
                                        {t('market.listCountPage', '{from}–{to} / {total}')
                                            .replace('{from}', String(marketListPage * MARKET_LIST_PAGE_SIZE + 1))
                                            .replace(
                                                '{to}',
                                                String(
                                                    Math.min(
                                                        marketListTotalElements,
                                                        (marketListPage + 1) * MARKET_LIST_PAGE_SIZE,
                                                    ),
                                                ),
                                            )
                                            .replace('{total}', String(marketListTotalElements))}
                                    </span>
                                    {marketListTotalPages > 1 ? (
                                        <div className="terminal-market-list-pagination__nav">
                                            <button
                                                type="button"
                                                className="terminal-market-list-page-btn"
                                                disabled={marketListPage <= 0 || loadingTerminalList}
                                                onClick={() => setMarketListPage((p) => Math.max(0, p - 1))}
                                            >
                                                {t('market.listPrev', 'Önceki')}
                                            </button>
                                            <span className="terminal-market-list-pagination__sep">
                                                {marketListPage + 1} / {marketListTotalPages}
                                            </span>
                                            <button
                                                type="button"
                                                className="terminal-market-list-page-btn"
                                                disabled={
                                                    marketListPage >= marketListTotalPages - 1 || loadingTerminalList
                                                }
                                                onClick={() =>
                                                    setMarketListPage((p) => Math.min(marketListTotalPages - 1, p + 1))
                                                }
                                            >
                                                {t('market.listNext', 'Sonraki')}
                                            </button>
                                        </div>
                                    ) : null}
                                </div>
                            ) : null}
                            </div>
            </div>
        );
    }

    function renderBondInstrumentSummaryRight(): ReactElement | null {
        if (!isBondInstrumentsView || !bondContractSummary) return null;
        return (
            <div className="terminal-bond-right-summary">
                <div className="terminal-bond-right-summary__grid">
                    <div className="terminal-mini-card">
                        <h4>{t('market.bondMaturitySummaryTitle', 'Vade / kupon özeti')}</h4>
                        <dl className="terminal-viop-summary-dl">
                            <div>
                                <dt>{t('market.maturity', 'Vade')}</dt>
                                <dd>{bondContractSummary.maturityDate ?? '—'}</dd>
                            </div>
                            <div>
                                <dt>{t('market.daysToMaturity', 'Vadeye kalan')}</dt>
                                <dd>
                                    {bondContractSummary.daysToMaturity != null
                                        ? bondContractSummary.daysToMaturity
                                        : '—'}
                                </dd>
                            </div>
                            <div>
                                <dt>{t('market.coupon', 'Kupon')}</dt>
                                <dd>
                                    {bondContractSummary.couponRate != null &&
                                    Number.isFinite(Number(bondContractSummary.couponRate))
                                        ? `${Number(bondContractSummary.couponRate).toLocaleString(numberLocale, { maximumFractionDigits: 2 })}%`
                                        : '—'}
                                </dd>
                            </div>
                            <div>
                                <dt>{t('market.currency', 'Para birimi')}</dt>
                                <dd>{bondContractSummary.currency ?? '—'}</dd>
                            </div>
                        </dl>
                    </div>
                    <div className="terminal-mini-card">
                        <h4>{t('market.bondPriceSummaryTitle', 'Piyasa değeri')}</h4>
                        <dl className="terminal-viop-summary-dl">
                            <div>
                                <dt>{t('market.dirtyPrice', 'Kirli fiyat')}</dt>
                                <dd>
                                    {bondContractSummary.dirtyPrice != null &&
                                    Number.isFinite(Number(bondContractSummary.dirtyPrice))
                                        ? Number(bondContractSummary.dirtyPrice).toLocaleString(numberLocale, {
                                              maximumFractionDigits: 4,
                                          })
                                        : '—'}
                                </dd>
                            </div>
                        </dl>
                    </div>
                </div>
                <div className="terminal-mini-card terminal-bond-right-summary__note">
                    <h4>{t('market.bondMacroNoteTitle', 'Faiz / enflasyon notu')}</h4>
                    <p className="terminal-viop-risk-note">
                        {t(
                            'market.bondMacroNoteBody',
                            'DİBS verilerinde Değer piyasa fiyatını, Kupon Faiz Oranı yıllık kupon oranını gösterir. Seri kodundaki D2/T2 ifadesi yılda 2 kupon ödemesine işaret ettiği için ödeme sıklığı 6 ayda bir olarak yorumlanır. Tahvil getirisi enflasyon ve politika faizi ile birlikte okunmalıdır.',
                        )}
                    </p>
                </div>
            </div>
        );
    }

    function renderMarketListPanel(forceDocked = false): ReactElement {
        const listDocked = forceDocked || isSpotTerminalLayout;
        return (
                        <div
                            ref={leftMarketPanelRef}
                            className={`terminal-card terminal-left-panel ${
                                pickerCategory === 'BOND' ? 'terminal-left-panel--page-flow' : ''
                            } ${listDocked ? 'terminal-left-panel--docked is-compact' : 'terminal-left-panel--undocked-slot'}`}
                        >
                            <div
                                className={`terminal-left-panel__inner${
                                    instrumentListOpen ? ' terminal-left-panel__inner--popover-sheet' : ''
                                }`}
                                style={instrumentListSheetStyle}
                            >
                            <div style={{ marginBottom: 6 }}>
                                <div
                                    style={{
                                        display: 'flex',
                                        alignItems: 'center',
                                        justifyContent: 'space-between',
                                        gap: 8,
                                        minWidth: 0,
                                    }}
                                >
                                    <div style={{ fontWeight: 700, minWidth: 0 }}>{t('market.marketList', 'Piyasa Listesi')}</div>
                                    <button
                                        type="button"
                                        className="terminal-market-list-expand-btn"
                                        onClick={() => {
                                            setInstrumentListOpen(false);
                                            setMarketListExpanded(true);
                                        }}
                                        aria-label={t('market.expandMarketListAria', 'Piyasa listesini büyük görünümde aç')}
                                        title={t('market.expandMarketList', 'Büyük liste')}
                                    >
                                        <Maximize2 size={18} strokeWidth={2.2} aria-hidden />
                                    </button>
                                </div>
                                {renderMarketListSubtitleKicker()}
                            </div>
                            {renderMarketListTableSection()}
                            </div>
                        </div>
        );
    }

    function renderLeftSpotColumn(): ReactElement {
        return (
            <div
                className={`terminal-left-column${
                    macroCompareEnabled ? '' : ' terminal-left-column--no-compare'
                }`}
            >
                <div className="terminal-left-column__card terminal-left-column__card--list">
                    {renderMarketListPanel()}
                </div>
                {macroCompareEnabled ? (
                    <div className="terminal-left-column__card terminal-left-column__card--compare">
                        {isMetalsPurchasingPower ? (
                            <MarketPreciousMetalSummaryCard
                                symbol={selectedSymbol}
                                displayName={selectedInstrumentVm?.displayName}
                                data={preciousMetalComparisonData}
                                tokens={chartTokens}
                            />
                        ) : (
                            <MarketPurchasingPowerSummaryLive
                                anchorDate={ppChartAnchor}
                                enabled={macroCompareEnabled}
                                range={trendChartRange}
                                candles={candles}
                                assetInTry={macroAssetInTry}
                                symbol={selectedSymbol}
                                displayName={selectedInstrumentVm?.displayName}
                                unitLabel={ppUnitLabel}
                                tokens={chartTokens}
                            />
                        )}
                    </div>
                ) : null}
                <div className="terminal-left-column__card terminal-left-column__card--macro">
                    <MarketMacroInfoCard tokens={chartTokens} queriesEnabled={macroSidebarQueriesEnabled} />
                </div>
            </div>
        );
    }

    function renderInstrumentHero(): ReactElement {
        const showHeroFxQuoteToggle =
            hero != null &&
            activeCategory !== 'FUTURES' &&
            activeCategory !== 'BOND' &&
            (activeCategory === 'CRYPTO' ||
                (activeCategory === 'EQUITY' && equitySubmarket !== 'BIST') ||
                activeCategory === 'FUNDS');

        const heroHorizonsEl =
            selectedInstrumentVm != null ? (
                <div
                    className="terminal-hero-horizons"
                    aria-label={t(
                        'market.heroHorizonStripAria',
                        'Seçili enstrüman için yaklaşık dönemsel fiyat değişimi',
                    )}
                >
                    {(
                        [
                            ['pctDay', t('market.horizonDay', 'Gün')] as const,
                            ['pctWeek', t('market.horizonWeek', 'Hafta')] as const,
                            ['pctMonth', t('market.horizonMonth', 'Ay')] as const,
                            ['pctYear', t('market.horizonYear', 'Yıl')] as const,
                        ] as const
                    ).map(([k, lab]) => {
                        const v = selectedInstrumentVm[k];
                        return (
                            <div key={k} className="terminal-hero-horizons__cell" title={lab}>
                                <span className="terminal-hero-horizons__lab">{lab}</span>
                                <span className={horizonPctClassName(v)}>{formatHorizonPct(v)}</span>
                            </div>
                        );
                    })}
                </div>
            ) : null;

        return (
            <div
                ref={terminalHeroRef}
                className={`terminal-hero ${activeCategory === 'FUTURES' ? 'terminal-hero--viop' : ''}${
                    isBondInstrumentsView ? ' terminal-hero--bond' : ''
                }${
                    isSpotTerminalLayout && activeCategory !== 'FUTURES' ? ' terminal-hero--spot-compact' : ''
                }`}
            >
                {hero && activeCategory === 'FUTURES' ? (
                    <>
                        <div className="terminal-hero-viop__selector-wrap">
                            <button
                                type="button"
                                className={`terminal-hero-selector terminal-hero-selector--viop ${
                                    instrumentListOpen ? 'is-open' : ''
                                }`}
                                onClick={handleInstrumentSelectorClick}
                                aria-expanded={!isSpotTerminalLayout && instrumentListOpen}
                                aria-haspopup="dialog"
                                title={t('market.instrumentPickerHint', 'Piyasa listesini aç / kapat')}
                            >
                            <div className="terminal-hero-viop__left">
                            <div className="terminal-hero-viop__eyebrow">{t('market.selectedInstrument', 'Seçili Enstrüman')}</div>
                            <div className="terminal-hero-viop__code" title={hero.symbol}>
                                {hero.symbol}
                            </div>
                            <div className="terminal-hero-viop__name">
                                {(() => {
                                    const snap = viopSnapshotBySymbol[normalizeSymbolKey(hero.symbol)];
                                    const n = getInstrumentDisplayName({
                                        symbol: hero.symbol,
                                        displayName: hero.displayName,
                                        name: snap?.contractName,
                                    });
                                    return n !== hero.symbol ? n : parseViopContractLabel(hero.symbol);
                                })()}
                            </div>
                            <div className="terminal-hero-viop__badges">
                                {(() => {
                                    const cat = viopCategoryFor(hero.symbol);
                                    if (!cat) return null;
                                    return (
                                        <span className={viopCatBadgeClass(cat)} title={viopAssetClassTitle(cat, t)}>
                                            {VIOP_CATEGORY_CHIP[cat].label}
                                        </span>
                                    );
                                })()}
                                <span className="instrument-highlight-chip" style={{ borderColor: 'var(--terminal-border)', color: 'var(--terminal-text)' }}>
                                    {viopAssetClassTitle(viopCategoryFor(hero.symbol), t)}
                                </span>
                                {(() => {
                                    const snap = viopSnapshotBySymbol[normalizeSymbolKey(hero.symbol)];
                                    const hint = viopDataQualityHint(snap, t);
                                    return hint ? <span className="viop-stale-pill">{hint}</span> : null;
                                })()}
                            </div>
                            <div className="terminal-hero-viop__src">
                                {t('market.source', 'Kaynak')}: {futuresHeaderMetrics?.sourceLabel ?? '—'}
                                {futuresHeaderMetrics?.delayMinutes != null && futuresHeaderMetrics.delayMinutes > 0
                                    ? ` · ${t('market.delayedData', 'Gecikmeli veri')}: ${futuresHeaderMetrics.delayMinutes} dk`
                                    : viopDelayMinutesDisplay != null && viopDelayMinutesDisplay > 0
                                      ? ` · ${viopDelayMinutesDisplay} dk`
                                      : ''}
                            </div>
                            <ChevronDown className="terminal-hero-selector__chevron" size={18} aria-hidden />
                        </div>
                            </button>
                            {heroHorizonsEl}
                        </div>
                        <div className="terminal-hero-viop__mid">
                            <div className="terminal-hero-price" title={heroScaled?.title}>
                                {heroScaled ? (
                                    <>
                                        <span style={{ opacity: 0.85 }}>{heroScaled.glyph}</span>{' '}
                                        {heroScaled.amount.toLocaleString(numberLocale, {
                                            maximumFractionDigits: viopPriceDecimals(hero.symbol),
                                        })}
                                    </>
                                ) : (
                                    '—'
                                )}
                            </div>
                            <div
                                className={`terminal-hero-change ${
                                    Math.abs(hero.changePercent) < 0.005 ? 'is-flat' : hero.trend === 'UP' ? 'up' : 'down'
                                }`}
                            >
                                {Math.abs(hero.changePercent) < 0.005 ? <>0,00% →</> : (
                                    <>
                                        {hero.changePercent >= 0 ? '+' : ''}
                                        {hero.changePercent.toFixed(2)}% {hero.trend === 'UP' ? '↑' : '↓'}
                                    </>
                                )}
                            </div>
                        </div>
                        <div className="terminal-hero-viop__metrics">
                            <dl className="terminal-hero-viop-metric-grid">
                                <dt>{t('market.bidAsk', 'Alış / Satış')}</dt>
                                <dd>
                                    {fmtViopBidAskLine(
                                        futuresHeaderMetrics?.bid ?? null,
                                        futuresHeaderMetrics?.ask ?? null,
                                        viopPriceDecimals(hero.symbol),
                                        numberLocale,
                                    )}
                                </dd>
                                <dt>{t('market.open', 'Açılış')}</dt>
                                <dd>
                                    {futuresHeaderMetrics?.open != null && Number.isFinite(futuresHeaderMetrics.open)
                                        ? futuresHeaderMetrics.open.toLocaleString(numberLocale, {
                                              maximumFractionDigits: viopPriceDecimals(hero.symbol),
                                          })
                                        : '—'}
                                </dd>
                                <dt>{t('market.dayHighLow', 'Gün Yük / Düş')}</dt>
                                <dd>
                                    {futuresHeaderMetrics?.high != null && Number.isFinite(futuresHeaderMetrics.high)
                                        ? futuresHeaderMetrics.high.toLocaleString(numberLocale, {
                                              maximumFractionDigits: viopPriceDecimals(hero.symbol),
                                          })
                                        : '—'}{' '}
                                    /{' '}
                                    {futuresHeaderMetrics?.low != null && Number.isFinite(futuresHeaderMetrics.low)
                                        ? futuresHeaderMetrics.low.toLocaleString(numberLocale, {
                                              maximumFractionDigits: viopPriceDecimals(hero.symbol),
                                          })
                                        : '—'}
                                </dd>
                                <dt>{t('market.volumeQty', 'Hacim / Adet')}</dt>
                                <dd>
                                    {futuresHeaderMetrics?.volume != null
                                        ? futuresHeaderMetrics.volume.toLocaleString(numberLocale)
                                        : '—'}{' '}
                                    /{' '}
                                    {futuresHeaderMetrics?.quantity != null
                                        ? futuresHeaderMetrics.quantity.toLocaleString(numberLocale)
                                        : '—'}
                                </dd>
                                <dt>{t('market.settlement', 'Uzlaşma')}</dt>
                                <dd>
                                    {futuresHeaderMetrics?.settlement != null && Number.isFinite(futuresHeaderMetrics.settlement)
                                        ? futuresHeaderMetrics.settlement.toLocaleString(numberLocale, {
                                              maximumFractionDigits: viopPriceDecimals(hero.symbol),
                                          })
                                        : '—'}
                                </dd>
                                <dt>{t('market.preSettlement', 'Ön uzlaşma')}</dt>
                                <dd>
                                    {futuresHeaderMetrics?.preSettlement != null && Number.isFinite(futuresHeaderMetrics.preSettlement)
                                        ? futuresHeaderMetrics.preSettlement.toLocaleString(numberLocale, {
                                              maximumFractionDigits: viopPriceDecimals(hero.symbol),
                                          })
                                        : '—'}
                                </dd>
                                <dt>{t('market.initialMargin', 'Teminat')}</dt>
                                <dd>
                                    {futuresHeaderMetrics?.initialMargin != null && Number.isFinite(futuresHeaderMetrics.initialMargin)
                                        ? futuresHeaderMetrics.initialMargin.toLocaleString(numberLocale)
                                        : '—'}
                                </dd>
                                <dt>{t('market.limitUpDown', 'Tavan / Taban')}</dt>
                                <dd>
                                    {futuresHeaderMetrics?.limitUp != null && Number.isFinite(futuresHeaderMetrics.limitUp)
                                        ? futuresHeaderMetrics.limitUp.toLocaleString(numberLocale, {
                                              maximumFractionDigits: viopPriceDecimals(hero.symbol),
                                          })
                                        : '—'}{' '}
                                    /{' '}
                                    {futuresHeaderMetrics?.limitDown != null && Number.isFinite(futuresHeaderMetrics.limitDown)
                                        ? futuresHeaderMetrics.limitDown.toLocaleString(numberLocale, {
                                              maximumFractionDigits: viopPriceDecimals(hero.symbol),
                                          })
                                        : '—'}
                                </dd>
                            </dl>
                        </div>
                    </>
                ) : (
                    <>
                        <div className="terminal-hero-selector-row">
                            <button
                                type="button"
                                className={`terminal-hero-selector terminal-hero-selector--with-inline-quote ${
                                    instrumentListOpen ? 'is-open' : ''
                                }`}
                                onClick={handleInstrumentSelectorClick}
                                aria-expanded={!isSpotTerminalLayout && instrumentListOpen}
                                aria-haspopup="dialog"
                                title={t('market.instrumentPickerHint', 'Piyasa listesini aç / kapat')}
                            >
                            <div
                                style={{
                                    display: 'flex',
                                    alignItems: 'center',
                                    gap: isSpotTerminalLayout ? 8 : 12,
                                    minWidth: 0,
                                    flex: '1 1 auto',
                                }}
                            >
                            {/* AssetLogo en basta -- onceden hero'da hic gorsel yoktu, sadece "Secili Enstrüman" yazisi
                             * ve sembol kodu vardi; kullanici "secili enstrumanin basinda adi gorunmuyor" geri bildirimi
                             * verdi. VIOP/Bond kontrat kodlarini logo CDN'e gondermek 404 doguruyor (assetBranding
                             * helper'i bunu yakalayip null donuyor), AssetLogo da fallback ikona dusuyor. */}
                            {hero && activeCategory === 'EQUITY' && equitySubmarket === 'BIST' ? (
                                <span
                                    className="bist-symbol-badge"
                                    style={{
                                        ...bistSymbolBadgeStyle(hero.symbol),
                                        width: isSpotTerminalLayout ? 28 : 36,
                                        height: isSpotTerminalLayout ? 28 : 36,
                                        fontSize: isSpotTerminalLayout ? 10 : 11,
                                    }}
                                    title={hero.symbol}
                                    aria-hidden
                                >
                                    {hero.symbol.slice(0, 2)}
                                </span>
                            ) : (
                                <AssetLogo
                                    src={
                                        hero && (activeCategory === 'EQUITY' || activeCategory === 'CRYPTO' || activeCategory === 'FX' || activeCategory === 'METALS' || activeCategory === 'FUNDS')
                                            ? getDynamicLogoUrl(hero.symbol, marketKindForCategory(activeCategory), {
                                                  equitySubmarket:
                                                      activeCategory === 'EQUITY' ? equitySubmarket : undefined,
                                              })
                                            : null
                                    }
                                    alt={hero ? `${hero.symbol} logo` : 'logo'}
                                    fallbackIcon={TrendingUp}
                                    fallbackColor={tokens.textMuted}
                                    size={isSpotTerminalLayout ? 28 : 36}
                                />
                            )}
                            <div style={{ minWidth: 0 }}>
                                <div
                                    style={{
                                        fontSize: isSpotTerminalLayout ? 10 : 12,
                                        color: tokens.textMuted,
                                        marginBottom: 2,
                                    }}
                                >
                                    {t('market.selectedInstrument', 'Seçili Enstrüman')}
                                </div>
                                <div
                                    style={{
                                        fontSize: isSpotTerminalLayout ? 16 : 22,
                                        fontWeight: 700,
                                        lineHeight: 1.15,
                                    }}
                                >
                                    {hero
                                        ? activeCategory === 'FUTURES'
                                            ? parseViopContractLabel(hero.symbol)
                                            : isBondInstrumentsView
                                            ? debtNameMap[hero.symbol] ?? hero.symbol
                                            : activeCategory === 'EQUITY' && equitySubmarket === 'BIST'
                                            ? hero.displayName
                                            : activeCategory === 'METALS'
                                            ? hero.displayName
                                            : formatAssetLabel(hero.symbol, marketKindForCategory(activeCategory))
                                        : '—'}
                                </div>
                                {isBondInstrumentsView && hero ? (
                                    <div
                                        style={{
                                            fontFamily: 'ui-monospace, monospace',
                                            fontSize: 13,
                                            fontWeight: 600,
                                            color: tokens.text,
                                            marginTop: 4,
                                            letterSpacing: 0.02,
                                        }}
                                        title={t('market.bondHeroIsinTitle', 'Tahvil ISIN kodu')}
                                    >
                                        {bondIsinDisplay(hero.symbol, t)}
                                    </div>
                                ) : (
                                    <div
                                        style={{
                                            fontSize: isSpotTerminalLayout ? 11 : 12,
                                            color: tokens.textMuted,
                                        }}
                                    >
                                        {hero?.symbol ?? ''}
                                    </div>
                                )}
                            </div>
                            </div>
                            <div className="terminal-hero-selector__quote">
                                <div
                                    className="terminal-hero-selector__price"
                                    title={
                                        isBondInstrumentsView
                                            ? t('market.bondHeroMarketPriceTip', 'Piyasa fiyatı (TRY). Bu değer tahvil faizi (yield) değildir.')
                                            : heroScaled?.title
                                    }
                                >
                                    {heroScaled ? (
                                        <>
                                            <span style={{ opacity: 0.8 }}>{heroScaled.glyph}</span>{' '}
                                            {heroScaled.amount.toLocaleString(numberLocale, { maximumFractionDigits: 4 })}
                                            {heroScaled.suffix ?? ''}
                                        </>
                                    ) : (
                                        '—'
                                    )}
                                </div>
                                <div
                                    className={`terminal-hero-selector__pct ${
                                        !hero || Math.abs(hero.changePercent) < 0.005
                                            ? 'is-flat'
                                            : hero.trend === 'UP'
                                              ? 'is-up'
                                              : 'is-down'
                                    }`}
                                    title={
                                        isBondInstrumentsView
                                            ? t(
                                                  'market.bondHeroDailyPriceChgTip',
                                                  'Günlük fiyat değişimi (%). Tahvil faizi veya garanti getiri değildir.',
                                              )
                                            : undefined
                                    }
                                    style={isBondInstrumentsView ? { display: 'flex', alignItems: 'center', gap: 6 } : undefined}
                                >
                                    {hero ? (
                                        Math.abs(hero.changePercent) < 0.005 ? (
                                            <>0,00%</>
                                        ) : (
                                            <>
                                                {hero.changePercent >= 0 ? '+' : ''}
                                                {hero.changePercent.toFixed(2)}% {hero.trend === 'UP' ? '↑' : '↓'}
                                            </>
                                        )
                                    ) : (
                                        '—'
                                    )}
                                    {isBondInstrumentsView ? (
                                        <span
                                            title={t(
                                                'market.bondHeroNotYieldTip',
                                                'Bu değer faiz oranı (yield) değildir; gösterilenler piyasa fiyatı ve fiyat performansıdır.',
                                            )}
                                            style={{ display: 'inline-flex', color: tokens.textMuted, cursor: 'help' }}
                                            aria-label={t('market.bondHeroNotYieldAria', 'Fiyat ile yield ayrımı bilgisi')}
                                        >
                                            <Info size={15} strokeWidth={2.2} aria-hidden />
                                        </span>
                                    ) : null}
                                </div>
                            </div>
                            <ChevronDown
                                className="terminal-hero-selector__chevron"
                                size={isSpotTerminalLayout ? 18 : 20}
                                aria-hidden
                            />
                        </button>
                            {heroHorizonsEl}
                            <div className="terminal-hero-selector-row__trailing">
                                {showHeroFxQuoteToggle ? (
                                    <MarketCategoryScrollTabs
                                        className="terminal-category-scroll-shell--hero-fx-quote"
                                        categories={fxQuoteScrollTabs}
                                        value={showUsdInTry ? 'TRY' : 'USD'}
                                        onChange={(id) => setShowUsdInTry(id === 'TRY')}
                                        ariaLabel={t(
                                            'market.fxQuoteToggleAria',
                                            'Liste fiyat birimi: ABD doları veya Türk lirası',
                                        )}
                                    />
                                ) : null}
                            </div>
                        </div>
                        <div
                            className="terminal-hero-hl-snippet"
                            style={{ textAlign: 'right', fontSize: 12, color: tokens.textMuted }}
                        >
                            <div title={heroScaled?.title}>
                                H:{' '}
                                <span style={{ opacity: 0.8 }}>{heroScaled?.glyph ?? ''}</span>
                                {heroScaled ? ' ' : ''}
                                {heroScaled || hero
                                    ? (heroScaled?.high ?? hero?.high ?? 0).toLocaleString(numberLocale, { maximumFractionDigits: 4 })
                                    : '—'}
                            </div>
                            <div title={heroScaled?.title}>
                                L:{' '}
                                <span style={{ opacity: 0.8 }}>{heroScaled?.glyph ?? ''}</span>
                                {heroScaled ? ' ' : ''}
                                {heroScaled || hero
                                    ? (heroScaled?.low ?? hero?.low ?? 0).toLocaleString(numberLocale, { maximumFractionDigits: 4 })
                                    : '—'}
                            </div>
                            {isBondInstrumentsView && bondContractSummary ? (
                                <>
                                    <div>
                                        {t('market.maturity', 'Vade')}: {bondContractSummary.maturityDate ?? '—'}
                                    </div>
                                    <div>
                                        {t('market.coupon', 'Kupon Oranı')}:{' '}
                                        {formatBondCouponRate(
                                            bondContractSummary.couponRate,
                                            lang === 'en' ? 'en-US' : 'tr-TR',
                                        )}
                                    </div>
                                    <div>
                                        {t('market.currency', 'Para birimi')}: {bondContractSummary.currency ?? '—'}
                                    </div>
                                </>
                            ) : null}
                            {activeCategory === 'FUTURES' && futuresHeaderMetrics ? (
                                <>
                                    <div>
                                        Δ:{' '}
                                        {futuresHeaderMetrics.changeAmount != null && Number.isFinite(futuresHeaderMetrics.changeAmount)
                                            ? futuresHeaderMetrics.changeAmount.toLocaleString(numberLocale, { maximumFractionDigits: 2 })
                                            : '—'}
                                        {futuresHeaderMetrics.changePercent != null && Number.isFinite(futuresHeaderMetrics.changePercent)
                                            ? ` (${futuresHeaderMetrics.changePercent >= 0 ? '+' : ''}${futuresHeaderMetrics.changePercent.toLocaleString(numberLocale, {
                                                  maximumFractionDigits: 2,
                                              })}%)`
                                            : ''}
                                    </div>
                                    <div>
                                        A/S:{' '}
                                        {futuresHeaderMetrics.bid != null ? futuresHeaderMetrics.bid.toLocaleString(numberLocale) : '—'} /{' '}
                                        {futuresHeaderMetrics.ask != null ? futuresHeaderMetrics.ask.toLocaleString(numberLocale) : '—'}
                                    </div>
                                    <div>
                                        Açılış / G / D:{' '}
                                        {futuresHeaderMetrics.open != null ? futuresHeaderMetrics.open.toLocaleString(numberLocale) : '—'} ·{' '}
                                        {futuresHeaderMetrics.high != null ? futuresHeaderMetrics.high.toLocaleString(numberLocale) : '—'} ·{' '}
                                        {futuresHeaderMetrics.low != null ? futuresHeaderMetrics.low.toLocaleString(numberLocale) : '—'}
                                    </div>
                                    <div>
                                        Hacim / Adet:{' '}
                                        {futuresHeaderMetrics.volume != null
                                            ? futuresHeaderMetrics.volume.toLocaleString(numberLocale)
                                            : '—'}{' '}
                                        /{' '}
                                        {futuresHeaderMetrics.quantity != null
                                            ? futuresHeaderMetrics.quantity.toLocaleString(numberLocale)
                                            : '—'}
                                    </div>
                                    <div>
                                        Uzlaşma / Ön uzlaşma:{' '}
                                        {futuresHeaderMetrics.settlement != null
                                            ? futuresHeaderMetrics.settlement.toLocaleString(numberLocale)
                                            : '—'}{' '}
                                        /{' '}
                                        {futuresHeaderMetrics.preSettlement != null
                                            ? futuresHeaderMetrics.preSettlement.toLocaleString(numberLocale)
                                            : '—'}
                                    </div>
                                    <div>
                                        Teminat / Tavan–Taban:{' '}
                                        {futuresHeaderMetrics.initialMargin != null
                                            ? futuresHeaderMetrics.initialMargin.toLocaleString(numberLocale)
                                            : '—'}{' '}
                                        /{' '}
                                        {futuresHeaderMetrics.limitUp != null ? futuresHeaderMetrics.limitUp.toLocaleString(numberLocale) : '—'} –{' '}
                                        {futuresHeaderMetrics.limitDown != null
                                            ? futuresHeaderMetrics.limitDown.toLocaleString(numberLocale)
                                            : '—'}
                                    </div>
                                    <div style={{ fontSize: 11, opacity: 0.85 }}>
                                        {futuresHeaderMetrics.sourceLabel}
                                        {futuresHeaderMetrics.delayMinutes != null
                                            ? ` · ${futuresHeaderMetrics.delayMinutes} dk gecikme`
                                            : ''}
                                    </div>
                                </>
                            ) : null}
                        </div>
                    </>
                )}
            </div>
        );
    }

    function renderMarketListExpandOverlay(): ReactElement | null {
        if (!marketListExpanded) return null;
        return createPortal(
            <div
                className="terminal-market-list-expand-overlay"
                onClick={() => setMarketListExpanded(false)}
                role="presentation"
            >
                <div
                    role="dialog"
                    aria-modal="true"
                    aria-labelledby="terminal-market-list-expand-title"
                    className="terminal-market-list-expand-dialog terminal-card"
                    style={terminalVars}
                    onClick={(e) => e.stopPropagation()}
                >
                    <div className="terminal-left-panel__inner terminal-left-panel__inner--expand-modal">
                        <div style={{ marginBottom: 6 }}>
                            <div
                                style={{
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'space-between',
                                    gap: 8,
                                    minWidth: 0,
                                }}
                            >
                                <div id="terminal-market-list-expand-title" style={{ fontWeight: 700, minWidth: 0 }}>
                                    {t('market.marketList', 'Piyasa Listesi')}
                                </div>
                                <button
                                    type="button"
                                    className="terminal-market-list-expand-btn"
                                    onClick={() => setMarketListExpanded(false)}
                                    aria-label={t('market.expandMarketListClose', 'Kapat')}
                                    title={t('market.expandMarketListClose', 'Kapat')}
                                >
                                    <X size={18} strokeWidth={2.2} aria-hidden />
                                </button>
                            </div>
                            {renderMarketListSubtitleKicker()}
                        </div>
                        {renderMarketListTableSection()}
                    </div>
                </div>
            </div>,
            document.body
        );
    }

    if (errMsg) {
        return <div className="terminal-page">{t('market.pageError', 'Piyasa terminali hatası')}: {errMsg}</div>;
    }

    return (
        <div className="terminal-page" style={terminalVars}>
            {renderMarketListExpandOverlay()}

            {instrumentListOpen && !isSpotTerminalLayout ? (
                <button
                    type="button"
                    className="terminal-market-list-backdrop"
                    aria-label={t('market.closeMarketListPopover', 'Listeyi kapat')}
                    onClick={() => setInstrumentListOpen(false)}
                />
            ) : null}

            {showDashboardBlockingSpinner ? (
                <div className="terminal-card">{t('market.loading', 'Piyasa terminali yükleniyor...')}</div>
            ) : (
                <>
                    <div className="terminal-workspace">
                        <div className="terminal-selected-instrument-row">{renderInstrumentHero()}</div>
                        <div
                            className={`terminal-grid market-main-grid${
                                isSpotTerminalLayout
                                    ? ' terminal-grid--heatmap-rail terminal-spot-unified'
                                    : isBondTerminalLayout
                                      ? ' terminal-grid--market-unified terminal-grid--bond-unified'
                                      : ' terminal-grid--list-undocked'
                            }`}
                        >
                            {isSpotTerminalLayout
                                ? renderLeftSpotColumn()
                                : isBondTerminalLayout
                                  ? renderMarketListPanel(true)
                                  : renderMarketListPanel()}

                            <div ref={centerStackRef} className="terminal-center-stack">
                        <div className="terminal-card terminal-center-panel market-chart-card">
                            <div className="terminal-controls">
                                <div className="terminal-controls__chart-toolbar">
                                    <MarketCategoryScrollTabs
                                        className="terminal-category-scroll-shell--chart-range"
                                        categories={chartRangeScrollTabs}
                                        value={activeCategory === 'EQUITY' && equitySubmarket === 'BIST' ? bistRange : range}
                                        onChange={
                                            activeCategory === 'EQUITY' && equitySubmarket === 'BIST'
                                                ? setBistRange
                                                : setRange
                                        }
                                        ariaLabel={t('market.chartRangeAria', 'Grafik zaman aralığı')}
                                    />
                                    {isBondInstrumentsView ? (
                                        <MarketCategoryScrollTabs
                                            className="terminal-category-scroll-shell--chart-mode"
                                            categories={bondChartModeTabs}
                                            value={bondChartMode}
                                            onChange={setBondChartMode}
                                            ariaLabel={t('market.chartDisplayModeAria', 'Grafik görünümü')}
                                        />
                                    ) : null}
                                    {activeCategory === 'FUTURES' ? (
                                        <MarketCategoryScrollTabs
                                            className="terminal-category-scroll-shell--chart-mode"
                                            categories={viopChartModeTabs}
                                            value={viopChartMode}
                                            onChange={setViopChartMode}
                                            ariaLabel={t('market.chartDisplayModeAria', 'Grafik görünümü')}
                                        />
                                    ) : null}
                                    {activeCategory !== 'BOND' && activeCategory !== 'FUTURES' ? (
                                        <MarketCategoryScrollTabs
                                            className="terminal-category-scroll-shell--chart-mode"
                                            categories={spotChartModeTabs}
                                            value={spotChartMode}
                                            onChange={setSpotChartMode}
                                            ariaLabel={t('market.chartDisplayModeAria', 'Grafik görünümü')}
                                        />
                                    ) : null}
                                </div>
                                <ChartIndicatorToggles
                                    showMa={showMa}
                                    showRsi={showRsi}
                                    onToggleMa={() => setShowMa((v) => !v)}
                                    onToggleRsi={() => setShowRsi((v) => !v)}
                                    rsiAvailable={chartIndicatorRsiAvailable}
                                />
                            </div>
                            {isBondInstrumentsView && bondChartMode === 'DUAL' ? (
                                <BondTerminalChart
                                    points={bondDualPoints}
                                    ma7={bondChartMa7}
                                    ma21={bondChartMa21}
                                    showMa={showMa}
                                    loading={loadingDebtHistory}
                                    trendLabel={hero?.trend}
                                    timeframeLabel={dataRangeForChart}
                                    tokens={chartTokens}
                                    showYieldSeries={selectedBondStructuredYield}
                                />
                            ) : activeCategory === 'FUTURES' && viopChartMode === 'LINE' ? (
                                <ViopTerminalChart
                                    title={viopTerminalTitle}
                                    subtitle={viopTerminalSubtitle}
                                    points={viopLinePoints}
                                    ma7={viopChartMa7}
                                    ma21={viopChartMa21}
                                    showMa={showMa}
                                    loading={loadingViopHistory}
                                    trendLabel={hero?.trend}
                                    timeframeLabel={chartRangeUiShortLabel(range)}
                                    dataTypeLabel={viopHistoryApi?.chartType ?? 'PRICE_SERIES'}
                                    tokens={chartTokens}
                                />
                            ) : activeCategory !== 'FUTURES' && activeCategory !== 'BOND' && spotChartMode === 'ANALYSIS' ? (
                                <SpotTerminalChart
                                    title={spotTerminalTitle}
                                    subtitle={spotTerminalSubtitle}
                                    candles={candles}
                                    ma7={ma7}
                                    ma21={ma21}
                                    showMa={showMa}
                                    showRsi={showRsi}
                                    rsi14={rsi14}
                                    loading={
                                        activeCategory === 'EQUITY' && equitySubmarket === 'BIST'
                                            ? loadingBistCandles
                                            : activeCategory === 'FUNDS' && fundSubmarket === 'TR'
                                              ? loadingTefasHistory || fetchingTefasHistory
                                              : loadingCandles || loadingIndicators
                                    }
                                    trendLabel={hero?.trend}
                                    timeframeLabel={chartTfLabel}
                                    chartTimePreferIstanbulBusinessDay={!terminalHourlyRange}
                                    tokens={chartTokens}
                                    onCrosshairDate={macroCompareEnabled ? onAnalysisCrosshairDate : undefined}
                                />
                            ) : (
                                <MarketTerminalChart
                                    candles={candles}
                                    ma7={
                                        isBondInstrumentsView
                                            ? bondChartMa7
                                            : activeCategory === 'FUTURES'
                                              ? viopChartMa7
                                              : ma7
                                    }
                                    ma21={
                                        isBondInstrumentsView
                                            ? bondChartMa21
                                            : activeCategory === 'FUTURES'
                                              ? viopChartMa21
                                              : ma21
                                    }
                                    rsi14={rsi14}
                                    showMa={showMa}
                                    showRsi={showRsi}
                                    markers={markers}
                                    loading={
                                        activeCategory === 'EQUITY' && equitySubmarket === 'BIST'
                                            ? loadingBistCandles
                                            : activeCategory === 'FUNDS' && fundSubmarket === 'TR'
                                              ? loadingTefasHistory || fetchingTefasHistory
                                              : loadingCandles || loadingIndicators || loadingViopHistory || loadingDebtHistory
                                    }
                                    symbol={selectedSymbol}
                                    trendLabel={hero?.trend}
                                    timeframeLabel={chartTfLabel}
                                    chartTimePreferIstanbulBusinessDay={!terminalHourlyRange}
                                    tokens={chartTokens}
                                />
                            )}
                            {activeCategory === 'FX' ? (
                                <FxEffectiveRatesComparisonSection
                                    tokens={chartTokens}
                                    selectedSymbol={selectedSymbol ?? undefined}
                                    enabled={activeCategory === 'FX'}
                                />
                            ) : null}
                        </div>
                        {/*
                         * Karşılaştırma: spot + ısı haritası rail ve VİOP/tahvil birleşik grid’de sağ sütunda.
                         */}
                        {!isSpotTerminalLayout ? renderComparisonCard('terminal-center-comparison') : null}
                        {activeCategory === 'FUTURES' && viopContractSummary ? (
                            <div className="terminal-bottom-cards">
                                <div className="terminal-mini-card">
                                    <h4>{t('market.viopContractSummaryTitle', 'Vade ve kontrat özeti')}</h4>
                                    <dl className="terminal-viop-summary-dl">
                                        <div>
                                            <dt>{t('market.underlying', 'Dayanak varlık')}</dt>
                                            <dd>{viopContractSummary.underlying}</dd>
                                        </div>
                                        <div>
                                            <dt>{t('market.maturityMonth', 'Vade ayı')}</dt>
                                            <dd>{viopContractSummary.maturity}</dd>
                                        </div>
                                        <div>
                                            <dt>{t('market.daysToExpiry', 'Vadeye kalan gün')}</dt>
                                            <dd>
                                                {viopContractSummary.daysToExpiry != null
                                                    ? viopContractSummary.daysToExpiry
                                                    : '—'}
                                            </dd>
                                        </div>
                                        <div>
                                            <dt>{t('market.settlement', 'Uzlaşma')}</dt>
                                            <dd>
                                                {viopContractSummary.settlement != null
                                                    ? viopContractSummary.settlement.toLocaleString(numberLocale, {
                                                          maximumFractionDigits: 4,
                                                      })
                                                    : '—'}
                                            </dd>
                                        </div>
                                        <div>
                                            <dt>{t('market.preSettlement', 'Ön uzlaşma')}</dt>
                                            <dd>
                                                {viopContractSummary.preSettlement != null
                                                    ? viopContractSummary.preSettlement.toLocaleString(numberLocale, {
                                                          maximumFractionDigits: 4,
                                                      })
                                                    : '—'}
                                            </dd>
                                        </div>
                                        <div>
                                            <dt>{t('market.initialMargin', 'Teminat')}</dt>
                                            <dd>
                                                {viopContractSummary.initialMargin != null
                                                    ? viopContractSummary.initialMargin.toLocaleString(numberLocale)
                                                    : '—'}
                                            </dd>
                                        </div>
                                    </dl>
                                </div>
                                <div className="terminal-mini-card">
                                    <h4>{t('market.viopRiskNoteTitle', 'Risk notu')}</h4>
                                    <p className="terminal-viop-risk-note">
                                        {t(
                                            'market.viopRiskNoteBody',
                                            'VIOP kaldıraçlı piyasadır. Fiyat hareketleri teminat gereksinimini etkileyebilir. Bu panel fiyat ve piyasa hareketini gösterir; yatırım tavsiyesi değildir.',
                                        )}
                                    </p>
                                </div>
                            </div>
                        ) : null}
                        {macroCompareEnabled ? (
                            isMetalsPurchasingPower ? (
                                <MarketPreciousMetalCompareChart
                                    data={preciousMetalComparisonData}
                                    tokens={chartTokens}
                                />
                            ) : (
                                <MarketPurchasingPowerCompareChart
                                    unitLabel={ppUnitLabel}
                                    data={purchasingPowerChartData}
                                    tokens={chartTokens}
                                />
                            )
                        ) : null}
                        </div>

                        <div
                            className={`terminal-card terminal-right-panel${
                                isBondTerminalLayout ? ' terminal-right-panel--bond' : ''
                            }`}
                            style={rightPanelStyle}
                        >
                            {renderBondInstrumentSummaryRight()}
                            <div
                                className="terminal-right-panel__insights-head"
                                style={{ fontWeight: 700, marginBottom: 8, flexShrink: 0 }}
                            >
                                {t('market.marketInsights', 'Piyasa İçgörüleri')}
                            </div>
                            <div className="terminal-right-panel-scroll">
                            {showMarketHeatmap ? (
                                <>
                                    {renderComparisonCard('terminal-right-comparison')}
                                    <div className="terminal-right-treemap-slot">
                                        <MarketFinvizTreemap
                                            tiles={insightsTreemapTilesEffective}
                                            borderColor="rgba(71, 85, 105, 0.55)"
                                            panelBg={tokens.bgCard}
                                            sectorDisplayName={heatmapSectorDisplay}
                                            floatingTooltip={{
                                                labelChange: `${t('market.change', 'Değişim')} (${chartRangeUiShortLabel(range)})`,
                                                labelSector: t('market.sector', 'Sektör'),
                                                mutedColor: tokens.textMuted,
                                            }}
                                        />
                                    </div>
                                    <div style={{ marginTop: 8 }}>
                                        <button
                                            type="button"
                                            className="terminal-btn"
                                            onClick={() => navigate('/market/heatmap')}
                                        >
                                            {t('market.detailedHeatmap', 'Detaylı ısı haritası')}
                                        </button>
                                    </div>
                                </>
                            ) : null}

                            {activeCategory === 'FUTURES' ? (
                                <>
                                    {renderComparisonCard('terminal-right-comparison')}
                                    <div className="terminal-mini-card" style={{ marginBottom: 10 }}>
                                        <h4 style={{ margin: '0 0 8px', fontSize: 13 }}>
                                            {t('market.futuresTopMovers', 'En çok hareket eden vadeliler')}
                                        </h4>
                                        <div className="terminal-mini-list" style={{ margin: 0 }}>
                                    {futuresTopMovers.map((v) => {
                                        const vm = instrumentVms.find((x) => x.symbol === v.symbol);
                                        const sub =
                                            vm && vm.displayName && vm.displayName !== v.symbol ? vm.displayName : '';
                                        return (
                                        <div key={`mv-${v.symbol}`} className="terminal-mini-item">
                                            <span style={{ minWidth: 0, flex: '1 1 120px' }}>
                                                <span className="terminal-mini-item__code" title={v.symbol}>
                                                    {v.symbol}
                                                </span>
                                                {sub ? (
                                                    <div
                                                        style={{
                                                            fontSize: 10,
                                                            color: tokens.textMuted,
                                                            lineHeight: 1.2,
                                                            marginTop: 2,
                                                            whiteSpace: 'nowrap',
                                                            overflow: 'hidden',
                                                            textOverflow: 'ellipsis',
                                                        }}
                                                        title={sub}
                                                    >
                                                        {sub}
                                                    </div>
                                                ) : null}
                                            </span>
                                            <span className={v.changePercent >= 0 ? 'terminal-pct-pos' : 'terminal-pct-neg'}>
                                                {v.changePercent >= 0 ? '+' : ''}
                                                {v.changePercent.toLocaleString(numberLocale, { maximumFractionDigits: 2 })}%
                                            </span>
                                        </div>
                                        );
                                    })}
                                        </div>
                                    </div>
                                    <div className="terminal-mini-card" style={{ marginBottom: 10 }}>
                                        <h4 style={{ margin: '0 0 8px', fontSize: 13 }}>
                                            {t('market.futuresVolumeLeaders', 'En yüksek hacim')}
                                        </h4>
                                        <div className="terminal-mini-list" style={{ margin: 0 }}>
                                    {futuresVolumeLeaders.map((v) => {
                                        const vm = instrumentVms.find((x) => x.symbol === v.symbol);
                                        const sub =
                                            vm && vm.displayName && vm.displayName !== v.symbol ? vm.displayName : '';
                                        return (
                                        <div key={`vol-${v.symbol}`} className="terminal-mini-item">
                                            <span style={{ minWidth: 0, flex: '1 1 120px' }}>
                                                <span className="terminal-mini-item__code" title={v.symbol}>
                                                    {v.symbol}
                                                </span>
                                                {sub ? (
                                                    <div
                                                        style={{
                                                            fontSize: 10,
                                                            color: tokens.textMuted,
                                                            lineHeight: 1.2,
                                                            marginTop: 2,
                                                            whiteSpace: 'nowrap',
                                                            overflow: 'hidden',
                                                            textOverflow: 'ellipsis',
                                                        }}
                                                        title={sub}
                                                    >
                                                        {sub}
                                                    </div>
                                                ) : null}
                                            </span>
                                            <span>{v.volume.toLocaleString(numberLocale)}</span>
                                        </div>
                                        );
                                    })}
                                        </div>
                                    </div>
                                    <div className="terminal-mini-card" style={{ marginBottom: 10 }}>
                                        <h4 style={{ margin: '0 0 8px', fontSize: 13 }}>
                                            {t('market.futuresTopPositive', 'En pozitif')}
                                        </h4>
                                        <div className="terminal-mini-list" style={{ margin: 0 }}>
                                    {futuresTopPositive.map((v) => {
                                        const vm = instrumentVms.find((x) => x.symbol === v.symbol);
                                        const sub =
                                            vm && vm.displayName && vm.displayName !== v.symbol ? vm.displayName : '';
                                        return (
                                        <div key={`tp-${v.symbol}`} className="terminal-mini-item">
                                            <span style={{ minWidth: 0, flex: '1 1 120px' }}>
                                                <span className="terminal-mini-item__code" title={v.symbol}>
                                                    {v.symbol}
                                                </span>
                                                {sub ? (
                                                    <div
                                                        style={{
                                                            fontSize: 10,
                                                            color: tokens.textMuted,
                                                            lineHeight: 1.2,
                                                            marginTop: 2,
                                                            whiteSpace: 'nowrap',
                                                            overflow: 'hidden',
                                                            textOverflow: 'ellipsis',
                                                        }}
                                                        title={sub}
                                                    >
                                                        {sub}
                                                    </div>
                                                ) : null}
                                            </span>
                                            <span className="terminal-pct-pos">
                                                +{v.changePercent.toLocaleString(numberLocale, { maximumFractionDigits: 2 })}%
                                            </span>
                                        </div>
                                        );
                                    })}
                                        </div>
                                        <h4 style={{ margin: '12px 0 8px', fontSize: 13 }}>
                                            {t('market.futuresTopNegative', 'En negatif')}
                                        </h4>
                                        <div className="terminal-mini-list" style={{ margin: 0 }}>
                                    {futuresTopNegative.map((v) => {
                                        const vm = instrumentVms.find((x) => x.symbol === v.symbol);
                                        const sub =
                                            vm && vm.displayName && vm.displayName !== v.symbol ? vm.displayName : '';
                                        return (
                                        <div key={`tn-${v.symbol}`} className="terminal-mini-item">
                                            <span style={{ minWidth: 0, flex: '1 1 120px' }}>
                                                <span className="terminal-mini-item__code" title={v.symbol}>
                                                    {v.symbol}
                                                </span>
                                                {sub ? (
                                                    <div
                                                        style={{
                                                            fontSize: 10,
                                                            color: tokens.textMuted,
                                                            lineHeight: 1.2,
                                                            marginTop: 2,
                                                            whiteSpace: 'nowrap',
                                                            overflow: 'hidden',
                                                            textOverflow: 'ellipsis',
                                                        }}
                                                        title={sub}
                                                    >
                                                        {sub}
                                                    </div>
                                                ) : null}
                                            </span>
                                            <span className="terminal-pct-neg">
                                                {v.changePercent.toLocaleString(numberLocale, { maximumFractionDigits: 2 })}%
                                            </span>
                                        </div>
                                        );
                                    })}
                                        </div>
                                    </div>
                                    <div className="terminal-mini-card">
                                        <div className="terminal-mini-list" style={{ margin: 0 }}>
                                    <div className="terminal-mini-item">
                                        <span>{t('market.breadth', 'Genel yön')}</span>
                                        <span>{futuresBreadthLabel ?? '—'}</span>
                                    </div>
                                    <div className="terminal-mini-item">
                                        <span>{t('market.dataDelay', 'Veri gecikmesi')}</span>
                                        <span>
                                            {viopDelayMinutesDisplay != null && viopDelayMinutesDisplay > 0
                                                ? `${viopDelayMinutesDisplay} dk`
                                                : '—'}
                                        </span>
                                    </div>
                                        </div>
                                    </div>
                                </>
                            ) : null}

                            {isBondInstrumentsView ? (
                                <>
                                    <div className="terminal-mini-card" style={{ marginBottom: 10 }}>
                                        <h4 style={{ margin: '0 0 8px', fontSize: 13 }}>
                                            {t('market.bondTopMovers', 'En çok hareket eden tahviller')}
                                        </h4>
                                        <div className="terminal-mini-list" style={{ margin: 0 }}>
                                            {bondTopMovers.map((v) => {
                                                const name = debtNameMap[v.symbol] ?? v.symbol;
                                                const sub = name !== v.symbol ? name : '';
                                                return (
                                                    <div key={`bond-mv-${v.symbol}`} className="terminal-mini-item">
                                                        <span style={{ minWidth: 0, flex: '1 1 120px' }}>
                                                            <span className="terminal-mini-item__code" title={v.symbol}>
                                                                {v.symbol}
                                                            </span>
                                                            {sub ? (
                                                                <div
                                                                    style={{
                                                                        fontSize: 10,
                                                                        color: tokens.textMuted,
                                                                        lineHeight: 1.2,
                                                                        marginTop: 2,
                                                                        whiteSpace: 'nowrap',
                                                                        overflow: 'hidden',
                                                                        textOverflow: 'ellipsis',
                                                                    }}
                                                                    title={sub}
                                                                >
                                                                    {sub}
                                                                </div>
                                                            ) : null}
                                                        </span>
                                                        <span className={v.changePercent >= 0 ? 'terminal-pct-pos' : 'terminal-pct-neg'}>
                                                            {v.changePercent >= 0 ? '+' : ''}
                                                            {v.changePercent.toLocaleString(numberLocale, { maximumFractionDigits: 2 })}%
                                                        </span>
                                                    </div>
                                                );
                                            })}
                                        </div>
                                    </div>
                                    <div className="terminal-mini-card">
                                        <div className="terminal-mini-list" style={{ margin: 0 }}>
                                            <div className="terminal-mini-item">
                                                <span>{t('market.breadth', 'Genel yön')}</span>
                                                <span>{bondBreadthLabel ?? '—'}</span>
                                            </div>
                                            <div className="terminal-mini-item">
                                                <span>{t('market.dataFrequency', 'Veri frekansı')}</span>
                                                <span>{t('market.bondDataFrequencyValue', 'Günlük / EVDS')}</span>
                                            </div>
                                            <div className="terminal-mini-item">
                                                <span>{t('market.source', 'Kaynak')}</span>
                                                <span>{t('market.bondSourceValue', 'TCMB / Borsa İstanbul')}</span>
                                            </div>
                                        </div>
                                    </div>
                                </>
                            ) : null}

                            {activeCategory === 'FX' ? (
                                <div className="terminal-mini-list">
                                    <strong style={{ fontSize: 13 }}>{t('market.fxSpreadVolTitle', 'Makas / volatilite')}</strong>
                                    {instruments.slice(0, 8).map((ins) => {
                                        const vk = `FX:${normalizeSymbolKey(ins.symbol)}`;
                                        const dv = volatilityByKey[vk];
                                        const volPct =
                                            dv != null && Number.isFinite(dv) && dv > 0
                                                ? (dv * 100).toLocaleString(numberLocale, { maximumFractionDigits: 2 })
                                                : Math.abs(ins.changePercent).toLocaleString(numberLocale, { maximumFractionDigits: 2 });
                                        return (
                                            <div key={`fx-metric-${ins.symbol}`} className="terminal-mini-item">
                                                <span>{ins.symbol}</span>
                                                <span>
                                                    {t('market.spreadShort', 'Makas')}{' '}
                                                    {(ins.metrics?.basis ?? 0).toLocaleString(numberLocale, { maximumFractionDigits: 4 })} ·{' '}
                                                    {t('market.volShort', 'Vol')} {volPct}%
                                                </span>
                                            </div>
                                        );
                                    })}
                                </div>
                            ) : null}

                            {activeCategory === 'CRYPTO' ? (
                                <div className="terminal-mini-list">
                                    <strong style={{ fontSize: 13 }}>{t('market.cryptoDominance', 'Dominans (pay)')}</strong>
                                    {cryptoDominance.map((r) => (
                                        <div key={`d-${r.symbol}`} className="terminal-mini-item">
                                            <span>{r.symbol}</span>
                                            <span>%{r.dominancePct.toLocaleString(numberLocale, { maximumFractionDigits: 2 })}</span>
                                        </div>
                                    ))}
                                </div>
                            ) : null}
                            </div>
                        </div>
                    </div>
                    </div>

                    <div
                        className="terminal-page-footnote"
                        style={{ marginTop: 8, display: 'flex', gap: 12, fontSize: 11, color: 'var(--terminal-muted)' }}
                    >
                        <span>{t('market.viopContracts', 'VİOP kontrat')}: {viopContracts.length}</span>
                        <span>{t('market.bondInstruments', 'Tahvil enstrüman')}: {debtCatalog.length}</span>
                    </div>
                </>
            )}
            {isDetailPanelOpen && selectedInstrumentVm ? (
                <>
                <button
                    type="button"
                    className="instrument-drawer-backdrop"
                    aria-label={t('market.drawerCloseAria', 'Detay panelini kapat')}
                    onClick={() => setIsDetailPanelOpen(false)}
                />
                <aside
                    className={`instrument-drawer ${selectedInstrumentVm.type === 'FUTURES' ? 'instrument-drawer--viop' : ''}`}
                >
                    <div className="instrument-drawer-head">
                        <strong>{t('market.instrumentDetail', 'Enstrüman Detayı')}</strong>
                        <button
                            type="button"
                            className="terminal-btn instrument-drawer-close"
                            onClick={() => setIsDetailPanelOpen(false)}
                        >
                            {t('common.close', 'Kapat')}
                        </button>
                    </div>
                    <div className="instrument-drawer-body">
                        <div className="instrument-drawer-symbol">
                            <div style={{ minWidth: 0, flex: 1 }}>
                                <div style={{ fontWeight: 700 }}>
                                    {selectedInstrumentVm.type === 'BOND'
                                        ? formatBondDisplayName(selectedInstrumentVm.symbol)
                                        : selectedInstrumentVm.symbol}
                                </div>
                                <div className="instrument-drawer-subtext">
                                    {selectedInstrumentVm.type === 'BOND'
                                        ? bondIsinDisplay(selectedInstrumentVm.symbol, t)
                                        : selectedInstrumentVm.displayName}
                                </div>
                                {selectedInstrumentVm.type === 'FUTURES' ? (
                                    <div className="viop-drawer-kicker" style={{ marginTop: 8 }}>
                                        {(() => {
                                            const cat = viopCategoryFor(selectedInstrumentVm.symbol);
                                            if (!cat) return null;
                                            return (
                                                <span className={viopCatBadgeClass(cat)} title={viopAssetClassTitle(cat, t)}>
                                                    {VIOP_CATEGORY_CHIP[cat].label}
                                                </span>
                                            );
                                        })()}
                                        <span className="instrument-highlight-chip" style={{ borderColor: 'var(--terminal-border)', color: 'var(--terminal-text)' }}>
                                            {viopAssetClassTitle(viopCategoryFor(selectedInstrumentVm.symbol), t)}
                                        </span>
                                        {selectedInstrumentVm.expiryDate ? (
                                            <span className="instrument-highlight-chip">{selectedInstrumentVm.expiryDate}</span>
                                        ) : null}
                                    </div>
                                ) : null}
                            </div>
                        </div>
                        {selectedInstrumentVm.type === 'FUTURES' ? (
                            <div className="instrument-drawer--viop-scroll">
                                {(() => {
                                    const symKey = normalizeSymbolKey(selectedInstrumentVm.symbol);
                                    const s = viopSnapshotBySymbol[symKey];
                                    const fd = viopPriceDecimals(selectedInstrumentVm.symbol);
                                    const fmt = (n: number | null | undefined) =>
                                        n != null && Number.isFinite(Number(n))
                                            ? Number(n).toLocaleString(numberLocale, { maximumFractionDigits: fd, minimumFractionDigits: 0 })
                                            : '—';
                                    const chgCls =
                                        (s?.changePercent != null && Number(s.changePercent) >= 0) ||
                                        (s == null && selectedInstrumentVm.changePercent >= 0)
                                            ? 'terminal-pct-pos'
                                            : 'terminal-pct-neg';
                                    const openVal = s?.open ?? s?.openPrice;
                                    return (
                                        <>
                                            <section className="viop-drawer-section">
                                                <h3 className="viop-drawer-section__title">{t('market.viopSectionPrice', 'Fiyat özeti')}</h3>
                                                <div className="viop-drawer-kv">
                                                    <span className="viop-drawer-kv__label">{t('market.last', 'Son')}</span>
                                                    <span className="viop-drawer-kv__val">{s?.last != null ? fmt(s.last) : drawerInstrumentPrice ? fmt(drawerInstrumentPrice.amount) : '—'}</span>
                                                    <span className="viop-drawer-kv__label">{t('market.change', 'Değişim')}</span>
                                                    <span className={`viop-drawer-kv__val ${chgCls}`}>
                                                        {s?.changePercent != null && Number.isFinite(Number(s.changePercent))
                                                            ? `${Number(s.changePercent) >= 0 ? '+' : ''}${Number(s.changePercent).toFixed(2)}%`
                                                            : `${selectedInstrumentVm.changePercent >= 0 ? '+' : ''}${selectedInstrumentVm.changePercent.toFixed(2)}%`}
                                                    </span>
                                                    <span className="viop-drawer-kv__label">{t('market.changeTlPct', 'Değişim (TL / %)')}</span>
                                                    <span className="viop-drawer-kv__val">
                                                        {fmt(s?.changeAmount)} /{' '}
                                                        {s?.changePercent != null && Number.isFinite(Number(s.changePercent))
                                                            ? `${Number(s.changePercent).toFixed(2)}%`
                                                            : '—'}
                                                    </span>
                                                    <span className="viop-drawer-kv__label">{t('market.bidAsk', 'Alış / Satış')}</span>
                                                    <span className="viop-drawer-kv__val">
                                                        {fmtViopBidAskLine(s?.bid ?? null, s?.ask ?? null, fd, numberLocale)}
                                                    </span>
                                                    <span className="viop-drawer-kv__label">{t('market.open', 'Açılış')}</span>
                                                    <span className="viop-drawer-kv__val">{fmt(openVal)}</span>
                                                    <span className="viop-drawer-kv__label">{t('market.dayHighLow', 'Gün Yük / Düş')}</span>
                                                    <span className="viop-drawer-kv__val">
                                                        {fmt(s?.high)} / {fmt(s?.low)}
                                                    </span>
                                                </div>
                                            </section>
                                            <section className="viop-drawer-section">
                                                <h3 className="viop-drawer-section__title">{t('market.viopSectionActivity', 'Piyasa aktivitesi')}</h3>
                                                <div className="viop-drawer-kv">
                                                    <span className="viop-drawer-kv__label">{t('market.volume', 'Hacim')}</span>
                                                    <span className="viop-drawer-kv__val">
                                                        {s?.volume != null ? Number(s.volume).toLocaleString(numberLocale) : '—'}
                                                    </span>
                                                    <span className="viop-drawer-kv__label">{t('market.quantity', 'Adet')}</span>
                                                    <span className="viop-drawer-kv__val">
                                                        {s?.quantity != null ? Number(s.quantity).toLocaleString(numberLocale) : '—'}
                                                    </span>
                                                    <span className="viop-drawer-kv__label">{t('market.settlement', 'Uzlaşma')}</span>
                                                    <span className="viop-drawer-kv__val">{fmt(s?.settlement)}</span>
                                                    <span className="viop-drawer-kv__label">{t('market.preSettlement', 'Ön uzlaşma')}</span>
                                                    <span className="viop-drawer-kv__val">{fmt(s?.preSettlement)}</span>
                                                    <span className="viop-drawer-kv__label">{t('market.limitUpDown', 'Tavan / Taban')}</span>
                                                    <span className="viop-drawer-kv__val">
                                                        {fmt(s?.limitUp)} / {fmt(s?.limitDown)}
                                                    </span>
                                                </div>
                                            </section>
                                            <section className="viop-drawer-section">
                                                <h3 className="viop-drawer-section__title">{t('market.viopSectionContract', 'Kontrat bilgisi')}</h3>
                                                <div className="viop-drawer-kv">
                                                    <span className="viop-drawer-kv__label">{t('market.underlying', 'Dayanak varlık')}</span>
                                                    <span className="viop-drawer-kv__val">{s?.underlying ?? '—'}</span>
                                                    <span className="viop-drawer-kv__label">{t('market.contractMonth', 'Vade ayı')}</span>
                                                    <span className="viop-drawer-kv__val">
                                                        {s?.maturityMonth != null ? String(s.maturityMonth) : selectedInstrumentVm.contractMonth ?? '—'}
                                                    </span>
                                                    <span className="viop-drawer-kv__label">{t('market.contractYear', 'Vade yılı')}</span>
                                                    <span className="viop-drawer-kv__val">
                                                        {s?.maturityYear != null ? String(s.maturityYear) : '—'}
                                                    </span>
                                                    <span className="viop-drawer-kv__label">{t('market.lastTradeExpiry', 'Son işlem / expiry')}</span>
                                                    <span className="viop-drawer-kv__val">{selectedInstrumentVm.expiryDate ?? '—'}</span>
                                                    <span className="viop-drawer-kv__label">{t('market.priceStep', 'Fiyat adımı')}</span>
                                                    <span className="viop-drawer-kv__val">{s?.priceStep != null ? String(s.priceStep) : '—'}</span>
                                                    <span className="viop-drawer-kv__label">Long / Short</span>
                                                    <span className="viop-drawer-kv__val">{selectedInstrumentVm.longShort ?? 'NÖTR'}</span>
                                                </div>
                                            </section>
                                            <section className="viop-drawer-section">
                                                <h3 className="viop-drawer-section__title">{t('market.viopSectionRisk', 'Risk / teminat')}</h3>
                                                <div className="viop-drawer-kv">
                                                    <span className="viop-drawer-kv__label">{t('market.initialMargin', 'Başlangıç teminatı')}</span>
                                                    <span className="viop-drawer-kv__val">{fmt(s?.initialMargin)}</span>
                                                    <span className="viop-drawer-kv__label">{t('market.marginRequirement', 'Teminat gereksinimi')}</span>
                                                    <span className="viop-drawer-kv__val">
                                                        {Number(selectedInstrumentVm.marginRequirement ?? 0).toLocaleString(numberLocale, {
                                                            maximumFractionDigits: 2,
                                                        })}
                                                    </span>
                                                    <span className="viop-drawer-kv__label">{t('market.delayAndSource', 'Gecikme / kaynak')}</span>
                                                    <span className="viop-drawer-kv__val">
                                                        {s?.delayMinutes != null ? `${s.delayMinutes} dk · ` : ''}
                                                        {s?.sourceLabel ?? '—'}
                                                    </span>
                                                </div>
                                            </section>
                                            <section className="viop-drawer-section">
                                                <h3 className="viop-drawer-section__title">{t('market.viopSectionNotes', 'Açıklama')}</h3>
                                                <p className="viop-drawer-notes">
                                                    {s?.contractName?.trim()
                                                        ? s.contractName.trim()
                                                        : t(
                                                              'market.viopMetaFallback',
                                                              'Bu VİOP kontratı için dayanak, vade ve teminat bilgileri İş Yatırım verisiyle gösterilmektedir.'
                                                          )}
                                                </p>
                                            </section>
                                        </>
                                    );
                                })()}
                                <div style={{ marginTop: 10 }}>
                                    <button type="button" className="terminal-btn active" onClick={() => navigate('/portfolio')}>
                                        {t('nav.portfolio', 'Spot Portföyüm')}
                                    </button>
                                </div>
                                <div style={{ marginTop: 12 }}>
                                    {selectedInstrumentMeta ? (
                                        <div className="instrument-meta-card">
                                            <div className="instrument-meta-title">
                                                {selectedInstrumentMeta.title}
                                            </div>
                                            <div className="instrument-meta-grid">
                                                {selectedInstrumentMeta.rows.map(([label, value]) => (
                                                    <div key={label}>
                                                        <div className="instrument-meta-label">{label}</div>
                                                        <div className="instrument-meta-value">{value}</div>
                                                    </div>
                                                ))}
                                            </div>
                                            <div className="instrument-meta-description">{selectedInstrumentMeta.description}</div>
                                        </div>
                                    ) : null}
                                </div>
                            </div>
                        ) : (
                            <>
                                <div className="instrument-drawer-grid">
                                    <div>{selectedInstrumentVm.type === 'BOND' ? t('market.bondColMarketPrice', 'Piyasa Fiyatı') : 'Fiyat'}</div>
                                    <div
                                        title={
                                            selectedInstrumentVm.type === 'BOND'
                                                ? t('market.bondHeroMarketPriceTip', 'Piyasa fiyatı (TRY). Bu değer tahvil faizi (yield) değildir.')
                                                : drawerInstrumentPrice?.title
                                        }
                                    >
                                        {drawerInstrumentPrice ? (
                                            <>
                                                <span style={{ opacity: 0.8 }}>{drawerInstrumentPrice.glyph}</span>{' '}
                                                {drawerInstrumentPrice.amount.toLocaleString(numberLocale, {
                                                    maximumFractionDigits: 4,
                                                })}
                                                {drawerInstrumentPrice.suffix ?? ''}
                                            </>
                                        ) : (
                                            '—'
                                        )}
                                    </div>
                                    <div>
                                        {selectedInstrumentVm.type === 'BOND'
                                            ? t('market.bondDrawerDailyPriceChg', 'Günlük fiyat değişimi')
                                            : 'Değişim'}
                                    </div>
                                    <div
                                        className={
                                            selectedInstrumentVm.changePercent >= 0 ? 'terminal-pct-pos' : 'terminal-pct-neg'
                                        }
                                        title={
                                            selectedInstrumentVm.type === 'BOND'
                                                ? t(
                                                      'market.bondHeroDailyPriceChgTip',
                                                      'Günlük fiyat değişimi (%). Tahvil faizi veya garanti getiri değildir.',
                                                  )
                                                : undefined
                                        }
                                    >
                                        {selectedInstrumentVm.changePercent >= 0 ? '+' : ''}
                                        {selectedInstrumentVm.changePercent.toFixed(2)}%
                                    </div>
                                    {selectedInstrumentVm.type === 'BOND' ? (
                                        <>
                                            <div>{bondIsinLabel(t)}</div>
                                            <div style={{ fontFamily: 'ui-monospace, monospace', fontWeight: 600 }}>{selectedInstrumentVm.symbol}</div>
                                            <div>{t('market.bondTypeField', 'Tür')}</div>
                                            <div>
                                                {bondTypeLabel(
                                                    bondTypeFromInstrument(
                                                        selectedInstrumentVm.symbol,
                                                        debtMetaMap[normalizeSymbolKey(selectedInstrumentVm.symbol)]
                                                            ?.name ??
                                                            debtNameMap[normalizeSymbolKey(selectedInstrumentVm.symbol)] ??
                                                            selectedInstrumentVm.displayName,
                                                        debtMetaMap[normalizeSymbolKey(selectedInstrumentVm.symbol)]
                                                            ?.issuer,
                                                    ),
                                                    t,
                                                )}
                                            </div>
                                            <div>{t('market.drawer.maturityDate', 'Vade tarihi')}</div>
                                            <div>{formatDateTr(selectedInstrumentVm.maturityDate ?? extractMaturityDate(selectedInstrumentVm.symbol))}</div>
                                            <div>{t('market.drawer.daysToMaturity', 'Vadeye kalan gün')}</div>
                                            <div>
                                                {(() => {
                                                    const days = selectedInstrumentVm.daysToMaturity ?? getRemainingDays(selectedInstrumentVm.symbol);
                                                    return (
                                                        <span className="instrument-highlight-chip">
                                                            {Number.isFinite(days) ? `${days} gün kaldı` : '—'}
                                                        </span>
                                                    );
                                                })()}
                                            </div>
                                            <div
                                                title={t(
                                                    'market.bondCouponTip',
                                                    'Kupon oranı, tahvilin nominal değer üzerinden yaptığı faiz ödemesidir; vadeye kadar getiri değildir.',
                                                )}
                                            >
                                                {t('market.coupon', 'Kupon Oranı')}
                                            </div>
                                            <div>
                                                {formatBondCouponRate(
                                                    selectedInstrumentVm.couponRate,
                                                    lang === 'en' ? 'en-US' : 'tr-TR',
                                                )}
                                            </div>
                                            <div
                                                title={t(
                                                    'market.bondCouponFreqTip',
                                                    'Kupon ödeme sıklığı, tahvilin yılda kaç kez faiz ödemesi yaptığını gösterir. Bu değer EVDS seri kodundan çıkarılmış olabilir.',
                                                )}
                                            >
                                                {t('market.bondCouponFreq', 'Kupon Ödeme Sıklığı')}
                                            </div>
                                            <div>
                                                {(() => {
                                                    const key = normalizeSymbolKey(selectedInstrumentVm.symbol);
                                                    const label =
                                                        debtLatestMap[key]?.couponFrequencyLabel ??
                                                        debtMetaMap[key]?.couponFrequencyLabel ??
                                                        selectedInstrumentVm.couponFrequencyLabel;
                                                    return label?.trim() ? label : '—';
                                                })()}
                                            </div>
                                        </>
                                    ) : null}
                                </div>
                                <div style={{ marginTop: 10 }}>
                                    <button type="button" className="terminal-btn active" onClick={() => navigate('/portfolio')}>
                                        {t('nav.portfolio', 'Spot Portföyüm')}
                                    </button>
                                </div>
                                <div style={{ marginTop: 12 }}>
                                    <div className="instrument-meta-card">
                                        <div className="instrument-meta-title">{selectedInstrumentMeta?.title ?? 'Enstrüman Bilgisi'}</div>
                                        {selectedInstrumentMeta ? (
                                            <>
                                                <div className="instrument-meta-grid">
                                                    {selectedInstrumentMeta.rows.map(([label, value]) => (
                                                        <div key={label}>
                                                            <div className="instrument-meta-label">{label}</div>
                                                            <div className="instrument-meta-value">{value}</div>
                                                        </div>
                                                    ))}
                                                </div>
                                                <div className="instrument-meta-description">{selectedInstrumentMeta.description}</div>
                                            </>
                                        ) : (
                                            <div className="instrument-meta-empty">
                                                {t(
                                                    'market.drawer.emptyMeta',
                                                    'Bu enstrüman için özel açıklama yakında eklenecek.',
                                                )}
                                            </div>
                                        )}
                                    </div>
                                </div>
                            </>
                        )}
                    </div>
                </aside>
                </>
            ) : null}
            {priceAlertTarget ? (
                <PriceAlertModal
                    open
                    onClose={() => setPriceAlertTarget(null)}
                    assetType={priceAlertTarget.assetType}
                    symbol={priceAlertTarget.symbol}
                    displayName={priceAlertTarget.displayName}
                    referencePrice={priceAlertTarget.referencePrice}
                    priceCurrency={priceAlertTarget.priceCurrency}
                />
            ) : null}
            <FxEffectiveRatesModal
                open={effectiveRatesOpen}
                onClose={() => setEffectiveRatesOpen(false)}
                tokens={{
                    bg: tokens.bg,
                    bgCard: tokens.bgCard,
                    border: tokens.border,
                    text: tokens.text,
                    textMuted: tokens.textMuted,
                }}
            />
        </div>
    );
}
