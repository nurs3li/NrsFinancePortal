import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';
import { macroEducationTerms, type MacroEducationTerm, type MacroTermId } from '../../../content/macroEducationTerms';
import type { MacroTheme } from '../MacroTheme';
import { InfoModal } from './InfoModal';

type Ctx = {
    openTerm: (id: MacroTermId) => void;
};

const InfoTermContext = createContext<Ctx | null>(null);

export function InfoTermProvider({ tokens, children }: { tokens: MacroTheme; children: ReactNode }) {
    const [activeId, setActiveId] = useState<MacroTermId | null>(null);

    const openTerm = useCallback((id: MacroTermId) => setActiveId(id), []);
    const close = useCallback(() => setActiveId(null), []);

    const term: MacroEducationTerm | null = activeId ? macroEducationTerms[activeId] : null;

    const value = useMemo(() => ({ openTerm }), [openTerm]);

    return (
        <InfoTermContext.Provider value={value}>
            {children}
            {term ? <InfoModal open={activeId != null} tokens={tokens} term={term} onClose={close} /> : null}
        </InfoTermContext.Provider>
    );
}

export function useInfoTerm() {
    const ctx = useContext(InfoTermContext);
    if (!ctx) throw new Error('useInfoTerm must be used within InfoTermProvider');
    return ctx;
}
