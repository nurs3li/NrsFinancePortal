import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';

export type ThemeMode = 'light' | 'dark';

export type ThemeTokens = {
    bg: string;
    bgCard: string;
    border: string;
    text: string;
    textMuted: string;
    accent: string;
    accentGradient: string;
    success: string;
    error: string;
    headerBg: string;
    headerText: string;
    inputBg: string;
    tableBorder: string;
};

const LIGHT: ThemeTokens = {
    bg: '#f1f5f9',
    bgCard: '#ffffff',
    border: '#e2e8f0',
    text: '#0f172a',
    textMuted: '#64748b',
    accent: '#1d4ed8',
    accentGradient: 'linear-gradient(135deg, #1d4ed8, #0ea5e9)',
    success: '#16a34a',
    error: '#dc2626',
    headerBg: 'rgba(255, 255, 255, 0.92)',
    headerText: '#0f172a',
    inputBg: '#ffffff',
    tableBorder: '#e2e8f0',
};

const DARK: ThemeTokens = {
    bg: '#0f172a',
    bgCard: '#1e293b',
    border: '#334155',
    text: '#f8fafc',
    textMuted: '#94a3b8',
    accent: '#3b82f6',
    accentGradient: 'linear-gradient(135deg, #1d4ed8, #0ea5e9)',
    success: '#22c55e',
    error: '#ef4444',
    headerBg: '#0f172a',
    headerText: '#f8fafc',
    inputBg: '#020617',
    tableBorder: '#334155',
};

type ThemeContextType = {
    theme: ThemeMode;
    setTheme: (mode: ThemeMode) => void;
    toggleTheme: () => void;
    tokens: ThemeTokens;
};

const ThemeContext = createContext<ThemeContextType | null>(null);

const STORAGE_KEY = 'nrs-finance-theme';

export function ThemeProvider({ children }: { children: React.ReactNode }) {
    const [theme, setThemeState] = useState<ThemeMode>(() => {
        try {
            const s = localStorage.getItem(STORAGE_KEY);
            if (s === 'light' || s === 'dark') return s;
        } catch (_) {}
        return 'dark';
    });

    useEffect(() => {
        try {
            localStorage.setItem(STORAGE_KEY, theme);
            document.documentElement.setAttribute('data-theme', theme);
        } catch (_) {}
    }, [theme]);

    const setTheme = useCallback((mode: ThemeMode) => setThemeState(mode), []);
    const toggleTheme = useCallback(
        () => setThemeState((t) => (t === 'light' ? 'dark' : 'light')),
        []
    );

    const tokens = theme === 'light' ? LIGHT : DARK;

    return (
        <ThemeContext.Provider value={{ theme, setTheme, toggleTheme, tokens }}>
            {children}
        </ThemeContext.Provider>
    );
}

export function useTheme() {
    const ctx = useContext(ThemeContext);
    if (!ctx) throw new Error('useTheme must be used within ThemeProvider');
    return ctx;
}