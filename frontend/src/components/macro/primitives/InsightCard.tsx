import type { ReactNode } from 'react';
import type { MacroTheme } from '../MacroTheme';

type Props = {
    title: string;
    tokens: MacroTheme;
    children: ReactNode;
    accent?: 'cyan' | 'violet' | 'amber' | 'green';
};

export function InsightCard({ title, tokens, children, accent = 'cyan' }: Props) {
    return (
        <aside className={`macro-insight macro-insight--${accent}`} style={{ borderColor: tokens.border, background: tokens.bgCard }}>
            <h4 className="macro-insight__title" style={{ color: tokens.text }}>
                {title}
            </h4>
            <div className="macro-insight__body" style={{ color: tokens.textMuted }}>
                {children}
            </div>
        </aside>
    );
}
