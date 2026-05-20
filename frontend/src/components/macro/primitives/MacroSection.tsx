import type { ReactNode } from 'react';
import type { MacroTermId } from '../../../content/macroEducationTerms';
import type { MacroTheme } from '../MacroTheme';
import { TermLabel } from '../education/TermLabel';

type Props = {
    id: string;
    title: string;
    summary?: string;
    termId?: MacroTermId;
    infoAriaLabel?: string;
    tokens: MacroTheme;
    children: ReactNode;
};

export function MacroSection({ id, title, summary, termId, infoAriaLabel, tokens, children }: Props) {
    return (
        <section id={id} className="macro-section" style={{ scrollMarginTop: 120 }}>
            <header className="macro-section__header">
                <TermLabel as="h2" className="macro-section__title" style={{ color: tokens.text }} termId={termId} infoAriaLabel={infoAriaLabel}>
                    {title}
                </TermLabel>
                {summary ? (
                    <p className="macro-section__summary" style={{ color: tokens.textMuted }}>
                        {summary}
                    </p>
                ) : null}
            </header>
            <div className="macro-section__body">{children}</div>
        </section>
    );
}
