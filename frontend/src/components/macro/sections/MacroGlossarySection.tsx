import { useMemo, useState } from 'react';
import { glossaryCategories, macroEducationTerms, type MacroTermId } from '../../../content/macroEducationTerms';
import { useInfoTerm } from '../education/InfoTermProvider';
import { MacroSection } from '../primitives/MacroSection';
import type { MacroTheme } from '../MacroTheme';

type Props = {
    tokens: MacroTheme;
};

export function MacroGlossarySection({ tokens }: Props) {
    const { openTerm } = useInfoTerm();
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
    }, [query, category]);

    return (
        <MacroSection
            id="macro-glossary"
            title="Kavramlar"
            summary="Finansal okuryazarlık sözlüğü — detaylar açılır pencerede."
            tokens={tokens}
        >
            <div className="macro-glossary-toolbar">
                <input
                    type="search"
                    className="macro-glossary-search"
                    placeholder="Kavram ara…"
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                    aria-label="Kavram ara"
                />
                <div className="macro-glossary-chips" role="tablist" aria-label="Kategori">
                    <button
                        type="button"
                        className={`macro-glossary-chip${category === 'all' ? ' macro-glossary-chip--active' : ''}`}
                        onClick={() => setCategory('all')}
                    >
                        Tümü
                    </button>
                    {glossaryCategories.map((c) => (
                        <button
                            key={c.id}
                            type="button"
                            className={`macro-glossary-chip${category === c.id ? ' macro-glossary-chip--active' : ''}`}
                            onClick={() => setCategory(c.id)}
                        >
                            {c.label}
                        </button>
                    ))}
                </div>
            </div>

            <ul className="macro-glossary-list">
                {entries.map(([id, term]) => (
                    <li key={id} className="macro-glossary-item" style={{ borderColor: tokens.border, background: tokens.bgCard }}>
                        <h3 style={{ color: tokens.text }}>{term.title.replace(' Nedir?', '')}</h3>
                        <p style={{ color: tokens.textMuted }}>{term.short}</p>
                        <button type="button" className="macro-link-btn" onClick={() => openTerm(id)}>
                            Detaylı gör
                        </button>
                    </li>
                ))}
            </ul>
        </MacroSection>
    );
}
