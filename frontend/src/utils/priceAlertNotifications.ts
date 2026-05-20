const PRICE_ALERT_TYPES = new Set(['PRICE_ALERT_TRIGGERED', 'PRICE_ALERT_IN_APP']);

export function isPriceAlertNotificationType(type: string | null | undefined): boolean {
    return PRICE_ALERT_TYPES.has((type ?? '').toUpperCase());
}

export function priceAlertNotificationTypeLabel(
    type: string,
    t: (key: string, fallback: string) => string,
): string {
    if (PRICE_ALERT_TYPES.has((type ?? '').toUpperCase())) {
        return t('notifications.typePriceAlert', 'Fiyat alarmı');
    }
    return type;
}
