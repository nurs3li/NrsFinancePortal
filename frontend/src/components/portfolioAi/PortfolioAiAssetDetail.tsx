import type { PortfolioAiAssetRow } from './portfolioAiAssetRows';
import type { TranslateFn } from './portfolioAiUiTypes';

type Props = {
    t: TranslateFn;
    row: PortfolioAiAssetRow | null;
    locale: string;
};

function assetNarrative(row: PortfolioAiAssetRow) {
    const c = row.comment;
    const role = c.role?.trim() || '';
    const impact =
        c.impactOnPortfolio?.trim() ||
        c.detailComment?.trim() ||
        c.shortComment?.trim() ||
        '';
    const positive =
        c.positiveView?.trim() ||
        c.positiveFactors?.find((p) => p.trim().length > 0)?.trim() ||
        c.allocationEffect?.trim() ||
        '';
    const risk =
        c.riskView?.trim() ||
        c.riskFactors?.find((r) => r.trim().length > 0)?.trim() ||
        c.riskComment?.trim() ||
        '';
    const watch = (c.whatToWatch ?? []).filter((w) => w.trim().length > 0);

    return { role, impact, positive, risk, watch };
}

export function PortfolioAiAssetDetail({ t, row }: Props) {
    const emptyTitle = t('portfolioAi.assetDetailTitle', 'Varlık Detaylı AI Yorumu');

    if (!row) {
        return (
            <section className="pf-ai-dash-card pf-ai-dash-card--fill pf-ai-asset-detail">
                <h2 className="pf-ai-dash-card__title">{emptyTitle}</h2>
                <p className="pf-ai-muted">
                    {t('portfolioAi.assetDetailPick', 'Detay için tablodan bir varlık seçin.')}
                </p>
            </section>
        );
    }

    const { role, impact, positive, risk, watch } = assetNarrative(row);
    const title = `${row.symbol} — ${emptyTitle}`;

    return (
        <section className="pf-ai-dash-card pf-ai-dash-card--fill pf-ai-asset-detail">
            <h2 className="pf-ai-dash-card__title pf-ai-asset-detail__title">{title}</h2>

            <div className="pf-ai-last-result-body">
                {role ? (
                    <section className="pf-ai-narrative-section">
                        <h3 className="pf-ai-narrative-section__title">
                            {t('portfolioAi.assetRoleLabel', 'Portföydeki rolü')}
                        </h3>
                        <p className="pf-ai-narrative-section__text">{role}</p>
                    </section>
                ) : null}

                {impact ? (
                    <section className="pf-ai-narrative-section">
                        <h3 className="pf-ai-narrative-section__title">
                            {t('portfolioAi.impactOnPortfolio', 'Portföye etkisi')}
                        </h3>
                        <p className="pf-ai-narrative-section__text">{impact}</p>
                    </section>
                ) : null}

                {positive ? (
                    <section className="pf-ai-narrative-section">
                        <h3 className="pf-ai-narrative-section__title">
                            {t('portfolioAi.positiveView', 'Olumlu taraf')}
                        </h3>
                        <p className="pf-ai-narrative-section__text">{positive}</p>
                    </section>
                ) : null}

                {risk ? (
                    <section className="pf-ai-narrative-section">
                        <h3 className="pf-ai-narrative-section__title">
                            {t('portfolioAi.riskView', 'Risk tarafı')}
                        </h3>
                        <p className="pf-ai-narrative-section__text">{risk}</p>
                    </section>
                ) : null}

                {watch.length > 0 ? (
                    <section className="pf-ai-narrative-section">
                        <h3 className="pf-ai-narrative-section__title">
                            {t('portfolioAi.watchPoints', 'İzlenecek noktalar')}
                        </h3>
                        <ul className="pf-ai-narrative-section__list">
                            {watch.map((w, i) => (
                                <li key={i}>{w}</li>
                            ))}
                        </ul>
                    </section>
                ) : null}
            </div>

            <p className="pf-ai-disclaimer-inline pf-ai-disclaimer-inline--sm">
                {t('portfolioAi.disclaimer', 'Bu analiz bilgilendirme amaçlıdır; yatırım tavsiyesi değildir.')}
            </p>
        </section>
    );
}
