import { useCallback, useEffect, useState } from 'react';

export type NotificationPreferenceKey =
    | 'email'
    | 'app'
    | 'marketAlerts'
    | 'weeklyReports'
    | 'announcements';

export type NotificationPreferences = Record<NotificationPreferenceKey, boolean>;

const STORAGE_PREFIX = 'nrs.user.notifPrefs.v1';

export const DEFAULT_NOTIFICATION_PREFERENCES: NotificationPreferences = {
    email: true,
    app: true,
    marketAlerts: true,
    weeklyReports: false,
    announcements: true,
};

function storageKey(userId: number | null | undefined): string | null {
    if (userId == null) return null;
    return `${STORAGE_PREFIX}.${userId}`;
}

function readPrefs(userId: number | null | undefined): NotificationPreferences {
    const key = storageKey(userId);
    if (!key || typeof window === 'undefined') return { ...DEFAULT_NOTIFICATION_PREFERENCES };
    try {
        const raw = window.localStorage.getItem(key);
        if (!raw) return { ...DEFAULT_NOTIFICATION_PREFERENCES };
        const parsed = JSON.parse(raw) as Partial<NotificationPreferences>;
        return { ...DEFAULT_NOTIFICATION_PREFERENCES, ...parsed };
    } catch {
        return { ...DEFAULT_NOTIFICATION_PREFERENCES };
    }
}

function writePrefs(userId: number, prefs: NotificationPreferences) {
    const key = storageKey(userId);
    if (!key || typeof window === 'undefined') return;
    window.localStorage.setItem(key, JSON.stringify(prefs));
}

export function useNotificationPreferences(userId: number | null | undefined) {
    const [prefs, setPrefs] = useState<NotificationPreferences>(() => readPrefs(userId));

    useEffect(() => {
        setPrefs(readPrefs(userId));
    }, [userId]);

    const setPreference = useCallback(
        (key: NotificationPreferenceKey, value: boolean) => {
            if (userId == null) return;
            setPrefs((prev) => {
                const next = { ...prev, [key]: value };
                writePrefs(userId, next);
                return next;
            });
        },
        [userId],
    );

    return { prefs, setPreference };
}
