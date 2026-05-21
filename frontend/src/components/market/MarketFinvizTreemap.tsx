import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import type { MouseEvent as ReactMouseEvent } from 'react';
import { hierarchy, treemap, treemapSquarify } from 'd3-hierarchy';
import type { HierarchyRectangularNode } from 'd3-hierarchy';

export type TreemapTile = {
    sector: string;
    industry?: string | null;
    symbol: string;
    assetClass: string;
    changePercent: number;
    layoutWeight: number;
    mode?: string;
    changeHorizon?: string;
    weightMode?: string;
    marketCapSource?: string | null;
    marketCapAsOf?: string | null;
};

type HNode = {
    name: string;
    /** Sektör gruplaması için sabit anahtar (gösterim `name` ile ayrılabilir). */
    sectorKey?: string;
    value?: number;
    change?: number;
    symbol?: string;
    industry?: string | null;
    assetClass?: string;
    mode?: string;
    changeHorizon?: string;
    weightMode?: string;
    marketCapSource?: string | null;
    marketCapAsOf?: string | null;
    children?: HNode[];
};

function cellBackground(pct: number): string {
    if (Math.abs(pct) < 0.008) {
        return 'rgba(51, 65, 85, 0.75)';
    }
    if (pct > 0) {
        const t = Math.min(1, pct / 2.8);
        const g = Math.round(110 + 87 * t);
        return `rgba(${Math.round(4 + 30 * t)}, ${g}, ${Math.round(52 + 42 * t)}, ${0.35 + 0.4 * t})`;
    }
    const t = Math.min(1, Math.abs(pct) / 2.8);
    return `rgba(${Math.round(185 + 54 * t)}, ${Math.round(28 + 40 * t)}, ${Math.round(28 + 30 * t)}, ${0.35 + 0.42 * t})`;
}

function textColor(pct: number): string {
    if (Math.abs(pct) < 0.008) return '#e2e8f0';
    return '#f8fafc';
}

export type TreemapFloatingTooltipLabels = {
    labelChange: string;
    labelSector: string;
    /** İkincil metin rengi (ör. tema textMuted) */
    mutedColor?: string;
};

type Props = {
    tiles: TreemapTile[];
    borderColor: string;
    panelBg: string;
    onTileHover?: (tile: TreemapTile) => void;
    onTileLeave?: () => void;
    onTileClick?: (tile: TreemapTile) => void;
    /** Verildiğinde hover bilgisi kutucuk altında değil, imleç yakınında sabitlenir */
    floatingTooltip?: TreemapFloatingTooltipLabels | null;
    /** Sektör kutusu başlığı: `tile.sector` anahtarı sabit kalır, yalnızca etiket çevrilir */
    sectorDisplayName?: (sectorKey: string) => string;
};

const TOOLTIP_W = 200;
const TOOLTIP_H = 76;

function clampTooltipPosition(clientX: number, clientY: number): { left: number; top: number } {
    const pad = 10;
    const vw = typeof window !== 'undefined' ? window.innerWidth : 1200;
    const vh = typeof window !== 'undefined' ? window.innerHeight : 800;
    const left = Math.min(Math.max(pad, clientX + 12), vw - TOOLTIP_W - pad);
    const top = Math.min(Math.max(pad, clientY + 12), vh - TOOLTIP_H - pad);
    return { left, top };
}

