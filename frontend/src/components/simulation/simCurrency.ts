export type SimDisplayCurrency = 'TRY' | 'USD';

export function simCurrencySymbol(currency: SimDisplayCurrency): string {
    return currency === 'USD' ? '$' : '₺';
}

export function formatSimMoney(
    locale: string,
    value: number,
    currency: SimDisplayCurrency,
    options?: Pick<Intl.NumberFormatOptions, 'maximumFractionDigits' | 'minimumFractionDigits'>,
): string {
    return new Intl.NumberFormat(locale, {
        style: 'currency',
        currency,
        maximumFractionDigits: 2,
        ...options,
    }).format(value);
}

export function resolveSessionDisplayCurrency(
    results: { displayCurrency?: SimDisplayCurrency }[],
    fallback: SimDisplayCurrency = 'TRY',
): SimDisplayCurrency {
    const first = results[0]?.displayCurrency;
    return first === 'USD' || first === 'TRY' ? first : fallback;
}
