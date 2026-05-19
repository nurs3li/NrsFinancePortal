import { useLanguage } from '../../i18n/LanguageContext';
import type { ManualViopPosition } from '../../types/viopPosition';
import { fmtDate, fmtMoney } from './formatViopBond';

type Props = {
    position: ManualViopPosition | null;
    tokens: { border: string; bgCard: string; textMuted: string };
};

export function ViopPositionDetailDrawer({ position, tokens }: Props) {
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
        { label: t('viopBond.colContract', 'Kontrat'), value: position.symbol },
        { label: t('viopBond.colUnderlying', 'Dayanak'), value: position.underlyingSymbol ?? '—' },
        { label: t('viopBond.colViopType', 'VİOP Türü'), value: position.viopCategory },
        { label: t('viopBond.colDirection', 'Yön'), value: position.direction },
        { label: t('viopBond.colEntryPrice', 'Giriş'), value: fmtMoney(position.entryPrice, locale) },
        { label: t('viopBond.colCurrentPrice', 'Güncel'), value: fmtMoney(position.currentPrice, locale) },
        { label: t('viopBond.colCount', 'Adet'), value: String(position.contractCount) },
        { label: t('viopBond.colMultiplier', 'Çarpan'), value: String(position.contractMultiplier) },
        { label: t('viopBond.colMargin', 'Teminat'), value: fmtMoney(position.initialMargin, locale) },
        { label: t('viopBond.colPnl', 'K/Z'), value: fmtMoney(position.unrealizedPnl, locale) },
        { label: t('viopBond.colExposure', 'Maruziyet'), value: fmtMoney(position.riskExposure, locale) },
        { label: t('viopBond.colExpiry', 'Vade'), value: fmtDate(position.expiryDate, locale) },
        {
            label: t('viopBond.daysToExpiry', 'Kalan gün'),
            value: position.daysToExpiry != null ? String(position.daysToExpiry) : '—',
        },
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
            {position.note ? (
                <p style={{ fontSize: '0.82rem', marginTop: '0.75rem' }}>{position.note}</p>
            ) : null}
            <p className="vb-risk-note">{t('viopBond.viopRiskNote', 'VİOP kaldıraçlı bir piyasadır. Bu bölüm portföy değeri değil, teminat, açık K/Z ve risk maruziyeti üzerinden değerlendirme yapar.')}</p>
        </div>
    );
}
