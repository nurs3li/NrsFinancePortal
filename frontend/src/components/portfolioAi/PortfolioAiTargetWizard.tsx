import { useMemo, useState } from 'react';
import { useLanguage } from '../../i18n/LanguageContext';
import type {
    CreateTargetPortfolioPlanPayload,
    TargetAssetAction,
    TargetPlanBasis,
    TargetPlanLine,
    TargetPlanPurpose,
    AiRiskProfile,
} from '../../types/portfolioAi';
import type { ManualPortfolioView } from '../../types/manualPortfolio';
import { fmtPct } from './portfolioAiFormat';

type Props = {
    open: boolean;
    onClose: () => void;
    openPositions: ManualPortfolioView[];
    locale: string;
    onCreate: (payload: CreateTargetPortfolioPlanPayload) => Promise<void>;
};

const ACTIONS: TargetAssetAction[] = ['KEEP', 'ADD', 'REDUCE', 'REMOVE', 'WATCH'];

export function PortfolioAiTargetWizard({ open, onClose, openPositions, locale, onCreate }: Props) {
    const { t } = useLanguage();
    const [step, setStep] = useState(1);
    const [name, setName] = useState('');
    const [targetDate, setTargetDate] = useState('');
    const [minHold, setMinHold] = useState('3 ay');
    const [risk, setRisk] = useState<AiRiskProfile>('BALANCED');
    const [purpose, setPurpose] = useState<TargetPlanPurpose>('BALANCED_GROWTH');
    const [basis, setBasis] = useState<TargetPlanBasis>('CURRENT_PORTFOLIO');
    const [lines, setLines] = useState<TargetPlanLine[]>([]);
    const [saving, setSaving] = useState(false);

    const openTotal = useMemo(
        () => openPositions.reduce((s, p) => s + Math.max(0, Number(p.currentValue ?? 0)), 0),
        [openPositions],
    );

    const initFromPortfolio = () => {
        setLines(
            openPositions.map((p) => {
                const v = Math.max(0, Number(p.currentValue ?? 0));
                const w = openTotal > 0 ? (100 * v) / openTotal : 0;
                return {
                    id: `tmp-${p.id}`,
                    symbol: p.symbol,
                    assetClass: p.type,
                    currentWeightPct: Math.round(w * 10) / 10,
                    targetWeightPct: Math.round(w * 10) / 10,
                    minHoldingPeriod: minHold,
                    currentPrice: p.currentPrice ?? null,
                    targetPrice: p.currentPrice ?? null,
                    userNote: '',
                    actionType: 'KEEP',
                };
            }),
        );
    };

    if (!open) return null;

    const totalTarget = lines.reduce((s, l) => s + l.targetWeightPct, 0);

    const submit = async () => {
        setSaving(true);
        try {
            await onCreate({
                name: name.trim() || t('portfolioAi.defaultPlanName', 'Hedef plan'),
                targetDate,
                minHoldingPeriod: minHold,
                riskProfile: risk,
                purpose,
                basis,
                lines: lines.map(({ id: _id, ...rest }) => rest),
            });
            onClose();
            setStep(1);
        } finally {
            setSaving(false);
        }
    };

    return (
        <div className="pf-ai-wizard-overlay" role="dialog" aria-modal="true">
            <div className="pf-ai-wizard">
                <header className="pf-ai-wizard__head">
                    <h3>{t('portfolioAi.wizardTitle', 'Hedef portföy planlayıcı')}</h3>
                    <button type="button" className="pf-ai-wizard__close" onClick={onClose}>
                        ×
                    </button>
                </header>
                <div className="pf-ai-wizard__steps">
                    {[1, 2, 3, 4, 5].map((n) => (
                        <span key={n} className={step === n ? 'pf-ai-step pf-ai-step--active' : 'pf-ai-step'}>
                            {n}
                        </span>
                    ))}
                </div>

                {step === 1 ? (
                    <div className="pf-ai-wizard__body">
                        <label>
                            {t('portfolioAi.planName', 'Plan adı')}
                            <input value={name} onChange={(e) => setName(e.target.value)} />
                        </label>
                        <label>
                            {t('portfolioAi.targetDate', 'Hedef tarih')}
                            <input type="date" value={targetDate} onChange={(e) => setTargetDate(e.target.value)} />
                        </label>
                        <label>
                            {t('portfolioAi.minHolding', 'Minimum tutma süresi')}
                            <input value={minHold} onChange={(e) => setMinHold(e.target.value)} />
                        </label>
                        <label>
                            {t('portfolioAi.riskProfile', 'Risk profili')}
                            <select value={risk} onChange={(e) => setRisk(e.target.value as AiRiskProfile)}>
                                <option value="LOW">{t('portfolioAi.riskLow', 'Düşük risk')}</option>
                                <option value="BALANCED">{t('portfolioAi.riskBalanced', 'Dengeli')}</option>
                                <option value="AGGRESSIVE">{t('portfolioAi.riskAggressive', 'Agresif')}</option>
                            </select>
                        </label>
                        <label>
                            {t('portfolioAi.planPurpose', 'Plan amacı')}
                            <select value={purpose} onChange={(e) => setPurpose(e.target.value as TargetPlanPurpose)}>
                                <option value="CAPITAL_PRESERVATION">
                                    {t('portfolioAi.purposePreserve', 'Sermayeyi koruma')}
                                </option>
                                <option value="BALANCED_GROWTH">
                                    {t('portfolioAi.purposeBalanced', 'Dengeli büyüme')}
                                </option>
                                <option value="HIGH_RETURN">
                                    {t('portfolioAi.purposeHigh', 'Yüksek getiri arayışı')}
                                </option>
                                <option value="INFLATION_BEAT">
                                    {t('portfolioAi.purposeInflation', 'Enflasyon üstü getiri')}
                                </option>
                            </select>
                        </label>
                    </div>
                ) : null}

                {step === 2 ? (
                    <div className="pf-ai-wizard__body">
                        <label className="pf-ai-radio">
                            <input
                                type="radio"
                                checked={basis === 'CURRENT_PORTFOLIO'}
                                onChange={() => {
                                    setBasis('CURRENT_PORTFOLIO');
                                    initFromPortfolio();
                                }}
                            />
                            {t('portfolioAi.basisCurrent', 'Mevcut portföyümü baz al')}
                        </label>
                        <label className="pf-ai-radio">
                            <input
                                type="radio"
                                checked={basis === 'FROM_SCRATCH'}
                                onChange={() => {
                                    setBasis('FROM_SCRATCH');
                                    setLines([]);
                                }}
                            />
                            {t('portfolioAi.basisScratch', 'Sıfırdan hedef portföy oluştur')}
                        </label>
                    </div>
                ) : null}

                {step === 3 ? (
                    <div className="pf-ai-wizard__body pf-ai-wizard__scroll">
                        <table className="pf-ai-table">
                            <thead>
                                <tr>
                                    <th>{t('portfolioAi.colAsset', 'Varlık')}</th>
                                    <th>{t('portfolioAi.colType', 'Tür')}</th>
                                    <th>{t('portfolioAi.colCurrentWt', 'Mevcut %')}</th>
                                    <th>{t('portfolioAi.colTargetWt', 'Hedef %')}</th>
                                    <th>{t('portfolioAi.colAction', 'İşlem')}</th>
                                </tr>
                            </thead>
                            <tbody>
                                {lines.map((l, idx) => (
                                    <tr key={l.id}>
                                        <td>{l.symbol}</td>
                                        <td>{l.assetClass}</td>
                                        <td>{fmtPct(l.currentWeightPct, locale)}</td>
                                        <td>
                                            <input
                                                type="number"
                                                className="pf-ai-input-sm"
                                                value={l.targetWeightPct}
                                                onChange={(e) => {
                                                    const next = [...lines];
                                                    next[idx] = {
                                                        ...l,
                                                        targetWeightPct: Number(e.target.value),
                                                    };
                                                    setLines(next);
                                                }}
                                            />
                                        </td>
                                        <td>
                                            <select
                                                value={l.actionType}
                                                onChange={(e) => {
                                                    const next = [...lines];
                                                    next[idx] = {
                                                        ...l,
                                                        actionType: e.target.value as TargetAssetAction,
                                                    };
                                                    setLines(next);
                                                }}
                                            >
                                                {ACTIONS.map((a) => (
                                                    <option key={a} value={a}>
                                                        {a}
                                                    </option>
                                                ))}
                                            </select>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                        {lines.length === 0 ? (
                            <p className="pf-ai-muted">{t('portfolioAi.wizardNoLines', 'Önce başlangıç seçimi yapın.')}</p>
                        ) : null}
                    </div>
                ) : null}

                {step === 4 ? (
                    <div className="pf-ai-wizard__body">
                        <p>
                            {t('portfolioAi.previewTotal', 'Toplam hedef ağırlık')}:{' '}
                            <strong className={Math.abs(totalTarget - 100) > 1 ? 'pf-ai-warn' : ''}>
                                {fmtPct(totalTarget, locale)}
                            </strong>
                        </p>
                        <ul className="pf-ai-list">
                            {lines.map((l) => (
                                <li key={l.id}>
                                    {l.symbol}: {fmtPct(l.currentWeightPct, locale)} → {fmtPct(l.targetWeightPct, locale)}{' '}
                                    ({l.actionType})
                                </li>
                            ))}
                        </ul>
                    </div>
                ) : null}

                {step === 5 ? (
                    <div className="pf-ai-wizard__body">
                        <p>{t('portfolioAi.wizardStep5Hint', 'Planı kaydedin; ardından listeden AI analizi başlatabilirsiniz.')}</p>
                    </div>
                ) : null}

                <footer className="pf-ai-wizard__foot">
                    {step > 1 ? (
                        <button type="button" className="pf-dash-btn" onClick={() => setStep((s) => s - 1)}>
                            {t('portfolioAi.wizardBack', 'Geri')}
                        </button>
                    ) : (
                        <span />
                    )}
                    {step < 5 ? (
                        <button
                            type="button"
                            className="pf-dash-btn pf-dash-btn--primary"
                            onClick={() => {
                                if (step === 2 && basis === 'CURRENT_PORTFOLIO' && lines.length === 0) {
                                    initFromPortfolio();
                                }
                                setStep((s) => s + 1);
                            }}
                        >
                            {t('portfolioAi.next', 'İleri')}
                        </button>
                    ) : (
                        <button
                            type="button"
                            className="pf-dash-btn pf-dash-btn--primary"
                            disabled={saving || lines.length === 0}
                            onClick={() => void submit()}
                        >
                            {t('portfolioAi.savePlan', 'Planı kaydet')}
                        </button>
                    )}
                </footer>
            </div>
        </div>
    );
}
