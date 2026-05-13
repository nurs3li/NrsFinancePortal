import { useEffect, useRef } from 'react';
import { useDocumentVisibility } from './useDocumentVisibility';

/**
 * Sekme ön plandayken her intervalMs'de bir refetch çağırır; arka planda zamanlayıcı durur.
 * Sekmeye dönünce tazelik için sayfadaki `useRefetchOnFocus` / odak dinleyicileri kullanılmalıdır.
 */
export function usePolling(
    refetch: () => void | Promise<void>,
    intervalMs: number,
    enabled = true
) {
    const refetchRef = useRef(refetch);
    const visible = useDocumentVisibility();

    useEffect(() => {
        refetchRef.current = refetch;
    }, [refetch]);

    useEffect(() => {
        if (!enabled || intervalMs <= 0 || !visible) return;

        const tick = () => {
            void Promise.resolve(refetchRef.current()).catch(() => {});
        };

        const id = setInterval(tick, intervalMs);
        return () => clearInterval(id);
    }, [intervalMs, enabled, visible]);
}