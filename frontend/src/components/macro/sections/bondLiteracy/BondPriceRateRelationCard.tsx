import { ArrowDown, ArrowUp } from 'lucide-react';
import type { MacroTheme } from '../../MacroTheme';
import { useLanguage } from '../../../../i18n/LanguageContext';
import { InfoButton } from '../../education/InfoButton';
import { useInfoTerm } from '../../education/InfoTermProvider';

type Props = {
    tokens: MacroTheme;
};

export function BondPriceRateRelationCard({ tokens }: Props) {
    const { t } = useLanguage();
    const { openTerm } = useInfoTerm();

    return (
        <article className="bond-literacy__panel-card" style={{ borderColor: tokens.border, background: tokens.bgCard }}>
            <div className="bond-literacy__panel-card-head">
                <h3 className="bond-literacy__panel-card-title" style={{ color: tokens.text }}>
                    {t('macro.bondLiteracy.price.title', 'Faiz-Fiyat İlişkisi')}
                </h3>
                <InfoButton
                    termId="bondPriceYield"
                    ariaLabel={t('macro.bondLiteracy.price.infoAria', 'Faiz-fiyat ilişkisi hakkında bilgi')}
                />
            </div>

            <div className="bond-literacy__rate-price-grid">
                <div className="bond-literacy__rate-price-row bond-literacy__rate-price-row--up">
                    <div className="bond-literacy__rate-price-side">
                        <ArrowUp size={26} aria-hidden />
                        <span>{t('macro.bondLiteracy.price.rateUp', 'Faiz ↑')}</span>
                    </div>
                    <span className="bond-literacy__rate-price-arrow" aria-hidden>
                        →
                    </span>
                    <div className="bond-literacy__rate-price-side bond-literacy__rate-price-side--down">
                        <ArrowDown size={26} aria-hidden />
                        <span>{t('macro.bondLiteracy.price.oldPriceDown', 'Eski tahvil fiyatı ↓')}</span>
                    </div>
                </div>
                <div className="bond-literacy__rate-price-row bond-literacy__rate-price-row--down">
                    <div className="bond-literacy__rate-price-side bond-literacy__rate-price-side--rate-down">
                        <ArrowDown size={26} aria-hidden />
                        <span>{t('macro.bondLiteracy.price.rateDown', 'Faiz ↓')}</span>
                    </div>
                    <span className="bond-literacy__rate-price-arrow" aria-hidden>
                        →
                    </span>
                    <div className="bond-literacy__rate-price-side bond-literacy__rate-price-side--price-up">
                        <ArrowUp size={26} aria-hidden />
                        <span>{t('macro.bondLiteracy.price.oldPriceUp', 'Eski tahvil fiyatı ↑')}</span>
                    </div>
                </div>
            </div>

            <div className="bond-literacy__scenario" style={{ borderColor: tokens.border, background: `${tokens.bg}` }}>
                <p className="bond-literacy__scenario-text" style={{ color: tokens.text }}>
                    {t(
                        'macro.bondLiteracy.price.note',
                        'Piyasada yeni tahviller daha yüksek getiri sunarsa, eski düşük getirili tahvillerin fiyatı düşebilir.',
                    )}
                </p>
                <button type="button" className="macro-link-btn" onClick={() => openTerm('bondPriceYield')}>
                    {t('macro.bondLiteracy.price.why', 'Neden böyle?')}
                </button>
            </div>
        </article>
    );
}
