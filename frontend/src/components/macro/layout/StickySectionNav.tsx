import { useEffect, useState } from 'react';
import { MACRO_SECTION_NAV } from '../macroSections';

type Props = {
    activeId: string;
    onNavigate: (id: string) => void;
};

export function StickySectionNav({ activeId, onNavigate }: Props) {
    return (
        <nav className="macro-sticky-nav" aria-label="Bölüm gezinme">
            <div className="macro-sticky-nav__scroll">
                {MACRO_SECTION_NAV.map((s) => (
                    <button
                        key={s.id}
                        type="button"
                        className={`macro-sticky-nav__tab${activeId === s.id ? ' macro-sticky-nav__tab--active' : ''}`}
                        onClick={() => onNavigate(s.id)}
                        aria-current={activeId === s.id ? 'true' : undefined}
                    >
                        {s.label}
                    </button>
                ))}
            </div>
        </nav>
    );
}

export function useMacroSectionSpy(sectionIds: readonly string[]) {
    const [activeId, setActiveId] = useState(sectionIds[0] ?? 'macro-overview');

    useEffect(() => {
        const observers: IntersectionObserver[] = [];
        const visible = new Map<string, number>();

        const io = new IntersectionObserver(
            (entries) => {
                for (const e of entries) {
                    const id = e.target.id;
                    if (!id) continue;
                    if (e.isIntersecting) visible.set(id, e.intersectionRatio);
                    else visible.delete(id);
                }
                if (visible.size === 0) return;
                const best = [...visible.entries()].sort((a, b) => b[1] - a[1])[0]?.[0];
                if (best) setActiveId(best);
            },
            { rootMargin: '-120px 0px -55% 0px', threshold: [0.1, 0.25, 0.5] },
        );

        for (const id of sectionIds) {
            const el = document.getElementById(id);
            if (el) {
                io.observe(el);
                observers.push(io);
            }
        }

        return () => io.disconnect();
    }, [sectionIds]);

    const scrollTo = (id: string) => {
        const el = document.getElementById(id);
        if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' });
        setActiveId(id);
    };

    return { activeId, scrollTo };
}
