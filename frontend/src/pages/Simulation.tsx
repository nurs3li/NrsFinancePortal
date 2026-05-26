import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { jsPDF } from 'jspdf';
import autoTable from 'jspdf-autotable';
import { registerSimulationPdfFont, SIMULATION_PDF_FONT_FAMILY } from '../utils/simulationPdfFont';
import { financeClient } from '../api/client';
import { useTheme } from '../theme/ThemeContext';
import './TerminalPages.css';
import './MarketTerminal.css';
import './Simulation.css';
import { fetchSimulationSymbolsByType } from '../services/marketDataService';
import { useLanguage } from '../i18n/LanguageContext';
import type { AssetType } from '../constants/OrderConstants';
import { getBistLatest, bistLatestPrice } from '../services/bistEquityApi';
import { SimulationHeader } from '../components/simulation/SimulationHeader';
import { SimulationCreateCard } from '../components/simulation/SimulationCreateCard';
import { SimulationSummaryCard } from '../components/simulation/SimulationSummaryCard';
import { SimulationPerformanceChart } from '../components/simulation/SimulationPerformanceChart';
import { SimulationResultsList } from '../components/simulation/SimulationResultsList';
import { SimulationResultDetailDrawer } from '../components/simulation/SimulationResultDetailDrawer';
import { SimulationHistoryCard } from '../components/simulation/SimulationHistoryCard';
import { SIMULATION_HISTORY_PREPARING, SIMULATION_USD_DENOMINATED } from '../components/simulation/constants';
import {
    appendSimulationHistory,
    cloneHistoryItemsForSession,
    loadSimulationHistory,
    removeSimulationHistoryEntry,
} from '../components/simulation/simulationHistoryStorage';
import { formatSimMoney, resolveSessionDisplayCurrency, simCurrencySymbol } from '../components/simulation/simCurrency';
import { defaultSimulationBuyDate } from '../components/simulation/simDates';
import {
    buildSimulationExportRow,
    computeSummaryStats,
    livePriceInDisplayCurrency,
    liveTryFromOverview,
    mergeLiveIntoSeries,
    unwrapData,
    usdTryFromOverview,
    type OverviewLite,
} from '../components/simulation/utils';
import type {
    BuyPriceMode,
    ChartMetricMode,
    CompareDraftItem,
    SimDisplayCurrency,
    SimulationHistoryEntry,
    SimulationResponse,
    SimulationResultItem,
    SortMode,
} from '../components/simulation/types';

function parseDisplayCurrency(raw?: string | null): SimDisplayCurrency {
    return String(raw ?? '').toUpperCase() === 'USD' ? 'USD' : 'TRY';
}

