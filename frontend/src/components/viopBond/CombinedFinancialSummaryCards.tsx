import { useEffect, useId, useRef, useState, type CSSProperties } from 'react';
import { Info } from 'lucide-react';
import { useLanguage } from '../../i18n/LanguageContext';
import type { ViopBondCombinedSummary } from '../../types/bondPosition';
import { fmtMoney } from './formatViopBond';
import { pctClass } from './vbTabShared';

type Props = {
    summary: ViopBondCombinedSummary | undefined;
    loading?: boolean;
    tokens: { border: string; bgCard: string; textMuted: string };
    totalPnl?: number | null;
    riskStatus?: string;
    riskReasons?: string[];
};

function RiskReasonsPopover({
    reasons,
    tokens,
    cardStyle,
}: {
    reasons: string[];
    tokens: { border: string; bgCard: string; textMuted: string };
    cardStyle: CSSProperties;
}) {
    const { t } = useLanguage();
    const [open, setOpen] = useState(false);
    const wrapRef = useRef<HTMLDivElement>(null);
    const panelId = useId();

    useEffect(() => {
        if (!open) return;
        const onDoc = (e: MouseEvent) => {
            if (wrapRef.current && !wrapRef.current.contains(e.target as Node)) {
                setOpen(false);
            }
        };
        const onKey = (e: KeyboardEvent) => {
            if (e.key === 'Escape') setOpen(false);
        };
        document.addEventListener('mousedown', onDoc);
        document.addEventListener('keydown', onKey);
        return () => {
            document.removeEventListener('mousedown', onDoc);
            document.removeEventListener('keydown', onKey);
        };
    }, [open]);

    if (reasons.length === 0) return null;

    return (
        <div
            ref={wrapRef}
            className={`vb-kpi-risk-wrap${open ? ' is-open' : ''}`}
            onMouseEnter={() => setOpen(true)}
            onMouseLeave={() => setOpen(false)}
        >
            <button
                type="button"
                className="vb-kpi-risk-trigger"
                style={{ color: tokens.textMuted }}
                aria-expanded={open}
                aria-controls={panelId}
                onClick={() => setOpen((v) => !v)}
            >
                <Info size={13} aria-hidden />
                <span>{t('viopBond.riskWhy', 'Neden?')}</span>
            </button>
            <div
                id={panelId}
                className="vb-kpi-reasons-popover"
                style={cardStyle}
                role="tooltip"
            >
                <ul className="vb-kpi-reasons vb-kpi-reasons--popover">
                    {reasons.map((r) => (
                        <li key={r}>{r}</li>
                    ))}
                </ul>
            </div>
        </div>
    );
}

export function CombinedFinancialSummaryCards({
    summary,
    loading,
    tokens,
    totalPnl,
    riskStatus,
    riskReasons = [],
}: Props) {
    const { t, lang } = useLanguage();
    const locale = lang === 'en' ? 'en-US' : 'tr-TR';
    const cardStyle: CSSProperties = {
        borderColor: tokens.border,
        background: tokens.bgCard,
    };

    const pnl = totalPnl ?? 0;
    const expiring = summary?.totalExpiringSoon ?? 0;

    const items = [
        {
            label: t('viopBond.kpiPortfolioEffect', 'Toplam Portföy Etkisi'),
            value: loading ? '…' : fmtMoney(summary?.totalFinancialEffect, locale),
            hint: t('viopBond.kpiPortfolioEffectHint', 'Tahvil güncel değeri + VİOP net etki'),
        },
        {
            label: t('viopBond.kpiBondValue', 'Tahvil Güncel Değeri'),
            value: loading ? '…' : fmtMoney(summary?.bondCurrentValue, locale),
            hint: t('viopBond.kpiBondValueHint', 'Açık tahvil pozisyonlarının piyasa değeri'),
        },
        {
            label: t('viopBond.kpiViopNet', 'VİOP Net Etki'),
            value: loading ? '…' : fmtMoney(summary?.viopNetEffect, locale),
            hint: t('viopBond.kpiViopNetHint', 'Teminat + açık pozisyon K/Z'),
        },
        {
            label: t('viopBond.kpiTotalPnl', 'Toplam K/Z'),
            value: loading ? '…' : fmtMoney(pnl, locale),
            hint: t('viopBond.kpiTotalPnlHint', 'Tahvil fiyat K/Z + VİOP açık K/Z'),
            pnlClass: pctClass(pnl),
        },
        {
            label: t('viopBond.kpiExpiring', 'Yaklaşan Vade'),
            value: loading ? '…' : expiring > 0 ? String(expiring) : t('viopBond.noExpiringShort', 'Yok'),
            hint:
                expiring > 0
                    ? t('viopBond.kpiExpiringHint', '14 gün (VİOP) + 30 gün (tahvil)')
                    : t('viopBond.noExpiringDetail', '30 gün içinde vadesi dolacak ürün yok'),
        },
        {
            label: t('viopBond.kpiRiskStatus', 'Risk Durumu'),
            value: loading ? '…' : riskStatus ?? '—',
            hint: t('viopBond.kpiRiskStatusHint', 'Maruziyet / finansal etki oranına göre'),
            isRisk: true,
        },
    ];

    return (
        <div className="vb-summary-grid">
            {items.map((item) => (
                <div
                    key={item.label}
                    className={`pf-card-premium vb-kpi-card${'isRisk' in item && item.isRisk ? ' vb-kpi-card--risk' : ''}`}
                    style={cardStyle}
                >
                    <div className="vb-kpi-label" style={{ color: tokens.textMuted }}>
                        {item.label}
                    </div>
                    <div className={`vb-kpi-value${'pnlClass' in item && item.pnlClass ? ` ${item.pnlClass}` : ''}`}>
                        {item.value}
                    </div>
                    <div className="vb-kpi-hint" style={{ color: tokens.textMuted }}>
                        {item.hint}
                    </div>
                    {'isRisk' in item && item.isRisk && !loading ? (
                        <RiskReasonsPopover reasons={riskReasons} tokens={tokens} cardStyle={cardStyle} />
                    ) : null}
                </div>
            ))}
        </div>
    );
}
