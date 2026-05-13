import { useSyncExternalStore } from 'react';

function subscribe(onStoreChange: () => void) {
    document.addEventListener('visibilitychange', onStoreChange);
    return () => document.removeEventListener('visibilitychange', onStoreChange);
}

function getSnapshot() {
    return document.visibilityState === 'visible';
}

function getServerSnapshot() {
    return true;
}

/**
 * Sekme ön planda mı (`document.visibilityState === 'visible'`).
 * Arka planda interval / refetchInterval kapatmak için kullanılır.
 */
export function useDocumentVisibility(): boolean {
    return useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);
}
