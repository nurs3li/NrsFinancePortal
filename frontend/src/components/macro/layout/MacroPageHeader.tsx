import { InfoButton } from '../education/InfoButton';
import { MACRO_CATEGORY_CHIPS } from '../macroSections';
import type { MacroTheme } from '../MacroTheme';

type Props = {
    title: string;
    subtitle: string;
    lastUpdated?: string;
    tokens: MacroTheme;
};

export function MacroPageHeader({ title, subtitle, lastUpdated, tokens }: Props) {
    return (
        <header className="macro-hero" style={{ borderColor: tokens.border }}>
            <div className="macro-hero__top">
                <h1 className="macro-hero__title" style={{ color: tokens.text }}>
                    {title}
                    <InfoButton termId="pageIntro" ariaLabel="Faiz ve enflasyon paneli hakkında bilgi" />
                </h1>
                {lastUpdated ? (
                    <p className="macro-hero__updated" style={{ color: tokens.textMuted }}>
                        Son güncelleme: {lastUpdated}
                    </p>
                ) : null}
            </div>
            <p className="macro-hero__subtitle" style={{ color: tokens.textMuted }}>
                {subtitle}
            </p>
            <ul className="macro-hero__chips" aria-label="Konu kategorileri">
                {MACRO_CATEGORY_CHIPS.map((chip) => (
                    <li key={chip} className="macro-hero__chip" style={{ borderColor: tokens.border, color: tokens.textMuted }}>
                        {chip}
                    </li>
                ))}
            </ul>
        </header>
    );
}
