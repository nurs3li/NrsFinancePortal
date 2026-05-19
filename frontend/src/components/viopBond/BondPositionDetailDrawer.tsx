import { useLanguage } from '../../i18n/LanguageContext';
import type { ManualBondPosition } from '../../types/bondPosition';
import { fmtDate, fmtMoney, fmtPct } from './formatViopBond';

type Props = {
    position: ManualBondPosition | null;
    tokens: { border: string; bgCard: string; textMuted: string };
};

export function BondPositionDetailDrawer({ position, tokens }: Props) {
    const { t, lang } = useLanguage();
    const locale = lang === 'en' ? 'en-US' : 'tr-TR';

    if (!position) {
        return (
            <div className="vb-drawer pf-card-premium" style={{ borderColor: tokens.border, background: tokens.bgCard }}>
                <p style={{ color: tokens.textMuted, margin: 0 }}>{t('viopBond.selectRow', 'Detay için tablodan bir pozisyon seçin.')}</p>
            </div>
        );
    }

    const rows: { label: string; value: string }[] = [
        { label: t('viopBond.colInstrument', 'Enstrüman'), value: position.symbol },
        { label: t('viopBond.colBondType', 'Tür'), value: position.bondType },
        { label: t('viopBond.colCurrency', 'Döviz'), value: position.currency },
        { label: t('viopBond.colNominal', 'Nominal'), value: fmtMoney(position.nominalValue, locale) },
        { label: t('viopBond.colBuyPrice', 'Alış Fiyatı'), value: fmtMoney(position.buyPrice, locale) },
        { label: t('viopBond.colCurrentPrice', 'Güncel Fiyat'), value: fmtMoney(position.currentPrice, locale) },
        { label: t('viopBond.colBuyValue', 'Alış Değeri'), value: fmtMoney(position.buyValue, locale) },
        { label: t('viopBond.colValue', 'Güncel Değer'), value: fmtMoney(position.currentValue, locale) },
        { label: t('viopBond.colPnl', 'K/Z'), value: fmtMoney(position.pnl, locale) },
        { label: t('viopBond.colReturn', 'Getiri'), value: fmtPct(position.returnPct, locale) },
        { label: t('viopBond.colMaturity', 'Vade'), value: fmtDate(position.maturityDate, locale) },
        {
            label: t('viopBond.daysToMaturity', 'Kalan gün'),
            value: position.daysToMaturity != null ? String(position.daysToMaturity) : '—',
        },
        { label: t('viopBond.colCouponRate', 'Kupon'), value: position.couponRate != null ? `${position.couponRate}%` : '—' },
        { label: t('viopBond.colStatus', 'Durum'), value: position.status },
    ];

    return (
        <div className="vb-drawer pf-card-premium" style={{ borderColor: tokens.border, background: tokens.bgCard }}>
            <h3>{position.displayName?.trim() || position.symbol}</h3>
            {rows.map((r) => (
                <div key={r.label} className="vb-detail-row">
                    <span style={{ color: tokens.textMuted }}>{r.label}</span>
                    <span>{r.value}</span>
                </div>
            ))}
            <p className="vb-risk-note" style={{ marginTop: '0.75rem' }}>
                {t(
                    'viopBond.bondNote',
                    'Tahvil ve eurobond değerlemesi nominal değer ve fiyat/100 mantığıyla hesaplanır.',
                )}
            </p>
        </div>
    );
}
