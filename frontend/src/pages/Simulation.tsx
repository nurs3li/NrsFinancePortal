import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { jsPDF } from 'jspdf';
import autoTable from 'jspdf-autotable';
import { registerSimulationPdfFont, SIMULATION_PDF_FONT_FAMILY } from '../utils/simulationPdfFont';
import {
    Area,
    CartesianGrid,
    ComposedChart,
    Legend,
    ResponsiveContainer,
    Tooltip,
    XAxis,
    YAxis,
} from 'recharts';
import { financeClient } from '../api/client';
import { useTheme } from '../theme/ThemeContext';
import { Trash2 } from 'lucide-react';
import './TerminalPages.css';
import './Simulation.css';
import { fetchSimulationSymbolsByType } from '../services/marketDataService';
import { useLanguage } from '../i18n/LanguageContext';

type AssetType = 'CRYPTO' | 'FX' | 'FUND' | 'METAL' | 'STOCK';

type SimulationPerformancePoint = {
    date: string;
    priceTry: number;
    cumulativeReturnPct: number;
};

type SimulationResponse = {
    type: string;
    symbol: string;
    buyDate: string | null;
    inputAmountTry: number;
    historicalPriceTry: number;
    currentPriceTry: number;
    unitsBought: number;
    currentValueTry: number;
    pnlTry: number;
    pnlPct: number;
    buyPriceSource: 'SYSTEM_HISTORY' | 'USER_INPUT' | string;
    historicalPriceDate: string | null;
    qualityFlag?: 'EXACT' | 'PREVIOUS_DAY' | 'FALLBACK' | 'MISSING' | string;
    performanceSeries: SimulationPerformancePoint[];
    message: string;
};

type SimulationResultItem = {
    id: string;
    assetName: string;
    assetType: AssetType;
    initialAmount: number;
    buyPrice: number;
    buyDate: string;
    currentPrice: number;
    pnl: number;
    pnlPct: number;
    currentValue: number;
    buyPriceSource: string;
    historicalPriceDate: string;
    qualityFlag: string;
    series: SimulationPerformancePoint[];
    visible: boolean;
    message: string;
};

type SortMode = 'LATEST' | 'PNL_DESC' | 'PNL_ASC' | 'PNL_PCT_DESC' | 'PNL_PCT_ASC' | 'NAME_ASC';
type BuyPriceMode = 'SYSTEM' | 'MANUAL';

function unwrapData<T>(res: any): T {
    return (res?.data?.data ?? res?.data) as T;
}

/** Premium çizgi paleti: altın, elektrik mavisi, neon yeşil, mor (4+ seri döngü) */
const CHART_PALETTE = ['#FFD700', '#00D4FF', '#39FF14', '#BC13FE'] as const;

function assetTypeOptionIcon(t: AssetType): string {
    switch (t) {
        case 'CRYPTO':
            return '₿';
        case 'FX':
            return '💱';
        case 'METAL':
            return '◆';
        case 'FUND':
            return '▣';
        case 'STOCK':
            return '📈';
        default:
            return '•';
    }
}

function useAnimatedNumber(target: number) {
    const [display, setDisplay] = useState(0);
    const displayRef = useRef(0);

    useEffect(() => {
        const from = displayRef.current;
        let raf = 0;
        const t0 = performance.now();
        const dur = 680;
        const step = (now: number) => {
            const p = Math.min(1, (now - t0) / dur);
            const eased = 1 - (1 - p) ** 3;
            const next = from + (target - from) * eased;
            displayRef.current = next;
            setDisplay(next);
            if (p < 1) raf = requestAnimationFrame(step);
        };
        raf = requestAnimationFrame(step);
        return () => cancelAnimationFrame(raf);
    }, [target]);

    return display;
}

type SimTooltipPayload = { name?: string; value?: number; color?: string };

function SimPerformanceTooltip({
    active,
    label,
    payload,
}: {
    active?: boolean;
    label?: string;
    payload?: SimTooltipPayload[];
}) {
    if (!active || !payload?.length) return null;
    return (
        <div className="sim-chart-tooltip">
            <div className="sim-chart-tooltip-title">{label}</div>
            {payload.map((e: SimTooltipPayload, i: number) => (
                <div key={i} className="sim-chart-tooltip-row">
                    <span style={{ color: e.color }}>{e.name}</span>
                    <span>
                        {e.value == null
                            ? '—'
                            : `${Number(e.value).toLocaleString('tr-TR', { maximumFractionDigits: 2 })}%`}
                    </span>
                </div>
            ))}
        </div>
    );
}

function SimAnimatedTry({ value, bold }: { value: number; bold?: boolean }) {
    const v = useAnimatedNumber(value);
    return (
        <span className="tp-mono" style={{ fontWeight: bold ? 700 : 500 }}>
            ₺{v.toLocaleString('tr-TR', { maximumFractionDigits: 2 })}
        </span>
    );
}

