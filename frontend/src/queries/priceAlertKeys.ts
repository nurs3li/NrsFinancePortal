export const priceAlertKeys = {
    all: ['price-alerts'] as const,
    list: () => [...priceAlertKeys.all, 'list'] as const,
};
