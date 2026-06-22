export type MacroTheme = {
    bg?: string;
    bgCard: string;
    border: string;
    text: string;
    textMuted: string;
    accent?: string;
};

export type MacroChartColorKey = 'blue' | 'violet' | 'green' | 'amber' | 'rose' | 'cyan' | 'indigo';

export type MacroChartColors = Record<MacroChartColorKey, string>;

/** Karanlık mod grafik serileri */
export const MACRO_CHART_COLORS_DARK: MacroChartColors = {
    blue: '#38bdf8',
    violet: '#a78bfa',
    green: '#166534',
    amber: '#fbbf24',
    rose: '#be123c',
    cyan: '#22d3ee',
    indigo: '#6366f1',
};

/** Aydınlık mod — beyaz zemin üzerinde yüksek kontrast */
export const MACRO_CHART_COLORS_LIGHT: MacroChartColors = {
    blue: '#1d4ed8',
    violet: '#6d28d9',
    green: '#166534',
    amber: '#b45309',
    rose: '#be123c',
    cyan: '#0e7490',
    indigo: '#4338ca',
};

/** @deprecated macroChartColorsForTheme kullanın */
export const MACRO_CHART_COLORS = MACRO_CHART_COLORS_DARK;

export function macroChartColorsForTheme(theme: 'light' | 'dark'): MacroChartColors {
    return theme === 'light' ? MACRO_CHART_COLORS_LIGHT : MACRO_CHART_COLORS_DARK;
}
