import type { SimulationResultItem } from './types';

export function cloneHistoryItemsForSession(items: SimulationResultItem[]): SimulationResultItem[] {
    const stamp = Date.now();
    return items.map((item, index) => ({
        ...item,
        displayCurrency: item.displayCurrency === 'USD' ? 'USD' : 'TRY',
        id: `hist-load-${stamp}-${index}-${Math.random().toString(36).slice(2, 6)}`,
        visible: true,
    }));
}
