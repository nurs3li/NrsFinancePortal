import type { MacroTheme } from '../MacroTheme';

type Props = {
    title: string;
    hint?: string;
    tokens: MacroTheme;
    compact?: boolean;
    onRetry?: () => void;
};

export function EmptyStateCard({ title, hint, tokens, compact, onRetry }: Props) {
    return (
        <aside
            className={compact ? 'macro-empty macro-empty--compact' : 'macro-empty'}
            style={{ borderColor: tokens.border, background: tokens.bgCard }}
        >
            <p className="macro-empty__title" style={{ color: tokens.text }}>
                {title}
            </p>
            {hint ? (
                <p className="macro-empty__hint" style={{ color: tokens.textMuted }}>
                    {hint}
                </p>
            ) : null}
            {onRetry ? (
                <button type="button" className="macro-empty__retry" onClick={onRetry}>
                    Yeniden dene
                </button>
            ) : null}
        </aside>
    );
}