export function MarketFinvizTreemap({
    tiles,
    borderColor,
    panelBg,
    onTileHover,
    onTileLeave,
    onTileClick,
    floatingTooltip,
    sectorDisplayName,
}: Props) {
    const wrapRef = useRef<HTMLDivElement>(null);
    const [size, setSize] = useState({ w: 300, h: 400 });
    const [floatTip, setFloatTip] = useState<{ tile: TreemapTile; left: number; top: number } | null>(null);
    const floatPosRafRef = useRef<number | null>(null);
    const floatPosPendingRef = useRef<{ clientX: number; clientY: number } | null>(null);

    const flushFloatPos = useCallback(() => {
        floatPosRafRef.current = null;
        const p = floatPosPendingRef.current;
        floatPosPendingRef.current = null;
        if (!p) return;
        const { left, top } = clampTooltipPosition(p.clientX, p.clientY);
        setFloatTip((prev) => (prev ? { tile: prev.tile, left, top } : null));
    }, []);

    const onLeafPointerMove = useCallback(
        (e: ReactMouseEvent) => {
            if (!floatingTooltip) return;
            floatPosPendingRef.current = { clientX: e.clientX, clientY: e.clientY };
            if (floatPosRafRef.current != null) return;
            floatPosRafRef.current = requestAnimationFrame(flushFloatPos);
        },
        [floatingTooltip, flushFloatPos],
    );

    useEffect(() => {
        return () => {
            if (floatPosRafRef.current != null) {
                cancelAnimationFrame(floatPosRafRef.current);
                floatPosRafRef.current = null;
            }
        };
    }, []);

    useLayoutEffect(() => {
        const el = wrapRef.current;
        if (!el) return;
        const measure = () => {
            const rectW = el.getBoundingClientRect().width || el.clientWidth;
            const edgePad =
                typeof window !== 'undefined' && window.innerWidth < 768 ? 48 : 32;
            const vw =
                typeof window !== 'undefined' ? Math.max(200, window.innerWidth - edgePad) : rectW;
            const w = Math.max(200, Math.floor(Math.min(rectW, vw)));
            const narrow = typeof window !== 'undefined' && window.innerWidth < 768;
            const h = narrow
                ? Math.max(180, Math.min(280, Math.round(w * 0.78)))
                : Math.max(320, Math.min(540, Math.round(w * 0.95)));
            setSize((prev) => (prev.w !== w || prev.h !== h ? { w, h } : prev));
        };
        measure();
        let resizeRaf = 0;
        const ro = new ResizeObserver(() => {
            if (resizeRaf) return;
            resizeRaf = requestAnimationFrame(() => {
                resizeRaf = 0;
                measure();
            });
        });
        ro.observe(el);
        return () => ro.disconnect();
    }, []);

    const root = useMemo(() => {
        if (!tiles.length) {
            return null;
        }
        const bySector = new Map<string, TreemapTile[]>();
        for (const t of tiles) {
            if (!bySector.has(t.sector)) bySector.set(t.sector, []);
            bySector.get(t.sector)!.push(t);
        }
        const children: HNode[] = [...bySector.entries()].map(([sectorKey, list]) => ({
            name: sectorDisplayName ? sectorDisplayName(sectorKey) : sectorKey,
            sectorKey,
            children: list.map((t) => ({
                name: t.symbol,
                sectorKey,
                value: Math.max(t.layoutWeight, 0.04),
                change: t.changePercent,
                symbol: t.symbol,
                industry: t.industry,
                assetClass: t.assetClass,
                mode: t.mode,
                changeHorizon: t.changeHorizon,
                weightMode: t.weightMode,
                marketCapSource: t.marketCapSource,
                marketCapAsOf: t.marketCapAsOf,
            })),
        }));

        const h = hierarchy<HNode>({ name: 'root', children })
            .sum((d) => (d.value != null ? d.value : 0))
            .sort((a, b) => (b.value ?? 0) - (a.value ?? 0));

        treemap<HNode>()
            .tile(treemapSquarify)
            .size([size.w, size.h])
            .paddingOuter(3)
            .paddingInner(1)
            .round(true)(h);

        return h;
    }, [tiles, size.w, size.h, sectorDisplayName]);

    if (!tiles.length) {
        return (
            <p style={{ margin: 0, fontSize: 13, color: '#94a3b8' }}>
                İsı haritası için veri yok.
            </p>
        );
    }

    if (!root) {
        return null;
    }

    const sectorNodes = (root.children ?? []) as HierarchyRectangularNode<HNode>[];
    const leafNodes = root.leaves() as HierarchyRectangularNode<HNode>[];
    const narrowViewport = typeof window !== 'undefined' && window.innerWidth < 768;
    const showPctMinArea = narrowViewport ? 260 : 420;
    const showSymMinArea = narrowViewport ? 100 : 180;

    return (
        <div className="terminal-treemap-scroll">
            <div className="terminal-treemap-scroll__inner">
                <div
                    ref={wrapRef}
                    className="terminal-treemap-host"
                    style={{
                        background: panelBg,
                        border: `1px solid ${borderColor}`,
                    }}
                >
                    <div
                        style={{
                            position: 'relative',
                            width: size.w,
                            maxWidth: '100%',
                            height: size.h,
                            margin: '0 auto',
                        }}
                    >
                {sectorNodes.map((node) => {
                    const sw = node.x1 - node.x0;
                    const sh = node.y1 - node.y0;
                    const sk = node.data.sectorKey ?? node.data.name;
                    return (
                        <div
                            key={`sector-bg-${sk}`}
                            style={{
                                position: 'absolute',
                                left: node.x0,
                                top: node.y0,
                                width: sw,
                                height: sh,
                                background: 'rgba(15, 23, 42, 0.35)',
                                border: '1px solid rgba(51, 65, 85, 0.6)',
                                boxSizing: 'border-box',
                                zIndex: 0,
                                pointerEvents: 'none',
                            }}
                        />
                    );
                })}
                {leafNodes.map((node) => {
                    const w = node.x1 - node.x0;
                    const h = node.y1 - node.y0;
                    const sym = node.data.symbol ?? node.data.name;
                    const ch = node.data.change ?? 0;
                    const industry = node.data.industry ?? '';
                    const mode = node.data.mode ?? '';
                    const horizon = node.data.changeHorizon ?? '';
                    const weightMode = node.data.weightMode ?? '';
                    const marketCapSource = node.data.marketCapSource ?? '';
                    const marketCapAsOf = node.data.marketCapAsOf ?? '';
                    const area = w * h;
                    const showPct = area > showPctMinArea;
                    const showSym = area > showSymMinArea;
                    const sk = node.data.sectorKey ?? node.parent?.data.sectorKey ?? 'OTHER';
                    const tile: TreemapTile = {
                        sector: sk,
                        industry: node.data.industry,
                        symbol: sym,
                        assetClass: node.data.assetClass ?? 'OTHER',
                        changePercent: ch,
                        layoutWeight: node.data.value ?? 1,
                        mode: node.data.mode,
                        changeHorizon: node.data.changeHorizon,
                        weightMode: node.data.weightMode,
                        marketCapSource: node.data.marketCapSource,
                        marketCapAsOf: node.data.marketCapAsOf,
                    };
                    const titleParts = [`${sym} · ${ch >= 0 ? '+' : ''}${ch.toFixed(2)}%`];
                    if (industry) titleParts.push(industry);
                    if (horizon) titleParts.push(`Horizon: ${horizon}`);
                    if (weightMode) titleParts.push(`Weight: ${weightMode}`);
                    if (marketCapSource) titleParts.push(`Cap Source: ${marketCapSource}`);
                    if (marketCapAsOf) titleParts.push(`Cap AsOf: ${new Date(marketCapAsOf).toLocaleString('tr-TR')}`);
                    if (mode) titleParts.push(`Mode: ${mode}`);
                    return (
                        <div
                            key={`leaf-${sym}-${node.x0}-${node.y0}`}
                            title={floatingTooltip ? undefined : titleParts.join(' | ')}
                            onMouseEnter={(e) => {
                                if (floatingTooltip) {
                                    const { left, top } = clampTooltipPosition(e.clientX, e.clientY);
                                    setFloatTip({ tile, left, top });
                                } else {
                                    onTileHover?.(tile);
                                }
                            }}
                            onMouseMove={floatingTooltip ? onLeafPointerMove : undefined}
                            onMouseLeave={() => {
                                if (floatingTooltip) {
                                    setFloatTip(null);
                                } else {
                                    onTileLeave?.();
                                }
                            }}
                            onClick={() => onTileClick?.(tile)}
                            style={{
                                position: 'absolute',
                                left: node.x0,
                                top: node.y0,
                                width: w,
                                height: h,
                                boxSizing: 'border-box',
                                border: '1px solid rgba(15, 23, 42, 0.92)',
                                background: cellBackground(ch),
                                display: 'flex',
                                flexDirection: 'column',
                                alignItems: 'center',
                                justifyContent: 'center',
                                padding: showSym ? 2 : 0,
                                zIndex: 1,
                                overflow: 'hidden',
                                cursor: onTileClick ? 'pointer' : 'default',
                            }}
                        >
                            {showSym ? (
                                <span
                                    style={{
                                        fontWeight: 700,
                                        fontSize: narrowViewport ? (w > 44 ? 10 : 8) : w > 56 ? 12 : 10,
                                        color: textColor(ch),
                                        letterSpacing: 0.02,
                                        textAlign: 'center',
                                        lineHeight: 1.1,
                                    }}
                                >
                                    {sym}
                                </span>
                            ) : null}
                            {showPct ? (
                                <span
                                    style={{
                                        fontSize: narrowViewport ? 8 : 10,
                                        fontWeight: 600,
                                        color: ch >= 0 ? '#bbf7d1' : '#fecaca',
                                        marginTop: 2,
                                    }}
                                >
                                    {ch >= 0 ? '+' : ''}
                                    {ch.toFixed(2)}%
                                </span>
                            ) : null}
                        </div>
                    );
                })}
                {sectorNodes.map((node) => {
                    const lw = node.x1 - node.x0;
                    const lh = node.y1 - node.y0;
                    if (lw < (narrowViewport ? 44 : 56) || lh < (narrowViewport ? 28 : 36)) return null;
                    const sk = node.data.sectorKey ?? node.data.name;
                    return (
                        <div
                            key={`sector-lbl-${sk}`}
                            style={{
                                position: 'absolute',
                                left: node.x0 + 4,
                                top: node.y0 + 3,
                                width: Math.max(0, lw - 8),
                                maxHeight: 22,
                                fontSize: narrowViewport ? 7 : lw < 140 ? 8 : 9,
                                fontWeight: 800,
                                letterSpacing: 0.04,
                                color: 'rgba(248, 250, 252, 0.92)',
                                textTransform: 'uppercase',
                                pointerEvents: 'none',
                                zIndex: 2,
                                textShadow: '0 1px 2px rgba(0,0,0,0.85)',
                                lineHeight: 1.05,
                                overflow: 'hidden',
                                whiteSpace: 'nowrap',
                                textOverflow: 'ellipsis',
                            }}
                        >
                            {node.data.name}
                        </div>
                    );
                })}
            </div>
            {floatTip && floatingTooltip ? (
                <div
                    role="tooltip"
                    style={{
                        position: 'fixed',
                        left: floatTip.left,
                        top: floatTip.top,
                        width: TOOLTIP_W,
                        zIndex: 420,
                        pointerEvents: 'none',
                        borderRadius: 8,
                        border: `1px solid ${borderColor}`,
                        background: panelBg,
                        boxShadow: '0 10px 32px rgba(2, 6, 23, 0.42)',
                        padding: '8px 10px',
                        fontSize: 11,
                        lineHeight: 1.38,
                    }}
                >
                    <div style={{ fontWeight: 700, marginBottom: 4, color: '#f8fafc' }}>
                        {floatTip.tile.symbol} · {floatTip.tile.assetClass}
                    </div>
                    <div style={{ color: floatingTooltip.mutedColor ?? 'rgba(148, 163, 184, 0.95)' }}>
                        {floatingTooltip.labelChange}: {floatTip.tile.changePercent >= 0 ? '+' : ''}
                        {floatTip.tile.changePercent.toFixed(2)}%
                    </div>
                    <div style={{ color: floatingTooltip.mutedColor ?? 'rgba(148, 163, 184, 0.95)' }}>
                        {floatingTooltip.labelSector}:{' '}
                        {sectorDisplayName ? sectorDisplayName(floatTip.tile.sector) : floatTip.tile.sector}
                    </div>
                </div>
            ) : null}
                </div>
            </div>
        </div>
    );
}
