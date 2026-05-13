/** TanStack Query: bildirim uçları ortak invalidasyon / stale paylaşımı için. */
export const notificationKeys = {
    all: ['notifications'] as const,
    unreadCount: () => [...notificationKeys.all, 'unread-count'] as const,
    headerDropdown: () => [...notificationKeys.all, 'header-dropdown'] as const,
    dashboardRecent: () => [...notificationKeys.all, 'dashboard-recent'] as const,
};
