import type { ReactNode } from 'react';
import type { MacroTermId } from '../../../content/macroEducationTerms';
import type { MacroTheme } from '../MacroTheme';
import { InfoButton } from '../education/InfoButton';

export type KpiStatus = 'positive' | 'negative' | 'neutral' | 'warning';

type Props = {
    title: ReactNode;
    value: string;
    meta?: string;
    status?: KpiStatus;
    termId?: MacroTermId;
    infoAriaLabel?: string;
    tokens: MacroTheme;
    loading?: boolean;
};

const STATUS_CLASS: Record<KpiStatus, string> = {
    positive: 'macro-kpi__badge--positive',
    negative: 'macro-kpi__badge--negative',
    neutral: 'macro-kpi__badge--neutral',
    warning: 'macro-kpi__badge--warning',
};

export function KpiCard({ title, value, meta, status, termId, infoAriaLabel, tokens, loading }: Props) {
    return (
        <article className="macro-kpi" style={{ borderColor: tokens.border, background: tokens.bgCard }}>
            <header className="macro-kpi__head">
                <div className="macro-kpi__title" style={{ color: tokens.textMuted }}>
                    {title}
                </div>
                {termId && infoAriaLabel ? <InfoButton termId={termId} ariaLabel={infoAriaLabel} /> : null}
            </header>
            {loading ? (
                <div className="macro-kpi__skeleton" />
            ) : (
                <>
                    <p className="macro-kpi__value" style={{ color: tokens.text }}>
                        {value}
                        {status ? <span className={`macro-kpi__badge ${STATUS_CLASS[status]}`} /> : null}
                    </p>
                    {meta ? (
                        <p className="macro-kpi__meta" style={{ color: tokens.textMuted }}>
                            {meta}
                        </p>
                    ) : null}
                </>
            )}
        </article>
    );
}
