import { useMemo } from 'react';
import type { AppLang } from '../i18n/LanguageContext';
import { useLanguage } from '../i18n/LanguageContext';

export function resolveNumberLocale(lang: AppLang): string {
    return lang === 'tr' ? 'tr-TR' : 'en-US';
}

function localeFor(lang: AppLang): string {
    return resolveNumberLocale(lang);
}

export function useLocaleFormat() {
    const { lang } = useLanguage();
    return useMemo(
        () => ({
            lang,
            locale: resolveNumberLocale(lang),
            formatNumber: (value: number | null | undefined, options?: Intl.NumberFormatOptions) =>
                formatNumberByLocale(value, lang, options),
            formatCurrency: (value: number | null | undefined, currency: string) =>
                formatCurrencyByLocale(value, currency, lang),
            formatPercent: (value: number | null | undefined) => formatPercentByLocale(value, lang),
            formatDate: (value: string | Date | null | undefined) => formatDateByLocale(value, lang),
        }),
        [lang],
    );
}

export function formatCurrencyByLocale(
    value: number | null | undefined,
    currency: string,
    lang: AppLang,
): string {
    if (value == null || Number.isNaN(value)) return '—';
    return new Intl.NumberFormat(localeFor(lang), {
        style: 'currency',
        currency,
        maximumFractionDigits: 2,
    }).format(value);
}

export function formatPercentByLocale(value: number | null | undefined, lang: AppLang): string {
    if (value == null || Number.isNaN(value)) return '—';
    return new Intl.NumberFormat(localeFor(lang), {
        style: 'percent',
        maximumFractionDigits: 2,
    }).format(value / 100);
}

export function formatDateByLocale(value: string | Date | null | undefined, lang: AppLang): string {
    if (!value) return '—';
    const d = value instanceof Date ? value : new Date(value);
    if (Number.isNaN(d.getTime())) return '—';
    return new Intl.DateTimeFormat(localeFor(lang), {
        day: '2-digit',
        month: 'short',
        year: 'numeric',
    }).format(d);
}

export function formatNumberByLocale(
    value: number | null | undefined,
    lang: AppLang,
    options?: Intl.NumberFormatOptions,
): string {
    if (value == null || Number.isNaN(value)) return '—';
    return new Intl.NumberFormat(localeFor(lang), options).format(value);
}
