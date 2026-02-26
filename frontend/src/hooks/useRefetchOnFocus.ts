import { useEffect, useRef } from 'react';

/**
 * Sekme tekrar odaklandığında (kullanıcı geri dönünce) refetch çağırır.
 */
export function useRefetchOnFocus(
    refetch: () => void | Promise<void>,
    enabled = true
) {
    const refetchRef = useRef(refetch);
    refetchRef.current = refetch;

    useEffect(() => {
        if (!enabled) return;

        const onVisibilityChange = () => {
            if (document.visibilityState === 'visible') {
                void Promise.resolve(refetchRef.current()).catch(() => {});
            }
        };

        document.addEventListener('visibilitychange', onVisibilityChange);
        return () => document.removeEventListener('visibilitychange', onVisibilityChange);
    }, [enabled]);
}