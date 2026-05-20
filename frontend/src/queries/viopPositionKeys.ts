export const viopPositionKeys = {
    all: ['viop-positions'] as const,
    list: () => [...viopPositionKeys.all, 'list'] as const,
    summary: () => [...viopPositionKeys.all, 'summary'] as const,
};
