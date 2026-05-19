import { fetchMarketTerminalList, type MarketTerminalListItem } from '../../services/marketTerminalListApi';
import { terminalListItemToVm, type TerminalListInstrumentVm } from '../../utils/marketTerminalListVm';
import { classifyDebtInstrument } from '../../constants/OrderConstants';
import { viopCategoryFor, type ViopCategory } from '../../constants/ViopWhitelist';
import type { ViopCategory as BackendViopCategory } from '../../types/viopPosition';
import type { BondType } from '../../types/bondPosition';
import { viopUnderlyingCode } from './viopContractMeta';

const PAGE_SIZE = 50;

export type ViopMarketFilter = 'ALL' | ViopCategory;

export async function fetchAllTerminalInstruments(category: 'FUTURES' | 'BOND'): Promise<TerminalListInstrumentVm[]> {
    const all: MarketTerminalListItem[] = [];
    let page = 0;
    let hasNext = true;
    while (hasNext) {
        const res = await fetchMarketTerminalList({
            category,
            page,
            size: PAGE_SIZE,
            filter: 'ALL',
            sort: 'changePercent',
            dir: 'desc',
        });
        all.push(...res.items);
        hasNext = res.hasNext;
        page += 1;
        if (page > 20) break;
    }
    return all.map(terminalListItemToVm);
}

export function filterViopByCategory(items: TerminalListInstrumentVm[], filter: ViopMarketFilter): TerminalListInstrumentVm[] {
    if (filter === 'ALL') return items;
    return items.filter((i) => viopCategoryFor(i.symbol) === filter);
}

export function viopCategoryToBackend(cat: ViopCategory | null): BackendViopCategory {
    switch (cat) {
        case 'FX':
            return 'FX';
        case 'INDEX':
            return 'INDEX';
        case 'COMMODITY':
            return 'COMMODITY';
        case 'EQUITY':
        default:
            return 'EQUITY';
    }
}

/** F_THYAO0726 → THYAO; F_XU0301226 → XU030 */
export function extractViopUnderlying(symbol: string): string {
    return viopUnderlyingCode(symbol);
}

export function guessViopExpiryFromContractMonth(contractMonth?: string): string | undefined {
    if (!contractMonth?.trim()) return undefined;
    const m = contractMonth.trim();
    if (/^\d{4}-\d{2}-\d{2}$/.test(m)) return m;
    return undefined;
}

export function bondTypeFromInstrument(symbol: string, displayName?: string, issuer?: string): BondType {
    const cls = classifyDebtInstrument(displayName, issuer, symbol);
    if (cls === 'BOND_EUROBOND') return 'EUROBOND';
    if (cls === 'BOND_GOV') {
        const label = `${displayName ?? ''} ${issuer ?? ''}`.toUpperCase();
        if (label.includes('BILL') || label.includes('BONO')) {
            return 'TREASURY_BILL';
        }
        return 'GOVERNMENT_BOND';
    }
    if (displayName?.toUpperCase().includes('BILL') || displayName?.toUpperCase().includes('BONO')) {
        return 'TREASURY_BILL';
    }
    return 'CORPORATE_BOND';
}

export function bondCurrencyFromInstrument(symbol: string, displayName?: string): string {
    const cls = classifyDebtInstrument(displayName, undefined, symbol);
    if (cls === 'BOND_EUROBOND' || symbol.toUpperCase().startsWith('XS')) return 'USD';
    if (displayName?.toUpperCase().includes('EUR')) return 'EUR';
    return 'TRY';
}

export function viopFilterLabel(filter: ViopMarketFilter, t: (k: string, d: string) => string): string {
    switch (filter) {
        case 'FX':
            return t('viopBond.filterFx', 'Döviz');
        case 'INDEX':
            return t('viopBond.filterIndex', 'Endeks');
        case 'COMMODITY':
            return t('viopBond.filterCommodity', 'Emtia');
        case 'EQUITY':
            return t('viopBond.filterEquity', 'Pay');
        default:
            return t('viopBond.filterAll', 'Tümü');
    }
}

export function viopCategoryLabel(cat: ViopCategory | null, t: (k: string, d: string) => string): string {
    return viopFilterLabel(cat ?? 'ALL', t);
}
