import { SIMULATION_HISTORY_STORAGE_KEY } from './constants';
import type { SimulationHistoryEntry, SimulationResultItem } from './types';

export function cloneHistoryItemsForSession(items: SimulationResultItem[]): SimulationResultItem[] {
    const stamp = Date.now();
    return items.map((item, index) => ({
        ...item,
        displayCurrency: item.displayCurrency === 'USD' ? 'USD' : 'TRY',
        id: `hist-load-${stamp}-${index}-${Math.random().toString(36).slice(2, 6)}`,
        visible: true,
    }));
}

const MAX_HISTORY = 40;

export function loadSimulationHistory(): SimulationHistoryEntry[] {
    try {
        const raw = localStorage.getItem(SIMULATION_HISTORY_STORAGE_KEY);
        if (!raw) return [];
        const parsed = JSON.parse(raw) as SimulationHistoryEntry[];
        return Array.isArray(parsed) ? parsed : [];
    } catch {
        return [];
    }
}

export function persistSimulationHistory(entries: SimulationHistoryEntry[]): void {
    localStorage.setItem(SIMULATION_HISTORY_STORAGE_KEY, JSON.stringify(entries.slice(0, MAX_HISTORY)));
}

export function appendSimulationHistory(entry: SimulationHistoryEntry): SimulationHistoryEntry[] {
    const list = [entry, ...loadSimulationHistory()].slice(0, MAX_HISTORY);
    persistSimulationHistory(list);
    return list;
}

export function removeSimulationHistoryEntry(id: string): SimulationHistoryEntry[] {
    const list = loadSimulationHistory().filter((e) => e.id !== id);
    persistSimulationHistory(list);
    return list;
}
