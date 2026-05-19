const PORTFOLIO_INSIGHT_TYPES = new Set([
    'REAL_RETURN_NEGATIVE',
    'REAL_RETURN_POSITIVE',
    'PORTFOLIO_CONCENTRATION_RISK',
    'PORTFOLIO_EVALUATION_REPORT',
]);

export function isPortfolioInsightNotificationType(type: string | null | undefined): boolean {
    return PORTFOLIO_INSIGHT_TYPES.has((type ?? '').toUpperCase());
}

export function portfolioInsightNotificationTypeLabel(
    type: string,
    t: (key: string, fallback: string) => string,
): string {
    switch ((type ?? '').toUpperCase()) {
        case 'REAL_RETURN_NEGATIVE':
            return t('notifications.typeRealReturnNegative', 'Reel Getiri Uyarısı');
        case 'REAL_RETURN_POSITIVE':
            return t('notifications.typeRealReturnPositive', 'Reel Getiri Pozitif');
        case 'PORTFOLIO_CONCENTRATION_RISK':
            return t('notifications.typeConcentrationRisk', 'Konsantrasyon Riski');
        case 'PORTFOLIO_EVALUATION_REPORT':
            return t('notifications.typePortfolioEvaluation', 'Portföy değerlendirmeniz');
        default:
            return type;
    }
}
