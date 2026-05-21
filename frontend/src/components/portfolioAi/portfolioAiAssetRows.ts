import type { ManualPortfolioView } from '../../types/manualPortfolio';
import type { AssetAiCommentRow } from '../../types/portfolioAi';

export type PortfolioAiAssetRow = {
    symbol: string;
    weightPct: number;
    returnPct: number | null;
    assetScore: number;
    riskScore: number;
    comment: AssetAiCommentRow;
};

export function buildAssetRows(
    positions: ManualPortfolioView[],
    comments: AssetAiCommentRow[],
): PortfolioAiAssetRow[] {
    const open = positions.filter((p) => String(p.status).toUpperCase() === 'OPEN');
    const total = open.reduce((s, p) => s + Math.max(0, Number(p.currentValue ?? 0)), 0);
    const bySym = new Map(comments.map((c) => [c.symbol.toUpperCase(), c]));

    const rows: PortfolioAiAssetRow[] = open.map((p) => {
        const v = Math.max(0, Number(p.currentValue ?? 0));
        const weightPct =
            bySym.get(p.symbol.toUpperCase())?.weightPct ??
            (total > 0 ? (100 * v) / total : 0);
        const sym = p.symbol.toUpperCase();
        const comment = bySym.get(sym);
        return {
            symbol: sym,
            weightPct,
            returnPct:
                comment?.returnPct ??
                p.unrealizedReturnPct ??
                p.holdReturnPctToday ??
                null,
            assetScore: comment?.assetScore ?? 50,
            riskScore: comment?.riskScore ?? 50,
            comment: comment ?? {
                symbol: sym,
                role: '—',
                positiveFactors: [],
                riskFactors: [],
                shortComment: '',
                detailComment: '',
            },
        };
    });

    const commentSyms = new Set(comments.map((c) => c.symbol.toUpperCase()));
    const withComments = rows.filter((r) => commentSyms.has(r.symbol));
    const without = rows.filter((r) => !commentSyms.has(r.symbol));
    const ordered = comments.length > 0 ? [...withComments, ...without] : rows;
    return ordered.sort((a, b) => {
        const aIn = commentSyms.has(a.symbol) ? 1 : 0;
        const bIn = commentSyms.has(b.symbol) ? 1 : 0;
        if (aIn !== bIn) return bIn - aIn;
        return b.weightPct - a.weightPct;
    });
}

export function weightRiskLabel(weightPct: number): 'LOW' | 'MEDIUM' | 'HIGH' {
    if (weightPct >= 25) return 'HIGH';
    if (weightPct >= 10) return 'MEDIUM';
    return 'LOW';
}

export function returnContributionPct(weightPct: number, returnPct: number | null): number | null {
    if (returnPct == null || !Number.isFinite(returnPct)) return null;
    return (weightPct / 100) * returnPct;
}
