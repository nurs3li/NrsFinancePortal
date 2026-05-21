import { ExternalLink } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import type { MacroTheme } from '../../MacroTheme';
import { useLanguage } from '../../../../i18n/LanguageContext';

/** Piyasalar sayfasında Tahvil sekmesini açmak için (Market.tsx ile paylaşılan anahtar). */
export const MARKET_PREF_CATEGORY_KEY = 'nrs.market.prefCategory';

type Props = {
    tokens: MacroTheme;
};

export function BondMarketRedirectCard({ tokens }: Props) {
    const navigate = useNavigate();
    const { t } = useLanguage();

    return (
        <article className="bond-literacy__cta-card" style={{ borderColor: tokens.border, background: tokens.bgCard }}>
            <div className="bond-literacy__cta-copy">
                <h3 className="bond-literacy__panel-card-title" style={{ color: tokens.text }}>
                    {t('macro.bondLiteracy.cta.title', 'Canlı tahvil fiyatları')}
                </h3>
                <p className="bond-literacy__cta-desc" style={{ color: tokens.textMuted }}>
                    {t(
                        'macro.bondLiteracy.cta.desc',
                        'Tekil tahvil fiyatları, ISIN bazlı piyasa değerleri ve performans grafikleri Piyasalar > Tahvil sekmesinde takip edilir.',
                    )}
                </p>
            </div>
            <button
                type="button"
                className="bond-literacy__cta-btn"
                onClick={() => {
                    try {
                        sessionStorage.setItem(MARKET_PREF_CATEGORY_KEY, 'BOND');
                    } catch {
                        /* ignore */
                    }
                    navigate('/market');
                }}
            >
                <span>{t('macro.bondLiteracy.cta.button', 'Canlı Tahvil Piyasasını Aç')}</span>
                <ExternalLink size={16} aria-hidden />
            </button>
        </article>
    );
}
