import type { CSSProperties } from 'react';
import { useLanguage } from '../../i18n/LanguageContext';
import type { ViopBondCombinedSummary } from '../../types/bondPosition';
import { fmtMoney } from './formatViopBond';

type Props = {
    summary: ViopBondCombinedSummary | undefined;
    loading?: boolean;
    tokens: { border: string; bgCard: string; textMuted: string };
    openProductCount?: number;
    riskStatus?: string;
};

export function CombinedFinancialSummaryCards({ summary, loading, tokens, openProductCount, riskStatus }: Props) {
    const { t, lang } = useLanguage();
    const locale = lang === 'en' ? 'en-US' : 'tr-TR';
    const cardStyle: CSSProperties = {
        borderColor: tokens.border,
        background: tokens.bgCard,
    };

    const items = [
        {
            label: t('viopBond.kpiTotalEffect', 'Toplam Finansal Etki'),
            value: loading ? '…' : fmtMoney(summary?.totalFinancialEffect, locale),
            hint: t('viopBond.kpiTotalEffectHint', 'Tahvil değeri + VİOP teminatı + açık K/Z'),
        },
        {
            label: t('viopBond.kpiTotalRisk', 'Toplam Risk Maruziyeti'),
            value: loading ? '…' : fmtMoney(summary?.totalRiskExposure, locale),
            hint: t('viopBond.kpiTotalRiskHint', 'VİOP risk maruziyeti toplam finansal varlığa dahil edilmez'),
        },
        {
            label: t('viopBond.kpiExpiring', 'Yaklaşan Vade'),
            value: loading ? '…' : String(summary?.totalExpiringSoon ?? 0),
            hint: t('viopBond.kpiExpiringHint', '14 gün (VİOP) + 30 gün (tahvil)'),
        },
        {
            label: t('viopBond.kpiAlerts', 'Aktif Alarm'),
            value: loading ? '…' : summary?.activeAlertCount != null ? String(summary.activeAlertCount) : '—',
            hint: t('viopBond.kpiAlertsHint', 'VİOP ve tahvil fiyat alarmları'),
        },
        {
            label: t('viopBond.kpiOpenProducts', 'Açık Ürün Sayısı'),
            value: loading ? '…' : String(openProductCount ?? 0),
            hint: t('viopBond.kpiOpenProductsHint', 'Açık VİOP + tahvil pozisyonları'),
        },
        {
            label: t('viopBond.kpiRiskStatus', 'Risk Durumu'),
            value: loading ? '…' : riskStatus ?? '—',
            hint: t('viopBond.kpiRiskStatusHint', 'Maruziyet / finansal etki oranına göre'),
        },
    ];

    return (
        <div className="vb-summary-grid">
            {items.map((item) => (
                <div key={item.label} className="pf-card-premium vb-kpi-card" style={cardStyle}>
                    <div className="vb-kpi-label" style={{ color: tokens.textMuted }}>
                        {item.label}
                    </div>
                    <div className="vb-kpi-value">{item.value}</div>
                    <div className="vb-kpi-hint" style={{ color: tokens.textMuted }}>
                        {item.hint}
                    </div>
                </div>
            ))}
        </div>
    );
}
