import React, { createContext, useContext, useMemo, useState } from 'react';
import en from './en';
import tr from './tr';

export type AppLang = 'tr' | 'en';

type LanguageContextType = {
    lang: AppLang;
    setLang: (lang: AppLang) => void;
    t: (key: string, fallback?: string) => string;
};

const STORAGE_KEY = 'app.lang';
const LEGACY_KEY = 'app.newsLang';

function normalizeLang(raw: string | null | undefined): AppLang {
    const v = String(raw ?? '').trim().toLowerCase();
    if (v.startsWith('en')) return 'en';
    return 'tr';
}

function readInitialLang(): AppLang {
    if (typeof window === 'undefined') return 'tr';
    const primary = window.localStorage.getItem(STORAGE_KEY);
    if (primary) return normalizeLang(primary);
    const legacy = window.localStorage.getItem(LEGACY_KEY);
    if (legacy) return normalizeLang(legacy);
    return 'tr';
}

const LanguageContext = createContext<LanguageContextType | null>(null);

export function LanguageProvider({ children }: { children: React.ReactNode }) {
    const [lang, setLangState] = useState<AppLang>(() => readInitialLang());

    const setLang = (next: AppLang) => {
        setLangState(next);
        if (typeof window !== 'undefined') {
            window.localStorage.setItem(STORAGE_KEY, next);
            // Backward-compatible legacy key during migration
            window.localStorage.setItem(LEGACY_KEY, next);
        }
    };

    const dict = lang === 'en' ? en : tr;
    const value = useMemo<LanguageContextType>(
        () => ({
            lang,
            setLang,
            t: (key: string, fallback?: string) => dict[key as keyof typeof dict] ?? fallback ?? key,
        }),
        [lang, dict]
    );

    return <LanguageContext.Provider value={value}>{children}</LanguageContext.Provider>;
}

export function useLanguage() {
    const ctx = useContext(LanguageContext);
    if (!ctx) throw new Error('useLanguage must be used within LanguageProvider');
    return ctx;
}