function SimAnimatedPnl({ pnl, pnlPct }: { pnl: number; pnlPct: number }) {
    const ap = useAnimatedNumber(pnl);
    const ac = useAnimatedNumber(pnlPct);
    const pos = pnl >= 0;
    return (
        <span className={`tp-mono sim-pnl-cell ${pos ? 'sim-pnl-pos' : 'sim-pnl-neg'}`}>
            {pos ? '+' : ''}₺{ap.toLocaleString('tr-TR', { maximumFractionDigits: 2 })} (
            {ac.toLocaleString('tr-TR', { maximumFractionDigits: 2 })}%)
        </span>
    );
}

function sourceLabel(source: string): string {
    const s = String(source ?? '').toUpperCase();
    if (s === 'SYSTEM_HISTORY') return 'Sistem Gecmis Fiyati';
    if (s === 'SYSTEM_LATEST_FALLBACK') return 'Sistem Son Fiyat Fallback';
    if (s === 'USER_INPUT') return 'Kullanici Manuel Fiyati';
    return source || 'Sistem';
}

function qualityLabel(quality: string): string {
    const q = String(quality ?? '').toUpperCase();
    if (q === 'EXACT') return 'EXACT (Secilen gun)';
    if (q === 'PREVIOUS_DAY') return 'PREVIOUS_DAY (Onceki uygun gun)';
    if (q === 'FALLBACK') return 'FALLBACK (En erken/mevcut nokta)';
    return quality || 'BILINMIYOR';
}

function formatExportDecimal(n: number, decimals: number): string {
    const f = 10 ** decimals;
    const v = Math.round(n * f) / f;
    return String(v);
}

/** CSV/PDF satırı: API/DB alan adları veya ham enum yerine kullanıcı dilinde etiketler */
function buildSimulationExportRow(
    r: SimulationResultItem,
    fmt: {
        assetType: (a: AssetType) => string;
        quality: (q: string) => string;
        source: (s: string) => string;
    }
): string[] {
    return [
        fmt.assetType(r.assetType),
        r.assetName,
        r.buyDate,
        formatExportDecimal(r.initialAmount, 2),
        formatExportDecimal(r.buyPrice, 4),
        formatExportDecimal(r.currentPrice, 4),
        formatExportDecimal(r.currentValue, 2),
        formatExportDecimal(r.pnl, 2),
        formatExportDecimal(r.pnlPct, 2),
        fmt.source(r.buyPriceSource),
        r.historicalPriceDate,
        fmt.quality(r.qualityFlag),
    ];
}

const SIMULATION_STORAGE_KEY = 'nrs-finance-portal-simulation-list-v1';

type OverviewPriceRow = { buyPrice?: number; sellPrice?: number };

type OverviewLite = {
    doviz?: Record<string, OverviewPriceRow>;
    metals?: Record<string, OverviewPriceRow>;
    crypto?: Record<string, OverviewPriceRow>;
    funds?: Record<string, OverviewPriceRow>;
    stocks?: Record<string, OverviewPriceRow>;
};

function midFromRow(row?: OverviewPriceRow | null): number | null {
    if (!row) return null;
    const b = Number(row.buyPrice ?? 0);
    const s = Number(row.sellPrice ?? 0);
    if (b > 0 && s > 0) return (b + s) / 2;
    if (b > 0) return b;
    if (s > 0) return s;
    return null;
}

/** Market overview ile SimulationService.getPriceTry mantığına yakın TRY birim fiyat */
function liveTryFromOverview(o: OverviewLite | null | undefined, assetType: AssetType, symbol: string): number | null {
    if (!o) return null;
    const sym = String(symbol ?? '').toUpperCase();
    const usdTry = midFromRow(o.doviz?.['USDTRY']);
    const usdMul = (usd: number | null) => {
        if (usd == null || usd <= 0) return null;
        if (!usdTry || usdTry <= 0) return null;
        return usd * usdTry;
    };
    switch (assetType) {
        case 'FX':
            return midFromRow(o.doviz?.[sym]);
        case 'METAL':
            return midFromRow(o.metals?.[sym]);
        case 'CRYPTO':
            return usdMul(midFromRow(o.crypto?.[sym]));
        case 'FUND':
            return usdMul(midFromRow(o.funds?.[sym]));
        case 'STOCK':
            return usdMul(midFromRow(o.stocks?.[sym]));
        default:
            return null;
    }
}

function mergeLiveIntoSeries(
    series: SimulationPerformancePoint[],
    buyPricePerUnit: number,
    liveTry: number
): SimulationPerformancePoint[] {
    if (buyPricePerUnit <= 0 || liveTry <= 0) return series;
    const today = new Date().toISOString().slice(0, 10);
    const cumulativeReturnPct = ((liveTry - buyPricePerUnit) / buyPricePerUnit) * 100;
    const sorted = [...series].sort((a, b) => a.date.localeCompare(b.date));
    const last = sorted[sorted.length - 1];
    if (last?.date === today) {
        sorted[sorted.length - 1] = { date: today, priceTry: liveTry, cumulativeReturnPct };
        return sorted;
    }
    sorted.push({ date: today, priceTry: liveTry, cumulativeReturnPct });
    return sorted;
}

