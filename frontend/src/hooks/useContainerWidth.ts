import { useLayoutEffect, useState, type RefObject } from 'react';

/**
 * Container genişliği — mobil orientation / adres çubuğu değişiminde grafik layout için.
 */
export function useContainerWidth(ref: RefObject<HTMLElement | null>, minWidth = 280): number {
    const [width, setWidth] = useState(minWidth);

    useLayoutEffect(() => {
        const el = ref.current;
        if (!el) return;

        let raf: number | null = null;
        const measure = () => {
            raf = null;
            const w = Math.max(minWidth, Math.floor(el.clientWidth));
            setWidth((prev) => (prev === w ? prev : w));
        };
        const schedule = () => {
            if (raf != null) return;
            raf = requestAnimationFrame(measure);
        };

        measure();
        const ro = new ResizeObserver(schedule);
        ro.observe(el);
        window.addEventListener('resize', schedule);
        const vv = window.visualViewport;
        vv?.addEventListener('resize', schedule);
        vv?.addEventListener('scroll', schedule);

        return () => {
            ro.disconnect();
            window.removeEventListener('resize', schedule);
            vv?.removeEventListener('resize', schedule);
            vv?.removeEventListener('scroll', schedule);
            if (raf != null) cancelAnimationFrame(raf);
        };
    }, [minWidth]);

    return width;
}
