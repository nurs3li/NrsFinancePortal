import { useLayoutEffect, useState, type RefObject } from 'react';

/** rAF-throttled resize — layout shift / zoom sırasında chart titremesini azaltır. */
export function createChartResizeScheduler(onResize: () => void): {
    schedule: () => void;
    bind: (el: HTMLElement) => () => void;
} {
    let raf: number | null = null;
    const schedule = () => {
        if (raf != null) return;
        raf = requestAnimationFrame(() => {
            raf = null;
            onResize();
        });
    };
    return {
        schedule,
        bind(el: HTMLElement) {
            const ro = new ResizeObserver(schedule);
            ro.observe(el);
            window.addEventListener('resize', schedule);
            return () => {
                ro.disconnect();
                window.removeEventListener('resize', schedule);
                if (raf != null) {
                    cancelAnimationFrame(raf);
                    raf = null;
                }
            };
        },
    };
}

export function useChartContainerSize(ref: RefObject<HTMLElement | null>): { width: number; height: number } {
    const [size, setSize] = useState({ width: 320, height: 300 });

    useLayoutEffect(() => {
        const el = ref.current;
        if (!el) return;

        let raf: number | null = null;
        const measure = () => {
            raf = null;
            const width = Math.max(280, Math.floor(el.clientWidth));
            const height = Math.max(260, Math.floor(el.clientHeight));
            setSize((prev) =>
                prev.width === width && prev.height === height ? prev : { width, height },
            );
        };
        const schedule = () => {
            if (raf != null) return;
            raf = requestAnimationFrame(measure);
        };

        measure();
        const ro = new ResizeObserver(schedule);
        ro.observe(el);
        window.addEventListener('resize', schedule);

        return () => {
            ro.disconnect();
            window.removeEventListener('resize', schedule);
            if (raf != null) cancelAnimationFrame(raf);
        };
    }, []);

    return size;
}
