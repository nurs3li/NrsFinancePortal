import { PfHelpTerm } from './PfHelpTerm';
import type { ManualPortfolioView, ManualPositionRealReturnStatus } from '../../types/manualPortfolio';

function formatYmd(ymd: string | null | undefined, locale: string): string {
    if (!ymd?.trim()) return '—';
    const d = new Date(`${ymd.trim()}T12:00:00`);
    if (Number.isNaN(d.getTime())) return ymd;
    return d.toLocaleDateString(locale, { year: 'numeric', month: 'short', day: 'numeric' });
}

type Props = {
    p: ManualPortfolioView;
    open: boolean;
    pnl: number | null | undefined;
    locale: string;
    t: (key: string, fallback?: string) => string;
    fmtTry: (n: number) => string;
};

function formatPct(v: number | null | undefined, locale: string): string {
    if (v == null || !Number.isFinite(Number(v))) return '—';
    return `${Number(v).toLocaleString(locale, { maximumFractionDigits: 2 })}%`;
}

function cpiMissingLabel(t: Props['t']): string {
    return t('portfolio.cpiDataMissing', 'TÜFE verisi yok');
}

function RealReturnStatusBadge({
    status,
    t,
}: {
    status: ManualPositionRealReturnStatus | string | null | undefined;
    t: Props['t'];
}) {
    if (!status || status === 'NO_CPI_DATA') {
        return (
            <span className="pf-real-return-badge pf-real-return-badge--no-cpi">
                {t('portfolio.realReturnNoCpiBadge', 'TÜFE verisi yok')}
            </span>
        );
    }
    if (status === 'BEAT_INFLATION') {
        return (
            <span className="pf-real-return-badge pf-real-return-badge--beat">
                {t('portfolio.realReturnBeatInflation', 'Enflasyonu yendi')}
            </span>
        );
    }
    return (
        <span className="pf-real-return-badge pf-real-return-badge--lost">
            {t('portfolio.realReturnLostToInflation', 'Enflasyona yenildi')}
        </span>
    );
}

export function PositionRealReturnDetailGrid({ p, open, pnl, locale, t, fmtTry }: Props) {
    const periodPct =
        p.nominalReturnPct != null && Number.isFinite(Number(p.nominalReturnPct))
            ? Number(p.nominalReturnPct)
            : open
              ? p.unrealizedReturnPct
              : p.realizedReturnPct;
    const periodEnd = p.calculationEndDate ?? (open ? p.cpiEndDate : p.sellDate);
    const periodRange =
        p.buyDate && periodEnd
            ? `${formatYmd(p.buyDate, locale)} → ${formatYmd(periodEnd, locale)}`
            : null;

    return (
        <div className="pf-row-detail-grid">
            <div>
                <span className="pf-row-detail-label">
                    <PfHelpTerm term="nominal-kz">{t('portfolio.detailNominalPnl', 'Nominal K/Z')}</PfHelpTerm>
                </span>
                <span className="pf-row-detail-value">
                    {pnl != null && Number.isFinite(Number(pnl)) ? fmtTry(Number(pnl)) : '—'}
                </span>
            </div>
            <div>
                <span className="pf-row-detail-label">
                    <span
                        title={t(
                            'portfolio.realReturnTooltip',
                            'Reel getiri, alış maliyetinin Türkiye TÜFE endeksiyle satış tarihine veya açık pozisyonlarda son açıklanan TÜFE tarihine taşınmasıyla hesaplanır.'
                        )}
                    >
                        <PfHelpTerm term="reel-kz">{t('portfolio.detailRealPnl', 'Reel K/Z')}</PfHelpTerm>
                    </span>
                </span>
                <span className="pf-row-detail-value pf-row-detail-value--with-badge">
                    {p.realReturnAvailable && p.realProfit != null && Number.isFinite(Number(p.realProfit))
                        ? fmtTry(Number(p.realProfit))
                        : cpiMissingLabel(t)}
                    <RealReturnStatusBadge status={p.realReturnStatus} t={t} />
                </span>
            </div>
            <div>
                <span className="pf-row-detail-label">
                    <PfHelpTerm term="enflasyon-etkisi">
                        {t('portfolio.detailInflation', 'Enflasyon etkisi')}
                    </PfHelpTerm>
                </span>
                <span className="pf-row-detail-value">
                    {p.realReturnAvailable
                        ? formatPct(p.inflationReturnPct, locale)
                        : cpiMissingLabel(t)}
                </span>
            </div>
            <div>
                <span className="pf-row-detail-label">{t('portfolio.detailNominalReturn', 'Nominal getiri')}</span>
                <span className="pf-row-detail-value">{formatPct(periodPct != null ? Number(periodPct) : null, locale)}</span>
            </div>
            <div>
                <span className="pf-row-detail-label">
                    <span
                        title={t(
                            'portfolio.realReturnTooltip',
                            'Reel getiri, alış maliyetinin Türkiye TÜFE endeksiyle satış tarihine veya açık pozisyonlarda son açıklanan TÜFE tarihine taşınmasıyla hesaplanır.'
                        )}
                    >
                        <PfHelpTerm term="reel-kz">{t('portfolio.detailRealReturn', 'Reel getiri')}</PfHelpTerm>
                    </span>
                </span>
                <span className="pf-row-detail-value pf-row-detail-value--with-badge">
                    {p.realReturnAvailable ? formatPct(p.realReturnPct, locale) : cpiMissingLabel(t)}
                    <RealReturnStatusBadge status={p.realReturnStatus} t={t} />
                </span>
            </div>
            <div>
                <span className="pf-row-detail-label">{t('portfolio.detailPeriodReturn', 'Hesap dönemi getirisi')}</span>
                <span className="pf-row-detail-value">
                    {formatPct(periodPct != null ? Number(periodPct) : null, locale)}
                    {periodRange ? (
                        <span className="pf-row-detail-period" title={periodRange}>
                            {periodRange}
                        </span>
                    ) : null}
                </span>
            </div>
            {!open ? (
                <div>
                    <span className="pf-row-detail-label">
                        <PfHelpTerm term="kacirilan-firsat">
                            {t('portfolio.detailPostSellMiss', 'Satış sonrası fırsat farkı')}
                        </PfHelpTerm>
                    </span>
                    <span className="pf-row-detail-value">
                        {p.missedProfit != null && Number.isFinite(Number(p.missedProfit))
                            ? fmtTry(Number(p.missedProfit))
                            : '—'}
                    </span>
                </div>
            ) : null}
        </div>
    );
}
