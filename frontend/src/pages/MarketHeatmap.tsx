import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { financeClient } from '../api/client';
import { useTheme } from '../theme/ThemeContext';
import { useLanguage } from '../i18n/LanguageContext';
import type { MarketDashboard } from '../components/market/marketTypes';
import { MarketFinvizTreemap, type TreemapTile } from '../components/market/MarketFinvizTreemap';
import { approxPctByDays } from '../components/market/heatmapApproxPct';

function fmtPct(v: number): string {
    return `${v >= 0 ? '+' : ''}${v.toFixed(2)}%`;
}

function unwrapData<T>(payload: unknown): T {
    if (payload && typeof payload === 'object' && 'data' in (payload as object)) {
        return (payload as { data: T }).data;
    }
    return payload as T;
}

export function MarketHeatmap() {
    const navigate = useNavigate();
    const { tokens } = useTheme();
    const { t, lang } = useLanguage();
    const [hovered, setHovered] = useState<TreemapTile | null>(null);
    const [timeframeDays, setTimeframeDays] = useState<1 | 7 | 14>(14);
    const [sectorFilter, setSectorFilter] = useState<string>('ALL');

    const { data, isLoading, error } = useQuery({
        queryKey: ['market', 'dashboard'],
        queryFn: () => financeClient.get<MarketDashboard>('/api/market/dashboard').then((r) => unwrapData<MarketDashboard>(r.data)),
        refetchInterval: 60_000,
    });

    const sparklineMap = useMemo(() => {
        const m = new Map<string, number[]>();
        for (const s of data?.sparklines ?? []) {
            m.set(`${s.assetClass}|${s.symbol}`, s.closes ?? []);
        }
        return m;
    }, [data?.sparklines]);

    const displayTiles = useMemo(() => {
        const source = data?.heatmapTiles ?? [];
        const withTimeframe = source.map((t) => {
            const closes = sparklineMap.get(`${t.assetClass}|${t.symbol}`) ?? [];
            const approxPct = approxPctByDays(closes, timeframeDays);
            return {
                ...t,
                changePercent: approxPct ?? t.changePercent,
                changeHorizon: `${timeframeDays}D`,
            };
        });
        if (sectorFilter === 'ALL') return withTimeframe;
        return withTimeframe.filter((t) => t.sector === sectorFilter);
    }, [data?.heatmapTiles, sectorFilter, sparklineMap, timeframeDays]);

    const sectorOptions = useMemo(() => {
        const all = new Set<string>();
        for (const t of data?.heatmapTiles ?? []) all.add(t.sector);
        return ['ALL', ...Array.from(all).sort((a, b) => a.localeCompare(b))];
    }, [data?.heatmapTiles]);

    const sortedByAbsMove = useMemo(() => {
        const list = [...displayTiles];
        list.sort((a, b) => Math.abs(b.changePercent) - Math.abs(a.changePercent));
        return list.slice(0, 8);
    }, [displayTiles]);

    const toAdvancedType = (assetClass: string): 'FX' | 'CRYPTO' | 'METALS' | 'FUNDS' | 'EQUITY' => {
        switch (assetClass) {
            case 'FX':
                return 'FX';
            case 'CRYPTO':
                return 'CRYPTO';
            case 'METAL':
                return 'METALS';
            case 'FUND':
                return 'FUNDS';
            case 'STOCK':
                return 'EQUITY';
            default:
                return 'EQUITY';
        }
    };

    const pageStyle: React.CSSProperties = {
        padding: 24,
        background: tokens.bg,
        color: tokens.text,
        minHeight: '100%',
    };
    const cardStyle: React.CSSProperties = {
        padding: 16,
        borderRadius: 12,
        background: tokens.bgCard,
        border: `1px solid ${tokens.border}`,
    };

    const errMsg =
        error instanceof Error
            ? error.message
            : error != null
              ? String((error as { message?: string }).message ?? error)
              : undefined;

    return (
        <div style={pageStyle}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
                <div>
                    <h1 style={{ margin: 0, fontSize: '1.55rem', fontWeight: 700 }}>{t('market.detailedHeatmap', 'Detaylı Isı Haritası')}</h1>
                    <p style={{ margin: '6px 0 0', color: tokens.textMuted, fontSize: '0.9rem' }}>
                        {t('heatmap.subtitle', 'Sektör / sembol dağılımı ve hover ile ayrıntılar.')}
                    </p>
                </div>
                <button
                    type="button"
                    onClick={() => navigate('/market')}
                    style={{
                        padding: '8px 14px',
                        borderRadius: 10,
                        border: `1px solid ${tokens.border}`,
                        background: tokens.bgCard,
                        color: tokens.text,
                        cursor: 'pointer',
                    }}
                >
                    {t('heatmap.backToMarket', '← Piyasa sayfasına dön')}
                </button>
            </div>

            {isLoading ? (
                <p style={{ color: tokens.textMuted }}>{t('common.loading', 'Yükleniyor...')}</p>
            ) : errMsg ? (
                <p style={{ color: tokens.error }}>{t('news.errorPrefix', 'Hata')}: {errMsg}</p>
            ) : (
                <div
                    className="heatmap-detail-grid"
                    style={{
                        display: 'grid',
                        gridTemplateColumns: 'minmax(0, 1fr) 320px',
                        gap: 16,
                        alignItems: 'start',
                    }}
                >
                    <style>{`
                    @media (max-width: 1100px) {
                      .heatmap-detail-grid { grid-template-columns: 1fr !important; }
                    }
                    `}</style>

                    <div style={cardStyle}>
                        <div
                            style={{
                                display: 'flex',
                                flexWrap: 'wrap',
                                alignItems: 'center',
                                gap: 12,
                                marginBottom: 10,
                            }}
                        >
                            <label style={{ fontSize: 13, color: tokens.textMuted }}>
                                {t('heatmap.timeframe', 'Zaman dilimi')}:
                                <select
                                    value={timeframeDays}
                                    onChange={(e) => setTimeframeDays(Number(e.target.value) as 1 | 7 | 14)}
                                    style={{
                                        marginLeft: 8,
                                        padding: '5px 10px',
                                        borderRadius: 8,
                                        border: `1px solid ${tokens.border}`,
                                        background: tokens.bgCard,
                                        color: tokens.text,
                                    }}
                                >
                                    <option value={1}>1D</option>
                                    <option value={7}>7D</option>
                                    <option value={14}>14D</option>
                                </select>
                            </label>
                            <label style={{ fontSize: 13, color: tokens.textMuted }}>
                                {t('market.sector', 'Sektör')}:
                                <select
                                    value={sectorFilter}
                                    onChange={(e) => setSectorFilter(e.target.value)}
                                    style={{
                                        marginLeft: 8,
                                        padding: '5px 10px',
                                        borderRadius: 8,
                                        border: `1px solid ${tokens.border}`,
                                        background: tokens.bgCard,
                                        color: tokens.text,
                                        minWidth: 160,
                                    }}
                                >
                                    {sectorOptions.map((s) => (
                                        <option key={s} value={s}>
                                            {s === 'ALL' ? t('heatmap.allSectors', 'Tüm sektörler') : s}
                                        </option>
                                    ))}
                                </select>
                            </label>
                            <span style={{ fontSize: 12, color: tokens.textMuted }}>
                                {t('heatmap.clickHint', 'Kutuya tıklayınca seçili dönem ile gelişmiş grafikte açılır.')}
                            </span>
                        </div>
                        <div style={{ marginBottom: 10, color: tokens.textMuted, fontSize: 13 }}>
                            Equity: {data?.heatmapMeta?.equityMode ?? 'EQUITY_FINVIZ'} (
                            {data?.heatmapMeta?.equityChangeHorizon ?? '1D'} / {data?.heatmapMeta?.equityWeightMode ?? 'EQUAL'})
                            {' · '}
                            {t('heatmap.otherAssets', 'Diğer varlıklar')}: {data?.heatmapMeta?.multiAssetMode ?? 'MULTI_ASSET'} (
                            {t('heatmap.uiHorizon', 'ekran')}: {timeframeDays}D /{' '}
                            {data?.heatmapMeta?.multiAssetWeightMode ?? 'PRICE_SQRT'})
                        </div>
                        <MarketFinvizTreemap
                            tiles={displayTiles}
                            borderColor={tokens.border}
                            panelBg={tokens.bg}
                            onTileHover={setHovered}
                            onTileLeave={() => setHovered(null)}
                            onTileClick={(tile) => {
                                const type = toAdvancedType(tile.assetClass);
                                navigate(
                                    `/market/advanced?type=${type}&symbol=${encodeURIComponent(tile.symbol)}&days=${timeframeDays}`,
                                );
                            }}
                        />
                    </div>

                    <div style={{ ...cardStyle, position: 'sticky', top: 16 }}>
                        <h3 style={{ marginTop: 0, marginBottom: 10, fontSize: '1rem' }}>{t('heatmap.hoverDetail', 'Hover detayı')}</h3>
                        {hovered ? (
                            <div style={{ display: 'grid', gap: 6, fontSize: '0.9rem' }}>
                                <div><strong>{hovered.symbol}</strong> ({hovered.assetClass})</div>
                                <div>{t('market.sector', 'Sektör')}: {hovered.sector}</div>
                                {hovered.industry ? <div>Industry: {hovered.industry}</div> : null}
                                <div>{t('market.change', 'Değişim')} ({timeframeDays}G): {fmtPct(hovered.changePercent)}</div>
                                {hovered.changeHorizon ? <div>Horizon: {hovered.changeHorizon}</div> : null}
                                {hovered.weightMode ? <div>Weight: {hovered.weightMode}</div> : null}
                                {hovered.marketCapSource ? <div>Cap Source: {hovered.marketCapSource}</div> : null}
                                {hovered.marketCapAsOf ? (
                                    <div>Cap AsOf: {new Date(hovered.marketCapAsOf).toLocaleString(lang === 'en' ? 'en-US' : 'tr-TR')}</div>
                                ) : null}
                                {hovered.mode ? <div>Mode: {hovered.mode}</div> : null}
                            </div>
                        ) : (
                            <p style={{ margin: 0, color: tokens.textMuted, fontSize: 13 }}>
                                {t('heatmap.hoverHint', 'Detayları görmek için bir kutunun üstüne gel veya tıkla.')}
                            </p>
                        )}

                        <hr style={{ borderColor: tokens.border, opacity: 0.5, margin: '14px 0' }} />
                        <h4 style={{ margin: 0, marginBottom: 8, fontSize: '0.95rem' }}>
                            En hareketli 8 sembol ({timeframeDays}G)
                        </h4>
                        <div style={{ display: 'grid', gap: 8 }}>
                            {sortedByAbsMove.map((t) => (
                                <div
                                    key={`${t.assetClass}-${t.symbol}`}
                                    style={{
                                        display: 'flex',
                                        justifyContent: 'space-between',
                                        fontSize: 13,
                                        color: tokens.textMuted,
                                    }}
                                >
                                    <span>{t.symbol}</span>
                                    <span style={{ color: t.changePercent >= 0 ? '#22c55e' : '#f87171', fontWeight: 700 }}>
                                        {fmtPct(t.changePercent)}
                                    </span>
                                </div>
                            ))}
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}

