import { useCallback, useEffect, useRef } from 'react';

/** Ardışık çağrıları en fazla `ms` aralıkla iletir (grafik imleci vb.). */
export function useThrottledCallback<T extends (arg: string) => void>(fn: T, ms: number): T {
    const fnRef = useRef(fn);
    const lastRunRef = useRef(0);
    const pendingRef = useRef<string | null>(null);
    const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

    fnRef.current = fn;

    useEffect(
        () => () => {
            if (timerRef.current != null) clearTimeout(timerRef.current);
        },
        [],
    );

    return useCallback(
        ((arg: string) => {
            const now = Date.now();
            const elapsed = now - lastRunRef.current;
            if (elapsed >= ms) {
                lastRunRef.current = now;
                pendingRef.current = null;
                fnRef.current(arg);
                return;
            }
            pendingRef.current = arg;
            if (timerRef.current != null) return;
            timerRef.current = setTimeout(() => {
                timerRef.current = null;
                const p = pendingRef.current;
                if (p == null) return;
                pendingRef.current = null;
                lastRunRef.current = Date.now();
                fnRef.current(p);
            }, ms - elapsed);
        }) as T,
        [ms],
    );
}
