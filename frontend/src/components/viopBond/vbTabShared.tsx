import type { ReactNode } from 'react';

export function AnalysisMiniCard({
    title,
    tokens,
    children,
}: {
    title: string;
    tokens: { border: string; bgCard: string };
    children: ReactNode;
}) {
    return (
        <div className="vb-analysis-card pf-card-premium" style={{ borderColor: tokens.border, background: tokens.bgCard }}>
            <h4>{title}</h4>
            {children}
        </div>
    );
}

export function RankList({
    items,
    empty,
    muted,
}: {
    items: { label: string; value: string }[];
    empty: string;
    muted: string;
}) {
    if (!items.length) {
        return <p style={{ color: muted, fontSize: '0.82rem', margin: 0 }}>{empty}</p>;
    }
    return (
        <ul className="vb-rank-list">
            {items.map((it) => (
                <li key={it.label}>
                    <span>{it.label}</span>
                    <strong>{it.value}</strong>
                </li>
            ))}
        </ul>
    );
}

export function pctClass(v: number | null | undefined): string {
    if (v == null || !Number.isFinite(v)) return '';
    return v >= 0 ? 'vb-pos' : 'vb-neg';
}

export function pnlClass(v: number | null | undefined): string {
    if (v == null || !Number.isFinite(v)) return '';
    return v >= 0 ? 'vb-pos' : 'vb-neg';
}
