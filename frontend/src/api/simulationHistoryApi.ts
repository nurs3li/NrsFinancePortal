import type { AssetType } from '../constants/OrderConstants';
import type {
    SimulationHistoryEntry,
    SimulationPerformancePoint,
    SimulationResultItem,
} from '../components/simulation/types';
import { financeClient } from './client';

type ApiPerformancePoint = {
    date: string;
    priceTry: number;
    cumulativeReturnPct: number;
};

type ApiHistoryItem = {
    id: string;
    assetName: string;
    assetType: string;
    pickerAssetType?: string;
    displayCurrency?: string;
    unitsBought: number;
    initialAmount: number;
    buyPrice: number;
    buyDate: string;
    currentPrice: number;
    pnl: number;
    pnlPct: number;
    currentValue: number;
    buyPriceSource: string;
    historicalPriceDate: string;
    qualityFlag: string;
    series?: ApiPerformancePoint[] | null;
    visible: boolean;
    message: string;
    approximationNoticeCode?: string | null;
    scenarioLabel?: string;
};

type ApiHistoryEntry = {
    id: string;
    savedAt: string;
    label: string;
    amountCurrency: string;
    items: ApiHistoryItem[];
};

type ApiHistoryListResponse = {
    entries: ApiHistoryEntry[];
};

function unwrapData<T>(payload: unknown): T {
    if (payload && typeof payload === 'object' && 'data' in payload) {
        return (payload as { data: T }).data;
    }
    return payload as T;
}

function toPerformancePoint(p: ApiPerformancePoint): SimulationPerformancePoint {
    return {
        date: p.date,
        priceTry: Number(p.priceTry),
        cumulativeReturnPct: Number(p.cumulativeReturnPct),
    };
}

function toResultItem(item: ApiHistoryItem): SimulationResultItem {
    return {
        id: item.id,
        assetName: item.assetName,
        assetType: item.assetType as AssetType,
        pickerAssetType: item.pickerAssetType as SimulationResultItem['pickerAssetType'],
        displayCurrency: item.displayCurrency === 'USD' ? 'USD' : 'TRY',
        unitsBought: Number(item.unitsBought),
        initialAmount: Number(item.initialAmount),
        buyPrice: Number(item.buyPrice),
        buyDate: item.buyDate,
        currentPrice: Number(item.currentPrice),
        pnl: Number(item.pnl),
        pnlPct: Number(item.pnlPct),
        currentValue: Number(item.currentValue),
        buyPriceSource: item.buyPriceSource,
        historicalPriceDate: item.historicalPriceDate,
        qualityFlag: item.qualityFlag,
        series: item.series ? item.series.map(toPerformancePoint) : [],
        visible: item.visible,
        message: item.message,
        approximationNoticeCode: item.approximationNoticeCode ?? undefined,
        scenarioLabel: item.scenarioLabel,
    };
}

function toHistoryEntry(entry: ApiHistoryEntry): SimulationHistoryEntry {
    return {
        id: entry.id,
        savedAt: entry.savedAt,
        label: entry.label,
        amountCurrency: entry.amountCurrency === 'USD' ? 'USD' : 'TRY',
        items: entry.items.map(toResultItem),
    };
}

function toApiItem(item: SimulationResultItem): ApiHistoryItem {
    return {
        id: item.id,
        assetName: item.assetName,
        assetType: item.assetType,
        pickerAssetType: item.pickerAssetType,
        displayCurrency: item.displayCurrency,
        unitsBought: item.unitsBought,
        initialAmount: item.initialAmount,
        buyPrice: item.buyPrice,
        buyDate: item.buyDate,
        currentPrice: item.currentPrice,
        pnl: item.pnl,
        pnlPct: item.pnlPct,
        currentValue: item.currentValue,
        buyPriceSource: item.buyPriceSource,
        historicalPriceDate: item.historicalPriceDate,
        qualityFlag: item.qualityFlag,
        series: item.series,
        visible: item.visible,
        message: item.message,
        approximationNoticeCode: item.approximationNoticeCode ?? null,
        scenarioLabel: item.scenarioLabel,
    };
}

export async function fetchSimulationHistoryList(): Promise<SimulationHistoryEntry[]> {
    const res = await financeClient.get('/api/me/simulation-history');
    const body = unwrapData<ApiHistoryListResponse>(res.data);
    return (body.entries ?? []).map(toHistoryEntry);
}

export async function fetchSimulationHistoryDetail(id: string): Promise<SimulationHistoryEntry> {
    const res = await financeClient.get(`/api/me/simulation-history/${encodeURIComponent(id)}`);
    const body = unwrapData<ApiHistoryEntry>(res.data);
    return toHistoryEntry(body);
}

export async function saveSimulationHistoryEntry(
    entry: Omit<SimulationHistoryEntry, 'id' | 'savedAt'>,
): Promise<SimulationHistoryEntry> {
    const res = await financeClient.post('/api/me/simulation-history', {
        label: entry.label,
        amountCurrency: entry.amountCurrency,
        items: entry.items.map(toApiItem),
    });
    const body = unwrapData<ApiHistoryEntry>(res.data);
    return toHistoryEntry(body);
}

export async function deleteSimulationHistoryEntry(id: string): Promise<void> {
    await financeClient.delete(`/api/me/simulation-history/${encodeURIComponent(id)}`);
}

export function historyEntryNeedsDetail(entry: SimulationHistoryEntry): boolean {
    return entry.items.some((item) => !item.series || item.series.length === 0);
}
