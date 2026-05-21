import type { PriceAlert, PriceAlertConditionType, PriceAlertStatus } from '../types/priceAlert';

export function priceAlertConditionLabel(
    type: PriceAlertConditionType,
    t: (key: string, fallback?: string) => string
): string {
    switch (type) {
        case 'PRICE_GTE':
            return t('priceAlert.condPriceGte', 'Fiyat ≥ (üstüne çıkarsa)');
        case 'PRICE_LTE':
            return t('priceAlert.condPriceLte', 'Fiyat ≤ (altına düşerse)');
        case 'CHANGE_PCT_GTE':
            return t('priceAlert.condPctUpDaily', '% artış (günlük)');
        case 'CHANGE_PCT_LTE':
            return t('priceAlert.condPctDownDaily', '% düşüş (günlük)');
        default:
            return type;
    }
}

export function priceAlertStatusLabel(
    status: PriceAlertStatus,
    t: (key: string, fallback?: string) => string
): string {
    switch (status) {
        case 'ACTIVE':
            return t('alarms.statusActive', 'Aktif');
        case 'TRIGGERED':
            return t('alarms.statusTriggered', 'Tetiklendi');
        case 'DISABLED':
            return t('alarms.statusDisabled', 'Pasif');
        default:
            return status;
    }
}

export function isActivePriceAlert(status: PriceAlertStatus): boolean {
    return status === 'ACTIVE';
}

export function isPastPriceAlert(status: PriceAlertStatus): boolean {
    return status === 'TRIGGERED' || status === 'DISABLED';
}

export function formatPriceAlertTitle(alert: PriceAlert): string {
    return `${alert.symbol} · ${alert.assetType}`;
}

export function formatPriceAlertMeta(
    alert: PriceAlert,
    t: (key: string, fallback?: string) => string,
    locale: string
): string {
    const cond = priceAlertConditionLabel(alert.conditionType, t);
    const threshold =
        alert.conditionType.startsWith('CHANGE_PCT')
            ? `${Number(alert.threshold).toLocaleString(locale, { maximumFractionDigits: 2 })}%`
            : Number(alert.threshold).toLocaleString(locale, { maximumFractionDigits: 4 });
    const status = priceAlertStatusLabel(alert.status, t);
    const triggered =
        alert.lastTriggeredAt != null
            ? ` · ${new Date(alert.lastTriggeredAt).toLocaleString(locale)}`
            : '';
    return `${cond} ${threshold} · ${status}${triggered}`;
}
