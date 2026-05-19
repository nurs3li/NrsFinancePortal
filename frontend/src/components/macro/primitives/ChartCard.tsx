import type { ReactNode } from 'react';
import type { MacroTermId } from '../../../content/macroEducationTerms';
import type { MacroTheme } from '../MacroTheme';
import { TermLabel } from '../education/TermLabel';
import { EmptyStateCard } from './EmptyStateCard';

type Props = {
    title: string;
    termId?: MacroTermId;
    infoAriaLabel?: string;
    height?: number;
    empty?: boolean;
    emptyTitle?: string;
    emptyHint?: string;
    tokens: MacroTheme;
    children: ReactNode;
    footer?: ReactNode;
};

export function ChartCard({
    title,
    termId,
    infoAriaLabel,
    height = 240,
    empty,
    emptyTitle = 'Bu veri şu anda kullanılamıyor.',
    emptyHint,
    tokens,
    children,
    footer,
}: Props) {
    return (
        <article className="macro-chart-card" style={{ borderColor: tokens.border, background: tokens.bgCard }}>
            <header className="macro-chart-card__head">
                <TermLabel as="h3" className="macro-chart-card__title" style={{ color: tokens.text }} termId={termId} infoAriaLabel={infoAriaLabel}>
                    {title}
                </TermLabel>
            </header>
            {empty ? (
                <EmptyStateCard title={emptyTitle} hint={emptyHint} tokens={tokens} compact />
            ) : (
                <div className="macro-chart-card__canvas" style={{ height }}>
                    {children}
                </div>
            )}
            {footer ? (
                <footer className="macro-chart-card__footer" style={{ color: tokens.textMuted }}>
                    {footer}
                </footer>
            ) : null}
        </article>
    );
}
