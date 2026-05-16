import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';

export const MARKET_LIST_ROW_HEIGHT = 50;
export const MARKET_LIST_OVERSCAN_ROWS = 6;
/** İç kaydırma kapalıyken bile en fazla bu kadar satır DOM'da tutulur */
export const MARKET_LIST_MAX_DOM_ROWS = 64;

export function useMarketListVirtualization<T>(items: T[], resetKey: string) {
    const tableWrapRef = useRef<HTMLDivElement>(null);
    const [listScrollTop, setListScrollTop] = useState(0);
    const [listViewportH, setListViewportH] = useState(280);
    const [scrollMeta, setScrollMeta] = useState({ canScroll: false, atBottom: true });
    const scrollRafRef = useRef<number | null>(null);
    const lastReportedScrollTopRef = useRef(0);
    const lastListViewportHRef = useRef(0);

    const updateScrollMeta = (el: HTMLDivElement) => {
        const canScroll = el.scrollHeight > el.clientHeight + 2;
        const atBottom = !canScroll || el.scrollTop + el.clientHeight >= el.scrollHeight - 4;
        setScrollMeta((prev) =>
            prev.canScroll === canScroll && prev.atBottom === atBottom ? prev : { canScroll, atBottom },
        );
    };

    useLayoutEffect(() => {
        const el = tableWrapRef.current;
        if (!el || typeof ResizeObserver === 'undefined') return;

        const onScroll = () => {
            if (scrollRafRef.current != null) return;
            scrollRafRef.current = requestAnimationFrame(() => {
                scrollRafRef.current = null;
                const current = el.scrollTop;
                updateScrollMeta(el);
                if (Math.abs(current - lastReportedScrollTopRef.current) >= MARKET_LIST_ROW_HEIGHT) {
                    lastReportedScrollTopRef.current = current;
                    setListScrollTop(current);
                }
            });
        };

        el.addEventListener('scroll', onScroll, { passive: true });
        const ro = new ResizeObserver(() => {
            const h = el.clientHeight;
            if (Math.abs(h - lastListViewportHRef.current) < 8) return;
            lastListViewportHRef.current = h;
            requestAnimationFrame(() => {
                setListViewportH(h);
                updateScrollMeta(el);
            });
        });
        ro.observe(el);
        lastReportedScrollTopRef.current = el.scrollTop;
        setListScrollTop(el.scrollTop);
        lastListViewportHRef.current = el.clientHeight;
        setListViewportH(el.clientHeight);
        updateScrollMeta(el);

        return () => {
            if (scrollRafRef.current != null) cancelAnimationFrame(scrollRafRef.current);
            el.removeEventListener('scroll', onScroll);
            ro.disconnect();
        };
    }, []);

    useEffect(() => {
        const el = tableWrapRef.current;
        if (el) el.scrollTop = 0;
        lastReportedScrollTopRef.current = 0;
        setListScrollTop(0);
        if (el) updateScrollMeta(el);
    }, [resetKey]);

    const listVirtual = useMemo(() => {
        const total = items.length;
        const rowH = MARKET_LIST_ROW_HEIGHT;
        if (total === 0) {
            return { rowHeight: rowH, topSpacer: 0, bottomSpacer: 0, rows: [] as T[], total: 0 };
        }
        if (!scrollMeta.canScroll && total <= MARKET_LIST_MAX_DOM_ROWS) {
            return {
                rowHeight: rowH,
                topSpacer: 0,
                bottomSpacer: 0,
                rows: items,
                total,
            };
        }
        const start = Math.min(total, Math.max(0, Math.floor(listScrollTop / rowH) - MARKET_LIST_OVERSCAN_ROWS));
        const visibleRows = Math.ceil(listViewportH / rowH) + 2 * MARKET_LIST_OVERSCAN_ROWS + 2;
        const end = Math.min(total, start + visibleRows);
        return {
            rowHeight: rowH,
            topSpacer: start * rowH,
            bottomSpacer: Math.max(0, (total - end) * rowH),
            rows: items.slice(start, end),
            total,
        };
    }, [items, listScrollTop, listViewportH, scrollMeta.canScroll]);

    return { tableWrapRef, listVirtual, scrollMeta };
}
