import { useEffect, useRef } from 'react';

/**
 * Her intervalMs milisaniyede bir refetch çağırır (sayfa açıkken).
 */
export function usePolling(
    refetch: () => void | Promise<void>,
    intervalMs: number,
    enabled = true
) {
    const refetchRef = useRef(refetch);

    useEffect(() => {
        refetchRef.current = refetch;
    }, [refetch]);

    useEffect(() => {
        if (!enabled || intervalMs <= 0) return;

        const tick = () => {
            void Promise.resolve(refetchRef.current()).catch(() => {});
        };

        const id = setInterval(tick, intervalMs);
        return () => clearInterval(id);
    }, [intervalMs, enabled]);
}