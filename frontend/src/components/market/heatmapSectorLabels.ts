import type { ChartRangeId } from './heatmapRange';

/** Isı haritası sektör anahtarı → i18n anahtarı (değer `tr` / `en` dosyalarında). */
const SECTOR_I18N_KEY: Record<string, string> = {
    ALL: 'heatmap.sector.all',
    BIST_EQUITY: 'heatmap.sector.bist',
    TEFAS_FUNDS: 'heatmap.sector.tefasFunds',
    PRECIOUS_METALS: 'heatmap.sector.preciousMetals',
    PRECIOUS_METALS_GRAM: 'heatmap.sector.preciousMetalsGram',
    PRECIOUS_METALS_OUNCE: 'heatmap.sector.preciousMetalsOunce',
    COMMODITIES: 'heatmap.sector.preciousMetals',
    FOREX: 'heatmap.sector.forex',
    CRYPTO: 'heatmap.sector.crypto',
    'ETFS & INDEX': 'heatmap.sector.etfsIndex',
    FINANCIAL: 'heatmap.sector.financial',
    TECHNOLOGY: 'heatmap.sector.technology',
    'COMMUNICATION SERVICES': 'heatmap.sector.communicationServices',
    'CONSUMER CYCLICAL': 'heatmap.sector.consumerCyclical',
    'CONSUMER DEFENSIVE': 'heatmap.sector.consumerDefensive',
    HEALTHCARE: 'heatmap.sector.healthcare',
    EQUITY: 'heatmap.sector.equityOther',
    OTHER: 'heatmap.sector.other',
};

export function heatmapSectorLabel(sectorKey: string, t: (key: string, defaultValue: string) => string): string {
    if (sectorKey === 'ALL') return t('heatmap.sector.all', 'Tüm sektörler');
    const k = SECTOR_I18N_KEY[sectorKey];
    if (k) return t(k, sectorKey);
    return sectorKey;
}

const RANGE_LABEL_KEY: Record<ChartRangeId, string> = {
    '1D': 'heatmap.range.1d',
    '1W': 'heatmap.range.1w',
    '1M': 'heatmap.range.1m',
    '3M': 'heatmap.range.3m',
    '6M': 'heatmap.range.6m',
    '1Y': 'heatmap.range.1y',
    '2Y': 'heatmap.range.2y',
};

export function heatmapRangeLabel(range: ChartRangeId, t: (key: string, defaultValue: string) => string): string {
    const defaults: Record<ChartRangeId, string> = {
        '1D': '1 gün',
        '1W': '1 hafta',
        '1M': '1 ay',
        '3M': '3 ay',
        '6M': '6 ay',
        '1Y': '1 yıl',
        '2Y': '2 yıl',
    };
    return t(RANGE_LABEL_KEY[range], defaults[range]);
}
