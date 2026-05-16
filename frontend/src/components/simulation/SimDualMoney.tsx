import { formatSimMoney } from './simCurrency';
import { computeDualTotalValue, formatNativeMoney } from './simNativeCurrency';
import type { SimulationResultItem } from './types';

type SimDualMoneyProps = {
    locale: string;
    value: number;
    res: SimulationResultItem;
    usdTryRate?: number | null;
    className?: string;
};

/** Oturum biriminde tutar + farklı kotasyonlu varlıklar için yerel kur karşılığı. */
export function SimDualMoney({ locale, value, res, usdTryRate, className }: SimDualMoneyProps) {
    const dual = computeDualTotalValue(res, value, usdTryRate);
    return (
        <span className={className ?? 'sim-dual-money'}>
            <span className="sim-dual-money__primary">{formatSimMoney(locale, dual.primary, res.displayCurrency)}</span>
            {dual.secondary != null ? (
                <span className="sim-dual-money__secondary">
                    {' '}
                    · {formatNativeMoney(locale, dual.secondary, dual.nativeCurrency)}
                </span>
            ) : null}
        </span>
    );
}
