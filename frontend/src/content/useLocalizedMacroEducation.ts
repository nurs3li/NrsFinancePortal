import { useMemo } from 'react';
import { useLanguage } from '../i18n/LanguageContext';
import { macroEducationTerms, type MacroEducationTerm, type MacroTermId } from './macroEducationTerms';

function localizeTerm(
    id: string,
    base: MacroEducationTerm,
    t: (key: string, fallback?: string) => string,
): MacroEducationTerm {
    const p = `macro.edu.${id}`;
    const out: MacroEducationTerm = {
        ...base,
        title: t(`${p}.title`, base.title),
        short: t(`${p}.short`, base.short),
        detail: t(`${p}.detail`, base.detail),
    };
    if (base.formula) out.formula = t(`${p}.formula`, base.formula);
    if (base.example) out.example = t(`${p}.example`, base.example);
    if (base.whyItMatters) out.whyItMatters = t(`${p}.whyItMatters`, base.whyItMatters);
    return out;
}

export function useLocalizedMacroEducation(): Record<MacroTermId, MacroEducationTerm> {
    const { t, lang } = useLanguage();
    return useMemo(() => {
        const out = {} as Record<MacroTermId, MacroEducationTerm>;
        for (const [id, term] of Object.entries(macroEducationTerms) as [MacroTermId, MacroEducationTerm][]) {
            out[id] = localizeTerm(id, term, t);
        }
        return out;
        // eslint-disable-next-line react-hooks/exhaustive-deps -- t() resolves from lang dict
    }, [lang, t]);
}
