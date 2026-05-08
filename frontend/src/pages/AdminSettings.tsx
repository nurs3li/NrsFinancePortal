import { useTheme } from '../theme/ThemeContext';
import { useLanguage } from '../i18n/LanguageContext';

export function AdminSettings() {
    const { tokens } = useTheme();
    const { t } = useLanguage();
    const pageStyle: React.CSSProperties = { padding: 24, background: tokens.bg, color: tokens.text, minHeight: '100%' };
    const titleStyle: React.CSSProperties = { fontSize: '1.75rem', fontWeight: 700, marginBottom: 4 };
    const mutedStyle: React.CSSProperties = { color: tokens.textMuted, fontSize: '0.875rem' };

    return (
        <div style={pageStyle}>
            <h1 style={titleStyle}>{t('admin.settingsTitle', 'Sistem Ayarları')}</h1>
            <p style={mutedStyle}>{t('admin.settingsSubtitle', 'Eşik ve risk config (Faz 6 / config API sonrası).')}</p>
        </div>
    );
}