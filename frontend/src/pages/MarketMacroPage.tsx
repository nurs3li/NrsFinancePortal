import { useMemo } from 'react';
import { BondMacroIntelligencePanel } from '../components/market/BondMacroIntelligencePanel';
import { useLanguage } from '../i18n/LanguageContext';
import { useTheme } from '../theme/ThemeContext';
import './MarketTerminal.css';

export function MarketMacroPage() {
    const { tokens } = useTheme();
    const { t } = useLanguage();

    const chartTokens = useMemo(
        () => ({
            bg: tokens.bg,
            bgCard: tokens.bgCard,
            border: tokens.border,
            text: tokens.text,
            textMuted: tokens.textMuted,
        }),
        [tokens.bg, tokens.bgCard, tokens.border, tokens.text, tokens.textMuted]
    );

    return (
        <div
            className="terminal-page"
            style={{
                padding: '16px 18px 28px',
                maxWidth: 1160,
                margin: '0 auto',
                boxSizing: 'border-box',
                minHeight: '100%',
                background: tokens.bg,
            }}
        >
            <div style={{ marginBottom: 12 }}>
                <h1 style={{ margin: 0, fontSize: 22, fontWeight: 750, color: tokens.text }}>
                    {t('nav.marketMacro', 'Faiz & Enflasyon Paneli')}
                </h1>
                <p style={{ margin: '8px 0 0', fontSize: 13, color: tokens.textMuted, lineHeight: 1.45 }}>
                    {t('market.bondMacroPageLead')}
                </p>
            </div>
            <BondMacroIntelligencePanel tokens={chartTokens} />
        </div>
    );
}