export function Simulation() {
    const { tokens } = useTheme();
    const { t, lang } = useLanguage();
    const locale = lang === 'en' ? 'en-US' : 'tr-TR';

    const [type, setType] = useState<AssetType>('CRYPTO');
    const [symbol, setSymbol] = useState('BTCUSDT');
    const [amount, setAmount] = useState('5000');
    const [amountCurrency, setAmountCurrency] = useState<SimDisplayCurrency>('TRY');
    const [buyDate, setBuyDate] = useState(defaultSimulationBuyDate);
    const [buyPriceMode, setBuyPriceMode] = useState<BuyPriceMode>('SYSTEM');
    const [manualBuyPrice, setManualBuyPrice] = useState('');
    const [scenarioLabel, setScenarioLabel] = useState('');

    const [compareDrafts, setCompareDrafts] = useState<CompareDraftItem[]>([]);
    const [compareType, setCompareType] = useState<AssetType>('STOCK');
    const [compareSymbol, setCompareSymbol] = useState('');
    const [compareSymbolOptions, setCompareSymbolOptions] = useState<string[]>([]);
    const [compareOptionsLoading, setCompareOptionsLoading] = useState(false);
    const [saveFeedback, setSaveFeedback] = useState<string | null>(null);

    const [symbolOptions, setSymbolOptions] = useState<string[]>([]);
    const [overviewLoading, setOverviewLoading] = useState(true);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [simulationResults, setSimulationResults] = useState<SimulationResultItem[]>([]);
    const [sortMode, setSortMode] = useState<SortMode>('LATEST');
    const [chartMetricMode, setChartMetricMode] = useState<ChartMetricMode>('RETURN_PCT');
    const [onlyVisibleLegend, setOnlyVisibleLegend] = useState(true);
    const [detailId, setDetailId] = useState<string | null>(null);
    const [manualPricePrompt, setManualPricePrompt] = useState(false);
    const [historyEntries, setHistoryEntries] = useState<SimulationHistoryEntry[]>([]);
    const [activeHistoryId, setActiveHistoryId] = useState<string | null>(null);
    const [usdTrySpot, setUsdTrySpot] = useState<number | null>(null);
    const simulationResultsRef = useRef<SimulationResultItem[]>([]);
    const manualPriceInputRef = useRef<HTMLInputElement | null>(null);
    const chartAnchorRef = useRef<HTMLDivElement | null>(null);

    useEffect(() => {
        simulationResultsRef.current = simulationResults;
    }, [simulationResults]);

    useEffect(() => {
        setHistoryEntries(loadSimulationHistory());
    }, []);

    useEffect(() => {
        financeClient
            .get('/api/market/overview')
            .then((res) => setUsdTrySpot(usdTryFromOverview(unwrapData<OverviewLite>(res))))
            .catch(() => undefined);
    }, []);

    useEffect(() => {
        if (simulationResults.length === 0) return;

        const tick = async () => {
            try {
                const res = await financeClient.get('/api/market/overview');
                const overview = unwrapData<OverviewLite>(res);
                let bistLive: Record<string, number> = {};
                if (simulationResultsRef.current.some((r) => r.assetType === 'BIST')) {
                    try {
                        const rows = await getBistLatest();
                        bistLive = Object.fromEntries(
                            rows
                                .map((row) => {
                                    const sym = String(row.symbol ?? '').toUpperCase();
                                    const p = bistLatestPrice(row);
                                    return [sym, p] as const;
                                })
                                .filter(([, p]) => p > 0),
                        );
                    } catch {
                        /* BIST canlı fiyat yoksa önceki değerler korunur */
                    }
                }
                const usdTry = usdTryFromOverview(overview);
                setUsdTrySpot(usdTry);
                setSimulationResults((prev) =>
                    prev.map((r) => {
                        const liveTry =
                            r.assetType === 'BIST'
                                ? bistLive[r.assetName.toUpperCase()] ?? null
                                : liveTryFromOverview(overview, r.assetType, r.assetName);
                        const live = livePriceInDisplayCurrency(liveTry, r.displayCurrency, usdTry);
                        if (live == null || live <= 0 || r.buyPrice <= 0) return r;
                        const units = r.initialAmount / r.buyPrice;
                        const currentValue = units * live;
                        const pnl = currentValue - r.initialAmount;
                        const pnlPct = r.initialAmount > 0 ? (pnl / r.initialAmount) * 100 : 0;
                        const series = mergeLiveIntoSeries(r.series, r.buyPrice, live);
                        return { ...r, currentPrice: live, currentValue, pnl, pnlPct, series };
                    }),
                );
            } catch {
                /* overview isteği başarısız */
            }
        };

        const id = window.setInterval(tick, 45_000);
        void tick();
        return () => window.clearInterval(id);
    }, [simulationResults.length]);

    const isManualPriceRequiredMessage = (msg: string) => msg.includes('MANUAL_PRICE_REQUIRED');

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

    useEffect(() => {
        setCompareOptionsLoading(true);
        fetchSimulationSymbolsByType(compareType)
            .then((rows) => setCompareSymbolOptions(rows))
            .catch(() => setCompareSymbolOptions([]))
            .finally(() => setCompareOptionsLoading(false));
    }, [compareType]);

    useEffect(() => {
        if (compareSymbolOptions.length > 0 && !compareSymbolOptions.includes(compareSymbol)) {
            setCompareSymbol(compareSymbolOptions[0]);
        } else if (compareSymbolOptions.length === 0) {
            setCompareSymbol('');
        }
    }, [compareSymbolOptions, compareSymbol]);

    const calculateForAsset = async (assetType: AssetType, assetSymbol: string): Promise<SimulationResultItem> => {
        const parsedAmount = Number(amount);
        if (!parsedAmount || parsedAmount <= 0) {
            throw new Error(t('wallet.amountPositive', 'Tutar sıfırdan büyük olmalı.'));
        }
        const sym = assetSymbol.trim().toUpperCase();
        if (!sym) {
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
                type: assetType,
                symbol: sym,
                amount: parsedAmount,
                date: buyDate,
                buyPrice: buyPriceMode === 'MANUAL' ? parsedManualBuyPrice : undefined,
                currency: amountCurrency,
            },
        });
        const dto = unwrapData<SimulationResponse>(res);
        if (
            String(dto.status ?? '').toUpperCase() === 'PREPARING' ||
            dto.approximationNoticeCode === SIMULATION_HISTORY_PREPARING
        ) {
            throw new Error(
                dto.message || t('simulation.historyPreparing', 'Kripto geçmiş verisi hazırlanıyor. Birkaç dakika sonra tekrar deneyin.'),
            );
        }
        const displayCurrency = parseDisplayCurrency(dto.displayCurrency);
        const label = scenarioLabel.trim();
        return {
            id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
            assetName: dto.symbol,
            assetType: dto.type as AssetType,
            displayCurrency,
            unitsBought: Number(dto.unitsBought ?? 0),
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
            approximationNoticeCode: dto.approximationNoticeCode ?? null,
            scenarioLabel: label || undefined,
        };
    };

    const addSimulationResult = async (e: React.FormEvent) => {
        e.preventDefault();
        setError(null);
        setManualPricePrompt(false);
        const targets: { assetType: AssetType; symbol: string }[] = [{ assetType: type, symbol }];
        for (const d of compareDrafts) {
            targets.push({ assetType: d.assetType, symbol: d.symbol });
        }
        const unique: { assetType: AssetType; symbol: string }[] = [];
        const seen = new Set<string>();
        for (const item of targets) {
            const key = `${item.assetType}:${item.symbol.trim().toUpperCase()}`;
            if (seen.has(key)) continue;
            seen.add(key);
            unique.push({ assetType: item.assetType, symbol: item.symbol.trim().toUpperCase() });
        }

        try {
            setLoading(true);
            const added: SimulationResultItem[] = [];
            const errors: string[] = [];
            for (const item of unique) {
                try {
                    const row = await calculateForAsset(item.assetType, item.symbol);
                    added.push(row);
                } catch (err: unknown) {
                    const ex = err as {
                        response?: { data?: { errors?: { error?: string }; message?: string } };
                        message?: string;
                    };
                    const msg =
                        ex?.response?.data?.errors?.error ??
                        ex?.response?.data?.message ??
                        ex?.message ??
                        t('simulation.error', 'Simülasyon hatası');
                    if (isManualPriceRequiredMessage(msg)) {
                        setBuyPriceMode('MANUAL');
                        setManualPricePrompt(true);
                        window.setTimeout(() => manualPriceInputRef.current?.focus(), 0);
                    }
                    errors.push(
                        `${item.symbol}: ${isManualPriceRequiredMessage(msg) ? t('simulation.manualPriceRequired', 'Seçilen tarih için sistem fiyatı yok. Lütfen o güne ait alış fiyatını (TRY/birim) girin ve tekrar deneyin.') : msg}`,
                    );
                }
            }
            if (added.length > 0) {
                const keyOf = (r: { assetType: AssetType; assetName: string }) =>
                    `${r.assetType}:${r.assetName.toUpperCase()}`;
                const replacedKeys = new Set(added.map(keyOf));
                setSimulationResults((prev) => {
                    const kept = prev.filter((r) => !replacedKeys.has(keyOf(r)));
                    return [...added, ...kept];
                });
                setCompareDrafts([]);
                setActiveHistoryId(null);
            }
            if (errors.length > 0) {
                setError(errors.join(' · '));
            }
        } finally {
            setLoading(false);
        }
    };

    const addCompareDraft = () => {
        const sym = compareSymbol.trim().toUpperCase();
        if (!sym) return;
        const key = `${compareType}:${sym}`;
        if (`${type}:${symbol.toUpperCase()}` === key) return;
        if (compareDrafts.some((d) => `${d.assetType}:${d.symbol}` === key)) return;
        setCompareDrafts((prev) => [
            ...prev,
            { id: `cmp-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`, assetType: compareType, symbol: sym },
        ]);
    };

    const removeCompareDraft = (id: string) => {
        setCompareDrafts((prev) => prev.filter((x) => x.id !== id));
    };

    const saveSimulationToHistory = useCallback(() => {
        if (simulationResults.length === 0) return;
        const entry: SimulationHistoryEntry = {
            id: `hist-${Date.now()}`,
            savedAt: new Date().toISOString(),
            label:
                scenarioLabel.trim() ||
                t('simulation.historyDefaultLabel', 'Simülasyon {date}').replace(
                    '{date}',
                    new Date().toLocaleString(locale),
                ),
            amountCurrency: resolveSessionDisplayCurrency(simulationResults, amountCurrency),
            items: JSON.parse(JSON.stringify(simulationResults)) as SimulationResultItem[],
        };
        try {
            const list = appendSimulationHistory(entry);
            setHistoryEntries(list);
            setSaveFeedback(t('simulation.saveSuccess', 'Simülasyon geçmişe kaydedildi.'));
            window.setTimeout(() => setSaveFeedback(null), 4000);
        } catch {
            setSaveFeedback(t('simulation.saveFailed', 'Kayıt başarısız.'));
        }
    }, [simulationResults, scenarioLabel, locale, t, amountCurrency]);

    const viewHistoryEntry = useCallback(
        (entry: SimulationHistoryEntry) => {
            if (activeHistoryId === entry.id) {
                setSimulationResults([]);
                setActiveHistoryId(null);
                return;
            }

            const cloned = cloneHistoryItemsForSession(entry.items);
            setSimulationResults(cloned);
            const first = entry.items[0];
            if (first) {
                setAmount(String(first.initialAmount));
                setBuyDate(first.buyDate);
                setType(first.assetType);
                setSymbol(first.assetName);
            }
            setAmountCurrency(entry.amountCurrency ?? first?.displayCurrency ?? 'TRY');
            setScenarioLabel(entry.label);
            setActiveHistoryId(entry.id);
            setError(null);
            window.requestAnimationFrame(() => {
                chartAnchorRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
            });
        },
        [activeHistoryId],
    );

    const deleteHistoryEntry = useCallback((id: string) => {
        setHistoryEntries(removeSimulationHistoryEntry(id));
        setActiveHistoryId((prev) => {
            if (prev === id) {
                setSimulationResults([]);
                return null;
            }
            return prev;
        });
    }, []);

    const visibleResults = useMemo(() => simulationResults.filter((r) => r.visible), [simulationResults]);

    const showUsdHistoricalNotice = useMemo(
        () => visibleResults.some((r) => r.approximationNoticeCode === SIMULATION_USD_DENOMINATED),
        [visibleResults],
    );

    const summaryStats = useMemo(() => computeSummaryStats(simulationResults), [simulationResults]);
    const sessionDisplayCurrency = useMemo(
        () => resolveSessionDisplayCurrency(simulationResults, amountCurrency),
        [simulationResults, amountCurrency],
    );

    const handleAmountCurrencyChange = (next: SimDisplayCurrency) => {
        if (next !== amountCurrency && simulationResults.length > 0) {
            setSimulationResults([]);
            setActiveHistoryId(null);
        }
        setAmountCurrency(next);
    };

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
                return arr.sort((a, b) => a.assetName.localeCompare(b.assetName, locale));
            case 'LATEST':
            default:
                return arr;
        }
    }, [simulationResults, sortMode, locale]);

    const detailItem = useMemo(
        () => (detailId ? simulationResults.find((r) => r.id === detailId) ?? null : null),
        [detailId, simulationResults],
    );

    const setAllVisible = (visible: boolean) => {
        setSimulationResults((prev) => prev.map((x) => ({ ...x, visible })));
    };

    const toggleVisible = (id: string) => {
        setSimulationResults((prev) => prev.map((x) => (x.id === id ? { ...x, visible: !x.visible } : x)));
    };

    const exportHeaders = useMemo(() => {
        const sym = simCurrencySymbol(sessionDisplayCurrency);
        return [
            t('simulation.exportColAssetType', 'Varlık türü'),
            t('simulation.exportColSymbol', 'Sembol'),
            t('simulation.exportColBuyDate', 'Alım tarihi'),
            t('simulation.exportColInitialTry', 'Başlangıç tutarı ({sym})').replace('{sym}', sym),
            t('simulation.exportColBuyUnitTry', 'Alış fiyatı ({sym} / birim)').replace('{sym}', sym),
            t('simulation.exportColCurrentUnitTry', 'Güncel fiyat ({sym} / birim)').replace('{sym}', sym),
            t('simulation.exportColValueTry', 'Güncel değer ({sym})').replace('{sym}', sym),
            t('simulation.exportColPnlTry', 'Kâr/zarar ({sym})').replace('{sym}', sym),
            t('simulation.exportColPnlPct', 'Getiri (%)'),
            t('simulation.exportColPriceSource', 'Fiyat kaynağı'),
            t('simulation.exportColRefDate', 'Referans tarihi'),
            t('simulation.exportColQuality', 'Veri kalitesi'),
        ];
    }, [t, sessionDisplayCurrency]);

    const exportCellFormatters = useMemo(
        () => ({
            assetType(at: AssetType) {
                switch (at) {
                    case 'CRYPTO':
                        return t('category.crypto', 'Kripto');
                    case 'FX':
                        return t('category.fx', 'Döviz');
                    case 'METAL':
                        return t('category.metals', 'Kıymetli madenler');
                    case 'FUND':
                        return t('category.funds', 'Fonlar');
                    case 'STOCK':
                        return t('category.equity', 'Hisse');
                    case 'BIST':
                        return t('category.bist', 'BIST hisse');
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
                if (u === 'SYSTEM_LATEST_FALLBACK') return t('simulation.exportSourceLatestFallback', 'Sistem son fiyat (yedek)');
                if (u === 'USER_INPUT') return t('simulation.exportSourceManual', 'Kullanıcı girişi');
                return s ? t('simulation.exportSourceOther', s) : t('simulation.exportSourceUnknown', 'Bilinmiyor');
            },
        }),
        [t],
    );

    const exportCsv = useCallback(() => {
        if (displayedResults.length === 0) return;
        const esc = (v: string) => `"${v.replaceAll('"', '""')}"`;
        const lines = displayedResults.map((r) => buildSimulationExportRow(r, exportCellFormatters).map(esc).join(','));
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
            window.alert(t('simulation.exportPdfFontError', 'PDF için Türkçe font yüklenemedi.'));
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

        let startY = 22;
        const stats = computeSummaryStats(displayedResults);
        if (stats.best || stats.worst) {
            doc.setFontSize(9);
            const lines: string[] = [];
            if (stats.best) {
                lines.push(
                    `${t('simulation.bestScenario', 'En iyi senaryo')}: ${stats.best.assetName} (+${formatSimMoney(localeStr, stats.best.pnl, sessionDisplayCurrency)}, ${stats.best.pnlPct.toFixed(2)}%)`,
                );
            }
            if (stats.worst) {
                lines.push(
                    `${t('simulation.worstScenario', 'En kötü senaryo')}: ${stats.worst.assetName} (${formatSimMoney(localeStr, stats.worst.pnl, sessionDisplayCurrency)}, ${stats.worst.pnlPct.toFixed(2)}%)`,
                );
            }
            lines.push(
                `${t('simulation.avgReturn', 'Ortalama getiri')}: ${stats.avgReturnPct.toFixed(2)}% · ${t('simulation.simCount', '{n} simülasyon').replace('{n}', String(stats.count))}`,
            );
            lines.forEach((line, i) => doc.text(line, 14, startY + i * 5));
            startY += lines.length * 5 + 4;
        }

        const body = displayedResults.map((r) => buildSimulationExportRow(r, exportCellFormatters));
        autoTable(doc, {
            startY,
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
        doc.text(t('simulation.exportPdfFooter', 'Kişisel kullanım içindir. Yatırım tavsiyesi değildir.'), 14, footY, {
            maxWidth: 275,
        });
        doc.save(`simulation-ozet-${new Date().toISOString().slice(0, 19).replaceAll(':', '-')}.pdf`);
    }, [displayedResults, exportHeaders, exportCellFormatters, lang, t, sessionDisplayCurrency]);

    return (
        <div
            style={
                {
                    padding: 24,
                    background: tokens.bg,
                    minHeight: '100%',
                    '--tp-bg': tokens.bg,
                    '--tp-card': tokens.bgCard,
                    '--tp-border': tokens.border,
                    '--tp-text': tokens.text,
                    '--tp-muted': tokens.textMuted,
                } as React.CSSProperties
            }
            className="terminal-pages-root sim-page"
        >
            <SimulationHeader showUsdNotice={showUsdHistoricalNotice} mutedColor={tokens.textMuted} />

            <div className="sim-top-grid">
                <SimulationCreateCard
                    textColor={tokens.text}
                    mutedColor={tokens.textMuted}
                    scenarioLabel={scenarioLabel}
                    onScenarioLabelChange={setScenarioLabel}
                    type={type}
                    onTypeChange={setType}
                    symbol={symbol}
                    onSymbolChange={setSymbol}
                    amount={amount}
                    onAmountChange={setAmount}
                    amountCurrency={amountCurrency}
                    onAmountCurrencyChange={handleAmountCurrencyChange}
                    buyDate={buyDate}
                    onBuyDateChange={setBuyDate}
                    buyPriceMode={buyPriceMode}
                    onBuyPriceModeChange={setBuyPriceMode}
                    manualBuyPrice={manualBuyPrice}
                    onManualBuyPriceChange={(v) => {
                        setManualBuyPrice(v);
                        if (v.trim()) setManualPricePrompt(false);
                    }}
                    manualPricePrompt={manualPricePrompt}
                    manualPriceInputRef={manualPriceInputRef}
                    symbolOptions={symbolOptions}
                    overviewLoading={overviewLoading}
                    compareType={compareType}
                    onCompareTypeChange={setCompareType}
                    compareSymbol={compareSymbol}
                    onCompareSymbolChange={setCompareSymbol}
                    compareSymbolOptions={compareSymbolOptions}
                    compareOptionsLoading={compareOptionsLoading}
                    compareDrafts={compareDrafts}
                    onAddCompare={addCompareDraft}
                    onRemoveCompare={removeCompareDraft}
                    loading={loading}
                    onSubmit={addSimulationResult}
                />
                <div className="sim-top-stack">
                    <SimulationSummaryCard
                        stats={summaryStats}
                        displayCurrency={sessionDisplayCurrency}
                        hasResults={simulationResults.length > 0}
                        mutedColor={tokens.textMuted}
                        onExportCsv={exportCsv}
                        onExportPdf={() => void exportPdf()}
                        onSaveSimulation={saveSimulationToHistory}
                        exportDisabled={displayedResults.length === 0}
                        saveFeedback={saveFeedback}
                    />
                    <SimulationResultsList
                        results={displayedResults}
                        displayCurrency={sessionDisplayCurrency}
                        usdTryRate={usdTrySpot}
                        sortMode={sortMode}
                        onSortModeChange={setSortMode}
                        onToggleVisible={toggleVisible}
                        onDelete={(id) => setSimulationResults((prev) => prev.filter((x) => x.id !== id))}
                        onShowDetail={setDetailId}
                        onShowAll={() => setAllVisible(true)}
                        onHideAll={() => setAllVisible(false)}
                        borderColor={tokens.border}
                        tableBorder={tokens.tableBorder}
                        textColor={tokens.text}
                        mutedColor={tokens.textMuted}
                        bgCard={tokens.bgCard}
                    />
                </div>
            </div>

            {error ? (
                <div className="card-premium sim-error-banner" role="alert">
                    <strong>{t('simulation.error', 'Simülasyon hatası')}:</strong> {error}
                    <p className="sim-lead sim-error-banner__hint">
                        {manualPricePrompt
                            ? t(
                                  'simulation.manualPriceRequiredHint',
                                  '“Manuel fiyat gir” seçeneğini kullanın, alım günü birim fiyatını yazıp tekrar simüle edin.',
                              )
                            : t(
                                  'simulation.priceNotFoundHint',
                                  'Seçilen tarih için fiyat bulunamadı. Önceki işlem günü otomatik denenir; yine de yoksa manuel fiyat girmeniz gerekir.',
                              )}
                    </p>
                </div>
            ) : null}

            <div ref={chartAnchorRef} className="sim-chart-history-stack">
                <SimulationPerformanceChart
                    visibleResults={visibleResults}
                    allResults={simulationResults}
                    displayCurrency={sessionDisplayCurrency}
                    usdTryRate={usdTrySpot}
                    metricMode={chartMetricMode}
                    onMetricModeChange={setChartMetricMode}
                    onlyVisibleOnChart={onlyVisibleLegend}
                    onOnlyVisibleChange={setOnlyVisibleLegend}
                    borderColor={tokens.border}
                    textMuted={tokens.textMuted}
                    textColor={tokens.text}
                    onToggleVisible={toggleVisible}
                />

                <SimulationHistoryCard
                    entries={historyEntries}
                    activeId={activeHistoryId}
                    onView={viewHistoryEntry}
                    onDelete={deleteHistoryEntry}
                    mutedColor={tokens.textMuted}
                    textColor={tokens.text}
                    borderColor={tokens.border}
                    tableBorder={tokens.tableBorder}
                />
            </div>

            <SimulationResultDetailDrawer
                item={detailItem}
                displayCurrency={detailItem?.displayCurrency ?? sessionDisplayCurrency}
                onClose={() => setDetailId(null)}
                textColor={tokens.text}
                mutedColor={tokens.textMuted}
                borderColor={tokens.border}
                bgCard={tokens.bgCard}
            />
        </div>
    );
}
