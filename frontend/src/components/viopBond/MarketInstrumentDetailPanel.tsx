import { Bell, Plus } from 'lucide-react';
import { useLanguage } from '../../i18n/LanguageContext';
import type { TerminalListInstrumentVm } from '../../utils/marketTerminalListVm';
import { viopCategoryFor } from '../../constants/ViopWhitelist';
import { viopCategoryLabel } from './viopBondMarket';
import { fmtMoney, fmtPct } from './formatViopBond';

type Props = {
    instrument: TerminalListInstrumentVm;
    mode: 'viop' | 'bond';
    tokens: { border: string; bgCard: string; textMuted: string };
    onAddPosition: () => void;
    onSetAlert: () => void;
};

export function MarketInstrumentDetailPanel({ instrument, mode, tokens, onAddPosition, onSetAlert }: Props) {
    const { t, lang } = useLanguage();
    const locale = lang === 'en' ? 'en-US' : 'tr-TR';
    const cat = mode === 'viop' ? viopCategoryFor(instrument.symbol) : null;

    const rows: { label: string; value: string; title?: string }[] =
        mode === 'viop'
            ? [
                  { label: t('viopBond.colContract', 'Kontrat'), value: instrument.symbol },
                  { label: t('viopBond.colDisplayName', 'Görünen ad'), value: instrument.displayName },
                  { label: t('viopBond.colViopType', 'Tür'), value: viopCategoryLabel(cat, t) },
                  { label: t('viopBond.colCurrentPrice', 'Son fiyat'), value: fmtMoney(instrument.price, locale) },
                  { label: t('viopBond.colDay', 'Gün'), value: fmtPct(instrument.pctDay, locale) },
                  { label: t('viopBond.colWeek', 'Hafta'), value: fmtPct(instrument.pctWeek, locale) },
                  { label: t('viopBond.colMonth', 'Ay'), value: fmtPct(instrument.pctMonth, locale) },
                  { label: t('viopBond.colYear', 'Yıl'), value: fmtPct(instrument.pctYear, locale) },
                  {
                      label: t('viopBond.colExpiry', 'Vade'),
                      value: instrument.contractMonth ?? instrument.maturityDate ?? '—',
                  },
                  {
                      label: t('viopBond.colMargin', 'Teminat'),
                      value: fmtMoney(instrument.marginRequirement, locale),
                  },
                  { label: t('viopBond.colSource', 'Kaynak'), value: instrument.source ?? '—' },
              ]
            : [
                  { label: t('viopBond.colInstrument', 'Enstrüman'), value: instrument.symbol },
                  { label: t('viopBond.colDisplayName', 'Görünen ad'), value: instrument.displayName },
                  { label: t('viopBond.colCurrency', 'Döviz'), value: instrument.currency ?? '—' },
                  { label: t('viopBond.colCurrentPrice', 'Güncel fiyat'), value: fmtMoney(instrument.price, locale) },
                  { label: t('viopBond.colMaturity', 'Vade'), value: instrument.maturityDate ?? '—' },
                  {
                      label: t('viopBond.colCouponRate', 'Kupon'),
                      value: instrument.couponRate != null ? fmtPct(instrument.couponRate, locale) : '—',
                  },
                  {
                      label: t('market.bondCouponFreq', 'Kupon Ödeme Sıklığı'),
                      value: instrument.couponFrequencyLabel?.trim() ? instrument.couponFrequencyLabel : '—',
                      title: t(
                          'market.bondCouponFreqTip',
                          'Kupon ödeme sıklığı, tahvilin yılda kaç kez faiz ödemesi yaptığını gösterir. Bu değer EVDS seri kodundan çıkarılmış olabilir.',
                      ),
                  },
                  { label: t('viopBond.colDay', 'Gün'), value: fmtPct(instrument.pctDay, locale) },
              ];

    return (
        <aside className="vb-drawer pf-card-premium" style={{ borderColor: tokens.border, background: tokens.bgCard }}>
            <h3>{t('viopBond.marketDetailTitle', 'Enstrüman detayı')}</h3>
            {rows.map((r) => (
                <div key={r.label} className="vb-detail-row">
                    <span style={{ color: tokens.textMuted }} title={r.title}>
                        {r.label}
                    </span>
                    <strong>{r.value}</strong>
                </div>
            ))}
            <div className="vb-drawer-actions">
                <button type="button" className="pf-dash-btn" onClick={onSetAlert}>
                    <Bell size={14} />
                    {t('viopBond.setAlert', 'Alarm Kur')}
                </button>
                <button type="button" className="pf-dash-btn pf-dash-btn--primary" onClick={onAddPosition}>
                    <Plus size={14} />
                    {t('viopBond.addToPosition', 'Pozisyona Ekle')}
                </button>
            </div>
        </aside>
    );
}
