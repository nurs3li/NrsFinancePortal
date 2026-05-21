import type { ViopCategory, ViopDirection, ViopPositionStatus } from '../../types/viopPosition';

export function viopDirectionLabel(direction: ViopDirection, t: (k: string, d: string) => string): string {
    return direction === 'LONG' ? t('viopBond.dirLong', 'Uzun') : t('viopBond.dirShort', 'Kısa');
}

export function viopStatusLabel(status: ViopPositionStatus, t: (k: string, d: string) => string): string {
    if (status === 'OPEN') return t('viopBond.statusOpen', 'Açık');
    if (status === 'CLOSED') return t('viopBond.statusClosed', 'Kapalı');
    if (status === 'DELETED') return t('viopBond.statusDeleted', 'Silindi');
    return status;
}

export function viopCategoryLabel(cat: ViopCategory, t: (k: string, d: string) => string): string {
    switch (cat) {
        case 'FX':
            return t('viopBond.filterFx', 'Döviz');
        case 'INDEX':
            return t('viopBond.filterIndex', 'Endeks');
        case 'COMMODITY':
            return t('viopBond.filterCommodity', 'Emtia');
        case 'EQUITY':
        default:
            return t('viopBond.filterEquity', 'Pay');
    }
}
