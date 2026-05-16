import type { MarketCategory } from '../components/market/marketTypes';
import { formatAssetLabel, type MarketKind } from '../lib/assetBranding';
import { getPreciousMetalDisplayMeta } from '../constants/preciousMetalsUsd';
import type { MarketTerminalListItem } from '../services/marketTerminalListApi';

export type TerminalListInstrumentVm = {
    symbol: string;
    category: MarketCategory;
    displayName: string;
    price: number;
    changePercent: number;
    dailyChangePercent?: number;
    trend: 'UP' | 'DOWN';
    volume?: number | null;
    currency?: string;
    marketRegion?: string;
    exchange?: string;
    sector?: string;
    source?: string;
    type: string;
    sparkline: number[];
    pctDay?: number | null;
    pctWeek?: number | null;
    pctMonth?: number | null;
    pctYear?: number | null;
    maturityDate?: string;
    daysToMaturity?: number;
    couponRate?: number;
    yieldToMaturity?: number | null;
    contractMonth?: string;
    basis?: number;
    marginRequirement?: number;
    longShort: string;
    listSubtitle?: string;
};

function marketKindForCategory(category: MarketCategory): MarketKind {
    if (category === 'CRYPTO') return 'CRYPTO';
    if (category === 'FX') return 'FX';
    if (category === 'METALS') return 'METALS';
    if (category === 'FUNDS') return 'FUNDS';
    return 'EQUITY';
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
    if (clean.length >= 2) return clean.slice(-points);
    return buildFallbackTrendSparkline(price, changePercent, points);
}

export function terminalListItemToVm(item: MarketTerminalListItem): TerminalListInstrumentVm {
    const symbol = item.symbol;
    const spark = item.sparklineCloses ?? [];
    const price = item.price;
    const changePercent = item.changePercent;
    const dailyChangePercent = item.dailyChangePercent ?? changePercent;

    if (item.category === 'BOND') {
        return {
            symbol,
            category: 'BOND',
            displayName: item.displayName ?? symbol,
            price,
            changePercent,
            dailyChangePercent,
            trend: item.trend,
            volume: item.volume,
            type: 'BOND',
            sparkline: normalizeTrendSparkline(spark, price, changePercent),
            pctDay: item.pctDay,
            pctWeek: item.pctWeek,
            pctMonth: item.pctMonth,
            pctYear: item.pctYear,
            maturityDate: item.maturityDate ?? undefined,
            daysToMaturity: item.daysToMaturity ?? undefined,
            couponRate: item.couponRate ?? 0,
            yieldToMaturity: item.yieldToMaturity ?? null,
            longShort: 'NÖTR',
        };
    }

    if (item.category === 'FUTURES') {
        return {
            symbol,
            category: 'FUTURES',
            displayName: item.displayName ?? symbol,
            price,
            changePercent,
            dailyChangePercent,
            trend: item.trend,
            volume: item.volume,
            type: 'FUTURES',
            sparkline: normalizeTrendSparkline(spark, price, changePercent),
            pctDay: item.pctDay,
            pctWeek: item.pctWeek,
            pctMonth: item.pctMonth,
            pctYear: item.pctYear,
            contractMonth: item.contractMonth ?? undefined,
            basis: item.basis ?? undefined,
            marginRequirement: item.marginRequirement ?? undefined,
            longShort: changePercent >= 0 ? 'LONG' : 'SHORT',
        };
    }

    if (item.category === 'EQUITY' && item.equitySubmarket === 'BIST') {
        const displayName =
            (item.displayName && item.displayName.trim()) ||
            (item.name && item.name.trim()) ||
            symbol;
        return {
            symbol,
            category: 'EQUITY',
            displayName,
            price,
            changePercent,
            dailyChangePercent,
            trend: item.trend,
            volume: item.volume,
            currency: item.currency ?? 'TRY',
            marketRegion: item.marketRegion ?? 'TR',
            exchange: item.exchange ?? 'BIST',
            sector: item.sector ?? undefined,
            source: item.source ?? undefined,
            type: 'STOCK',
            sparkline: normalizeTrendSparkline(spark, price, changePercent),
            pctDay: item.pctDay,
            pctWeek: item.pctWeek,
            pctMonth: item.pctMonth,
            pctYear: item.pctYear,
            longShort: item.trend === 'UP' ? 'LONG' : 'SHORT',
        };
    }

    if (item.category === 'METALS') {
        const meta = getPreciousMetalDisplayMeta(symbol);
        return {
            symbol,
            category: 'METALS',
            displayName: meta?.displayName ?? item.displayName ?? formatAssetLabel(symbol, 'METALS'),
            price,
            changePercent,
            dailyChangePercent,
            trend: item.trend,
            volume: item.volume,
            currency: item.currency ?? undefined,
            source: item.source ?? undefined,
            type: 'STOCK',
            listSubtitle: meta?.subtitle,
            sparkline: normalizeTrendSparkline(spark, price, dailyChangePercent, 90),
            pctDay: item.pctDay,
            pctWeek: item.pctWeek,
            pctMonth: item.pctMonth,
            pctYear: item.pctYear,
            longShort: item.trend === 'UP' ? 'LONG' : 'SHORT',
        };
    }

    return {
        symbol,
        category: item.category,
        displayName: item.displayName ?? formatAssetLabel(symbol, marketKindForCategory(item.category)),
        price,
        changePercent,
        dailyChangePercent,
        trend: item.trend,
        volume: item.volume,
        currency: item.currency ?? undefined,
        marketRegion: item.marketRegion ?? undefined,
        exchange: item.exchange ?? undefined,
        sector: item.sector ?? undefined,
        source: item.source ?? undefined,
        type: 'STOCK',
        sparkline: normalizeTrendSparkline(spark, price, dailyChangePercent, item.category === 'EQUITY' ? 320 : 90),
        pctDay: item.pctDay,
        pctWeek: item.pctWeek,
        pctMonth: item.pctMonth,
        pctYear: item.pctYear,
        longShort: item.trend === 'UP' ? 'LONG' : 'SHORT',
    };
}
