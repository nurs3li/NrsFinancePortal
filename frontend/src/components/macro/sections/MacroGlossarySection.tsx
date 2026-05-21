import { useMemo, useState } from 'react';
import { glossaryCategories, type MacroTermId } from '../../../content/macroEducationTerms';
import { useLocalizedMacroEducation } from '../../../content/useLocalizedMacroEducation';
import { useLanguage } from '../../../i18n/LanguageContext';
import { useInfoTerm } from '../education/InfoTermProvider';
import { MacroSection } from '../primitives/MacroSection';
import type { MacroTheme } from '../MacroTheme';

type Props = {
    tokens: MacroTheme;
};

function glossaryListTitle(title: string): string {
    return title
        .replace(/\s+Nedir\?$/i, '')
        .replace(/^What is (the )?/i, '')
        .replace(/\?$/i, '')
        .trim();
}

export function MacroGlossarySection({ tokens }: Props) {
    const { t } = useLanguage();
    const { openTerm } = useInfoTerm();
    const macroEducationTerms = useLocalizedMacroEducation();
    const [query, setQuery] = useState('');
    const [category, setCategory] = useState<string>('all');

    const entries = useMemo(() => {
        return (Object.entries(macroEducationTerms) as [MacroTermId, (typeof macroEducationTerms)[MacroTermId]][])
            .filter(([id]) => id !== 'pageIntro')
            .filter(([, term]) => {
                if (category !== 'all' && term.category !== category) return false;
                if (!query.trim()) return true;
                const q = query.toLowerCase();
                return term.title.toLowerCase().includes(q) || term.short.toLowerCase().includes(q);
            });
    }, [query, category, macroEducationTerms]);

    return (
        <MacroSection
            id="macro-glossary"
            title={t('macro.glossary.title', 'Kavramlar')}
            summary={t('macro.glossary.summary', 'Finansal okuryazarlık sözlüğü — detaylar açılır pencerede.')}
            tokens={tokens}
        >
            <div className="macro-glossary-toolbar">
                <input
                    type="search"
                    className="macro-glossary-search"
                    placeholder={t('macro.glossary.search', 'Kavram ara…')}
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                    aria-label={t('macro.glossary.search', 'Kavram ara…')}
                />
                <div className="macro-glossary-chips" role="tablist" aria-label={t('macro.glossary.title', 'Kavramlar')}>
                    <button
                        type="button"
                        className={`macro-glossary-chip${category === 'all' ? ' macro-glossary-chip--active' : ''}`}
                        onClick={() => setCategory('all')}
                    >
                        {t('macro.glossary.allCategories', 'Tümü')}
                    </button>
                    {glossaryCategories.map((c) => (
                        <button
                            key={c.id}
                            type="button"
                            className={`macro-glossary-chip${category === c.id ? ' macro-glossary-chip--active' : ''}`}
                            onClick={() => setCategory(c.id)}
                        >
                            {t(`macro.glossary.cat.${c.id}`, c.label)}
                        </button>
                    ))}
                </div>
            </div>

            <ul className="macro-glossary-list">
                {entries.map(([id, term]) => (
                    <li key={id} className="macro-glossary-item" style={{ borderColor: tokens.border, background: tokens.bgCard }}>
                        <h3 style={{ color: tokens.text }}>{glossaryListTitle(term.title)}</h3>
                        <p style={{ color: tokens.textMuted }}>{term.short}</p>
                        <button type="button" className="macro-link-btn" onClick={() => openTerm(id)}>
                            {t('common.detail', 'Detaylı gör')}
                        </button>
                    </li>
                ))}
            </ul>
        </MacroSection>
    );
}
