import { useMemo } from 'react';
import { MarketCategoryScrollTabs } from '../market/MarketCategoryScrollTabs';
import { useLanguage } from '../../i18n/LanguageContext';

import type { SimDisplayCurrency } from './types';

type SimCurrencyToggleProps = {
    value: SimDisplayCurrency;
    onChange: (currency: SimDisplayCurrency) => void;
    compact?: boolean;
};

export function SimCurrencyToggle({ value, onChange, compact = false }: SimCurrencyToggleProps) {
    const { t } = useLanguage();

    const tabs = useMemo(
        () => [
            {
                id: 'TRY' as const,
                label: t('simulation.currencyTry', 'Türk lirası'),
                icon: <span className="sim-currency-toggle__glyph" aria-hidden>₺</span>,
            },
            {
                id: 'USD' as const,
                label: t('simulation.currencyUsd', 'ABD doları'),
                icon: <span className="sim-currency-toggle__glyph" aria-hidden>$</span>,
            },
        ],
        [t],
    );

    return (
        <MarketCategoryScrollTabs
            className={[
                'terminal-category-scroll-shell--hero-fx-quote',
                'sim-currency-toggle',
                compact ? 'sim-currency-toggle--compact' : '',
            ]
                .filter(Boolean)
                .join(' ')}
            categories={tabs}
            value={value}
            onChange={onChange}
            ariaLabel={t('simulation.amountCurrencyAria', 'Simülasyon tutarı para birimi')}
        />
    );
}
