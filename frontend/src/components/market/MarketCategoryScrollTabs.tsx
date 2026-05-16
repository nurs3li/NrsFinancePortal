import { useCallback, useLayoutEffect, useRef, useState, type ReactNode } from 'react';

export type MarketCategoryScrollTab<T extends string> = {
    id: T;
    /** Erişilebilir ad + ipucu (ikon kullanıldığında görünür metin yerine) */
    label: string;
    /** Varsa sekmede metin yerine gösterilir (ör. çizgi / mum grafik simgesi). */
    icon?: ReactNode;
};

type Props<T extends string> = {
    categories: MarketCategoryScrollTab<T>[];
    value: T;
    onChange: (id: T) => void;
    ariaLabel: string;
    className?: string;
};

/**
 * Yatay kaydırmalı kategori çubuğu; seçim değişince vurgu yumuşak geçişle kayar.
 */
export function MarketCategoryScrollTabs<T extends string>({
    categories,
    value,
    onChange,
    ariaLabel,
    className,
}: Props<T>) {
    const trackRef = useRef<HTMLDivElement>(null);
    const btnRefs = useRef<Partial<Record<string, HTMLButtonElement>>>({});
    const didMountRef = useRef(false);
    const [glider, setGlider] = useState({ left: 0, width: 0, visible: false });

    const updateGlider = useCallback(() => {
        const track = trackRef.current;
        const btn = btnRefs.current[value];
        if (!track || !btn) {
            setGlider((g) => ({ ...g, visible: false }));
            return;
        }
        const left = btn.offsetLeft;
        const width = btn.offsetWidth;
        setGlider({ left, width, visible: width > 0 });
    }, [value]);

    useLayoutEffect(() => {
        updateGlider();
    }, [updateGlider, categories]);

    useLayoutEffect(() => {
        const track = trackRef.current;
        if (!track) return undefined;
        const ro = new ResizeObserver(() => {
            updateGlider();
        });
        ro.observe(track);
        return () => ro.disconnect();
    }, [updateGlider]);

    useLayoutEffect(() => {
        const btn = btnRefs.current[value];
        if (!btn) return;
        btn.scrollIntoView({
            behavior: didMountRef.current ? 'smooth' : 'auto',
            inline: 'nearest',
            block: 'nearest',
        });
        didMountRef.current = true;
    }, [value]);

    return (
        <div className={['terminal-category-scroll-shell', className].filter(Boolean).join(' ')}>
            <div ref={trackRef} className="terminal-category-scroll-track" role="tablist" aria-label={ariaLabel}>
                <div
                    className={`terminal-category-scroll-glider ${glider.visible ? 'is-visible' : ''}`}
                    style={{ left: glider.left, width: glider.width }}
                    aria-hidden
                />
                {categories.map((cat) => (
                    <button
                        key={cat.id}
                        type="button"
                        role="tab"
                        ref={(el) => {
                            if (el) btnRefs.current[cat.id] = el;
                            else delete btnRefs.current[cat.id];
                        }}
                        aria-selected={value === cat.id}
                        aria-label={cat.label}
                        title={cat.label}
                        className={`terminal-category-scroll-tab ${value === cat.id ? 'is-active' : ''}${
                            cat.icon != null ? ' terminal-category-scroll-tab--icon' : ''
                        }`}
                        onClick={() => onChange(cat.id)}
                    >
                        {cat.icon ?? cat.label}
                    </button>
                ))}
            </div>
        </div>
    );
}
