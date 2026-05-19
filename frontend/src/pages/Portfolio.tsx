import { Fragment, useCallback, useEffect, useMemo, useRef, useState, type CSSProperties, type DragEvent } from 'react';
import { Area, AreaChart, CartesianGrid, Line, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { useMutation, useQuery, useQueries, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { manualPortfolioKeys } from '../queries/manualPortfolioKeys';
import { financeClient } from '../api/client';
import { useTheme } from '../theme/ThemeContext';
import { useRefetchOnFocus } from '../hooks/useRefetchOnFocus';
import { usePolling } from '../hooks/usePolling';
import { Bell, FileText, LineChart as LineChartIcon, Plus, Scale, TrendingDown, TrendingUp } from 'lucide-react';
import { PriceAlertModal } from '../components/priceAlert/PriceAlertModal';
import { portfolioTypeToPriceAlertAsset } from '../utils/priceAlertAsset';
import type { PriceAlertAssetType } from '../types/priceAlert';
import './TerminalPages.css';
import './Portfolio.css';
import { useLanguage } from '../i18n/LanguageContext';
import {
    ManualInvestmentAnalysisSection,
    type ManualInvestmentAnalysisSectionHandle,
} from '../components/portfolio/ManualInvestmentAnalysisSection';
import {
    evaluatePortfolioInsightNotifications,
    getManualPortfolioInsights,
    getManualPositions,
    getManualSummary,
    getManualTimeseries,
    getManualTimeseriesSegment,
    getManualSoldHoldHypotheticalTimeseries,
    getManualSoldHoldHypotheticalSegment,
    getManualSoldLifecyclePnlTimeseries,
    getManualSoldLifecyclePnlSegment,
    getManualOpenUnrealizedPnlTimeseries,
    getManualOpenUnrealizedPnlSegment,
    readFinanceApiError,
} from '../services/manualPortfolioApi';
import { PfHelpTerm } from '../components/portfolio/PfHelpTerm';
import { notificationKeys } from '../queries/notificationKeys';
import type { ManualPortfolioTimeseriesPoint, ManualPortfolioView } from '../types/manualPortfolio';
import {
    buildConcentrationRiskLine,
    buildRadarRiskFacts,
    buildSoldScenarioSummary,
    computeFallbackHealthScore,
    isFiniteNum,
    resolveHealthScore,
} from '../utils/portfolioSmartAnalysis';
type SnapRange = '1M' | '3M' | '6M' | '1Y' | 'ALL';

type ChartSeriesMode =
    | 'value'
    | 'cost'
    | 'pnl'
    | 'returnPct'
    | 'positionCount'
    | 'unrealizedPnl'
    | 'soldHoldHypothetical'
    | 'soldLifecyclePnl';

type TimeseriesChartMode = 'value' | 'cost' | 'pnl' | 'returnPct';

type PositionCountVariant = 'open' | 'sold';

const MAX_CHART_OVERLAYS = 6;

type ChartOverlaySpec = {
    id: string;
    mode: 'TYPE' | 'SYMBOL';
    key: string;
    label: string;
    color: string;
};

type DistRow = { name: string; typeKey: string; value: number; colorKey?: string };

type DistViewMode = 'category' | 'asset';

const TABLE_PAGE = 10;
const POSITION_CHART_MAX_POINTS = 400;

function addCalendarDaysYmd(ymd: string, days: number): string {
    const [y, m, d] = ymd.split('-').map((x) => Number(x));
    const dt = new Date(y, m - 1, d);
    dt.setDate(dt.getDate() + days);
    return formatLocalYmd(dt);
}

function isPositionOpenOnYmd(p: ManualPortfolioView, ymd: string): boolean {
    const buy = String(p.buyDate ?? '').trim();
    if (!buy || ymd < buy) return false;
    if (String(p.status).toUpperCase() !== 'SOLD') return true;
    const sell = p.sellDate?.trim();
    if (!sell) return true;
    return ymd < sell;
}

/** Alım günü dahil, satış günü hariç (backend ile uyumlu). */
function buildOpenPositionCountSeries(
    positions: ManualPortfolioView[],
    rangeStart: Date,
    rangeEnd: Date,
    locale: string,
): { key: string; t: number; label: string; balance: number }[] {
    if (!positions.length) return [];
    let earliest: string | null = null;
    for (const p of positions) {
        const bd = String(p.buyDate ?? '').trim();
        if (!bd) continue;
        if (earliest == null || bd < earliest) earliest = bd;
    }
    if (!earliest) return [];

    const rangeStartYmd = formatLocalYmd(rangeStart);
    const rangeEndYmd = formatLocalYmd(rangeEnd);
    const effectiveStartYmd = earliest > rangeStartYmd ? earliest : rangeStartYmd;
    if (effectiveStartYmd > rangeEndYmd) return [];

    const t0 = new Date(`${effectiveStartYmd}T12:00:00`).getTime();
    const t1 = new Date(`${rangeEndYmd}T12:00:00`).getTime();
    const spanDays = Math.max(1, Math.ceil((t1 - t0) / 86400000) + 1);
    const step = Math.max(1, Math.ceil(spanDays / POSITION_CHART_MAX_POINTS));

    const out: { key: string; t: number; label: string; balance: number }[] = [];
    let cur = effectiveStartYmd;
    while (cur <= rangeEndYmd) {
        const count = positions.filter((p) => isPositionOpenOnYmd(p, cur)).length;
        const tMs = new Date(`${cur}T12:00:00`).getTime();
        const label = new Date(`${cur}T12:00:00`).toLocaleDateString(locale, { month: 'short', day: 'numeric' });
        out.push({ key: cur, t: tMs, label, balance: count });
        cur = addCalendarDaysYmd(cur, step);
    }
    const lastKey = out.length ? out[out.length - 1]!.key : null;
    if (lastKey != null && lastKey < rangeEndYmd) {
        const count = positions.filter((p) => isPositionOpenOnYmd(p, rangeEndYmd)).length;
        const tMs = new Date(`${rangeEndYmd}T12:00:00`).getTime();
        const label = new Date(`${rangeEndYmd}T12:00:00`).toLocaleDateString(locale, { month: 'short', day: 'numeric' });
        out.push({ key: rangeEndYmd, t: tMs, label, balance: count });
    }
    return out;
}

/** Satış tarihi bu güne kadar (dahil) olan SOLD pozisyonların kümülatif adedi. */
function buildCumulativeSoldPositionCountSeries(
    positions: ManualPortfolioView[],
    rangeStart: Date,
    rangeEnd: Date,
    locale: string,
): { key: string; t: number; label: string; balance: number }[] {
    if (!positions.length) return [];

    const rangeStartYmd = formatLocalYmd(rangeStart);
    const rangeEndYmd = formatLocalYmd(rangeEnd);
    if (rangeStartYmd > rangeEndYmd) return [];

    const t0 = new Date(`${rangeStartYmd}T12:00:00`).getTime();
    const t1 = new Date(`${rangeEndYmd}T12:00:00`).getTime();
    const spanDays = Math.max(1, Math.ceil((t1 - t0) / 86400000) + 1);
    const step = Math.max(1, Math.ceil(spanDays / POSITION_CHART_MAX_POINTS));

    const countSold = (ymd: string) =>
        positions.filter((p) => {
            if (String(p.status).toUpperCase() !== 'SOLD') return false;
            const sd = p.sellDate?.trim();
            if (!sd) return false;
            return sd <= ymd;
        }).length;

    const out: { key: string; t: number; label: string; balance: number }[] = [];
    let cur = rangeStartYmd;
    while (cur <= rangeEndYmd) {
        const count = countSold(cur);
        const tMs = new Date(`${cur}T12:00:00`).getTime();
        const label = new Date(`${cur}T12:00:00`).toLocaleDateString(locale, { month: 'short', day: 'numeric' });
        out.push({ key: cur, t: tMs, label, balance: count });
        cur = addCalendarDaysYmd(cur, step);
    }
    const lastKey = out.length ? out[out.length - 1]!.key : null;
    if (lastKey != null && lastKey < rangeEndYmd) {
        const count = countSold(rangeEndYmd);
        const tMs = new Date(`${rangeEndYmd}T12:00:00`).getTime();
        const label = new Date(`${rangeEndYmd}T12:00:00`).toLocaleDateString(locale, { month: 'short', day: 'numeric' });
        out.push({ key: rangeEndYmd, t: tMs, label, balance: count });
    }
    return out;
}

function formatLocalYmd(d: Date): string {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
}

function formatPositionTableYmd(ymd: string | null | undefined, locale: string): string {
    const s = String(ymd ?? '').trim();
    if (!s) return '—';
    const d = new Date(`${s}T12:00:00`);
    if (Number.isNaN(d.getTime())) return s;
    return d.toLocaleDateString(locale, { day: '2-digit', month: 'short', year: 'numeric' });
}

/** Backend timeseries: PnL = taşınan değer − açık maliyet; getiri % = 100×PnL/açık maliyet (özet kartındaki payda toplam yatırım değil, günlük açık maliyet). */
function buildChartPointsFromTimeseries(
    rows: ManualPortfolioTimeseriesPoint[],
    mode: TimeseriesChartMode,
    locale: string,
): { key: string; t: number; label: string; balance: number }[] {
    let valueCarry: number | null = null;
    const out: { key: string; t: number; label: string; balance: number }[] = [];
    const minCost = 1e-9;
    for (const r of rows) {
        const tMs = new Date(`${r.date}T12:00:00`).getTime();
        const label = new Date(`${r.date}T12:00:00`).toLocaleDateString(locale, { month: 'short', day: 'numeric' });
        if (mode === 'cost') {
            const bal = Number(r.openCostBasisTry);
            if (!Number.isFinite(bal)) continue;
            out.push({ key: r.date, t: tMs, label, balance: bal });
            continue;
        }
        if (r.marketValueTry != null && Number.isFinite(Number(r.marketValueTry))) {
            valueCarry = Number(r.marketValueTry);
        }
        if (valueCarry == null || !Number.isFinite(valueCarry)) continue;
        if (mode === 'value') {
            out.push({ key: r.date, t: tMs, label, balance: valueCarry });
            continue;
        }
        const cost = Number(r.openCostBasisTry);
        if (!Number.isFinite(cost)) continue;
        if (mode === 'pnl') {
            out.push({ key: r.date, t: tMs, label, balance: valueCarry - cost });
        } else if (mode === 'returnPct') {
            if (cost <= minCost) continue;
            const pctVal = (100 * (valueCarry - cost)) / cost;
            if (!Number.isFinite(pctVal)) continue;
            out.push({ key: r.date, t: tMs, label, balance: pctVal });
        }
    }
    return out;
}

function snapRangeStart(range: SnapRange): Date {
    const start = new Date();
    switch (range) {
        case '1M':
            start.setMonth(start.getMonth() - 1);
            return start;
        case '3M':
            start.setMonth(start.getMonth() - 3);
            return start;
        case '6M':
            start.setMonth(start.getMonth() - 6);
            return start;
        case '1Y':
            start.setFullYear(start.getFullYear() - 1);
            return start;
        case 'ALL':
        default:
            start.setTime(0);
            return start;
    }
}

function earliestBuyYmdFromPositions(positions: ManualPortfolioView[]): string | null {
    let min: string | null = null;
    for (const p of positions) {
        const bd = String(p.buyDate ?? '').trim();
        if (!bd) continue;
        if (min == null || bd < min) min = bd;
    }
    return min;
}

function earliestSellYmdFromPositions(positions: ManualPortfolioView[]): string | null {
    let min: string | null = null;
    for (const p of positions) {
        if (String(p.status).toUpperCase() !== 'SOLD') continue;
        const sd = p.sellDate?.trim();
        if (!sd) continue;
        if (min == null || sd < min) min = sd;
    }
    return min;
}

/** Satılmış (satış tarihi dolu) pozisyonlar arasında en erken alım tarihi — satılmış K/Z yaşam grafiği başlangıcı. */
function earliestBuyYmdAmongSoldPositions(positions: ManualPortfolioView[]): string | null {
    let min: string | null = null;
    for (const p of positions) {
        if (String(p.status).toUpperCase() !== 'SOLD') continue;
        const sd = p.sellDate?.trim();
        if (!sd) continue;
        const bd = String(p.buyDate ?? '').trim();
        if (!bd) continue;
        if (min == null || bd < min) min = bd;
    }
    return min;
}

function earliestBuyYmdAmongOpenPositions(positions: ManualPortfolioView[]): string | null {
    let min: string | null = null;
    for (const p of positions) {
        if (String(p.status).toUpperCase() === 'SOLD') continue;
        const bd = String(p.buyDate ?? '').trim();
        if (!bd) continue;
        if (min == null || bd < min) min = bd;
    }
    return min;
}

function earliestTimeseriesYmdFromRows(rows: ManualPortfolioTimeseriesPoint[]): string | null {
    let min: string | null = null;
    for (const r of rows) {
        const d = String(r.date ?? '').trim();
        if (!d) continue;
        if (min == null || d < min) min = d;
    }
    return min;
}

function minYmdNullable(a: string | null, b: string | null): string | null {
    if (a && b) return a < b ? a : b;
    return a ?? b ?? null;
}

/** Yerel gün başlangıcı (grafik x-domain). */
function ymdToLocalStartOfDay(ymd: string): Date {
    const [y, mo, d] = ymd.split('-').map((x) => Number(x));
    if (!Number.isFinite(y) || !Number.isFinite(mo) || !Number.isFinite(d)) {
        return new Date(`${ymd}T00:00:00`);
    }
    return new Date(y, mo - 1, d, 0, 0, 0, 0);
}

function chartFallbackStartDaysAgo(end: Date, days: number): Date {
    return new Date(end.getTime() - days * 86400000);
}

function normalizeSymbolKey(symbol: string): string {
    return String(symbol ?? '')
        .trim()
        .replace(/\s+/g, '')
        .toUpperCase();
}

/** Küçük ağırlıklar tek ondalıkta 0% görünmesin; büyük dilimlerde okunaklı kalır. */
function formatAllocationSharePct(
    pct: number | null | undefined,
    locale: string,
    t: (key: string, defaultText: string) => string,
): string {
    if (pct == null || !Number.isFinite(pct)) {
        return '—';
    }
    if (pct > 0 && pct < 0.01) {
        const threshold = (0.01).toLocaleString(locale, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
        return t('portfolio.distPctLessThan', '<{n}%').replace('{n}', threshold);
    }
    const maxFrac = pct >= 10 ? 1 : 2;
    return `${pct.toLocaleString(locale, { maximumFractionDigits: maxFrac })}%`;
}

type PnlRankRow = { key: string; label: string; pnlTry: number };

/** Açık pozisyonlar — sembol bazında gerçekleşmemiş K/Z toplamı (sıralama paneli). */
function aggregateOpenUnrealizedPnlBySymbol(positions: ManualPortfolioView[]): PnlRankRow[] {
    const bySym = new Map<string, { label: string; pnl: number; bestAbs: number }>();
    for (const p of positions) {
        if (statusOf(p) !== 'OPEN') continue;
        const pnl = Number(p.unrealizedProfit ?? NaN);
        if (!Number.isFinite(pnl) || pnl === 0) continue;
        const sym = normalizeSymbolKey(p.symbol);
        if (!sym) continue;
        const niceLabel = (p.displayName && p.displayName.trim()) || p.symbol.trim() || sym;
        const abs = Math.abs(pnl);
        const cur = bySym.get(sym);
        if (!cur) {
            bySym.set(sym, { label: niceLabel, pnl, bestAbs: abs });
        } else {
            cur.pnl += pnl;
            if (abs > cur.bestAbs) {
                cur.bestAbs = abs;
                cur.label = niceLabel;
            }
        }
    }
    return [...bySym.entries()].map(([key, v]) => ({ key, label: v.label, pnlTry: v.pnl }));
}

function statusOf(p: ManualPortfolioView): string {
    return String(p.status ?? 'OPEN').toUpperCase();
}

function aggregateOpenByType(positions: ManualPortfolioView[], typeLabel: (k: string) => string): DistRow[] {
    const map = new Map<string, number>();
    for (const p of positions) {
        if (statusOf(p) !== 'OPEN') continue;
        const t = String(p.type ?? 'OTHER').toUpperCase();
        const v = Number(p.currentValue ?? 0);
        if (!Number.isFinite(v) || v <= 0) continue;
        map.set(t, (map.get(t) ?? 0) + v);
    }
    return [...map.entries()]
        .map(([typeKey, value]) => ({
            name: typeLabel(typeKey),
            typeKey,
            value,
        }))
        .sort((a, b) => b.value - a.value);
}

/** Açık pozisyonları sembol bazında toplar; renk için baskın tür (en büyük tek satır) kullanılır. */
function aggregateOpenByAsset(positions: ManualPortfolioView[]): DistRow[] {
    const bySym = new Map<string, { total: number; bestVal: number; bestType: string; label: string }>();
    for (const p of positions) {
        if (statusOf(p) !== 'OPEN') continue;
        const v = Number(p.currentValue ?? 0);
        if (!Number.isFinite(v) || v <= 0) continue;
        const sym = normalizeSymbolKey(p.symbol);
        if (!sym) continue;
        const t = String(p.type ?? 'OTHER').toUpperCase();
        const niceLabel = (p.displayName && p.displayName.trim()) || p.symbol.trim() || sym;
        const cur = bySym.get(sym);
        if (!cur) {
            bySym.set(sym, { total: v, bestVal: v, bestType: t, label: niceLabel });
        } else {
            cur.total += v;
            if (v > cur.bestVal) {
                cur.bestVal = v;
                cur.bestType = t;
                cur.label = niceLabel;
            }
        }
    }
    return [...bySym.entries()]
        .map(([sym, x]) => ({
            name: x.label,
            typeKey: sym,
            value: x.total,
            colorKey: x.bestType,
        }))
        .sort((a, b) => b.value - a.value);
}

function aggregateSoldByType(positions: ManualPortfolioView[], typeLabel: (k: string) => string): DistRow[] {
    const map = new Map<string, number>();
    for (const p of positions) {
        if (statusOf(p) !== 'SOLD') continue;
        const t = String(p.type ?? 'OTHER').toUpperCase();
        const v = Number(p.holdValueToday ?? 0);
        if (!Number.isFinite(v) || v <= 0) continue;
        map.set(t, (map.get(t) ?? 0) + v);
    }
    return [...map.entries()]
        .map(([typeKey, value]) => ({
            name: typeLabel(typeKey),
            typeKey,
            value,
        }))
        .sort((a, b) => b.value - a.value);
}

function soldPositionWeightTry(p: ManualPortfolioView): number {
    const sp = Number(p.sellProceeds ?? 0);
    if (Number.isFinite(sp) && sp > 0) return sp;
    const bc = Number(p.buyCost ?? 0);
    if (Number.isFinite(bc) && bc > 0) return bc;
    return 0;
}

/** Satılmış K/Z görünümü: dağılım ağırlığı satış tutarı (yoksa alış maliyeti). */
function aggregateSoldByTypeAtSaleWeight(positions: ManualPortfolioView[], typeLabel: (k: string) => string): DistRow[] {
    const map = new Map<string, number>();
    for (const p of positions) {
        if (statusOf(p) !== 'SOLD') continue;
        if (!p.sellDate?.trim()) continue;
        const t = String(p.type ?? 'OTHER').toUpperCase();
        const w = soldPositionWeightTry(p);
        if (w <= 0) continue;
        map.set(t, (map.get(t) ?? 0) + w);
    }
    return [...map.entries()]
        .map(([typeKey, value]) => ({
            name: typeLabel(typeKey),
            typeKey,
            value,
        }))
        .sort((a, b) => b.value - a.value);
}

function aggregateSoldByAssetAtSaleWeight(positions: ManualPortfolioView[]): DistRow[] {
    const bySym = new Map<string, { total: number; bestVal: number; bestType: string; label: string }>();
    for (const p of positions) {
        if (statusOf(p) !== 'SOLD') continue;
        if (!p.sellDate?.trim()) continue;
        const w = soldPositionWeightTry(p);
        if (w <= 0) continue;
        const sym = normalizeSymbolKey(p.symbol);
        if (!sym) continue;
        const t = String(p.type ?? 'OTHER').toUpperCase();
        const niceLabel = (p.displayName && p.displayName.trim()) || p.symbol.trim() || sym;
        const cur = bySym.get(sym);
        if (!cur) {
            bySym.set(sym, { total: w, bestVal: w, bestType: t, label: niceLabel });
        } else {
            cur.total += w;
            if (w > cur.bestVal) {
                cur.bestVal = w;
                cur.bestType = t;
                cur.label = niceLabel;
            }
        }
    }
    return [...bySym.entries()]
        .map(([sym, x]) => ({
            name: x.label,
            typeKey: sym,
            value: x.total,
            colorKey: x.bestType,
        }))
        .sort((a, b) => b.value - a.value);
}

function aggregateSoldByAsset(positions: ManualPortfolioView[]): DistRow[] {
    const bySym = new Map<string, { total: number; bestVal: number; bestType: string; label: string }>();
    for (const p of positions) {
        if (statusOf(p) !== 'SOLD') continue;
        const v = Number(p.holdValueToday ?? 0);
        if (!Number.isFinite(v) || v <= 0) continue;
        const sym = normalizeSymbolKey(p.symbol);
        if (!sym) continue;
        const t = String(p.type ?? 'OTHER').toUpperCase();
        const niceLabel = (p.displayName && p.displayName.trim()) || p.symbol.trim() || sym;
        const cur = bySym.get(sym);
        if (!cur) {
            bySym.set(sym, { total: v, bestVal: v, bestType: t, label: niceLabel });
        } else {
            cur.total += v;
            if (v > cur.bestVal) {
                cur.bestVal = v;
                cur.bestType = t;
                cur.label = niceLabel;
            }
        }
    }
    return [...bySym.entries()]
        .map(([sym, x]) => ({
            name: x.label,
            typeKey: sym,
            value: x.total,
            colorKey: x.bestType,
        }))
        .sort((a, b) => b.value - a.value);
}

const PIE_COLOR_BY_TYPE: Record<string, string> = {
    STOCK: '#3b82f6',
    BIST: '#2563eb',
    CRYPTO: '#06b6d4',
    FX: '#f59e0b',
    FUND: '#a855f7',
    METAL: '#eab308',
};

function useAnimatedNumber(target: number) {
    const [display, setDisplay] = useState(0);
    const displayRef = useRef(0);

    useEffect(() => {
        const from = displayRef.current;
        let raf = 0;
        const t0 = performance.now();
        const dur = 720;
        const step = (now: number) => {
            const p = Math.min(1, (now - t0) / dur);
            const eased = 1 - (1 - p) ** 3;
            const next = from + (target - from) * eased;
            displayRef.current = next;
            setDisplay(next);
            if (p < 1) raf = requestAnimationFrame(step);
        };
        raf = requestAnimationFrame(step);
        return () => cancelAnimationFrame(raf);
    }, [target]);

    return display;
}

function buildPageIndices(current: number, totalPages: number): (number | 'gap')[] {
    if (totalPages <= 1) return [];
    if (totalPages <= 9) return Array.from({ length: totalPages }, (_, i) => i);
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

export function Portfolio() {
    const qc = useQueryClient();
    const navigate = useNavigate();
    const { tokens } = useTheme();
    const { t, lang } = useLanguage();
    const locale = lang === 'en' ? 'en-US' : 'tr-TR';
    const manualRef = useRef<ManualInvestmentAnalysisSectionHandle>(null);

    const [snapRange, setSnapRange] = useState<SnapRange>('6M');
    const [chartSeriesMode, setChartSeriesMode] = useState<ChartSeriesMode>('value');
    const [positionCountVariant, setPositionCountVariant] = useState<PositionCountVariant>('open');
    const [tablePage, setTablePage] = useState(0);
    const [distViewMode, setDistViewMode] = useState<DistViewMode>('category');
    const [chartOverlays, setChartOverlays] = useState<ChartOverlaySpec[]>([]);
    const [chartDropActive, setChartDropActive] = useState(false);
    const [expandedPositionId, setExpandedPositionId] = useState<number | null>(null);
    const [priceAlertTarget, setPriceAlertTarget] = useState<{
        assetType: PriceAlertAssetType;
        symbol: string;
        displayName?: string;
    } | null>(null);

    const invalidateManualPage = useCallback(async () => {
        await qc.invalidateQueries({ queryKey: manualPortfolioKeys.all });
        await qc.invalidateQueries({ queryKey: manualPortfolioKeys.insights() });
        await qc.invalidateQueries({ queryKey: ['manual', 'timeseries-segment'] });
        await qc.invalidateQueries({ queryKey: ['manual', 'timeseries-sold-hold'] });
        await qc.invalidateQueries({ queryKey: ['manual', 'timeseries-sold-hold-segment'] });
        await qc.invalidateQueries({ queryKey: ['manual', 'timeseries-sold-lifecycle-pnl'] });
        await qc.invalidateQueries({ queryKey: ['manual', 'timeseries-sold-lifecycle-pnl-segment'] });
        await qc.invalidateQueries({ queryKey: ['manual', 'timeseries-open-unrealized-pnl'] });
        await qc.invalidateQueries({ queryKey: ['manual', 'timeseries-open-unrealized-pnl-segment'] });
        await qc.invalidateQueries({ queryKey: ['manual', 'timeseries-sold-overlay'] });
        await qc.invalidateQueries({ queryKey: ['manual', 'timeseries-open-unrealized-overlay'] });
        await qc.invalidateQueries({ queryKey: ['market', 'dashboard'] });
    }, [qc]);

    const summaryQuery = useQuery({
        queryKey: manualPortfolioKeys.summary(),
        queryFn: getManualSummary,
    });

    const positionsQuery = useQuery({
        queryKey: manualPortfolioKeys.positions(),
        queryFn: getManualPositions,
    });

    const insightsQuery = useQuery({
        queryKey: manualPortfolioKeys.insights(),
        queryFn: getManualPortfolioInsights,
        enabled: (positionsQuery.data?.length ?? 0) > 0,
        staleTime: 60_000,
    });

    const evaluateInsightsMutation = useMutation({
        mutationFn: evaluatePortfolioInsightNotifications,
        onSuccess: async (result) => {
            await qc.invalidateQueries({ queryKey: notificationKeys.all });
            const n = result?.generatedCount ?? 0;
            alert(
                t(
                    'portfolio.evaluationSent',
                    'Portföy değerlendirmeniz e-posta ve bildirimlerinize gönderildi. ({count} kayıt)',
                ).replace('{count}', String(n)),
            );
        },
        onError: (err: unknown) => {
            alert(readFinanceApiError(err).message || t('portfolio.evaluationFailed', 'Portföy değerlendirmesi gönderilemedi.'));
        },
    });

    const summary = summaryQuery.data;
    const positions = positionsQuery.data ?? [];
    const insights = insightsQuery.data;
    const insightSummary = insights?.summary;

    const earliestBuyYmd = useMemo(() => earliestBuyYmdFromPositions(positions), [positions]);

    const earliestOpenBuyYmd = useMemo(() => earliestBuyYmdAmongOpenPositions(positions), [positions]);

    const earliestSellYmd = useMemo(() => earliestSellYmdFromPositions(positions), [positions]);

    const soldHoldTsQuery = useQuery({
        queryKey: ['manual', 'timeseries-sold-hold', earliestSellYmd ?? 'none'] as const,
        queryFn: async () => {
            if (!earliestSellYmd) return [];
            return getManualSoldHoldHypotheticalTimeseries(earliestSellYmd, formatLocalYmd(new Date()));
        },
        enabled: chartSeriesMode === 'soldHoldHypothetical' && earliestSellYmd != null,
        staleTime: 60_000,
    });

    const timeseriesFromYmd = useMemo(() => {
        if (snapRange !== 'ALL') return formatLocalYmd(snapRangeStart(snapRange));
        return earliestBuyYmd ?? formatLocalYmd(chartFallbackStartDaysAgo(new Date(), 7));
    }, [snapRange, earliestBuyYmd]);

    const timeseriesQueryKey = useMemo(() => {
        if (snapRange === 'ALL') return manualPortfolioKeys.timeseries(`ALL:${earliestBuyYmd ?? 'none'}`);
        return manualPortfolioKeys.timeseries(snapRange);
    }, [snapRange, earliestBuyYmd]);

    const timeseriesQuery = useQuery({
        queryKey: timeseriesQueryKey,
        queryFn: async () => {
            const end = new Date();
            return getManualTimeseries(timeseriesFromYmd, formatLocalYmd(end));
        },
    });

    const segmentTimeseriesQueries = useQueries({
        queries: chartOverlays.map((c) => ({
            queryKey: ['manual', 'timeseries-segment', timeseriesQueryKey, timeseriesFromYmd, c.mode, c.key] as const,
            queryFn: () => getManualTimeseriesSegment(timeseriesFromYmd, formatLocalYmd(new Date()), c.mode, c.key),
            enabled:
                chartSeriesMode !== 'positionCount' &&
                chartSeriesMode !== 'unrealizedPnl' &&
                chartSeriesMode !== 'soldHoldHypothetical' &&
                chartSeriesMode !== 'soldLifecyclePnl' &&
                chartOverlays.length > 0,
            staleTime: 30_000,
        })),
    });

    const openUnrealizedFromYmd = useMemo(() => {
        if (snapRange !== 'ALL') return formatLocalYmd(snapRangeStart(snapRange));
        return earliestOpenBuyYmd ?? formatLocalYmd(chartFallbackStartDaysAgo(new Date(), 7));
    }, [snapRange, earliestOpenBuyYmd]);

    const openUnrealizedToYmd = formatLocalYmd(new Date());

    const openUnrealizedTsQuery = useQuery({
        queryKey: ['manual', 'timeseries-open-unrealized-pnl', snapRange, openUnrealizedFromYmd, openUnrealizedToYmd] as const,
        queryFn: async () => {
            if (!openUnrealizedFromYmd) return [];
            return getManualOpenUnrealizedPnlTimeseries(openUnrealizedFromYmd, openUnrealizedToYmd);
        },
        enabled: chartSeriesMode === 'unrealizedPnl' && (summary?.openPositions ?? 0) > 0,
        staleTime: 60_000,
    });

    const openUnrealizedOverlayQueries = useQueries({
        queries: chartOverlays.map((c) => ({
            queryKey: [
                'manual',
                'timeseries-open-unrealized-overlay',
                snapRange,
                openUnrealizedFromYmd,
                openUnrealizedToYmd,
                c.mode,
                c.key,
            ] as const,
            queryFn: () => getManualOpenUnrealizedPnlSegment(openUnrealizedFromYmd, openUnrealizedToYmd, c.mode, c.key),
            enabled: chartSeriesMode === 'unrealizedPnl' && chartOverlays.length > 0 && (summary?.openPositions ?? 0) > 0,
            staleTime: 30_000,
        })),
    });

    const soldLifecycleFromYmd = useMemo(() => earliestBuyYmdAmongSoldPositions(positions), [positions]);
    const soldLifecycleToYmd = formatLocalYmd(new Date());

    const soldLifecycleTsQuery = useQuery({
        queryKey: ['manual', 'timeseries-sold-lifecycle-pnl', soldLifecycleFromYmd ?? 'none', soldLifecycleToYmd ?? 'none'] as const,
        queryFn: async () => {
            if (!soldLifecycleFromYmd || !soldLifecycleToYmd) return [];
            return getManualSoldLifecyclePnlTimeseries(soldLifecycleFromYmd, soldLifecycleToYmd);
        },
        enabled: chartSeriesMode === 'soldLifecyclePnl' && soldLifecycleFromYmd != null && soldLifecycleToYmd != null,
        staleTime: 60_000,
    });

    const soldOverlayTimeseriesQueries = useQueries({
        queries: chartOverlays.map((c) => ({
            queryKey: [
                'manual',
                'timeseries-sold-overlay',
                chartSeriesMode,
                chartSeriesMode === 'soldHoldHypothetical' ? earliestSellYmd ?? 'none' : soldLifecycleFromYmd ?? 'none',
                chartSeriesMode === 'soldHoldHypothetical' ? formatLocalYmd(new Date()) : soldLifecycleToYmd ?? 'none',
                c.mode,
                c.key,
            ] as const,
            queryFn: () => {
                if (chartSeriesMode === 'soldHoldHypothetical') {
                    if (!earliestSellYmd) return Promise.resolve([]);
                    return getManualSoldHoldHypotheticalSegment(earliestSellYmd, formatLocalYmd(new Date()), c.mode, c.key);
                }
                if (chartSeriesMode === 'soldLifecyclePnl') {
                    if (!soldLifecycleFromYmd || !soldLifecycleToYmd) return Promise.resolve([]);
                    return getManualSoldLifecyclePnlSegment(soldLifecycleFromYmd, soldLifecycleToYmd, c.mode, c.key);
                }
                return Promise.resolve([]);
            },
            enabled:
                (chartSeriesMode === 'soldHoldHypothetical' || chartSeriesMode === 'soldLifecyclePnl') &&
                chartOverlays.length > 0 &&
                (chartSeriesMode !== 'soldHoldHypothetical' || earliestSellYmd != null) &&
                (chartSeriesMode !== 'soldLifecyclePnl' || (soldLifecycleFromYmd != null && soldLifecycleToYmd != null)),
            staleTime: 30_000,
        })),
    });

    useEffect(() => {
        if (chartSeriesMode === 'positionCount') {
            setChartOverlays([]);
        }
    }, [chartSeriesMode]);

    const prevChartModeForOverlaysRef = useRef(chartSeriesMode);
    useEffect(() => {
        const prev = prevChartModeForOverlaysRef.current;
        prevChartModeForOverlaysRef.current = chartSeriesMode;
        const overlayFamily = (m: ChartSeriesMode) => {
            if (m === 'unrealizedPnl') return 'openUnrealized';
            if (m === 'soldHoldHypothetical' || m === 'soldLifecyclePnl') return 'sold';
            if (m === 'value' || m === 'cost' || m === 'pnl' || m === 'returnPct') return 'portfolio';
            return 'other';
        };
        if (overlayFamily(prev) !== overlayFamily(chartSeriesMode)) {
            setChartOverlays([]);
        }
    }, [chartSeriesMode]);

    const refetchAll = useCallback(() => {
        void summaryQuery.refetch();
        void positionsQuery.refetch();
        void timeseriesQuery.refetch();
        void qc.invalidateQueries({ queryKey: ['manual', 'timeseries-segment'] });
        void qc.invalidateQueries({ queryKey: ['manual', 'timeseries-sold-hold'] });
        void qc.invalidateQueries({ queryKey: ['manual', 'timeseries-sold-hold-segment'] });
        void qc.invalidateQueries({ queryKey: ['manual', 'timeseries-sold-lifecycle-pnl'] });
        void qc.invalidateQueries({ queryKey: ['manual', 'timeseries-sold-lifecycle-pnl-segment'] });
        void qc.invalidateQueries({ queryKey: ['manual', 'timeseries-open-unrealized-pnl'] });
        void qc.invalidateQueries({ queryKey: ['manual', 'timeseries-open-unrealized-pnl-segment'] });
        void qc.invalidateQueries({ queryKey: ['manual', 'timeseries-sold-overlay'] });
        void qc.invalidateQueries({ queryKey: ['manual', 'timeseries-open-unrealized-overlay'] });
    }, [summaryQuery, positionsQuery, timeseriesQuery, qc]);

    useRefetchOnFocus(refetchAll);
    usePolling(refetchAll, 60_000);

    const typeLabel = useCallback(
        (k: string) => t(`portfolio.typeLabel.${String(k).toUpperCase()}`, String(k).toUpperCase()),
        [t],
    );

    const distributionOpen = useMemo(() => aggregateOpenByType(positions, typeLabel), [positions, typeLabel]);

    const distributionByAsset = useMemo(() => aggregateOpenByAsset(positions), [positions]);

    const soldDistByType = useMemo(() => aggregateSoldByType(positions, typeLabel), [positions, typeLabel]);
    const soldDistByAsset = useMemo(() => aggregateSoldByAsset(positions), [positions]);
    const soldLifecycleDistByType = useMemo(() => aggregateSoldByTypeAtSaleWeight(positions, typeLabel), [positions, typeLabel]);
    const soldLifecycleDistByAsset = useMemo(() => aggregateSoldByAssetAtSaleWeight(positions), [positions]);

    const isSoldDistributionView = chartSeriesMode === 'soldHoldHypothetical' || chartSeriesMode === 'soldLifecyclePnl';

    const distRowsForPanel = useMemo(() => {
        if (chartSeriesMode === 'soldLifecyclePnl') {
            return distViewMode === 'category' ? soldLifecycleDistByType : soldLifecycleDistByAsset;
        }
        if (chartSeriesMode === 'soldHoldHypothetical') {
            return distViewMode === 'category' ? soldDistByType : soldDistByAsset;
        }
        return distViewMode === 'category' ? distributionOpen : distributionByAsset;
    }, [
        chartSeriesMode,
        distViewMode,
        soldLifecycleDistByType,
        soldLifecycleDistByAsset,
        soldDistByType,
        soldDistByAsset,
        distributionOpen,
        distributionByAsset,
    ]);

    const distTotalForPanel = useMemo(() => distRowsForPanel.reduce((s, d) => s + d.value, 0), [distRowsForPanel]);

    const soldChartPoints = useMemo(
        () => buildChartPointsFromTimeseries(soldHoldTsQuery.data ?? [], 'value', locale),
        [soldHoldTsQuery.data, locale],
    );

    const soldChartMergedRows = useMemo(() => {
        if (chartSeriesMode !== 'soldHoldHypothetical' || chartOverlays.length === 0) {
            return soldChartPoints;
        }
        const overlayMaps = chartOverlays.map((_, idx) => {
            const raw = soldOverlayTimeseriesQueries[idx]?.data ?? [];
            const pts = buildChartPointsFromTimeseries(raw, 'value', locale);
            return new Map(pts.map((p) => [p.key, p.balance]));
        });
        return soldChartPoints.map((row) => {
            const rowObj: Record<string, unknown> = { ...row };
            chartOverlays.forEach((c, idx) => {
                rowObj[c.id] = overlayMaps[idx]?.get(row.key);
            });
            return rowObj as (typeof soldChartPoints)[number];
        });
    }, [chartSeriesMode, soldChartPoints, chartOverlays, soldOverlayTimeseriesQueries, locale]);

    const soldLifecycleChartPoints = useMemo(
        () => buildChartPointsFromTimeseries(soldLifecycleTsQuery.data ?? [], 'value', locale),
        [soldLifecycleTsQuery.data, locale],
    );

    const soldLifecycleChartMergedRows = useMemo(() => {
        if (chartSeriesMode !== 'soldLifecyclePnl' || chartOverlays.length === 0) {
            return soldLifecycleChartPoints;
        }
        const overlayMaps = chartOverlays.map((_, idx) => {
            const raw = soldOverlayTimeseriesQueries[idx]?.data ?? [];
            const pts = buildChartPointsFromTimeseries(raw, 'value', locale);
            return new Map(pts.map((p) => [p.key, p.balance]));
        });
        return soldLifecycleChartPoints.map((row) => {
            const rowObj: Record<string, unknown> = { ...row };
            chartOverlays.forEach((c, idx) => {
                rowObj[c.id] = overlayMaps[idx]?.get(row.key);
            });
            return rowObj as (typeof soldLifecycleChartPoints)[number];
        });
    }, [chartSeriesMode, soldLifecycleChartPoints, chartOverlays, soldOverlayTimeseriesQueries, locale]);

    const soldLifecycleChartXDomain = useMemo((): [number, number] | null => {
        if (!soldLifecycleChartPoints.length) return null;
        const ts = soldLifecycleChartPoints.map((p) => p.t);
        return [Math.min(...ts), Math.max(...ts)];
    }, [soldLifecycleChartPoints]);

    const soldLifecycleXDomainFallback = useMemo((): [number, number] | null => {
        if (!soldLifecycleFromYmd) return null;
        const start = ymdToLocalStartOfDay(soldLifecycleFromYmd).getTime();
        const toY = formatLocalYmd(new Date());
        const end = new Date(`${toY}T12:00:00`).getTime();
        return [start, end];
    }, [soldLifecycleFromYmd]);

    const openUnrealizedChartPoints = useMemo(
        () => buildChartPointsFromTimeseries(openUnrealizedTsQuery.data ?? [], 'pnl', locale),
        [openUnrealizedTsQuery.data, locale],
    );

    const openUnrealizedChartMergedRows = useMemo(() => {
        if (chartSeriesMode !== 'unrealizedPnl' || chartOverlays.length === 0) {
            return openUnrealizedChartPoints;
        }
        const overlayMaps = chartOverlays.map((_, idx) => {
            const raw = openUnrealizedOverlayQueries[idx]?.data ?? [];
            const pts = buildChartPointsFromTimeseries(raw, 'pnl', locale);
            return new Map(pts.map((p) => [p.key, p.balance]));
        });
        return openUnrealizedChartPoints.map((row) => {
            const rowObj: Record<string, unknown> = { ...row };
            chartOverlays.forEach((c, idx) => {
                rowObj[c.id] = overlayMaps[idx]?.get(row.key);
            });
            return rowObj as (typeof openUnrealizedChartPoints)[number];
        });
    }, [chartSeriesMode, openUnrealizedChartPoints, chartOverlays, openUnrealizedOverlayQueries, locale]);

    const openUnrealizedChartXDomain = useMemo((): [number, number] | null => {
        if (!openUnrealizedChartPoints.length) return null;
        const ts = openUnrealizedChartPoints.map((p) => p.t);
        return [Math.min(...ts), Math.max(...ts)];
    }, [openUnrealizedChartPoints]);

    const openUnrealizedXDomainFallback = useMemo((): [number, number] | null => {
        if (!openUnrealizedFromYmd) return null;
        const start = ymdToLocalStartOfDay(openUnrealizedFromYmd).getTime();
        const end = new Date(`${openUnrealizedToYmd}T12:00:00`).getTime();
        return [start, end];
    }, [openUnrealizedFromYmd, openUnrealizedToYmd]);

    const soldChartXDomain = useMemo((): [number, number] | null => {
        if (!soldChartPoints.length) return null;
        const ts = soldChartPoints.map((p) => p.t);
        return [Math.min(...ts), Math.max(...ts)];
    }, [soldChartPoints]);

    const soldXDomainFallback = useMemo((): [number, number] | null => {
        if (!earliestSellYmd) return null;
        const start = ymdToLocalStartOfDay(earliestSellYmd).getTime();
        const end = new Date(`${formatLocalYmd(new Date())}T12:00:00`).getTime();
        return [start, end];
    }, [earliestSellYmd]);

    const openPositions = useMemo(() => positions.filter((p) => statusOf(p) === 'OPEN'), [positions]);

    const concentration = useMemo(() => {
        const total = openPositions.reduce((s, p) => s + Math.max(0, Number(p.currentValue ?? 0)), 0);
        const sorted = [...openPositions].sort((a, b) => Number(b.currentValue ?? 0) - Number(a.currentValue ?? 0));
        const top = sorted[0];
        const topPct = top && total > 0 ? (Number(top.currentValue ?? 0) / total) * 100 : null;
        const top3sum = sorted.slice(0, 3).reduce((s, p) => s + Math.max(0, Number(p.currentValue ?? 0)), 0);
        const top3Pct = total > 0 ? (top3sum / total) * 100 : null;
        const fxSum = openPositions
            .filter((p) => String(p.type).toUpperCase() === 'FX')
            .reduce((s, p) => s + Math.max(0, Number(p.currentValue ?? 0)), 0);
        const fxPct = total > 0 ? (fxSum / total) * 100 : null;
        return { top, topPct, top3Pct, fxPct };
    }, [openPositions]);

    const chartRangeBounds = useMemo(() => {
        const end = new Date();
        if (snapRange !== 'ALL') {
            return { start: snapRangeStart(snapRange), end };
        }
        if (chartSeriesMode === 'positionCount') {
            if (positionCountVariant === 'open') {
                const ymd = earliestBuyYmdFromPositions(positions);
                if (!ymd) return { start: chartFallbackStartDaysAgo(end, 7), end };
                return { start: ymdToLocalStartOfDay(ymd), end };
            }
            const eb = earliestBuyYmdFromPositions(positions);
            const es = earliestSellYmdFromPositions(positions);
            const ymd = minYmdNullable(eb, es);
            if (!ymd) return { start: chartFallbackStartDaysAgo(end, 7), end };
            return { start: ymdToLocalStartOfDay(ymd), end };
        }
        const eb = earliestBuyYmdFromPositions(positions);
        const eRows = earliestTimeseriesYmdFromRows(timeseriesQuery.data ?? []);
        const ymd = minYmdNullable(eb, eRows);
        if (!ymd) return { start: chartFallbackStartDaysAgo(end, 7), end };
        return { start: ymdToLocalStartOfDay(ymd), end };
    }, [snapRange, chartSeriesMode, positionCountVariant, positions, timeseriesQuery.data]);

    const chartPoints = useMemo(() => {
        if (chartSeriesMode === 'positionCount') {
            if (positionCountVariant === 'open') {
                return buildOpenPositionCountSeries(positions, chartRangeBounds.start, chartRangeBounds.end, locale);
            }
            return buildCumulativeSoldPositionCountSeries(positions, chartRangeBounds.start, chartRangeBounds.end, locale);
        }
        if (
            chartSeriesMode === 'soldHoldHypothetical' ||
            chartSeriesMode === 'soldLifecyclePnl' ||
            chartSeriesMode === 'unrealizedPnl'
        ) {
            return [];
        }
        return buildChartPointsFromTimeseries(timeseriesQuery.data ?? [], chartSeriesMode, locale);
    }, [chartSeriesMode, positionCountVariant, positions, chartRangeBounds.start, chartRangeBounds.end, locale, timeseriesQuery.data]);

    const chartUsesTimeseries =
        chartSeriesMode !== 'positionCount' &&
        chartSeriesMode !== 'unrealizedPnl' &&
        chartSeriesMode !== 'soldHoldHypothetical' &&
        chartSeriesMode !== 'soldLifecyclePnl';
    const chartShowsMetricToggle =
        chartSeriesMode === 'value' || chartSeriesMode === 'cost' || chartSeriesMode === 'pnl';
    const chartAllowDistDrag =
        chartUsesTimeseries ||
        chartSeriesMode === 'unrealizedPnl' ||
        chartSeriesMode === 'soldHoldHypothetical' ||
        chartSeriesMode === 'soldLifecyclePnl';
    const chartSeriesForOverlay: TimeseriesChartMode =
        chartSeriesMode === 'positionCount' ||
        chartSeriesMode === 'soldHoldHypothetical' ||
        chartSeriesMode === 'soldLifecyclePnl'
            ? 'value'
            : chartSeriesMode === 'unrealizedPnl'
              ? 'pnl'
              : chartSeriesMode === 'returnPct'
                ? 'returnPct'
                : chartSeriesMode;

    const chartMergedRows = useMemo(() => {
        if (!chartUsesTimeseries || chartOverlays.length === 0) {
            return chartPoints;
        }
        const overlayMaps = chartOverlays.map((_, idx) => {
            const raw = segmentTimeseriesQueries[idx]?.data ?? [];
            const pts = buildChartPointsFromTimeseries(raw, chartSeriesForOverlay, locale);
            return new Map(pts.map((p) => [p.key, p.balance]));
        });
        return chartPoints.map((row) => {
            const rowObj: Record<string, unknown> = { ...row };
            chartOverlays.forEach((c, idx) => {
                rowObj[c.id] = overlayMaps[idx]?.get(row.key);
            });
            return rowObj as (typeof chartPoints)[number];
        });
    }, [chartUsesTimeseries, chartOverlays, chartPoints, segmentTimeseriesQueries, chartSeriesForOverlay, locale]);

    const chartLoading =
        chartSeriesMode === 'soldHoldHypothetical'
            ? soldHoldTsQuery.isLoading
            : chartSeriesMode === 'soldLifecyclePnl'
              ? soldLifecycleTsQuery.isLoading
              : chartSeriesMode === 'unrealizedPnl'
                ? openUnrealizedTsQuery.isLoading
                : chartUsesTimeseries
                  ? timeseriesQuery.isLoading
                  : positionsQuery.isLoading;
    const chartError =
        chartSeriesMode === 'soldHoldHypothetical' && soldHoldTsQuery.isError
            ? soldHoldTsQuery.error
            : chartSeriesMode === 'soldLifecyclePnl' && soldLifecycleTsQuery.isError
              ? soldLifecycleTsQuery.error
              : chartSeriesMode === 'unrealizedPnl' && openUnrealizedTsQuery.isError
                ? openUnrealizedTsQuery.error
                : chartUsesTimeseries && timeseriesQuery.isError
                  ? timeseriesQuery.error
                  : null;

    const chartXDomain = useMemo(
        (): [number, number] => [chartRangeBounds.start.getTime(), chartRangeBounds.end.getTime()],
        [chartRangeBounds],
    );

    const chartRowsForPlot =
        chartSeriesMode === 'soldHoldHypothetical'
            ? soldChartMergedRows
            : chartSeriesMode === 'soldLifecyclePnl'
              ? soldLifecycleChartMergedRows
              : chartSeriesMode === 'unrealizedPnl'
                ? openUnrealizedChartMergedRows
                : chartMergedRows;

    const chartXDomainForPlot = useMemo((): [number, number] => {
        if (chartSeriesMode === 'soldHoldHypothetical') {
            return soldChartXDomain ?? soldXDomainFallback ?? chartXDomain;
        }
        if (chartSeriesMode === 'soldLifecyclePnl') {
            return soldLifecycleChartXDomain ?? soldLifecycleXDomainFallback ?? chartXDomain;
        }
        if (chartSeriesMode === 'unrealizedPnl') {
            return openUnrealizedChartXDomain ?? openUnrealizedXDomainFallback ?? chartXDomain;
        }
        return chartXDomain;
    }, [
        chartSeriesMode,
        soldChartXDomain,
        soldXDomainFallback,
        soldLifecycleChartXDomain,
        soldLifecycleXDomainFallback,
        openUnrealizedChartXDomain,
        openUnrealizedXDomainFallback,
        chartXDomain,
    ]);

    const chartAreaVisual = useMemo(() => {
        if (chartSeriesMode === 'cost') {
            return { stroke: 'var(--tp-accent-cost, #f59e0b)', fill: 'url(#pfSnapAreaCost)' as const };
        }
        if (chartSeriesMode === 'pnl' || chartSeriesMode === 'unrealizedPnl') {
            return { stroke: 'var(--tp-accent-pnl, #34d399)', fill: 'url(#pfSnapAreaPnl)' as const };
        }
        if (chartSeriesMode === 'returnPct') {
            return { stroke: 'var(--tp-accent-pct, #a78bfa)', fill: 'url(#pfSnapAreaPct)' as const };
        }
        if (chartSeriesMode === 'soldLifecyclePnl') {
            return { stroke: 'var(--tp-accent-pnl, #34d399)', fill: 'url(#pfSnapAreaPnl)' as const };
        }
        if (chartSeriesMode === 'soldHoldHypothetical') {
            return { stroke: '#a78bfa', fill: 'url(#pfSnapAreaSoldHold)' as const };
        }
        if (chartSeriesMode === 'positionCount' && positionCountVariant === 'sold') {
            return { stroke: 'var(--tp-accent-sold, #fb923c)', fill: 'url(#pfSnapAreaSold)' as const };
        }
        if (chartSeriesMode === 'positionCount') {
            return { stroke: 'var(--tp-accent-poscnt, #38bdf8)', fill: 'url(#pfSnapAreaPosCnt)' as const };
        }
        return { stroke: 'var(--tp-accent, #60a5fa)', fill: 'url(#pfSnapArea)' as const };
    }, [chartSeriesMode, positionCountVariant]);

    const selectValueChart = useCallback(() => {
        setChartSeriesMode('value');
    }, []);

    const selectCostChart = useCallback(() => {
        setChartSeriesMode('cost');
    }, []);

    const selectPnlChart = useCallback(() => {
        setChartSeriesMode('pnl');
    }, []);

    const selectReturnPctChart = useCallback(() => {
        setChartSeriesMode('returnPct');
    }, []);

    const selectPositionCountChart = useCallback(() => {
        setPositionCountVariant('open');
        setChartSeriesMode('positionCount');
    }, []);

    const selectSoldHoldHypotheticalChart = useCallback(() => {
        setChartSeriesMode('soldHoldHypothetical');
    }, []);

    const selectSoldLifecyclePnlChart = useCallback(() => {
        setChartSeriesMode('soldLifecyclePnl');
    }, []);

    const selectUnrealizedPnlChart = useCallback(() => {
        setChartSeriesMode('unrealizedPnl');
    }, []);

    const handleChartDragOver = useCallback(
        (e: DragEvent<HTMLDivElement>) => {
            if (!chartAllowDistDrag || chartLoading) return;
            e.preventDefault();
            e.dataTransfer.dropEffect = 'copy';
            setChartDropActive(true);
        },
        [chartAllowDistDrag, chartLoading],
    );

    const handleChartDragLeave = useCallback((e: DragEvent<HTMLDivElement>) => {
        const rel = e.relatedTarget as Node | null;
        if (rel && (e.currentTarget as HTMLElement).contains(rel)) return;
        setChartDropActive(false);
    }, []);

    const handleChartDrop = useCallback(
        (e: DragEvent<HTMLDivElement>) => {
            e.preventDefault();
            setChartDropActive(false);
            if (!chartAllowDistDrag || chartLoading) return;
            try {
                const raw = e.dataTransfer.getData('application/json');
                if (!raw) return;
                const p = JSON.parse(raw) as { mode?: string; key?: string; label?: string; color?: string };
                if (p.mode !== 'TYPE' && p.mode !== 'SYMBOL') return;
                const key = p.key?.trim();
                const label = p.label?.trim();
                if (!key || !label) return;
                const id = `${p.mode}:${key}`;
                setChartOverlays((prev) => {
                    if (prev.some((x) => x.id === id)) return prev;
                    if (prev.length >= MAX_CHART_OVERLAYS) return prev;
                    return [
                        ...prev,
                        {
                            id,
                            mode: p.mode as 'TYPE' | 'SYMBOL',
                            key,
                            label,
                            color: p.color && p.color.length > 0 ? p.color : '#64748b',
                        },
                    ];
                });
            } catch {
                /* ignore */
            }
        },
        [chartAllowDistDrag, chartLoading],
    );

    const removeChartOverlay = useCallback((id: string) => {
        setChartOverlays((prev) => prev.filter((x) => x.id !== id));
    }, []);

    const pnlRankRows = useMemo(() => {
        const rows = aggregateOpenUnrealizedPnlBySymbol(openPositions);
        const pos = rows.filter((r) => r.pnlTry > 0).sort((a, b) => b.pnlTry - a.pnlTry);
        const neg = rows.filter((r) => r.pnlTry < 0).sort((a, b) => a.pnlTry - b.pnlTry);
        return { pos, neg };
    }, [openPositions]);

    const soldRows = useMemo(() => positions.filter((p) => statusOf(p) === 'SOLD'), [positions]);

    const fallbackHealthScore = useMemo(
        () => computeFallbackHealthScore(openPositions, summary, distributionByAsset, distributionOpen),
        [openPositions, summary, distributionByAsset, distributionOpen],
    );

    const healthScoreView = useMemo(
        () => resolveHealthScore(insights, fallbackHealthScore, t),
        [insights, fallbackHealthScore, t],
    );

    const openConcentrationLine = useMemo(
        () =>
            buildConcentrationRiskLine(
                concentration.top?.symbol ?? null,
                concentration.topPct,
                t,
            ),
        [concentration.top, concentration.topPct, t],
    );

    const soldScenarioSummary = useMemo(() => buildSoldScenarioSummary(soldRows), [soldRows]);

    const radarRiskFacts = useMemo(
        () => buildRadarRiskFacts(pnlRankRows, distributionOpen, concentration.top?.symbol ?? null, concentration.topPct),
        [pnlRankRows, distributionOpen, concentration.top, concentration.topPct],
    );

    const soldConcentration = useMemo(() => {
        const total = soldRows.reduce((s, p) => s + Math.max(0, Number(p.holdValueToday ?? 0)), 0);
        const sorted = [...soldRows].sort((a, b) => Number(b.holdValueToday ?? 0) - Number(a.holdValueToday ?? 0));
        const top = sorted[0];
        const topPct = top && total > 0 ? (Number(top.holdValueToday ?? 0) / total) * 100 : null;
        const top3sum = sorted.slice(0, 3).reduce((s, p) => s + Math.max(0, Number(p.holdValueToday ?? 0)), 0);
        const top3Pct = total > 0 ? (top3sum / total) * 100 : null;
        const fxSum = soldRows
            .filter((p) => String(p.type).toUpperCase() === 'FX')
            .reduce((s, p) => s + Math.max(0, Number(p.holdValueToday ?? 0)), 0);
        const fxPct = total > 0 ? (fxSum / total) * 100 : null;
        return { top, topPct, top3Pct, fxPct };
    }, [soldRows]);

    const soldLifecycleConcentration = useMemo(() => {
        const rows = soldRows.filter((p) => p.sellDate?.trim());
        const total = rows.reduce((s, p) => s + soldPositionWeightTry(p), 0);
        const sorted = [...rows].sort((a, b) => soldPositionWeightTry(b) - soldPositionWeightTry(a));
        const top = sorted[0];
        const tw = top ? soldPositionWeightTry(top) : 0;
        const topPct = top && total > 0 ? (tw / total) * 100 : null;
        const top3sum = sorted.slice(0, 3).reduce((s, p) => s + soldPositionWeightTry(p), 0);
        const top3Pct = total > 0 ? (top3sum / total) * 100 : null;
        const fxSum = rows.filter((p) => String(p.type).toUpperCase() === 'FX').reduce((s, p) => s + soldPositionWeightTry(p), 0);
        const fxPct = total > 0 ? (fxSum / total) * 100 : null;
        return { top, topPct, top3Pct, fxPct };
    }, [soldRows]);

    const distConcentration =
        chartSeriesMode === 'soldLifecyclePnl'
            ? soldLifecycleConcentration
            : isSoldDistributionView
              ? soldConcentration
              : concentration;

    const fmtTry = useCallback(
        (v: number) =>
            new Intl.NumberFormat(locale, { style: 'currency', currency: 'TRY', maximumFractionDigits: 2 }).format(v),
        [locale],
    );

    const realPnlDisplay = useMemo(() => {
        if (insightSummary?.realReturnAvailable === true && isFiniteNum(insightSummary.realReturn)) {
            const pct =
                isFiniteNum(insightSummary.realReturnPct) && insightSummary.realReturnPct != null
                    ? ` · ${insightSummary.realReturnPct.toLocaleString(locale, { maximumFractionDigits: 2 })}%`
                    : '';
            return { text: `${fmtTry(insightSummary.realReturn)}${pct}`, positive: insightSummary.realReturn >= 0 };
        }
        return {
            text: t('portfolio.realPnlWaitingCpi', 'TÜFE verisi bekleniyor'),
            positive: null as boolean | null,
        };
    }, [insightSummary, locale, t, fmtTry]);

    const animCost = useAnimatedNumber(summary?.totalInvested ?? 0);
    const animOpenVal = useAnimatedNumber(summary?.currentOpenValue ?? 0);
    const animNominal = useAnimatedNumber(summary?.totalNominalProfit ?? 0);
    const animNominalPct = useAnimatedNumber(summary?.totalNominalReturnPct ?? 0);
    const animReal = useAnimatedNumber(summary?.realizedProfit ?? 0);
    const animUnr = useAnimatedNumber(summary?.unrealizedProfit ?? 0);
    const animMiss = useAnimatedNumber(summary?.missedProfit ?? 0);

    const tableTotalPages = Math.max(1, Math.ceil(positions.length / TABLE_PAGE));
    const tableSlice = useMemo(() => {
        const start = tablePage * TABLE_PAGE;
        return positions.slice(start, start + TABLE_PAGE);
    }, [positions, tablePage]);

    useEffect(() => {
        setTablePage((p) => Math.min(p, tableTotalPages - 1));
    }, [tableTotalPages]);

    const pageIndices = buildPageIndices(tablePage, tableTotalPages);

    const deleteManual = async (id: number) => {
        if (!window.confirm(t('portfolio.confirmDelete', 'Bu manuel pozisyonu silmek istediğinize emin misiniz?'))) return;
        try {
            await financeClient.delete(`/api/portfolio/manual/${id}`);
            await invalidateManualPage();
        } catch (err: unknown) {
            alert(readFinanceApiError(err).message || t('portfolio.deleteFailed', 'Pozisyon silinemedi'));
        }
    };

    const pageStyle: CSSProperties = {
        background: tokens.bg,
        color: tokens.text,
    };

    const loading = summaryQuery.isLoading && positionsQuery.isLoading;
    const pageError =
        summaryQuery.error || positionsQuery.isError
            ? readFinanceApiError(summaryQuery.error ?? positionsQuery.error).message
            : null;

    if (loading) {
        return (
            <div className="portfolio-page" style={pageStyle}>
                <h1 style={{ fontSize: '1.75rem', fontWeight: 700 }}>{t('portfolio.analysisTitle', 'Portföy analizi')}</h1>
                <p style={{ color: tokens.textMuted }}>{t('common.loading', 'Yükleniyor...')}</p>
            </div>
        );
    }

    if (pageError) {
        return (
            <div className="portfolio-page" style={pageStyle}>
                <h1 style={{ fontSize: '1.75rem', fontWeight: 700 }}>{t('portfolio.analysisTitle', 'Portföy analizi')}</h1>
                <p style={{ color: tokens.error }}>
                    {t('news.errorPrefix', 'Hata')}: {pageError}
                </p>
            </div>
        );
    }

    return (
        <div
            className="portfolio-page"
            style={
                {
                    ...pageStyle,
                    '--tp-bg': tokens.bg,
                    '--tp-card': tokens.bgCard,
                    '--tp-border': tokens.border,
                    '--tp-text': tokens.text,
                    '--tp-muted': tokens.textMuted,
                    '--tp-success': '#22c55e',
                    '--tp-danger': '#ef4444',
                    '--tp-accent': '#60a5fa',
                    '--tp-accent-cost': '#f59e0b',
                    '--tp-accent-pnl': '#34d399',
                    '--tp-accent-pct': '#a78bfa',
                    '--tp-accent-poscnt': '#38bdf8',
                    '--tp-accent-sold': '#fb923c',
                } as CSSProperties
            }
        >
            <header className="pf-dash-header portfolio-fade-in">
                <div className="pf-dash-header-text">
                    <h1 className="pf-dash-title">{t('portfolio.analysisTitle', 'Portföy analizi')}</h1>
                    <p className="pf-dash-subtitle" style={{ color: tokens.textMuted }}>
                        {t(
                            'portfolio.heroSubtitleManual',
                            'Manuel yatırımlarınızın geçmiş karar, güncel değer, enflasyon ve satış sonrası senaryo göstergeleriyle analizi.',
                        )}
                    </p>
                </div>
                <div className="pf-dash-actions">
                    <button type="button" className="pf-dash-btn pf-dash-btn--primary" onClick={() => manualRef.current?.openCreate()}>
                        <Plus size={16} aria-hidden />
                        {t('portfolio.btnNewPosition', 'Yeni pozisyon ekle')}
                    </button>
                    <button
                        type="button"
                        className="pf-dash-btn"
                        disabled={evaluateInsightsMutation.isPending || positions.length === 0}
                        onClick={() => evaluateInsightsMutation.mutate()}
                        title={t(
                            'portfolio.btnEvaluatePortfolioHint',
                            'Portföy özetinizi e-posta ve uygulama bildirimi olarak alın.',
                        )}
                    >
                        {evaluateInsightsMutation.isPending
                            ? t('common.loading', 'Yükleniyor…')
                            : t('portfolio.btnEvaluatePortfolio', 'Portföyümü değerlendir')}
                    </button>
                    <button type="button" className="pf-dash-btn" onClick={() => navigate('/market/macro')}>
                        <Scale size={16} aria-hidden />
                        {t('portfolio.btnMacro', 'Makro karşılaştır')}
                    </button>
                    <button type="button" className="pf-dash-btn" onClick={() => window.print()}>
                        <FileText size={16} aria-hidden />
                        {t('portfolio.btnReport', 'Rapor al')}
                    </button>
                </div>
            </header>

            <div className="pf-kpi-grid portfolio-fade-in portfolio-fade-in--delay-1">
                <button
                    type="button"
                    className={`pf-stat-card pf-stat-card--kpi-click ${chartSeriesMode === 'cost' ? 'pf-stat-card--kpi-active' : ''}`}
                    onClick={selectCostChart}
                    aria-pressed={chartSeriesMode === 'cost'}
                    aria-label={t('portfolio.kpiTotalCostChart', 'Toplam maliyet grafiğini göster')}
                    title={t('portfolio.kpiTotalCostHint', 'Tıklayın: portföy maliyet grafiği')}
                >
                    <div className="pf-stat-label">{t('portfolio.totalCost', 'Toplam maliyet')}</div>
                    <div className="pf-stat-value">{fmtTry(animCost)}</div>
                </button>
                <button
                    type="button"
                    className={`pf-stat-card pf-stat-card--kpi-click ${chartSeriesMode === 'value' ? 'pf-stat-card--kpi-active' : ''}`}
                    onClick={selectValueChart}
                    aria-pressed={chartSeriesMode === 'value'}
                    aria-label={t('portfolio.kpiOpenValueChart', 'Portföy değeri grafiğini göster')}
                    title={t('portfolio.kpiOpenValueHint', 'Tıklayın: portföy değeri grafiği')}
                >
                    <div className="pf-stat-label">{t('portfolio.kpiOpenValue', 'Güncel açık değer')}</div>
                    <div className="pf-stat-value">{fmtTry(animOpenVal)}</div>
                </button>
                <button
                    type="button"
                    className={`pf-stat-card pf-stat-card--kpi-click ${chartSeriesMode === 'pnl' ? 'pf-stat-card--kpi-active' : ''} ${
                        (summary?.totalNominalProfit ?? 0) >= 0 ? 'pf-stat-card--pnl-pos' : 'pf-stat-card--pnl-neg'
                    }`}
                    onClick={selectPnlChart}
                    aria-pressed={chartSeriesMode === 'pnl'}
                    aria-label={t('portfolio.kpiNominalPnlChart', 'Nominal K/Z zaman serisini göster')}
                    title={t('portfolio.kpiNominalPnlHint', 'Tıklayın: günlük nominal kar/zarar (değer − maliyet)')}
                >
                    <div className="pf-stat-label">{t('portfolio.kpiNominalPnl', 'Toplam nominal K/Z')}</div>
                    <div className="pf-stat-value" style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                        {(summary?.totalNominalProfit ?? 0) >= 0 ? <TrendingUp size={18} /> : <TrendingDown size={18} />}
                        {fmtTry(animNominal)}
                    </div>
                </button>
                <div
                    className={`pf-stat-card ${
                        realPnlDisplay.positive === true ? 'pf-stat-card--pnl-pos' : realPnlDisplay.positive === false ? 'pf-stat-card--pnl-neg' : ''
                    }`}
                    aria-label={t('portfolio.kpiRealPnl', 'Reel K/Z')}
                >
                    <div className="pf-stat-label">
                        <PfHelpTerm term="reel-kz">{t('portfolio.kpiRealPnl', 'Reel K/Z')}</PfHelpTerm>
                    </div>
                    <div
                        className="pf-stat-value"
                        style={{ fontSize: insightSummary?.realReturnAvailable === true ? undefined : '0.92rem' }}
                    >
                        {realPnlDisplay.text}
                    </div>
                </div>
                <div
                    className="pf-stat-card"
                    aria-label={t('portfolio.kpiHealthScore', 'Portföy sağlık skoru')}
                    title={healthScoreView.fromBackend ? undefined : t('portfolio.healthScoreFallbackHint', 'Tahmini skor (kural tabanlı)')}
                >
                    <div className="pf-stat-label">
                        <PfHelpTerm term="portfoy-saglik-skoru">{t('portfolio.kpiHealthScore', 'Portföy Sağlık Skoru')}</PfHelpTerm>
                    </div>
                    <div className="pf-stat-value" style={{ fontSize: '1.05rem' }}>
                        {positions.length === 0 ? '—' : `${healthScoreView.score} · ${healthScoreView.labelDefault}`}
                    </div>
                </div>
                <button
                    type="button"
                    className={`pf-stat-card pf-stat-card--kpi-click ${chartSeriesMode === 'returnPct' ? 'pf-stat-card--kpi-active' : ''} ${
                        (summary?.totalNominalReturnPct ?? 0) >= 0 ? 'pf-stat-card--pnl-pos' : 'pf-stat-card--pnl-neg'
                    }`}
                    onClick={selectReturnPctChart}
                    aria-pressed={chartSeriesMode === 'returnPct'}
                    aria-label={t('portfolio.kpiReturnPctChart', 'Toplam getiri % grafiğini göster')}
                    title={t('portfolio.kpiReturnPctHint', 'Tıklayın: günlük getiri % (değer − açık maliyet) / açık maliyet')}
                >
                    <div className="pf-stat-label">{t('portfolio.totalPnlPct', 'Toplam getiri %')}</div>
                    <div className="pf-stat-value" style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                        {(summary?.totalNominalReturnPct ?? 0) >= 0 ? <TrendingUp size={18} /> : <TrendingDown size={18} />}
                        {animNominalPct.toLocaleString(locale, { maximumFractionDigits: 2 })}%
                    </div>
                </button>
                <button
                    type="button"
                    className={`pf-stat-card pf-stat-card--kpi-click ${(summary?.realizedProfit ?? 0) >= 0 ? 'pf-stat-card--pnl-pos' : 'pf-stat-card--pnl-neg'} ${
                        (summary?.soldPositions ?? 0) === 0 ? 'pf-stat-card--kpi-disabled' : ''
                    } ${chartSeriesMode === 'soldLifecyclePnl' ? 'pf-stat-card--kpi-active' : ''}`}
                    disabled={(summary?.soldPositions ?? 0) === 0}
                    onClick={() => {
                        if ((summary?.soldPositions ?? 0) === 0) return;
                        selectSoldLifecyclePnlChart();
                    }}
                    aria-label={t('portfolio.kpiRealizedChart', 'Gerçekleşmiş K/Z grafiğini ve satılmış dağılımını göster')}
                    aria-pressed={chartSeriesMode === 'soldLifecyclePnl'}
                    title={t(
                        'portfolio.kpiRealizedHint',
                        'Satışa kadar günlük nominal K/Z, satıştan sonra satılan miktarın bugüne tutulsaydı TRY değeri; satılmış dağılım (ana grafik; ayrı pencere yok).',
                    )}
                >
                    <div className="pf-stat-label">{t('portfolio.kpiRealized', 'Gerçekleşmiş K/Z')}</div>
                    <div className="pf-stat-value">{fmtTry(animReal)}</div>
                </button>
                <button
                    type="button"
                    className={`pf-stat-card pf-stat-card--kpi-click ${(summary?.unrealizedProfit ?? 0) >= 0 ? 'pf-stat-card--pnl-pos' : 'pf-stat-card--pnl-neg'} ${
                        (summary?.openPositions ?? 0) === 0 ? 'pf-stat-card--kpi-disabled' : ''
                    } ${chartSeriesMode === 'unrealizedPnl' ? 'pf-stat-card--kpi-active' : ''}`}
                    disabled={(summary?.openPositions ?? 0) === 0}
                    onClick={() => {
                        if ((summary?.openPositions ?? 0) === 0) return;
                        selectUnrealizedPnlChart();
                    }}
                    aria-label={t('portfolio.kpiUnrealizedChart', 'Gerçekleşmemiş K/Z grafiğini ve açık varlık dağılımını göster')}
                    aria-pressed={chartSeriesMode === 'unrealizedPnl'}
                    title={t(
                        'portfolio.kpiUnrealizedHint',
                        'Yalnızca açık pozisyonların günlük gerçekleşmemiş nominal K/Z serisi; açık varlık dağılımı (ana grafik).',
                    )}
                >
                    <div className="pf-stat-label">{t('portfolio.kpiUnrealized', 'Gerçekleşmemiş K/Z')}</div>
                    <div className="pf-stat-value">{fmtTry(animUnr)}</div>
                </button>
                <button
                    type="button"
                    className={`pf-stat-card pf-stat-card--kpi-click ${(summary?.missedProfit ?? 0) >= 0 ? 'pf-stat-card--pnl-pos' : 'pf-stat-card--pnl-neg'} ${
                        (summary?.soldPositions ?? 0) === 0 ? 'pf-stat-card--kpi-disabled' : ''
                    } ${chartSeriesMode === 'soldHoldHypothetical' ? 'pf-stat-card--kpi-active' : ''}`}
                    disabled={(summary?.soldPositions ?? 0) === 0}
                    onClick={() => {
                        if ((summary?.soldPositions ?? 0) === 0) return;
                        selectSoldHoldHypotheticalChart();
                    }}
                    aria-pressed={chartSeriesMode === 'soldHoldHypothetical'}
                    title={t(
                        'portfolio.kpiMissedOppHint',
                        'Satılmış pozisyonların tutulsaydı dağılımı ve satıştan bugüne değer grafiği (açılır pencere yok; ana grafik ve varlık dağılımı güncellenir).',
                    )}
                >
                    <div className="pf-stat-label">{t('portfolio.kpiMissedOpp', 'Kaçırılan fırsat (satış sonrası)')}</div>
                    <div className="pf-stat-value">{fmtTry(animMiss)}</div>
                </button>
                <button
                    type="button"
                    className={`pf-stat-card pf-stat-card--kpi-click ${chartSeriesMode === 'positionCount' ? 'pf-stat-card--kpi-active' : ''}`}
                    onClick={selectPositionCountChart}
                    aria-pressed={chartSeriesMode === 'positionCount'}
                    aria-label={t('portfolio.kpiPositionCountChart', 'Açık pozisyon sayısı grafiğini göster')}
                    title={t('portfolio.kpiPositionCountHint', 'Tıklayın: alım/satım tarihlerine göre günlük açık pozisyon adedi')}
                >
                    <div className="pf-stat-label">{t('portfolio.kpiPositionCounts', 'Pozisyon durumu')}</div>
                    <div className="pf-stat-value" style={{ fontSize: '1.05rem' }}>
                        {summary?.openPositions ?? 0} {t('portfolio.kpiOpenLabel', 'açık')} / {summary?.soldPositions ?? 0}{' '}
                        {t('portfolio.kpiSoldLabel', 'satılmış')}
                    </div>
                </button>
            </div>

            <div className="pf-main-grid portfolio-fade-in portfolio-fade-in--delay-1">
                <section className="pf-card-premium pf-panel-chart" style={{ borderColor: tokens.border, background: tokens.bgCard }}>
                    <div className="pf-panel-head">
                        <div className="pf-panel-title">
                            <LineChartIcon size={18} aria-hidden />
                            <span>
                                {chartSeriesMode === 'cost'
                                    ? t('portfolio.chartTitleCost')
                                    : chartSeriesMode === 'pnl'
                                      ? t('portfolio.chartTitlePnl')
                                      : chartSeriesMode === 'returnPct'
                                        ? t('portfolio.chartTitleReturnPct')
                                      : chartSeriesMode === 'unrealizedPnl'
                                        ? t(
                                              'portfolio.chartTitleUnrealizedPnl',
                                              'Açık pozisyonlar — gerçekleşmemiş nominal K/Z (TRY)',
                                          )
                                : chartSeriesMode === 'soldHoldHypothetical'
                                  ? t('portfolio.soldMissChartTitle', 'Satış tarihinden bugüne tutulsaydı toplam değer (TRY)')
                                  : chartSeriesMode === 'soldLifecyclePnl'
                                    ? t(
                                          'portfolio.soldLifecycleChartTitle',
                                          'Satılmış pozisyonlar — satışa kadar nominal K/Z, sonrasında tutulsaydı TRY değeri',
                                      )
                                    : chartSeriesMode === 'positionCount'
                                            ? positionCountVariant === 'sold'
                                                ? t('portfolio.chartTitleSoldPositionCount')
                                                : t('portfolio.chartTitlePositionCount')
                                            : t('portfolio.chartTitle')}
                            </span>
                            {(chartSeriesMode === 'soldHoldHypothetical'
                                ? soldHoldTsQuery.isFetching
                                : chartSeriesMode === 'soldLifecyclePnl'
                                  ? soldLifecycleTsQuery.isFetching
                                  : chartSeriesMode === 'unrealizedPnl'
                                    ? openUnrealizedTsQuery.isFetching
                                    : chartUsesTimeseries
                                    ? timeseriesQuery.isFetching
                                    : positionsQuery.isFetching) ? (
                                <span className="pf-chart-fetching" style={{ color: tokens.textMuted }}>
                                    {t('portfolio.chartRefreshing', 'Güncelleniyor…')}
                                </span>
                            ) : null}
                        </div>
                        <div className="pf-chart-tabs-row">
                            {chartSeriesMode !== 'soldHoldHypothetical' && chartSeriesMode !== 'soldLifecyclePnl' ? (
                                <div className="pf-range-tabs" role="tablist" aria-label={t('portfolio.chartRangeAria', 'Dönem')}>
                                    {(['1M', '3M', '6M', '1Y', 'ALL'] as SnapRange[]).map((r) => (
                                        <button
                                            key={r}
                                            type="button"
                                            role="tab"
                                            aria-selected={snapRange === r}
                                            className={snapRange === r ? 'pf-range-tabs__btn--on' : ''}
                                            onClick={() => setSnapRange(r)}
                                        >
                                            {t(`portfolio.snapRange.${r}`, r)}
                                        </button>
                                    ))}
                                </div>
                            ) : chartSeriesMode === 'soldLifecyclePnl' ? (
                                <p className="pf-chart-sold-hold-range-note" style={{ margin: 0, fontSize: '0.78rem', color: tokens.textMuted }}>
                                    {t(
                                        'portfolio.soldLifecycleChartRangeNote',
                                        'Dönem: en erken alımdan bugüne (otomatik). Satış gününe kadar K/Z, sonrasında satılan miktarın günlük değeri.',
                                    )}
                                </p>
                            ) : (
                                <p className="pf-chart-sold-hold-range-note" style={{ margin: 0, fontSize: '0.78rem', color: tokens.textMuted }}>
                                    {t('portfolio.soldMissChartRangeNote', 'Dönem: en erken satış tarihinden bugüne (otomatik).')}
                                </p>
                            )}
                            {chartSeriesMode === 'positionCount' ? (
                                <div
                                    className="pf-range-tabs pf-position-variant-tabs"
                                    role="tablist"
                                    aria-label={t('portfolio.positionVariantAria', 'Pozisyon türü')}
                                >
                                    <button
                                        type="button"
                                        role="tab"
                                        aria-selected={positionCountVariant === 'open'}
                                        className={positionCountVariant === 'open' ? 'pf-range-tabs__btn--on' : ''}
                                        onClick={() => setPositionCountVariant('open')}
                                    >
                                        {t('portfolio.chartTabOpen', 'Açık')}
                                    </button>
                                    <button
                                        type="button"
                                        role="tab"
                                        aria-selected={positionCountVariant === 'sold'}
                                        className={
                                            positionCountVariant === 'sold'
                                                ? 'pf-range-tabs__btn--on pf-range-tabs__btn--on-sold'
                                                : ''
                                        }
                                        onClick={() => setPositionCountVariant('sold')}
                                    >
                                        {t('portfolio.chartTabSold', 'Satılmış')}
                                    </button>
                                </div>
                            ) : null}
                            {chartShowsMetricToggle ? (
                                <div
                                    className="pf-range-tabs pf-chart-metric-tabs"
                                    role="tablist"
                                    aria-label={t('portfolio.chartMetricAria', 'Grafik gösterimi')}
                                >
                                    <button
                                        type="button"
                                        role="tab"
                                        aria-selected={chartSeriesMode === 'value'}
                                        className={chartSeriesMode === 'value' ? 'pf-range-tabs__btn--on' : ''}
                                        onClick={selectValueChart}
                                    >
                                        {t('portfolio.chartMetricValue', 'Değer')}
                                    </button>
                                    <button
                                        type="button"
                                        role="tab"
                                        aria-selected={chartSeriesMode === 'pnl'}
                                        className={chartSeriesMode === 'pnl' ? 'pf-range-tabs__btn--on' : ''}
                                        onClick={selectPnlChart}
                                    >
                                        {t('portfolio.chartMetricNominalPnl', 'Nominal K/Z')}
                                    </button>
                                    <button
                                        type="button"
                                        role="tab"
                                        aria-selected={chartSeriesMode === 'cost'}
                                        className={chartSeriesMode === 'cost' ? 'pf-range-tabs__btn--on' : ''}
                                        onClick={selectCostChart}
                                    >
                                        {t('portfolio.chartMetricCost', 'Maliyet')}
                                    </button>
                                </div>
                            ) : null}
                        </div>
                    </div>
                    {chartAllowDistDrag ? (
                        chartSeriesMode === 'soldHoldHypothetical' ? (
                            <>
                                <p className="pf-chart-dnd-hint" style={{ color: tokens.textMuted, fontSize: '0.74rem', margin: '0 0 6px' }}>
                                    {t(
                                        'portfolio.soldMissInlineHint',
                                        'Satılmış pozisyonlar satılan miktarda tutulsaydı toplam TRY değeri; günlük kapanışlar eksikse grafik noktası atlanabilir.',
                                    )}
                                </p>
                                <p className="pf-chart-dnd-hint" style={{ color: tokens.textMuted, fontSize: '0.74rem', margin: '0 0 10px' }}>
                                    {t(
                                        'portfolio.soldMissDragHint',
                                        'Satılmış dağılımdan bir satırı grafiğin üzerine sürükleyin; seçilen tür veya varlığın tutulsaydı TRY serisi ek çizgi olarak eklenir.',
                                    )}
                                </p>
                            </>
                        ) : chartSeriesMode === 'unrealizedPnl' ? (
                            <>
                                <p className="pf-chart-dnd-hint" style={{ color: tokens.textMuted, fontSize: '0.74rem', margin: '0 0 6px' }}>
                                    {t(
                                        'portfolio.openUnrealizedInlineHint',
                                        'Yalnızca açık pozisyonlar: günlük piyasa değeri eksi açık maliyet (nominal gerçekleşmemiş K/Z). Fiyat eksikse gün atlanabilir.',
                                    )}
                                </p>
                                <p className="pf-chart-dnd-hint" style={{ color: tokens.textMuted, fontSize: '0.74rem', margin: '0 0 10px' }}>
                                    {t(
                                        'portfolio.openUnrealizedDragHint',
                                        'Açık varlık dağılımından bir satırı grafiğe sürükleyin; seçilen tür veya sembol için gerçekleşmemiş K/Z üst çizgi olarak eklenir.',
                                    )}
                                </p>
                            </>
                        ) : chartSeriesMode === 'soldLifecyclePnl' ? (
                            <>
                                <p className="pf-chart-dnd-hint" style={{ color: tokens.textMuted, fontSize: '0.74rem', margin: '0 0 6px' }}>
                                    {t(
                                        'portfolio.soldLifecycleInlineHint',
                                        'Satış gününe kadar (dahil) günlük nominal K/Z toplamı; satıştan sonraki günlerde aynı satılan miktarda günlük piyasa değeri (TRY). Fiyat eksikse gün atlanabilir.',
                                    )}
                                </p>
                                <p className="pf-chart-dnd-hint" style={{ color: tokens.textMuted, fontSize: '0.74rem', margin: '0 0 10px' }}>
                                    {t(
                                        'portfolio.soldLifecycleDragHint',
                                        'Satılmış dağılımdan bir satırı grafiğe sürükleyin; seçilen tür veya varlık için aynı kuralda üst çizgi (K/Z sonra tutulsaydı değer) eklenir.',
                                    )}
                                </p>
                            </>
                        ) : (
                            <p className="pf-chart-dnd-hint" style={{ color: tokens.textMuted, fontSize: '0.74rem', margin: '0 0 10px' }}>
                                {t(
                                    'portfolio.chartDragHint',
                                    'Varlık dağılımından bir satırı sürükleyip grafiğin üzerine bırakın; seçili modda (değer / maliyet / K-Z / getiri %) ek renkli çizgi olarak eklenir.',
                                )}
                            </p>
                        )
                    ) : null}
                    {chartAllowDistDrag && chartOverlays.length > 0 ? (
                        <div
                            className="pf-chart-compare-chips"
                            role="list"
                            aria-label={t('portfolio.chartCompareAria', 'Karşılaştırma serileri')}
                        >
                            {chartOverlays.map((c, idx) => {
                                const q =
                                    chartSeriesMode === 'soldHoldHypothetical' || chartSeriesMode === 'soldLifecyclePnl'
                                        ? soldOverlayTimeseriesQueries[idx]
                                        : chartSeriesMode === 'unrealizedPnl'
                                          ? openUnrealizedOverlayQueries[idx]
                                          : segmentTimeseriesQueries[idx];
                                const err = q?.isError ? readFinanceApiError(q.error).message : null;
                                return (
                                    <span
                                        key={c.id}
                                        className={`pf-chart-compare-chip${err ? ' pf-chart-compare-chip--err' : ''}`}
                                        style={{ borderColor: c.color, color: tokens.text }}
                                        role="listitem"
                                        title={err ?? undefined}
                                    >
                                        <span className="pf-chart-compare-chip-dot" style={{ background: c.color }} aria-hidden />
                                        <span className="pf-chart-compare-chip-label">{c.label}</span>
                                        <button
                                            type="button"
                                            className="pf-chart-compare-chip-remove"
                                            aria-label={t('portfolio.chartCompareRemove', 'Seriyi kaldır')}
                                            onClick={() => removeChartOverlay(c.id)}
                                        >
                                            ×
                                        </button>
                                    </span>
                                );
                            })}
                        </div>
                    ) : null}
                    {chartLoading ? (
                        <p className="pf-empty-state" style={{ color: tokens.textMuted }}>
                            {t('common.loading', 'Yükleniyor…')}
                        </p>
                    ) : chartError ? (
                        <p className="pf-empty-state" style={{ color: tokens.error }}>
                            {readFinanceApiError(chartError).message}
                        </p>
                    ) : chartRowsForPlot.length === 0 ? (
                        <p className="pf-empty-state" style={{ color: tokens.textMuted }}>
                            {chartSeriesMode === 'soldHoldHypothetical'
                                ? t('portfolio.soldMissEmptyChart', 'Bu aralık için grafik oluşturulamadı. Tarihsel fiyatlar eksik olabilir.')
                                : chartSeriesMode === 'soldLifecyclePnl'
                                  ? t(
                                        'portfolio.soldLifecycleEmptyChart',
                                        'Bu dönem için grafik oluşturulamadı. Satış tarihi veya fiyat serisi eksik olabilir.',
                                    )
                                  : chartSeriesMode === 'unrealizedPnl'
                                    ? t(
                                          'portfolio.openUnrealizedEmptyChart',
                                          'Bu dönem için gerçekleşmemiş K/Z grafiği oluşturulamadı. Açık pozisyon veya fiyat serisi eksik olabilir.',
                                      )
                                  : chartSeriesMode === 'cost'
                                  ? t(
                                        'portfolio.snapshotsEmptyCost',
                                        'Bu aralıkta yeterli maliyet kaydı yok. Kayıtlar oluştukça grafik dolar.',
                                    )
                                  : chartSeriesMode === 'positionCount'
                                    ? t('portfolio.snapshotsEmptyPositionCount')
                                    : chartSeriesMode === 'pnl' || chartSeriesMode === 'returnPct'
                                      ? t('portfolio.snapshotsEmptyPnl')
                                      : t(
                                            'portfolio.snapshotsEmpty',
                                            'Bu aralıkta yeterli portföy değeri kaydı yok. Özet güncellemeleri için anlık değerleri kullanın.',
                                        )}
                        </p>
                    ) : (
                        <div
                            className={`pf-area-chart-wrap pf-chart-drop-zone${
                                chartAllowDistDrag && !chartLoading ? ' pf-chart-drop-zone--can-drop' : ''
                            }${chartDropActive ? ' pf-chart-drop-zone--drag-over' : ''}`}
                            onDragOver={handleChartDragOver}
                            onDragLeave={handleChartDragLeave}
                            onDrop={handleChartDrop}
                        >
                            <ResponsiveContainer width="100%" height="100%">
                                <AreaChart data={chartRowsForPlot} margin={{ top: 8, right: 8, left: 4, bottom: 4 }}>
                                    <defs>
                                        <linearGradient id="pfSnapArea" x1="0" y1="0" x2="0" y2="1">
                                            <stop offset="0%" stopColor="var(--tp-accent, #60a5fa)" stopOpacity={0.35} />
                                            <stop offset="100%" stopColor="var(--tp-accent, #60a5fa)" stopOpacity={0.02} />
                                        </linearGradient>
                                        <linearGradient id="pfSnapAreaCost" x1="0" y1="0" x2="0" y2="1">
                                            <stop offset="0%" stopColor="var(--tp-accent-cost, #f59e0b)" stopOpacity={0.38} />
                                            <stop offset="100%" stopColor="var(--tp-accent-cost, #f59e0b)" stopOpacity={0.03} />
                                        </linearGradient>
                                        <linearGradient id="pfSnapAreaPnl" x1="0" y1="0" x2="0" y2="1">
                                            <stop offset="0%" stopColor="var(--tp-accent-pnl, #34d399)" stopOpacity={0.38} />
                                            <stop offset="100%" stopColor="var(--tp-accent-pnl, #34d399)" stopOpacity={0.03} />
                                        </linearGradient>
                                        <linearGradient id="pfSnapAreaPct" x1="0" y1="0" x2="0" y2="1">
                                            <stop offset="0%" stopColor="var(--tp-accent-pct, #a78bfa)" stopOpacity={0.38} />
                                            <stop offset="100%" stopColor="var(--tp-accent-pct, #a78bfa)" stopOpacity={0.03} />
                                        </linearGradient>
                                        <linearGradient id="pfSnapAreaPosCnt" x1="0" y1="0" x2="0" y2="1">
                                            <stop offset="0%" stopColor="var(--tp-accent-poscnt, #38bdf8)" stopOpacity={0.38} />
                                            <stop offset="100%" stopColor="var(--tp-accent-poscnt, #38bdf8)" stopOpacity={0.03} />
                                        </linearGradient>
                                        <linearGradient id="pfSnapAreaSold" x1="0" y1="0" x2="0" y2="1">
                                            <stop offset="0%" stopColor="var(--tp-accent-sold, #fb923c)" stopOpacity={0.38} />
                                            <stop offset="100%" stopColor="var(--tp-accent-sold, #fb923c)" stopOpacity={0.03} />
                                        </linearGradient>
                                        <linearGradient id="pfSnapAreaSoldHold" x1="0" y1="0" x2="0" y2="1">
                                            <stop offset="0%" stopColor="#a78bfa" stopOpacity={0.38} />
                                            <stop offset="100%" stopColor="#a78bfa" stopOpacity={0.05} />
                                        </linearGradient>
                                    </defs>
                                    <CartesianGrid strokeDasharray="3 3" stroke={tokens.border} opacity={0.45} />
                                    <XAxis
                                        type="number"
                                        dataKey="t"
                                        domain={chartXDomainForPlot}
                                        scale="time"
                                        allowDataOverflow
                                        tick={{ fontSize: 10, fill: tokens.textMuted }}
                                        tickFormatter={(ts: number) =>
                                            new Date(ts).toLocaleDateString(locale, { month: 'short', day: 'numeric' })
                                        }
                                    />
                                    <YAxis
                                        tick={{ fontSize: 10, fill: tokens.textMuted }}
                                        tickFormatter={(v) =>
                                            chartSeriesMode === 'returnPct'
                                                ? `${new Intl.NumberFormat(locale, { maximumFractionDigits: 1 }).format(Number(v))}%`
                                                : chartSeriesMode === 'positionCount'
                                                  ? new Intl.NumberFormat(locale, { maximumFractionDigits: 0 }).format(Number(v))
                                                  : new Intl.NumberFormat(locale, { notation: 'compact', maximumFractionDigits: 1 }).format(
                                                        Number(v),
                                                    )
                                        }
                                    />
                                    <Tooltip
                                        labelFormatter={(label) => {
                                            const ts = typeof label === 'number' ? label : Number(label);
                                            return Number.isFinite(ts)
                                                ? new Date(ts).toLocaleString(locale, {
                                                      day: '2-digit',
                                                      month: 'short',
                                                      year: 'numeric',
                                                      hour: '2-digit',
                                                      minute: '2-digit',
                                                  })
                                                : '';
                                        }}
                                        formatter={(v: number | undefined) =>
                                            v == null
                                                ? ''
                                                : chartSeriesMode === 'returnPct'
                                                  ? `${Number(v).toLocaleString(locale, { maximumFractionDigits: 2 })}%`
                                                  : chartSeriesMode === 'positionCount'
                                                  ? positionCountVariant === 'sold'
                                                      ? t('portfolio.chartTooltipSoldCount', '{n} satılmış (kümülatif)').replace(
                                                            '{n}',
                                                            String(Math.round(Number(v))),
                                                        )
                                                      : t('portfolio.chartTooltipOpenCount', '{n} açık pozisyon').replace(
                                                            '{n}',
                                                            String(Math.round(Number(v))),
                                                        )
                                                  : new Intl.NumberFormat(locale, { style: 'currency', currency: 'TRY' }).format(v)
                                        }
                                        contentStyle={{
                                            background: tokens.bgCard,
                                            border: `1px solid ${tokens.border}`,
                                            borderRadius: 10,
                                            color: tokens.text,
                                        }}
                                    />
                                    <Area
                                        type="stepAfter"
                                        name={
                                            chartSeriesMode === 'soldHoldHypothetical'
                                                ? t('portfolio.chartSeriesSoldHold', 'Tutulsaydı toplam (satılmış)')
                                                : chartSeriesMode === 'soldLifecyclePnl'
                                                  ? t('portfolio.chartSeriesSoldLifecyclePnl', 'Satılmış: K/Z (alım–satış) + sonrası tutulsaydı değer')
                                                  : chartSeriesMode === 'unrealizedPnl'
                                                    ? t('portfolio.chartSeriesOpenUnrealizedPnl', 'Açık pozisyonlar — gerçekleşmemiş K/Z')
                                                    : t('portfolio.chartSeriesMain', 'Portföy (tümü)')
                                        }
                                        dataKey="balance"
                                        stroke={chartAreaVisual.stroke}
                                        strokeWidth={2}
                                        fill={chartAreaVisual.fill}
                                        dot={false}
                                        isAnimationActive={false}
                                        connectNulls={false}
                                    />
                                    {chartAllowDistDrag
                                        ? chartOverlays.map((c) => (
                                              <Line
                                                  key={c.id}
                                                  type="stepAfter"
                                                  name={c.label}
                                                  dataKey={c.id}
                                                  stroke={c.color}
                                                  strokeWidth={2}
                                                  dot={false}
                                                  isAnimationActive={false}
                                                  connectNulls={false}
                                              />
                                          ))
                                        : null}
                                </AreaChart>
                            </ResponsiveContainer>
                        </div>
                    )}
                </section>

                <section className="pf-card-premium pf-panel-dist" style={{ borderColor: tokens.border, background: tokens.bgCard }}>
                    <div className="pf-panel-head pf-dist-panel-head">
                        <h2 className="pf-panel-heading pf-dist-panel-heading">
                            {isSoldDistributionView
                                ? t('portfolio.soldMissDistTitle', 'Satılmış varlık dağılımı')
                                : chartSeriesMode === 'unrealizedPnl'
                                  ? t('portfolio.openDistTitle', 'Açık varlık dağılımı')
                                  : t('portfolio.distPanelTitle', 'Varlık dağılımı')}
                        </h2>
                        <div
                            className="pf-range-tabs pf-dist-mode-tabs"
                            role="tablist"
                            aria-label={t('portfolio.distModeAria', 'Dağılım görünümü')}
                        >
                            <button
                                type="button"
                                role="tab"
                                aria-selected={distViewMode === 'category'}
                                className={distViewMode === 'category' ? 'pf-range-tabs__btn--on' : ''}
                                onClick={() => setDistViewMode('category')}
                            >
                                {t('portfolio.distModeCategory', 'Kategori')}
                            </button>
                            <button
                                type="button"
                                role="tab"
                                aria-selected={distViewMode === 'asset'}
                                className={distViewMode === 'asset' ? 'pf-range-tabs__btn--on' : ''}
                                onClick={() => setDistViewMode('asset')}
                            >
                                {t('portfolio.distModeAsset', 'Varlık')}
                            </button>
                        </div>
                    </div>
                    <p className="pf-panel-hint" style={{ color: tokens.textMuted }}>
                        {isSoldDistributionView
                            ? chartSeriesMode === 'soldLifecyclePnl'
                                ? distViewMode === 'category'
                                    ? t(
                                          'portfolio.soldLifecycleDistHintCategory',
                                          'Satış tutarı (TRY, yoksa alış maliyeti) üzerinden tür kırılımı — grafikte tür filtresi için sürükleyin.',
                                      )
                                    : t(
                                          'portfolio.soldLifecycleDistHintAsset',
                                          'Satış tutarı (TRY, yoksa alış maliyeti) üzerinden sembol kırılımı — grafikte varlık filtresi için sürükleyin.',
                                      )
                                : distViewMode === 'category'
                                  ? t('portfolio.soldMissDistHintCategory', 'Bugünkü tut ve gör değeri (TRY) üzerinden tür kırılımı.')
                                  : t('portfolio.soldMissDistHintAsset', 'Bugünkü tut ve gör değeri (TRY) üzerinden sembol kırılımı.')
                            : chartSeriesMode === 'unrealizedPnl'
                              ? distViewMode === 'category'
                                  ? t(
                                        'portfolio.openUnrealizedDistHintCategory',
                                        'Güncel TRY değeri üzerinden açık tür kırılımı — grafikte tür filtresi için sürükleyin.',
                                    )
                                  : t(
                                        'portfolio.openUnrealizedDistHintAsset',
                                        'Güncel TRY değeri üzerinden açık sembol kırılımı — grafikte varlık filtresi için sürükleyin.',
                                    )
                              : distViewMode === 'category'
                                ? t('portfolio.distPanelHintCategory', 'Güncel TRY değeri üzerinden tür kırılımı.')
                                : t('portfolio.distPanelHintAsset', 'Güncel TRY değeri üzerinden sembol bazında ağırlık.')}
                    </p>
                    {distTotalForPanel <= 0 ? (
                        <p className="pf-empty-state" style={{ color: tokens.textMuted }}>
                            {isSoldDistributionView
                                ? chartSeriesMode === 'soldLifecyclePnl'
                                    ? t(
                                          'portfolio.soldLifecycleEmptyDist',
                                          'Satış tutarı veya maliyet hesaplanamadı veya satılmış pozisyon yok.',
                                      )
                                    : t('portfolio.soldMissEmptyDist', 'Tutulsaydı değer hesaplanamadı veya satılmış pozisyon yok.')
                                : t('portfolio.distEmpty', 'Açık pozisyon değeri hesaplanamadı veya kayıt yok.')}
                        </p>
                    ) : (
                        <>
                            <div className="pf-hbar-list">
                                {distRowsForPanel.map((d) => {
                                    const pct = (d.value / distTotalForPanel) * 100;
                                    const colorKey = d.colorKey ?? d.typeKey;
                                    return (
                                        <div
                                            key={d.typeKey}
                                            className={`pf-hbar-row${chartAllowDistDrag ? ' pf-hbar-row--draggable' : ''}`}
                                            draggable={chartAllowDistDrag}
                                            title={
                                                chartAllowDistDrag
                                                    ? t('portfolio.distDragRowTitle', 'Grafiğe sürükleyerek karşılaştırma serisi ekleyin')
                                                    : undefined
                                            }
                                            onDragStart={(e) => {
                                                if (!chartAllowDistDrag) return;
                                                const ck = d.colorKey ?? d.typeKey;
                                                const payload = {
                                                    mode: distViewMode === 'category' ? 'TYPE' : 'SYMBOL',
                                                    key: d.typeKey,
                                                    label: d.name,
                                                    color: PIE_COLOR_BY_TYPE[ck] ?? '#64748b',
                                                };
                                                e.dataTransfer.setData('application/json', JSON.stringify(payload));
                                                e.dataTransfer.effectAllowed = 'copy';
                                            }}
                                        >
                                            <div className="pf-hbar-label">
                                                <span className="pf-hbar-swatch" style={{ background: PIE_COLOR_BY_TYPE[colorKey] ?? '#64748b' }} />
                                                {d.name}
                                            </div>
                                            <div className="pf-hbar-track">
                                                <div className="pf-hbar-fill" style={{ width: `${pct}%`, background: PIE_COLOR_BY_TYPE[colorKey] ?? '#64748b' }} />
                                            </div>
                                            <div className="pf-hbar-pct">{formatAllocationSharePct(pct, locale, t)}</div>
                                        </div>
                                    );
                                })}
                            </div>
                            <ul className="pf-concentration-list" style={{ color: tokens.textMuted }}>
                                <li>
                                    {t('portfolio.distLargest', 'En büyük pozisyon')}:{' '}
                                    <strong style={{ color: tokens.text }}>
                                        {distConcentration.top
                                            ? `${distConcentration.top.symbol} · ${formatAllocationSharePct(distConcentration.topPct, locale, t)}`
                                            : '—'}
                                    </strong>
                                </li>
                                <li>
                                    {t('portfolio.distTop3', 'İlk üç ağırlık')}:{' '}
                                    <strong style={{ color: tokens.text }}>
                                        {distConcentration.top3Pct != null ? formatAllocationSharePct(distConcentration.top3Pct, locale, t) : '—'}
                                    </strong>
                                </li>
                                <li>
                                    {t('portfolio.distFxShare', 'Döviz türü ağırlığı (FX)')}:{' '}
                                    <strong style={{ color: tokens.text }}>
                                        {distConcentration.fxPct != null ? formatAllocationSharePct(distConcentration.fxPct, locale, t) : '—'}
                                    </strong>
                                </li>
                            </ul>
                            {!isSoldDistributionView && chartSeriesMode !== 'unrealizedPnl' && distTotalForPanel > 0 ? (
                                <p
                                    className={`pf-concentration-alert${
                                        concentration.topPct != null && concentration.topPct > 70
                                            ? ' pf-concentration-alert--critical'
                                            : concentration.topPct != null && concentration.topPct > 50
                                              ? ' pf-concentration-alert--warn'
                                              : ' pf-concentration-alert--ok'
                                    }`}
                                >
                                    <PfHelpTerm term="yogunlasma-riski">
                                        {insights?.concentrationRisk?.message?.trim() || openConcentrationLine}
                                    </PfHelpTerm>
                                </p>
                            ) : null}
                        </>
                    )}
                </section>
            </div>

            <div className="pf-secondary-grid portfolio-fade-in portfolio-fade-in--delay-2">
                <section className="pf-card-premium pf-post-sell-card" style={{ borderColor: tokens.border, background: tokens.bgCard }}>
                    <h2 className="pf-panel-heading">{t('portfolio.postSellTitle', 'Satış sonrası senaryo analizi')}</h2>
                    <p className="pf-panel-hint" style={{ color: tokens.textMuted }}>
                        {t(
                            'portfolio.postSellHint',
                            'Satılmış pozisyonlar için gerçekleşen K/Z, bugün aynı miktarda tutulsaydı değer ve satış sonrası fırsat farkı.',
                        )}
                    </p>
                    {soldRows.length === 0 ? (
                        <p className="pf-empty-state" style={{ color: tokens.textMuted }}>
                            {t('portfolio.postSellEmpty', 'Satılmış pozisyon yok.')}
                        </p>
                    ) : (
                        <>
                            {soldScenarioSummary ? (
                                <div className="pf-sold-summary-strip" style={{ color: tokens.textMuted }}>
                                    <span>
                                        <PfHelpTerm term="gerceklesmis-kz">{t('portfolio.soldSummaryPnl', 'Toplam satış K/Z')}</PfHelpTerm>
                                        :{' '}
                                        <strong style={{ color: tokens.text }}>{fmtTry(soldScenarioSummary.totalSellPnl)}</strong>
                                    </span>
                                    <span>
                                        <PfHelpTerm term="kacirilan-firsat">
                                            {t('portfolio.soldSummaryMissed', 'Toplam kaçırılan fırsat')}
                                        </PfHelpTerm>
                                        :{' '}
                                        <strong style={{ color: tokens.text }}>{fmtTry(soldScenarioSummary.totalMissed)}</strong>
                                    </span>
                                    <span>
                                        {t('portfolio.soldSummaryBest', 'En iyi satış')}:{' '}
                                        <strong style={{ color: tokens.text }}>
                                            {soldScenarioSummary.best
                                                ? `${soldScenarioSummary.best.symbol} (${fmtTry(soldScenarioSummary.best.pnl)})`
                                                : '—'}
                                        </strong>
                                    </span>
                                    <span>
                                        {t('portfolio.soldSummaryWorst', 'En kötü satış')}:{' '}
                                        <strong style={{ color: tokens.text }}>
                                            {soldScenarioSummary.worst
                                                ? `${soldScenarioSummary.worst.symbol} (${fmtTry(soldScenarioSummary.worst.pnl)})`
                                                : '—'}
                                        </strong>
                                    </span>
                                </div>
                            ) : null}
                        <div className="pf-table-scroll">
                            <table className="pf-table pf-table--post-sell">
                                <thead>
                                    <tr>
                                        <th>{t('portfolio.colSymbol', 'Sembol')}</th>
                                        <th className="pf-th-num">{t('portfolio.colSellPnl', 'Satış K/Z')}</th>
                                        <th className="pf-th-num">{t('portfolio.colIfHeldToday', 'Bugün tutsaydınız değer')}</th>
                                        <th className="pf-th-num">{t('portfolio.colMissedOpp', 'Kaçırılan fırsat')}</th>
                                        <th className="pf-th-num">{t('portfolio.colMissedPct', 'Fırsat %')}</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {soldRows.map((p) => (
                                        <tr key={p.id}>
                                            <td className="pf-num-strong">{p.symbol}</td>
                                            <td className="pf-num-strong" style={{ textAlign: 'right' }}>
                                                {p.realizedProfit != null ? fmtTry(Number(p.realizedProfit)) : '—'}
                                            </td>
                                            <td className="pf-num-strong" style={{ textAlign: 'right' }}>
                                                {p.holdValueToday != null ? fmtTry(Number(p.holdValueToday)) : '—'}
                                            </td>
                                            <td className="pf-num-strong" style={{ textAlign: 'right' }}>
                                                {p.missedProfit != null ? fmtTry(Number(p.missedProfit)) : '—'}
                                            </td>
                                            <td className="pf-num-strong" style={{ textAlign: 'right' }}>
                                                {p.missedReturnPct != null
                                                    ? `${Number(p.missedReturnPct).toLocaleString(locale, { maximumFractionDigits: 2 })}%`
                                                    : '—'}
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                        </>
                    )}
                </section>

                <section className="pf-card-premium" style={{ borderColor: tokens.border, background: tokens.bgCard }}>
                    <h2 className="pf-panel-heading">{t('portfolio.radarTitle', 'Portföy K/Z sıralaması')}</h2>
                    <p className="pf-panel-hint" style={{ color: tokens.textMuted }}>
                        {t(
                            'portfolio.radarHint',
                            'Açık pozisyonların gerçekleşmemiş K/Z değeri; aynı semboldeki lotlar toplanır. Kar edenler ve zarar edenler TRY tutarına göre sıralanır.',
                        )}
                    </p>
                    {positionsQuery.isLoading ? (
                        <p className="pf-empty-state" style={{ color: tokens.textMuted }}>
                            {t('common.loading', 'Yükleniyor…')}
                        </p>
                    ) : positionsQuery.isError ? (
                        <p className="pf-empty-state" style={{ color: tokens.error }}>
                            {readFinanceApiError(positionsQuery.error).message}
                        </p>
                    ) : pnlRankRows.pos.length === 0 && pnlRankRows.neg.length === 0 ? (
                        <div className="pf-empty-state pf-empty-state--boxed" style={{ borderColor: tokens.border, color: tokens.textMuted }}>
                            {t('portfolio.radarEmpty', 'Kar veya zarar gösterilecek açık pozisyon yok.')}
                        </div>
                    ) : (
                        <>
                        <div className="pf-radar-risk" style={{ color: tokens.textMuted }}>
                            <div className="pf-radar-risk-title">{t('portfolio.radarRiskTitle', 'Risk tespiti')}</div>
                            <div className="pf-radar-risk-grid">
                                <span>
                                    {t('portfolio.radarRiskTopWeight', 'En büyük ağırlık')}:{' '}
                                    <strong style={{ color: tokens.text }}>
                                        {radarRiskFacts.topWeightSymbol && radarRiskFacts.topWeightPct != null
                                            ? `${radarRiskFacts.topWeightSymbol} · ${formatAllocationSharePct(radarRiskFacts.topWeightPct, locale, t)}`
                                            : '—'}
                                    </strong>
                                </span>
                                <span>
                                    {t('portfolio.radarRiskBiggestLoss', 'En büyük zarar')}:{' '}
                                    <strong style={{ color: tokens.text }}>
                                        {radarRiskFacts.biggestLoss
                                            ? `${radarRiskFacts.biggestLoss.label} (${fmtTry(radarRiskFacts.biggestLoss.pnlTry)})`
                                            : '—'}
                                    </strong>
                                </span>
                                <span>
                                    {t('portfolio.radarRiskStrongest', 'En güçlü katkı')}:{' '}
                                    <strong style={{ color: tokens.text }}>
                                        {radarRiskFacts.strongestContribution
                                            ? `${radarRiskFacts.strongestContribution.label} (+${fmtTry(radarRiskFacts.strongestContribution.pnlTry)})`
                                            : '—'}
                                    </strong>
                                </span>
                                <span>
                                    {t('portfolio.radarRiskCategory', 'En riskli kategori')}:{' '}
                                    <strong style={{ color: tokens.text }}>
                                        {radarRiskFacts.riskiestCategory
                                            ? `${radarRiskFacts.riskiestCategory.label} · ${formatAllocationSharePct(radarRiskFacts.riskiestCategory.weightPct, locale, t)}`
                                            : '—'}
                                    </strong>
                                </span>
                            </div>
                        </div>
                        <div className="pf-radar-split">
                            <div>
                                <div className="pf-radar-col-title" style={{ color: tokens.text }}>
                                    {t('portfolio.radarPositive', 'Kar edenler (TRY)')}
                                </div>
                                <ul className="pf-radar-ul">
                                    {pnlRankRows.pos.slice(0, 8).map((r) => (
                                        <li key={r.key}>
                                            <span className="pf-radar-sym" title={r.key}>
                                                {r.label}
                                            </span>
                                            <span className="pf-radar-pos">+{fmtTry(r.pnlTry)}</span>
                                        </li>
                                    ))}
                                    {pnlRankRows.pos.length === 0 ? <li className="pf-radar-none">{t('portfolio.radarNonePos', '—')}</li> : null}
                                </ul>
                            </div>
                            <div>
                                <div className="pf-radar-col-title" style={{ color: tokens.text }}>
                                    {t('portfolio.radarNegative', 'Zarar edenler (TRY)')}
                                </div>
                                <ul className="pf-radar-ul">
                                    {pnlRankRows.neg.slice(0, 8).map((r) => (
                                        <li key={r.key}>
                                            <span className="pf-radar-sym" title={r.key}>
                                                {r.label}
                                            </span>
                                            <span className="pf-radar-neg">{fmtTry(r.pnlTry)}</span>
                                        </li>
                                    ))}
                                    {pnlRankRows.neg.length === 0 ? <li className="pf-radar-none">{t('portfolio.radarNoneNeg', '—')}</li> : null}
                                </ul>
                            </div>
                        </div>
                        </>
                    )}
                </section>
            </div>

            <section className="pf-card-premium pf-positions-block portfolio-fade-in portfolio-fade-in--delay-2" style={{ borderColor: tokens.border, background: tokens.bgCard }}>
                <h2 className="pf-panel-heading">{t('portfolio.positionsTitle', 'Pozisyonlar')}</h2>
                <div className="pf-table-scroll">
                    <table className="pf-table pf-table--positions">
                        <thead>
                            <tr>
                                <th>{t('portfolio.colSymbol', 'Sembol')}</th>
                                <th>{t('portfolio.colType', 'Tür')}</th>
                                <th className="pf-th-num">{t('portfolio.colQty', 'Miktar')}</th>
                                <th className="pf-th-num">{t('portfolio.colBuyPrice', 'Alış fiyatı')}</th>
                                <th className="pf-th-num">{t('portfolio.colSellPrice', 'Satış fiyatı')}</th>
                                <th className="pf-th-num pf-th-date">{t('portfolio.colBuyDate', 'Alış tarihi')}</th>
                                <th className="pf-th-num pf-th-date">{t('portfolio.colSellDate', 'Satış tarihi')}</th>
                                <th className="pf-th-num">{t('portfolio.colCurrent', 'Güncel fiyat')}</th>
                                <th className="pf-th-num">{t('manualInvest.colPnl', 'K/Z')}</th>
                                <th className="pf-th-num">{t('manualInvest.colPct', 'Getiri %')}</th>
                                <th>{t('manualInvest.colStatus', 'Durum')}</th>
                                <th className="pf-th-num">{t('portfolio.colActions', 'İşlem')}</th>
                            </tr>
                        </thead>
                        <tbody>
                            {positions.length === 0 ? (
                                <tr>
                                    <td colSpan={12} style={{ padding: 16, color: tokens.textMuted, textAlign: 'center' }}>
                                        {t('portfolio.tableEmptyManual', 'Henüz manuel pozisyon yok.')}
                                    </td>
                                </tr>
                            ) : (
                                tableSlice.map((p) => {
                                    const open = statusOf(p) === 'OPEN';
                                    const pnl = open ? p.unrealizedProfit : p.realizedProfit;
                                    const pct = open ? p.unrealizedReturnPct : p.realizedReturnPct;
                                    const expanded = expandedPositionId === p.id;
                                    const portfolioInflationEffect =
                                        insightSummary?.realReturnAvailable &&
                                        isFiniteNum(insightSummary.nominalReturn) &&
                                        isFiniteNum(insightSummary.realReturn)
                                            ? insightSummary.nominalReturn - insightSummary.realReturn
                                            : null;
                                    return (
                                        <Fragment key={p.id}>
                                        <tr>
                                            <td className="pf-num-strong">{p.symbol}</td>
                                            <td>{typeLabel(String(p.type).toUpperCase())}</td>
                                            <td className="pf-num-strong" style={{ textAlign: 'right' }}>
                                                {Number(p.quantity).toLocaleString(locale, { maximumFractionDigits: 8 })}
                                            </td>
                                            <td className="pf-num-strong" style={{ textAlign: 'right' }}>
                                                {p.buyPrice != null ? fmtTry(Number(p.buyPrice)) : '—'}
                                            </td>
                                            <td className="pf-num-strong" style={{ textAlign: 'right' }}>
                                                {p.sellPrice != null ? fmtTry(Number(p.sellPrice)) : '—'}
                                            </td>
                                            <td className="pf-num-strong pf-td-date" style={{ textAlign: 'right' }}>
                                                {formatPositionTableYmd(p.buyDate, locale)}
                                            </td>
                                            <td className="pf-num-strong pf-td-date" style={{ textAlign: 'right' }}>
                                                {formatPositionTableYmd(p.sellDate, locale)}
                                            </td>
                                            <td className="pf-num-strong" style={{ textAlign: 'right' }}>
                                                {p.currentPrice != null ? fmtTry(Number(p.currentPrice)) : '—'}
                                            </td>
                                            <td className="pf-num-strong" style={{ textAlign: 'right' }}>
                                                {pnl != null ? fmtTry(Number(pnl)) : '—'}
                                            </td>
                                            <td className="pf-num-strong" style={{ textAlign: 'right' }}>
                                                {pct != null ? `${Number(pct).toLocaleString(locale, { maximumFractionDigits: 2 })}%` : '—'}
                                            </td>
                                            <td>
                                                <span className={open ? 'pf-status-pill pf-status-pill--open' : 'pf-status-pill pf-status-pill--sold'}>
                                                    {open ? t('portfolio.statusOpen', 'Açık') : t('portfolio.statusSold', 'Satılmış')}
                                                </span>
                                            </td>
                                            <td style={{ textAlign: 'right' }}>
                                                <button
                                                    type="button"
                                                    className="pf-icon-inline"
                                                    onClick={() => setExpandedPositionId(expanded ? null : p.id)}
                                                >
                                                    {expanded ? t('portfolio.detailHide', 'Gizle') : t('portfolio.detailShow', 'Detay')}
                                                </button>
                                                <button
                                                    type="button"
                                                    className="pf-icon-inline"
                                                    title={t('priceAlert.title', 'Alarm Kur')}
                                                    onClick={() => {
                                                        const pa = portfolioTypeToPriceAlertAsset(String(p.type), p.symbol);
                                                        if (pa) {
                                                            setPriceAlertTarget({ ...pa, displayName: p.symbol });
                                                        }
                                                    }}
                                                >
                                                    <Bell size={14} style={{ verticalAlign: 'middle', marginRight: 4 }} />
                                                    {t('priceAlert.short', 'Alarm')}
                                                </button>
                                                <button type="button" className="pf-icon-inline" onClick={() => manualRef.current?.openEdit(p.id)} title={t('common.update', 'Düzenle')}>
                                                    {t('common.update', 'Düzenle')}
                                                </button>
                                                <button type="button" className="pf-icon-inline pf-icon-inline--danger" onClick={() => deleteManual(p.id)} title={t('portfolio.delete', 'Sil')}>
                                                    {t('portfolio.delete', 'Sil')}
                                                </button>
                                            </td>
                                        </tr>
                                        {expanded ? (
                                            <tr key={`${p.id}-detail`} className="pf-row-detail">
                                                <td colSpan={12}>
                                                    <div className="pf-row-detail-grid">
                                                        <div>
                                                            <span className="pf-row-detail-label">
                                                                <PfHelpTerm term="nominal-kz">{t('portfolio.detailNominalPnl', 'Nominal K/Z')}</PfHelpTerm>
                                                            </span>
                                                            <span className="pf-row-detail-value">
                                                                {pnl != null && Number.isFinite(Number(pnl)) ? fmtTry(Number(pnl)) : '—'}
                                                            </span>
                                                        </div>
                                                        <div>
                                                            <span className="pf-row-detail-label">
                                                                <PfHelpTerm term="reel-kz">{t('portfolio.detailRealPnl', 'Reel K/Z')}</PfHelpTerm>
                                                            </span>
                                                            <span className="pf-row-detail-value">
                                                                {insightSummary?.realReturnAvailable
                                                                    ? t('portfolio.detailRealPortfolioLevel', 'Portföy özeti KPI’da')
                                                                    : insightSummary?.realReturnUnavailableReason?.trim() ||
                                                                      t('portfolio.realPnlWaitingCpi', 'TÜFE verisi bekleniyor')}
                                                            </span>
                                                        </div>
                                                        <div>
                                                            <span className="pf-row-detail-label">
                                                                <PfHelpTerm term="enflasyon-etkisi">
                                                                    {t('portfolio.detailInflation', 'Enflasyon etkisi')}
                                                                </PfHelpTerm>
                                                            </span>
                                                            <span className="pf-row-detail-value">
                                                                {portfolioInflationEffect != null
                                                                    ? fmtTry(portfolioInflationEffect)
                                                                    : '—'}
                                                            </span>
                                                        </div>
                                                        <div>
                                                            <span className="pf-row-detail-label">
                                                                {t('portfolio.detailReturnSinceBuy', 'Alıştan bugüne getiri')}
                                                            </span>
                                                            <span className="pf-row-detail-value">
                                                                {pct != null && Number.isFinite(Number(pct))
                                                                    ? `${Number(pct).toLocaleString(locale, { maximumFractionDigits: 2 })}%`
                                                                    : '—'}
                                                            </span>
                                                        </div>
                                                        {!open ? (
                                                            <div>
                                                                <span className="pf-row-detail-label">
                                                                    <PfHelpTerm term="kacirilan-firsat">
                                                                        {t('portfolio.detailPostSellMiss', 'Satış sonrası fırsat farkı')}
                                                                    </PfHelpTerm>
                                                                </span>
                                                                <span className="pf-row-detail-value">
                                                                    {p.missedProfit != null && Number.isFinite(Number(p.missedProfit))
                                                                        ? fmtTry(Number(p.missedProfit))
                                                                        : '—'}
                                                                </span>
                                                            </div>
                                                        ) : null}
                                                    </div>
                                                </td>
                                            </tr>
                                        ) : null}
                                        </Fragment>
                                    );
                                })
                            )}
                        </tbody>
                    </table>
                </div>
                {tableTotalPages > 1 && (
                    <div className="pf-pagination">
                        <button type="button" className="pf-page-btn" disabled={tablePage <= 0} onClick={() => setTablePage((p) => Math.max(0, p - 1))}>
                            {t('portfolio.prev', 'Önceki')}
                        </button>
                        {pageIndices.map((item, idx) =>
                            item === 'gap' ? (
                                <span key={`g-${idx}`} style={{ color: tokens.textMuted }}>
                                    …
                                </span>
                            ) : (
                                <button
                                    key={item}
                                    type="button"
                                    className={`pf-page-btn ${item === tablePage ? 'pf-page-btn--active' : ''}`}
                                    onClick={() => setTablePage(item)}
                                >
                                    {item + 1}
                                </button>
                            ),
                        )}
                        <button
                            type="button"
                            className="pf-page-btn"
                            disabled={tablePage >= tableTotalPages - 1}
                            onClick={() => setTablePage((p) => Math.min(tableTotalPages - 1, p + 1))}
                        >
                            {t('portfolio.next', 'Sonraki')}
                        </button>
                    </div>
                )}
            </section>

            <ManualInvestmentAnalysisSection
                ref={manualRef}
                surface="modalsOnly"
                tokens={{
                    bg: tokens.bg,
                    bgCard: tokens.bgCard,
                    border: tokens.border,
                    text: tokens.text,
                    textMuted: tokens.textMuted,
                    error: tokens.error,
                }}
                locale={locale}
                onPortfolioMutated={invalidateManualPage}
            />
            {priceAlertTarget ? (
                <PriceAlertModal
                    open
                    onClose={() => setPriceAlertTarget(null)}
                    assetType={priceAlertTarget.assetType}
                    symbol={priceAlertTarget.symbol}
                    displayName={priceAlertTarget.displayName}
                />
            ) : null}
        </div>
    );
}
