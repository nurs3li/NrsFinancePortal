import { Info } from 'lucide-react';
import { useLanguage } from '../../i18n/LanguageContext';

type Props = {
    tokens: { textMuted: string };
};

export function BondRealReturnHeader({ tokens }: Props) {
    const { t } = useLanguage();
    const title = t('viopBond.colRealReturnPeriod', 'Dönemsel reel getiri');
    const tooltip = t(
        'viopBond.colRealReturnTooltip',
        'Reel getiri, nominal getirinin enflasyondan arındırılmış halidir. Tahvil pozisyonlarında bu hesaplama, pozisyonun alış tarihinden bugüne kadar gerçekleşen TÜFE değişimiyle yapılır. Yıllık TÜFE doğrudan çıkarılmaz.',
    );
    const detail = t(
        'viopBond.colRealReturnTooltipDetail',
        'Bu değer, pozisyonun alış tarihinden bugüne gerçekleşen TÜFE değişimine göre hesaplanan dönemsel reel getiridir. Son 12 aylık TÜFE doğrudan nominal getiriden çıkarılmaz.',
    );

    return (
        <span className="vb-th-with-info">
            {title}
            <button
                type="button"
                className="vb-th-info-btn"
                aria-label={title}
                title={`${tooltip} ${detail}`}
                style={{ color: tokens.textMuted }}
            >
                <Info size={14} />
            </button>
        </span>
    );
}

export function bondRealReturnMissingTitle(t: (k: string, d: string) => string): string {
    return t(
        'viopBond.colRealReturnMissing',
        'Bu pozisyon için dönemsel TÜFE verisi bulunamadığından reel getiri hesaplanamadı.',
    );
}
