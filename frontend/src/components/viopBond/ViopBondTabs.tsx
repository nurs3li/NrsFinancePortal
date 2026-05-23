import { useLanguage } from '../../i18n/LanguageContext';
import { TabHelpPopover } from './TabHelpPopover';

type TabId = 'viop' | 'bond';

type Props = {
    active: TabId;
    onChange: (tab: TabId) => void;
    viopLabel: string;
    bondLabel: string;
    tokens: { border: string; bgCard: string; textMuted: string };
};

export function ViopBondTabs({ active, onChange, viopLabel, bondLabel, tokens }: Props) {
    const { t } = useLanguage();

    const viopHelp = {
        title: t('viopBond.viopHowToRead', 'VİOP nasıl okunur?'),
        body: t(
            'viopBond.viopHowToReadBody',
            'VİOP kaldıraçlı bir piyasadır. Teminat, pozisyon açmak için ayrılan tutardır; maruziyet sözleşmenin kaldıraçlı büyüklüğünü gösterir (portföy değeri değildir). Açık K/Z, giriş ve güncel fiyat farkına göre hesaplanır. Net etki = teminat + açık K/Z.',
        ),
    };

    const bondHelp = {
        title: t('viopBond.bondHowToRead', 'Tahvil & bono nasıl okunur?'),
        body: t(
            'viopBond.bondHowToReadBody',
            'Kupon oranı ile piyasa getirisi (yield) aynı şey değildir. Güncel değer nominal × fiyat/100 ile hesaplanır. Fiyat K/Z, alış ile güncel fiyat farkından oluşur. Reel getiri, TÜFE verisi varsa nominal getiriden enflasyon etkisi arındırılarak gösterilir.',
        ),
    };

    return (
        <div className="vb-tab-bar-inner" role="tablist">
            <div className="pf-range-tabs vb-range-tabs--with-help">
                <div className="vb-tab-pair">
                    <button
                        type="button"
                        role="tab"
                        aria-selected={active === 'viop'}
                        className={active === 'viop' ? 'pf-range-tabs__btn--on' : ''}
                        onClick={() => onChange('viop')}
                    >
                        {viopLabel}
                    </button>
                    <TabHelpPopover title={viopHelp.title} body={viopHelp.body} tokens={tokens} />
                </div>
                <div className="vb-tab-pair">
                    <button
                        type="button"
                        role="tab"
                        aria-selected={active === 'bond'}
                        className={active === 'bond' ? 'pf-range-tabs__btn--on' : ''}
                        onClick={() => onChange('bond')}
                    >
                        {bondLabel}
                    </button>
                    <TabHelpPopover title={bondHelp.title} body={bondHelp.body} tokens={tokens} />
                </div>
            </div>
        </div>
    );
}