export function Simulation() {
    const { tokens } = useTheme();
    const { t, lang } = useLanguage();
    const [type, setType] = useState<AssetType>('CRYPTO');
    const [symbol, setSymbol] = useState('BTCUSDT');
    const [amount, setAmount] = useState('5000');
    const [buyDate, setBuyDate] = useState(new Date().toISOString().slice(0, 10));
    const [buyPriceMode, setBuyPriceMode] = useState<BuyPriceMode>('SYSTEM');
    const [manualBuyPrice, setManualBuyPrice] = useState('');

    const [symbolOptions, setSymbolOptions] = useState<string[]>([]);
    const [overviewLoading, setOverviewLoading] = useState(true);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [simulationResults, setSimulationResults] = useState<SimulationResultItem[]>([]);
    const [sortMode, setSortMode] = useState<SortMode>('LATEST');
    const [listHydrated, setListHydrated] = useState(false);

    useEffect(() => {
        try {
            const raw = localStorage.getItem(SIMULATION_STORAGE_KEY);
            if (raw) {
                const parsed = JSON.parse(raw) as SimulationResultItem[];
                if (Array.isArray(parsed)) setSimulationResults(parsed);
            }
        } catch {
            /* ignore */
        }
        setListHydrated(true);
    }, []);

    useEffect(() => {
        if (!listHydrated) return;
        try {
            localStorage.setItem(SIMULATION_STORAGE_KEY, JSON.stringify(simulationResults));
        } catch {
            /* ignore */
        }
    }, [simulationResults, listHydrated]);

    useEffect(() => {
        if (!listHydrated || simulationResults.length === 0) return;

        const tick = async () => {
            try {
                const res = await financeClient.get('/api/market/overview');
                const overview = unwrapData<OverviewLite>(res);
                setSimulationResults((prev) =>
                    prev.map((r) => {
                        const live = liveTryFromOverview(overview, r.assetType, r.assetName);
                        if (live == null || live <= 0 || r.buyPrice <= 0) return r;
                        const units = r.initialAmount / r.buyPrice;
                        const currentValue = units * live;
                        const pnl = currentValue - r.initialAmount;
                        const pnlPct = r.initialAmount > 0 ? (pnl / r.initialAmount) * 100 : 0;
                        const series = mergeLiveIntoSeries(r.series, r.buyPrice, live);
                        return {
                            ...r,
                            currentPrice: live,
                            currentValue,
                            pnl,
                            pnlPct,
                            series,
                        };
                    })
                );
            } catch {
                /* overview isteği başarısız — önceki seri korunur */
            }
        };

        const id = window.setInterval(tick, 45_000);
        void tick();
        return () => window.clearInterval(id);
    }, [listHydrated, simulationResults.length]);

    useEffect(() => {
        setOverviewLoading(true);
        fetchSimulationSymbolsByType(type)
            .then((rows) => setSymbolOptions(rows))
            .catch(() => setSymbolOptions([]))
            .finally(() => setOverviewLoading(false));
    }, [type]);

    useEffect(() => {
        if (symbolOptions.length > 0 && !symbolOptions.includes(symbol)) {
            setSymbol(symbolOptions[0]);
        } else if (symbolOptions.length === 0) {
            setSymbol('');
        }
    }, [symbolOptions, symbol]);

    const calculateSimulationFromService = async (): Promise<SimulationResultItem> => {
        const parsedAmount = Number(amount);
        if (!parsedAmount || parsedAmount <= 0) {
            throw new Error(t('wallet.amountPositive', 'Tutar sıfırdan büyük olmalı.'));
        }
        if (!symbol.trim()) {
            throw new Error(t('simulation.selectSymbol', 'Lütfen bir sembol seçin.'));
        }
        if (!buyDate) {
            throw new Error(t('simulation.enterBuyDate', 'Lütfen alım tarihi girin.'));
        }

        const parsedManualBuyPrice = Number(manualBuyPrice);
        if (buyPriceMode === 'MANUAL' && (!parsedManualBuyPrice || parsedManualBuyPrice <= 0)) {
            throw new Error(t('simulation.manualPricePositive', 'Manuel alış fiyatı sıfırdan büyük olmalı.'));
        }

        const res = await financeClient.get('/api/simulation', {
            params: {
                type,
                symbol: symbol.trim().toUpperCase(),
                amount: parsedAmount,
                date: buyDate,
                buyPrice: buyPriceMode === 'MANUAL' ? parsedManualBuyPrice : undefined,
            },
        });
        const dto = unwrapData<SimulationResponse>(res);
        return {
            id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
            assetName: dto.symbol,
            assetType: dto.type as AssetType,
            initialAmount: Number(dto.inputAmountTry ?? 0),
            buyPrice: Number(dto.historicalPriceTry ?? 0),
            buyDate: dto.buyDate ?? buyDate,
            currentPrice: Number(dto.currentPriceTry ?? 0),
            pnl: Number(dto.pnlTry ?? 0),
            pnlPct: Number(dto.pnlPct ?? 0),
            currentValue: Number(dto.currentValueTry ?? 0),
            buyPriceSource: dto.buyPriceSource ?? 'SYSTEM_HISTORY',
            historicalPriceDate: dto.historicalPriceDate ?? buyDate,
            qualityFlag: dto.qualityFlag ?? 'EXACT',
            series: (dto.performanceSeries ?? []).map((p) => ({
                date: p.date,
                priceTry: Number(p.priceTry ?? 0),
                cumulativeReturnPct: Number(p.cumulativeReturnPct ?? 0),
            })),
            visible: true,
            message: dto.message ?? '',
        };
    };

    const addSimulationResult = async (e: React.FormEvent) => {
        e.preventDefault();
        setError(null);
        try {
            setLoading(true);
            const next = await calculateSimulationFromService();
            setSimulationResults((prev) => [next, ...prev]);
        } catch (err: any) {
            const msg =
                err?.response?.data?.errors?.error ??
                err?.response?.data?.message ??
                err?.message ??
                t('simulation.error', 'Simülasyon hatası');
            setError(msg);
        } finally {
            setLoading(false);
        }
    };

    const visibleResults = useMemo(
        () => simulationResults.filter((r) => r.visible),
        [simulationResults]
    );

    /** Ortak zaman ekseninde her serinin son bilinen kümülatif %-ini taşıyarak çizgilerin kopmamasını sağla */
    const chartData = useMemo(() => {
        if (!visibleResults.length) return [];

        const seriesKeys = visibleResults.map(
            (res) => `${res.assetType}-${res.assetName}-${res.id.slice(-4)}`,
        );
        const dateSet = new Set<string>();
        visibleResults.forEach((res) => {
            res.series.forEach((p) => dateSet.add(p.date));
        });
        const sortedDates = [...dateSet].sort((a, b) => a.localeCompare(b));

        const lastPct: Record<string, number> = {};
        const rows: Array<Record<string, number | string>> = [];

        for (const date of sortedDates) {
            const row: Record<string, number | string> = { date };
            visibleResults.forEach((res, i) => {
                const key = seriesKeys[i];
                const pt = res.series.find((p) => p.date === date);
                if (pt != null) {
                    lastPct[key] = pt.cumulativeReturnPct;
                }
                if (key in lastPct) {
                    row[key] = lastPct[key];
                }
            });
            rows.push(row);
        }
        return rows;
    }, [visibleResults]);

    const displayedResults = useMemo(() => {
        const arr = [...simulationResults];
        switch (sortMode) {
            case 'PNL_DESC':
                return arr.sort((a, b) => b.pnl - a.pnl);
            case 'PNL_ASC':
                return arr.sort((a, b) => a.pnl - b.pnl);
            case 'PNL_PCT_DESC':
                return arr.sort((a, b) => b.pnlPct - a.pnlPct);
            case 'PNL_PCT_ASC':
                return arr.sort((a, b) => a.pnlPct - b.pnlPct);
            case 'NAME_ASC':
                return arr.sort((a, b) => a.assetName.localeCompare(b.assetName, 'tr-TR'));
            case 'LATEST':
            default:
                return arr;
        }
    }, [simulationResults, sortMode]);

    const setAllVisible = (visible: boolean) => {
        setSimulationResults((prev) => prev.map((x) => ({ ...x, visible })));
    };

    const exportHeaders = useMemo(
        () => [
            t('simulation.exportColAssetType', 'Varlık türü'),
            t('simulation.exportColSymbol', 'Sembol'),
            t('simulation.exportColBuyDate', 'Alım tarihi'),
            t('simulation.exportColInitialTry', 'Başlangıç tutarı (TRY)'),
            t('simulation.exportColBuyUnitTry', 'Alış fiyatı (TRY / birim)'),
            t('simulation.exportColCurrentUnitTry', 'Güncel fiyat (TRY / birim)'),
            t('simulation.exportColValueTry', 'Güncel değer (TRY)'),
            t('simulation.exportColPnlTry', 'Kâr/zarar (TRY)'),
            t('simulation.exportColPnlPct', 'Getiri (%)'),
            t('simulation.exportColPriceSource', 'Fiyat kaynağı'),
            t('simulation.exportColRefDate', 'Referans tarihi'),
            t('simulation.exportColQuality', 'Veri kalitesi'),
        ],
        [t]
    );

    const exportCellFormatters = useMemo(
        () => ({
            assetType(at: AssetType) {
                switch (at) {
                    case 'CRYPTO':
                        return t('category.crypto', 'Kripto');
                    case 'FX':
                        return t('category.fx', 'Döviz');
                    case 'METAL':
                        return t('category.metals', 'Altın');
                    case 'FUND':
                        return t('category.funds', 'Fonlar');
                    case 'STOCK':
                        return t('category.equity', 'Hisse');
                    default:
                        return at;
                }
            },
            quality(q: string) {
                const u = String(q ?? '').toUpperCase();
                if (u === 'EXACT') return t('simulation.exportQualityExact', 'Seçilen güne tam veri');
                if (u === 'PREVIOUS_DAY') return t('simulation.exportQualityPrevious', 'Önceki işlem günü');
                if (u === 'FALLBACK') return t('simulation.exportQualityFallback', 'En yakın geçerli tarih');
                return t('simulation.exportQualityUnknown', 'Belirtilmedi');
            },
            source(s: string) {
                const u = String(s ?? '').toUpperCase();
                if (u === 'SYSTEM_HISTORY') return t('simulation.exportSourceHistory', 'Sistem geçmiş fiyatı');
                if (u === 'SYSTEM_LATEST_FALLBACK')
                    return t('simulation.exportSourceLatestFallback', 'Sistem son fiyat (yedek)');
                if (u === 'USER_INPUT') return t('simulation.exportSourceManual', 'Kullanıcı girişi');
                return s ? t('simulation.exportSourceOther', s) : t('simulation.exportSourceUnknown', 'Bilinmiyor');
            },
        }),
        [t]
    );

    const exportCsv = useCallback(() => {
        if (displayedResults.length === 0) return;
        const esc = (v: string) => `"${v.replaceAll('"', '""')}"`;
        const lines = displayedResults.map((r) =>
            buildSimulationExportRow(r, exportCellFormatters).map(esc).join(',')
        );
        const csv = '\uFEFF' + [exportHeaders.join(','), ...lines].join('\n');
        const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `simulation-ozet-${new Date().toISOString().slice(0, 19).replaceAll(':', '-')}.csv`;
        a.click();
        URL.revokeObjectURL(url);
    }, [displayedResults, exportHeaders, exportCellFormatters]);

    const exportPdf = useCallback(async () => {
        if (displayedResults.length === 0) return;
        const doc = new jsPDF({ orientation: 'landscape', unit: 'mm', format: 'a4' });
        try {
            await registerSimulationPdfFont(doc);
        } catch {
            window.alert(
                t(
                    'simulation.exportPdfFontError',
                    'PDF için Türkçe font yüklenemedi. fonts/NotoSans-Regular.ttf dosyasının public klasöründe olduğundan emin olun.'
                )
            );
            return;
        }
        const title = t('simulation.exportPdfTitle', 'NRS Finance Portal — Simülasyon özeti');
        const genLabel = t('simulation.exportPdfGeneratedLabel', 'Oluşturulma');
        const localeStr = lang === 'en' ? 'en-GB' : 'tr-TR';
        doc.setFont(SIMULATION_PDF_FONT_FAMILY, 'normal');
        doc.setFontSize(11);
        doc.setTextColor(25, 25, 30);
        doc.text(title, 14, 12);
        doc.setFontSize(8);
        doc.setTextColor(60, 60, 60);
        doc.text(`${genLabel}: ${new Date().toLocaleString(localeStr)}`, 14, 17);
        const body = displayedResults.map((r) => buildSimulationExportRow(r, exportCellFormatters));
        autoTable(doc, {
            startY: 22,
            head: [exportHeaders],
            body,
            styles: {
                font: SIMULATION_PDF_FONT_FAMILY,
                fontStyle: 'normal',
                fontSize: 7,
                cellPadding: 1.2,
                textColor: [25, 25, 30],
            },
            headStyles: {
                font: SIMULATION_PDF_FONT_FAMILY,
                fontStyle: 'normal',
                fillColor: [16, 22, 35],
                textColor: [235, 238, 245],
            },
            margin: { left: 10, right: 10 },
        });
        const withTable = doc as jsPDF & { lastAutoTable?: { finalY: number } };
        const footY = (withTable.lastAutoTable?.finalY ?? 180) + 8;
        doc.setFont(SIMULATION_PDF_FONT_FAMILY, 'normal');
        doc.setFontSize(7);
        doc.setTextColor(90, 90, 90);
        doc.text(
            t(
                'simulation.exportPdfFooter',
                'Internal IDs are not exported. Personal use only.'
            ),
            14,
            footY,
            { maxWidth: 275 }
        );
        doc.save(`simulation-ozet-${new Date().toISOString().slice(0, 19).replaceAll(':', '-')}.pdf`);
    }, [displayedResults, exportHeaders, exportCellFormatters, lang, t]);

    const fmtMoney = (v: number) => `₺${Number(v).toLocaleString('tr-TR', { maximumFractionDigits: 2 })}`;

    return (
        <div
            style={
                {
                    padding: 24,
                    background: tokens.bg,
                    minHeight: '100%',
                    '--tp-bg': '#0a192f',
                    '--tp-card': tokens.bgCard,
                    '--tp-border': tokens.border,
                    '--tp-text': tokens.text,
                    '--tp-muted': tokens.textMuted,
                    '--tp-success': '#22c55e',
                    '--tp-danger': '#ef4444',
                } as React.CSSProperties
            }
            className="terminal-pages-root sim-page"
        >
            <h1 style={{ fontSize: '1.75rem', fontWeight: 700, marginBottom: 6 }}>{t('simulation.title', 'Portföy Analiz Aracı')}</h1>
            <p className="sim-lead" style={{ fontSize: '0.875rem', marginBottom: 16 }}>
                Çoklu simülasyon ekle, varlıkları karşılaştır, kümülatif getiri eğrilerini aynı grafikte takip et.
            </p>
            <div className="card-premium" style={{ marginBottom: 12, fontSize: '0.8125rem', padding: 14, color: 'rgba(255,255,255,0.72)' }}>
                Simülasyon hesapları TRY bazında yapılır. USD bazlı varlıklarda geçmiş fiyatlar simülasyon sırasında USDTRY ile normalize edilir.
            </div>

            <div
                className="card-premium card-premium--static tp-card"
                style={{
                    marginBottom: 16,
                    position: 'sticky',
                    top: 12,
                    zIndex: 3,
                    padding: 16,
                }}
            >
                <form
                    onSubmit={addSimulationResult}
                    style={{
                        display: 'grid',
                        gridTemplateColumns: 'repeat(auto-fit, minmax(190px, 1fr))',
                        gap: 12,
                        alignItems: 'end',
                    }}
                >
                    <label style={{ fontSize: '0.875rem', color: tokens.text }}>
                        {t('simulation.assetType', 'Varlık Türü')}
                        <select
                            value={type}
                            onChange={(e) => setType(e.target.value as AssetType)}
                            className="sim-premium-select"
                            style={{ marginTop: 6 }}
                        >
                            <option value="CRYPTO">{assetTypeOptionIcon('CRYPTO')} CRYPTO</option>
                            <option value="FX">{assetTypeOptionIcon('FX')} FX</option>
                            <option value="METAL">{assetTypeOptionIcon('METAL')} METAL</option>
                            <option value="FUND">{assetTypeOptionIcon('FUND')} FUND</option>
                            <option value="STOCK">{assetTypeOptionIcon('STOCK')} STOCK</option>
                        </select>
                    </label>

                    <label style={{ fontSize: '0.875rem', color: tokens.text }}>
                        Sembol
                        {overviewLoading ? (
                            <div className="sim-premium-input" style={{ marginTop: 6, color: tokens.textMuted }}>
                                {t('common.loading', 'Yükleniyor...')}
                            </div>
                        ) : symbolOptions.length > 0 ? (
                            <select
                                value={symbol}
                                onChange={(e) => setSymbol(e.target.value)}
                                className="sim-premium-select"
                                style={{ marginTop: 6 }}
                            >
                                {symbolOptions.map((s) => (
                                    <option key={s} value={s}>
                                        {assetTypeOptionIcon(type)} {s}
                                    </option>
                                ))}
                            </select>
                        ) : (
                            <div className="sim-premium-input" style={{ marginTop: 6, color: tokens.textMuted }}>
                                {t('simulation.noSymbolForType', 'Bu varlık türü için kayıtlı sembol yok.')}
                            </div>
                        )}
                    </label>

                    <label style={{ fontSize: '0.875rem', color: tokens.text }}>
                        {t('simulation.initialAmountTry', 'Başlangıç Tutarı (TRY)')}
                        <input
                            type="number"
                            step="0.01"
                            value={amount}
                            onChange={(e) => setAmount(e.target.value)}
                            className="sim-premium-input"
                            style={{ marginTop: 6 }}
                        />
                    </label>

                    <label style={{ fontSize: '0.875rem', color: tokens.text }}>
                        {t('simulation.buyDate', 'Alım Tarihi')}
                        <input
                            type="date"
                            value={buyDate}
                            onChange={(e) => setBuyDate(e.target.value)}
                            className="sim-premium-input"
                            style={{ marginTop: 6 }}
                        />
                    </label>

                    <label style={{ fontSize: '0.875rem', color: tokens.text }}>
                        Alış Fiyat Kaynağı
                        <select
                            value={buyPriceMode}
                            onChange={(e) => setBuyPriceMode(e.target.value as BuyPriceMode)}
                            className="sim-premium-select"
                            style={{ marginTop: 6 }}
                        >
                            <option value="SYSTEM">⚙ Sistem Geçmiş Fiyatı</option>
                            <option value="MANUAL">✎ Kullanıcı Manuel Fiyatı</option>
                        </select>
                    </label>

                    {buyPriceMode === 'MANUAL' ? (
                        <label style={{ fontSize: '0.875rem', color: tokens.text }}>
                            Manuel Alış Fiyatı (TRY / birim)
                            <input
                                type="number"
                                step="0.00000001"
                                value={manualBuyPrice}
                                onChange={(e) => setManualBuyPrice(e.target.value)}
                                className="sim-premium-input"
                                style={{ marginTop: 6 }}
                                placeholder="Örn: 1250.75"
                            />
                        </label>
                    ) : null}

                    <button type="submit" disabled={loading} className="sim-submit-btn">
                        {loading ? t('simulation.adding', 'Ekleniyor...') : t('simulation.simulateAndAdd', 'Simüle Et ve Listeye Ekle')}
                    </button>
                </form>
            </div>

            {error && (
                <div
                    className="card-premium"
                    style={{
                        marginBottom: 16,
                        padding: 14,
                        borderColor: 'rgba(239, 68, 68, 0.45)',
                        color: '#fecaca',
                    }}
                >
                    Hata: {error}
                </div>
            )}

            <div className="card-premium tp-card" style={{ marginBottom: 16, padding: 16 }}>
                <h2 style={{ marginTop: 0, marginBottom: 10, fontSize: '1rem' }}>{t('simulation.performanceChart', 'Karşılaştırmalı Performans Grafiği')}</h2>
                <div className="sim-lead" style={{ fontSize: '0.8125rem', marginBottom: 12 }}>
                    Kümülatif getiri (%) — premium palet ve alan dolgusu
                </div>
                {visibleResults.length === 0 || chartData.length === 0 ? (
                    <p className="sim-lead" style={{ margin: 0 }}>
                        Grafikte göstermek için en az bir simülasyon ekleyip görünür yap.
                    </p>
                ) : (
                    <div className="sim-chart-surface" style={{ width: '100%', height: 380 }}>
                        <ResponsiveContainer width="100%" height="100%">
                            <ComposedChart data={chartData} margin={{ top: 10, right: 16, left: 0, bottom: 8 }}>
                                <defs>
                                    {visibleResults.map((res, i) => {
                                        const c = CHART_PALETTE[i % CHART_PALETTE.length];
                                        const gid = `simFill-${res.id.replace(/[^a-zA-Z0-9_-]/g, '')}`;
                                        return (
                                            <linearGradient key={res.id} id={gid} x1="0" y1="0" x2="0" y2="1">
                                                <stop offset="0%" stopColor={c} stopOpacity={0.35} />
                                                <stop offset="100%" stopColor={c} stopOpacity={0} />
                                            </linearGradient>
                                        );
                                    })}
                                </defs>
                                <CartesianGrid strokeDasharray="3 3" stroke={tokens.border} opacity={0.35} />
                                <XAxis dataKey="date" tick={{ fill: tokens.textMuted, fontSize: 11 }} stroke={tokens.border} />
                                <YAxis
                                    tick={{ fill: tokens.textMuted, fontSize: 11 }}
                                    stroke={tokens.border}
                                    tickFormatter={(v) => `${Number(v).toFixed(1)}%`}
                                />
                                <Tooltip content={<SimPerformanceTooltip />} />
                                <Legend
                                    wrapperStyle={{ color: tokens.text, fontSize: 12 }}
                                    formatter={(value) => <span style={{ color: tokens.text }}>{value}</span>}
                                />
                                {visibleResults.map((res, i) => {
                                    const key = `${res.assetType}-${res.assetName}-${res.id.slice(-4)}`;
                                    const color = CHART_PALETTE[i % CHART_PALETTE.length];
                                    const gid = `simFill-${res.id.replace(/[^a-zA-Z0-9_-]/g, '')}`;
                                    return (
                                        <Area
                                            key={res.id}
                                            type="monotone"
                                            dataKey={key}
                                            name={`${res.assetName} (${res.assetType})`}
                                            stroke={color}
                                            strokeWidth={3}
                                            fill={`url(#${gid})`}
                                            fillOpacity={1}
                                            connectNulls
                                            dot={false}
                                            activeDot={{ r: 5, strokeWidth: 2, stroke: color, fill: '#0a0f1a' }}
                                            isAnimationActive={false}
                                        />
                                    );
                                })}
                            </ComposedChart>
                        </ResponsiveContainer>
                    </div>
                )}
            </div>

            <div className="card-premium tp-card" style={{ padding: 16 }}>
                <div
                    style={{
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                        gap: 8,
                        flexWrap: 'wrap',
                        marginBottom: 12,
                    }}
                >
                    <h2 style={{ margin: 0, fontSize: '1rem' }}>{t('simulation.list', 'Simülasyon Listesi')}</h2>
                    <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                        <select
                            value={sortMode}
                            onChange={(e) => setSortMode(e.target.value as SortMode)}
                            className="sim-premium-select"
                            style={{ width: 220, padding: '8px 10px', fontSize: '0.8125rem' }}
                        >
                            <option value="LATEST">Sıralama: En Yeni</option>
                            <option value="PNL_DESC">Sıralama: En Yüksek Getiri (₺)</option>
                            <option value="PNL_ASC">Sıralama: En Kötü Getiri (₺)</option>
                            <option value="PNL_PCT_DESC">Sıralama: En Yüksek Getiri (%)</option>
                            <option value="PNL_PCT_ASC">Sıralama: En Kötü Getiri (%)</option>
                            <option value="NAME_ASC">Sıralama: Sembol (A-Z)</option>
                        </select>
                        <button type="button" onClick={() => setAllVisible(true)} className="sim-toolbar-btn">
                            Tümünü Göster
                        </button>
                        <button type="button" onClick={() => setAllVisible(false)} className="sim-toolbar-btn">
                            Tümünü Gizle
                        </button>
                        <button type="button" onClick={exportCsv} className="sim-toolbar-btn sim-csv-btn">
                            {t('simulation.exportCsv', 'CSV indir')}
                        </button>
                        <button
                            type="button"
                            onClick={() => void exportPdf()}
                            className="sim-toolbar-btn sim-csv-btn"
                        >
                            {t('simulation.exportPdf', 'PDF indir')}
                        </button>
                    </div>
                </div>
                <div style={{ overflowX: 'auto' }}>
                    <table className="tp-table sim-table">
                        <thead>
                            <tr>
                                <th style={{ textAlign: 'left', padding: 8, borderBottom: `2px solid ${tokens.border}` }}>Görünür</th>
                                <th style={{ textAlign: 'left', padding: 8, borderBottom: `2px solid ${tokens.border}` }}>Varlık</th>
                                <th style={{ textAlign: 'right', padding: 8, borderBottom: `2px solid ${tokens.border}` }}>Başlangıç</th>
                                <th style={{ textAlign: 'right', padding: 8, borderBottom: `2px solid ${tokens.border}` }} title="TRY / birim">
                                    Alış (birim)
                                </th>
                                <th style={{ textAlign: 'right', padding: 8, borderBottom: `2px solid ${tokens.border}` }} title="Güncel birim TRY fiyatı">
                                    Güncel (birim)
                                </th>
                                <th style={{ textAlign: 'right', padding: 8, borderBottom: `2px solid ${tokens.border}` }} title="Başlangıç tutarı + PNL">
                                    Şu an toplam (TRY)
                                </th>
                                <th style={{ textAlign: 'right', padding: 8, borderBottom: `2px solid ${tokens.border}` }}>PNL</th>
                                <th style={{ textAlign: 'left', padding: 8, borderBottom: `2px solid ${tokens.border}` }}>Kaynak/Tarih/Kalite</th>
                                <th style={{ textAlign: 'right', padding: 8, borderBottom: `2px solid ${tokens.border}` }}>İşlem</th>
                            </tr>
                        </thead>
                        <tbody>
                            {displayedResults.length === 0 ? (
                                <tr>
                                    <td colSpan={9} style={{ padding: 10, color: tokens.textMuted, textAlign: 'center' }}>
                                        Henüz simülasyon yok.
                                    </td>
                                </tr>
                            ) : (
                                displayedResults.map((r) => (
                                    <tr key={r.id} className="tp-table-row-hover sim-table-row">
                                        <td style={{ padding: 8, borderBottom: `1px solid ${tokens.tableBorder}` }}>
                                            <button
                                                type="button"
                                                onClick={() => setSimulationResults((prev) => prev.map((x) => x.id === r.id ? { ...x, visible: !x.visible } : x))}
                                                style={{ background: 'transparent', border: 'none', cursor: 'pointer', color: tokens.text, fontSize: '1rem' }}
                                                title={r.visible ? 'Grafikten gizle' : 'Grafikte göster'}
                                            >
                                                {r.visible ? '👁' : '🙈'}
                                            </button>
                                        </td>
                                        <td style={{ padding: 8, borderBottom: `1px solid ${tokens.tableBorder}` }}>
                                            <div>{r.assetName}</div>
                                            <div style={{ color: tokens.textMuted, fontSize: '0.75rem' }}>{r.assetType} • {new Date(r.buyDate).toLocaleDateString('tr-TR')}</div>
                                        </td>
                                        <td className="tp-mono" style={{ padding: 8, textAlign: 'right', borderBottom: `1px solid ${tokens.tableBorder}` }}>{fmtMoney(r.initialAmount)}</td>
                                        <td className="tp-mono" style={{ padding: 8, textAlign: 'right', borderBottom: `1px solid ${tokens.tableBorder}` }}>{fmtMoney(r.buyPrice)}</td>
                                        <td className="tp-mono" style={{ padding: 8, textAlign: 'right', borderBottom: `1px solid ${tokens.tableBorder}` }}>{fmtMoney(r.currentPrice)}</td>
                                        <td style={{ padding: 8, textAlign: 'right', borderBottom: `1px solid ${tokens.tableBorder}` }}>
                                            <SimAnimatedTry value={r.currentValue} bold />
                                        </td>
                                        <td style={{ padding: 8, textAlign: 'right', borderBottom: `1px solid ${tokens.tableBorder}` }}>
                                            <SimAnimatedPnl pnl={r.pnl} pnlPct={r.pnlPct} />
                                        </td>
                                        <td style={{ padding: 8, borderBottom: `1px solid ${tokens.tableBorder}` }}>
                                            <div style={{ fontSize: '0.8rem', color: tokens.text }}>{sourceLabel(r.buyPriceSource)}</div>
                                            <div className="sim-lead" style={{ fontSize: '0.75rem' }}>
                                                {new Date(r.historicalPriceDate).toLocaleDateString('tr-TR')}
                                            </div>
                                            <span
                                                className={
                                                    r.qualityFlag === 'EXACT'
                                                        ? 'quality-pill quality-pill-exact'
                                                        : r.qualityFlag === 'PREVIOUS_DAY'
                                                        ? 'quality-pill quality-pill-prev'
                                                        : 'quality-pill quality-pill-fallback'
                                                }
                                            >
                                                {qualityLabel(r.qualityFlag)}
                                            </span>
                                        </td>
                                        <td style={{ padding: 8, textAlign: 'right', borderBottom: `1px solid ${tokens.tableBorder}` }}>
                                            <button
                                                type="button"
                                                onClick={() => setSimulationResults((prev) => prev.filter((x) => x.id !== r.id))}
                                                className="tp-icon-btn tp-icon-btn-danger"
                                                style={{ borderRadius: 6, border: `1px solid ${tokens.border}`, background: tokens.bgCard, color: '#ef4444', padding: '4px 8px', cursor: 'pointer' }}
                                                title="Sil"
                                            >
                                                <Trash2 size={14} />
                                            </button>
                                        </td>
                                    </tr>
                                ))
                            )}
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    );
}
