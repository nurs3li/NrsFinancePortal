import { bondTypeFromInstrument } from '../components/viopBond/viopBondMarket';
import { bondTypeLabel } from '../components/viopBond/bondPositionLabels';
import { extractMaturityDate, formatBondDisplayName } from './bondFormatter';

export function bondIsinLabel(t: (k: string, d: string) => string): string {
    return t('market.bondIsinLabel', 'ISIN Kodu');
}

export function bondIsinDisplay(isin: string, t: (k: string, d: string) => string): string {
    return `${bondIsinLabel(t)}: ${isin}`;
}

/** Liste alt satırı: tür + vade (ISIN ana satırda). */
export function bondListSubtitle(
    isin: string,
    opts: {
        displayName?: string | null;
        issuer?: string | null;
        maturityDate?: string | null;
        daysToMaturity?: number | null;
        formatDate: (d: string | Date | null | undefined) => string;
        t: (k: string, d: string) => string;
    },
): string {
    const typeLabel = bondTypeLabel(
        bondTypeFromInstrument(isin, opts.displayName ?? undefined, opts.issuer ?? undefined),
        opts.t,
    );
    const days = opts.daysToMaturity;
    if (days != null && Number.isFinite(Number(days))) {
        return `${typeLabel} · ${opts.t('market.bondMaturityDaysShort', 'Vade {n} gün').replace('{n}', String(Math.round(Number(days))))}`;
    }
    const md = opts.maturityDate ?? extractMaturityDate(isin)?.toISOString();
    if (md) {
        return `${typeLabel} · ${opts.t('market.bondMaturityDateShort', 'Vade {date}').replace('{date}', opts.formatDate(md))}`;
    }
    return typeLabel;
}

export function bondHeroTitle(isin: string): string {
    return formatBondDisplayName(isin);
}

export function formatBondCouponRate(
    couponRate: number | null | undefined,
    locale: string,
): string {
    if (couponRate == null || !Number.isFinite(Number(couponRate)) || Number(couponRate) <= 0) {
        return '—';
    }
    return `${Number(couponRate).toLocaleString(locale, { minimumFractionDigits: 1, maximumFractionDigits: 2 })}%`;
}
