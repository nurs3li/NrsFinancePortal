export const bondPositionKeys = {
    all: ['bond-positions'] as const,
    list: () => [...bondPositionKeys.all, 'list'] as const,
    summary: () => [...bondPositionKeys.all, 'summary'] as const,
    combined: () => [...bondPositionKeys.all, 'combined-summary'] as const,
};
