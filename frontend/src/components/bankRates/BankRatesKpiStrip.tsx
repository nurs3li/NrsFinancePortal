import type { BankRatesKpis } from '../../utils/bankRatesVm';
import { BankLogo } from './BankLogo';

type Theme = { bgCard: string; border: string; text: string; textMuted: string };

type Props = {
    kpis: BankRatesKpis;
    tokens: Theme;
    locale: string;
    t: (key: string, fallback?: string) => string;
};

function fmt(n: number | null | undefined, locale: string, digits = 4) {
    if (n == null || !Number.isFinite(n)) return '—';
    return Number(n).toLocaleString(locale, { minimumFractionDigits: digits, maximumFractionDigits: digits });
}

type KpiCardProps = {
    label: string;
    row: { bankName: string; buy?: number; sell?: number; spread?: number } | null;
    value: string;
    valueClass?: string;
    cardStyle: React.CSSProperties;
};

function KpiCard({ label, row, value, valueClass, cardStyle }: KpiCardProps) {
    return (
        <article className="br-kpi-card br-kpi-card--highlight" style={cardStyle}>
            <span className="br-kpi-card__label">{label}</span>
            <div className="br-kpi-card__head">
                {row ? <BankLogo bankName={row.bankName} size="md" /> : null}
                <div className="br-kpi-card__meta">
                    <strong className="br-kpi-card__bank">{row?.bankName ?? '—'}</strong>
                    <span className={`br-kpi-card__value${valueClass ? ` ${valueClass}` : ''}`}>{value}</span>
                </div>
            </div>
        </article>
    );
}

export function BankRatesKpiStrip({ kpis, tokens, locale, t }: Props) {
    const cardStyle = { background: tokens.bgCard, borderColor: tokens.border, color: tokens.text };
    return (
        <div className="br-kpi-strip">
            <KpiCard
                label={t('bankRates.kpiBestBuy', 'En İyi Alış')}
                row={kpis.bestBuy}
                value={kpis.bestBuy ? `${fmt(kpis.bestBuy.buy, locale)} ₺` : '—'}
                valueClass="br-kpi-card__value--pos"
                cardStyle={cardStyle}
            />
            <KpiCard
                label={t('bankRates.kpiBestSell', 'En İyi Satış')}
                row={kpis.bestSell}
                value={kpis.bestSell ? `${fmt(kpis.bestSell.sell, locale)} ₺` : '—'}
                valueClass="br-kpi-card__value--neg"
                cardStyle={cardStyle}
            />
            <KpiCard
                label={t('bankRates.kpiNarrowSpread', 'En Dar Makas')}
                row={kpis.narrowestSpread}
                value={kpis.narrowestSpread ? `${fmt(kpis.narrowestSpread.spread, locale)} ₺` : '—'}
                cardStyle={cardStyle}
            />
        </div>
    );
}
