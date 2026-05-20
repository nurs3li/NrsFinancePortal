import type { PositionHistoricalPriceResolve } from '../../types/historicalPriceResolve';
import { fmtDate } from './formatViopBond';

type Props = {
    resolve: PositionHistoricalPriceResolve | null;
    resolving?: boolean;
    locale: string;
    t: (k: string, d: string) => string;
};

export function HistoricalPriceResolveBanner({ resolve, resolving, locale, t }: Props) {
    if (resolving) {
        return <p className="vb-field-hint">{t('viopBond.resolvingPrice', 'Tarihsel fiyat aranıyor…')}</p>;
    }
    if (!resolve) return null;

    if (resolve.matchType === 'NOT_FOUND') {
        return <p className="vb-warn">{resolve.message ?? t('viopBond.priceResolveNotFound', 'Bu tarih için fiyat bulunamadı, manuel giriş yapabilirsiniz.')}</p>;
    }

    return (
        <div className="vb-resolve-banner">
            <p className="vb-resolve-banner__title">
                {t('viopBond.priceResolveFilled', 'Tarihsel fiyatla otomatik dolduruldu.')}
            </p>
            {resolve.matchedDate ? (
                <p className="vb-resolve-banner__meta">
                    {t('viopBond.priceResolveMatched', 'Eşleşen tarih')}: {fmtDate(resolve.matchedDate, locale)}
                </p>
            ) : null}
            {resolve.source ? (
                <p className="vb-resolve-banner__meta">
                    {t('viopBond.priceResolveSource', 'Kaynak')}: {resolve.source}
                </p>
            ) : null}
            {resolve.matchType === 'PREVIOUS_CLOSE' || resolve.matchType === 'NEXT_AVAILABLE' ? (
                <p className="vb-resolve-banner__meta vb-warn-inline">
                    {resolve.message ??
                        t(
                            'viopBond.priceResolveApprox',
                            'Seçilen tarihte veri yok; en yakın işlem günü fiyatı kullanıldı.',
                        )}
                </p>
            ) : null}
        </div>
    );
}
