import type { ThemeMode, ThemeTokens } from '../theme/ThemeContext';

type ChartTokenSlice = Pick<ThemeTokens, 'bgCard' | 'border' | 'text' | 'textMuted'>;

export function chartGridStroke(theme: ThemeMode): string {
    return theme === 'light' ? 'rgba(148, 163, 184, 0.38)' : 'rgba(148, 163, 184, 0.2)';
}

export function chartAxisTick(tokens: ChartTokenSlice, fontSize = 11) {
    return { fill: tokens.textMuted, fontSize };
}

export function chartTooltipContentStyle(tokens: ChartTokenSlice) {
    return {
        backgroundColor: tokens.bgCard,
        color: tokens.text,
        border: `1px solid ${tokens.border}`,
        borderRadius: 8,
        fontSize: 12,
    } as const;
}

export function chartLegendStyle(tokens: ChartTokenSlice) {
    return { color: tokens.textMuted, fontSize: 11 };
}
