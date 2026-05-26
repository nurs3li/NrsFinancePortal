import {
    forwardRef,
    useCallback,
    useEffect,
    useImperativeHandle,
    useMemo,
    useState,
    type CSSProperties,
    type FormEvent,
} from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { BookOpen, Info, LineChart as LineChartIcon, Plus, Search, X } from 'lucide-react';
import { manualPortfolioKeys } from '../../queries/manualPortfolioKeys';
import {
    closeManualPosition,
    createManualPosition,
    deleteManualPosition,
    evaluatePortfolioInsightNotifications,
    getManualPortfolioInsights,
    getManualPositionAnalysis,
    getManualPositions,
    getManualSummary,
    readFinanceApiError,
    resolveManualPrice,
    updateManualPosition,
} from '../../services/manualPortfolioApi';
import { notificationKeys } from '../../queries/notificationKeys';
import type { ManualAssetType, ManualPortfolioAnalysis, ManualPortfolioView } from '../../types/manualPortfolio';
import { useLanguage } from '../../i18n/LanguageContext';
import { cryptoMeta, etfMeta, fxMeta, instrumentMeta } from '../../utils/instrumentMeta';
import { PRECIOUS_METAL_DISPLAY_META, PRECIOUS_METAL_SYMBOLS } from '../../constants/preciousMetalsUsd';
import { fetchSimulationSymbolsByType } from '../../services/marketDataService';
import { getBistSymbols } from '../../services/bistEquityApi';
import { istanbulTodayYmd } from '../simulation/simDates';

const ASSET_TYPE_ORDER: ManualAssetType[] = ['STOCK', 'CRYPTO', 'FX', 'METAL', 'FUND'];

type ManualSymbolOption = { value: string; label: string };

function manualPortfolioFallbackSymbolOptionsForType(type: ManualAssetType): ManualSymbolOption[] {
    switch (type) {
        case 'STOCK':
            return Object.keys(instrumentMeta)
                .sort()
                .map((k) => ({ value: k, label: `${k} — ${instrumentMeta[k]!.name}` }));
        case 'CRYPTO':
            return Object.keys(cryptoMeta)
                .sort()
                .map((k) => ({ value: k, label: `${k} — ${cryptoMeta[k]!.name}` }));
        case 'FX':
            return Object.keys(fxMeta)
                .sort()
                .map((k) => ({ value: k, label: `${k} — ${fxMeta[k]!.baseName} / ${fxMeta[k]!.quoteName}` }));
        case 'METAL':
            return [...PRECIOUS_METAL_SYMBOLS].map((k) => {
                const m = PRECIOUS_METAL_DISPLAY_META[k];
                return { value: k, label: m ? `${k} — ${m.displayName}` : k };
            });
        case 'FUND':
            return Object.keys(etfMeta)
                .sort()
                .map((k) => ({ value: k, label: `${k} — ${etfMeta[k]!.name}` }));
        default:
            return [];
    }
}

function manualPortfolioSymbolLabel(
    type: ManualAssetType,
    symbol: string,
    bistNameBySymbol: Record<string, string> = {},
): string {
    const sym = String(symbol ?? '').trim().toUpperCase();
    if (!sym) return symbol;
    switch (type) {
        case 'STOCK': {
            if (sym.endsWith('.IS')) {
                const bistBase = sym.slice(0, -3);
                const bistName = bistNameBySymbol[bistBase];
                return bistName ? `${sym} — ${bistName} (BIST)` : `${sym} — BIST`;
            }
            const meta = instrumentMeta[sym];
            return meta?.name ? `${sym} — ${meta.name}` : sym;
        }
        case 'CRYPTO': {
            const meta = cryptoMeta[sym];
            return meta?.name ? `${sym} — ${meta.name}` : sym;
        }
        case 'FX': {
            const meta = fxMeta[sym];
            return meta ? `${sym} — ${meta.baseName} / ${meta.quoteName}` : sym;
        }
        case 'METAL': {
            const meta = PRECIOUS_METAL_DISPLAY_META[sym];
            return meta?.displayName ? `${sym} — ${meta.displayName}` : sym;
        }
        case 'FUND': {
            const meta = etfMeta[sym];
            return meta?.name ? `${sym} — ${meta.name}` : sym;
        }
        default:
            return sym;
    }
}

function n(v: unknown): number | null {
    if (v == null) return null;
    const x = typeof v === 'number' ? v : Number(v);
    return Number.isFinite(x) ? x : null;
}

function formatCurrencyTry(locale: string, value: number | null | undefined, empty = '—'): string {
    const v = n(value);
    if (v == null) return empty;
    return new Intl.NumberFormat(locale, { style: 'currency', currency: 'TRY', maximumFractionDigits: 2 }).format(v);
}

function formatPercent(locale: string, value: number | null | undefined, empty = '—'): string {
    const v = n(value);
    if (v == null) return empty;
    return `${v.toLocaleString(locale, { maximumFractionDigits: 2 })}%`;
}

function pnlClass(v: number | null | undefined): string {
    const x = n(v);
    if (x == null) return 'mia-muted';
    if (x > 0) return 'mia-pos';
    if (x < 0) return 'mia-neg';
    return 'mia-muted';
}

function statusOf(p: ManualPortfolioView): string {
    return String(p.status ?? 'OPEN').toUpperCase();
}

function priceSourceLabel(t: (k: string, d: string) => string, src: string | null | undefined): string {
    switch (src) {
        case 'USER_INPUT':
            return t('manualInvest.sourceUser', 'Manuel giriş');
        case 'MARKET_HISTORY_EXACT':
            return t('manualInvest.sourceExact', 'Seçilen gün kapanışı');
        case 'MARKET_HISTORY_SAME_DAY_HOURLY':
            return t('manualInvest.sourceSameDayHourly', 'Aynı gün son saatlik fiyat');
        case 'MARKET_HISTORY_PREVIOUS_CLOSE':
            return t('manualInvest.sourcePrevClose', 'En yakın önceki kapanış');
        case 'MARKET_HISTORY_NEXT_CLOSE':
            return t('manualInvest.sourceNextClose', 'En yakın sonraki kapanış');
        case 'NOT_RESOLVED':
            return t('manualInvest.sourceNotResolved', 'Bulunamadı');
        default:
            return src ?? '—';
    }
}

export type ManualInvestmentAnalysisSectionHandle = {
    openCreate: () => void;
    openEdit: (id: number) => void;
    openAnalysis: (id: number) => void;
    openSellEntry: () => void;
};

type Props = {
    tokens: {
        bg: string;
        bgCard: string;
        border: string;
        text: string;
        textMuted: string;
        error: string;
    };
    locale: string;
    onPortfolioMutated: () => void;
    /** full: liste + formlar; modalsOnly: yalnızca modal ve analiz çekmecesi. */
    surface?: 'full' | 'modalsOnly';
};

type StatusFilter = 'ALL' | 'OPEN' | 'SOLD';
type TypeFilter = 'ALL' | ManualAssetType;

const TABLE_PAGE = 8;

export const ManualInvestmentAnalysisSection = forwardRef<ManualInvestmentAnalysisSectionHandle, Props>(function ManualInvestmentAnalysisSectionInner(
    { tokens, locale, onPortfolioMutated, surface = 'full' },
    ref,
) {
    const { t } = useLanguage();
    const qc = useQueryClient();

    const manualAssetTypeLabel = useCallback(
        (at: ManualAssetType) => t(`portfolio.typeLabel.${at}`, at),
        [t],
    );

    const positionsQuery = useQuery({
        queryKey: manualPortfolioKeys.positions(),
        queryFn: getManualPositions,
    });
    const summaryQuery = useQuery({
        queryKey: manualPortfolioKeys.summary(),
        queryFn: getManualSummary,
    });
    const insightsQuery = useQuery({
        queryKey: manualPortfolioKeys.insights(),
        queryFn: getManualPortfolioInsights,
        enabled: surface === 'full',
    });
    const evaluateAlertsMutation = useMutation({
        mutationFn: evaluatePortfolioInsightNotifications,
        onSuccess: async () => {
            await qc.invalidateQueries({ queryKey: notificationKeys.all });
        },
    });

    const positions = positionsQuery.data ?? [];
    const summary = summaryQuery.data;
    const insights = insightsQuery.data;
    const insightSummary = insights?.summary;

    const riskLevelClass = (level: string | undefined) => {
        const l = (level ?? '').toUpperCase();
        if (l === 'HIGH' || l === 'WEAK') return 'mia-neg';
        if (l === 'MEDIUM') return 'mia-muted';
        return 'mia-pos';
    };

    const invalidateManual = useCallback(async () => {
        await qc.invalidateQueries({ queryKey: manualPortfolioKeys.all });
        onPortfolioMutated();
    }, [qc, onPortfolioMutated]);

    const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL');
    const [typeFilter, setTypeFilter] = useState<TypeFilter>('ALL');
    const [search, setSearch] = useState('');
    const [tablePage, setTablePage] = useState(0);

    const [analysisOpen, setAnalysisOpen] = useState(false);
    const [analysisId, setAnalysisId] = useState<number | null>(null);
    const analysisQuery = useQuery({
        queryKey: analysisId != null ? manualPortfolioKeys.analysis(analysisId) : ['manualPortfolio', 'analysis', 'idle'],
        queryFn: () => getManualPositionAnalysis(analysisId!),
        enabled: analysisOpen && analysisId != null,
    });

    const [formOpen, setFormOpen] = useState(false);
    const [formMode, setFormMode] = useState<'create' | 'edit'>('create');
    const [editId, setEditId] = useState<number | null>(null);
    const [positionStatus, setPositionStatus] = useState<'OPEN' | 'SOLD'>('OPEN');
    const [fType, setFType] = useState<ManualAssetType>('CRYPTO');
    const [fSymbol, setFSymbol] = useState('');
    const [fQty, setFQty] = useState('1');
    const [fBuyDate, setFBuyDate] = useState(() => istanbulTodayYmd());
    const [fBuyFee, setFBuyFee] = useState('0');
    const [fNote, setFNote] = useState('');
    const [fSellDate, setFSellDate] = useState(() => istanbulTodayYmd());
    const [fSellFee, setFSellFee] = useState('0');
    const [fBuyPriceStr, setFBuyPriceStr] = useState('');
    const [fSellPriceStr, setFSellPriceStr] = useState('');
    const [buySubmitMode, setBuySubmitMode] = useState<'auto' | 'manual'>('auto');
    const [sellSubmitMode, setSellSubmitMode] = useState<'auto' | 'manual'>('auto');
    const [lastBuyResolve, setLastBuyResolve] = useState<Awaited<ReturnType<typeof resolveManualPrice>> | null>(null);
    const [lastSellResolve, setLastSellResolve] = useState<Awaited<ReturnType<typeof resolveManualPrice>> | null>(null);
    const [formBanner, setFormBanner] = useState<string | null>(null);
    const [resolveBusy, setResolveBusy] = useState<'buy' | 'sell' | null>(null);

    const [closeOpen, setCloseOpen] = useState(false);
    const [closeId, setCloseId] = useState<number | null>(null);
    const [cSellDate, setCSellDate] = useState(() => istanbulTodayYmd());
    const [cSellFee, setCSellFee] = useState('0');
    const [cSellPriceStr, setCSellPriceStr] = useState('');
    const [cSellSubmitMode, setCSellSubmitMode] = useState<'auto' | 'manual'>('auto');
    const [cLastResolve, setCLastResolve] = useState<Awaited<ReturnType<typeof resolveManualPrice>> | null>(null);
    const [cResolveBusy, setCResolveBusy] = useState(false);
    const [closeBanner, setCloseBanner] = useState<string | null>(null);

    const openFormCreate = useCallback(() => {
        setFormMode('create');
        setEditId(null);
        setPositionStatus('OPEN');
        setFType('CRYPTO');
        setFSymbol('');
        setFQty('1');
        setFBuyDate(istanbulTodayYmd());
        setFBuyFee('0');
        setFNote('');
        setFSellDate(istanbulTodayYmd());
        setFSellFee('0');
        setFBuyPriceStr('');
        setFSellPriceStr('');
        setBuySubmitMode('auto');
        setSellSubmitMode('auto');
        setLastBuyResolve(null);
        setLastSellResolve(null);
        setFormBanner(null);
        setFormOpen(true);
    }, []);

    const openFormEdit = useCallback((id: number) => {
        const row = positions.find((p) => p.id === id);
        if (!row) return;
        setFormMode('edit');
        setEditId(id);
        setPositionStatus(statusOf(row) === 'SOLD' ? 'SOLD' : 'OPEN');
        setFType((String(row.type).toUpperCase() as ManualAssetType) || 'CRYPTO');
        setFSymbol(String(row.symbol ?? '').trim().toUpperCase());
        setFQty(String(n(row.quantity) ?? ''));
        setFBuyDate(String(row.buyDate ?? '').slice(0, 10));
        setFBuyFee(String(n(row.buyFee) ?? 0));
        setFNote(row.note ?? '');
        setFSellDate(row.sellDate ? String(row.sellDate).slice(0, 10) : istanbulTodayYmd());
        setFSellFee(String(n(row.sellFee) ?? 0));
        setFBuyPriceStr(row.buyPrice != null ? String(n(row.buyPrice)) : '');
        setFSellPriceStr(row.sellPrice != null ? String(n(row.sellPrice)) : '');
        setBuySubmitMode('manual');
        setSellSubmitMode('manual');
        setLastBuyResolve(null);
        setLastSellResolve(null);
        setFormBanner(null);
        setFormOpen(true);
    }, [positions]);

    const filteredRows = useMemo(() => {
        const q = search.trim().toLowerCase();
        return positions.filter((p) => {
            const st = statusOf(p);
            if (statusFilter === 'OPEN' && st !== 'OPEN') return false;
            if (statusFilter === 'SOLD' && st !== 'SOLD') return false;
            if (typeFilter !== 'ALL' && String(p.type).toUpperCase() !== typeFilter) return false;
            if (q) {
                const sym = String(p.symbol ?? '').toLowerCase();
                const note = String(p.note ?? '').toLowerCase();
                if (!sym.includes(q) && !note.includes(q)) return false;
            }
            return true;
        });
    }, [positions, statusFilter, typeFilter, search]);

    const totalPages = Math.max(1, Math.ceil(filteredRows.length / TABLE_PAGE));
    const pageSlice = useMemo(() => {
        const start = tablePage * TABLE_PAGE;
        return filteredRows.slice(start, start + TABLE_PAGE);
    }, [filteredRows, tablePage]);

    useEffect(() => {
        setTablePage((p) => Math.min(p, totalPages - 1));
    }, [totalPages]);

    useEffect(() => {
        setTablePage(0);
    }, [statusFilter, typeFilter, search]);

    const fallbackSymbolOptions = useMemo(() => manualPortfolioFallbackSymbolOptionsForType(fType), [fType]);
    const symbolOptionsQuery = useQuery({
        queryKey: ['manualPortfolio', 'symbolOptions', fType] as const,
        queryFn: async () => {
            const fallback = manualPortfolioFallbackSymbolOptionsForType(fType);
            try {
                const [dynamicSymbols, bistSymbols] = await Promise.all([
                    fetchSimulationSymbolsByType(fType as 'STOCK' | 'CRYPTO' | 'FX' | 'METAL' | 'FUND'),
                    fType === 'STOCK' ? getBistSymbols() : Promise.resolve([]),
                ]);
                const bistNameBySymbol: Record<string, string> = Object.fromEntries(
                    (bistSymbols ?? [])
                        .map((row) => [
                            String(row.symbol ?? '')
                                .trim()
                                .toUpperCase(),
                            String(row.displayName ?? '').trim(),
                        ] as const)
                        .filter(([sym]) => Boolean(sym)),
                );
                const merged = new Set<string>(fallback.map((opt) => opt.value));
                for (const sym of dynamicSymbols ?? []) {
                    const normalized = String(sym ?? '').trim().toUpperCase();
                    if (normalized) merged.add(normalized);
                }
                if (fType === 'STOCK') {
                    for (const row of bistSymbols ?? []) {
                        const sym = String(row.symbol ?? '').trim().toUpperCase();
                        if (sym) merged.add(`${sym}.IS`);
                    }
                }
                return [...merged]
                    .sort((a, b) => a.localeCompare(b, 'tr-TR'))
                    .map((value) => ({
                        value,
                        label: manualPortfolioSymbolLabel(fType, value, bistNameBySymbol),
                    }));
            } catch {
                return fallback;
            }
        },
        staleTime: 5 * 60_000,
        placeholderData: fallbackSymbolOptions,
    });
    const baseSymbolOptions = symbolOptionsQuery.data ?? fallbackSymbolOptions;
    const symbolSelectOptions = useMemo(() => {
        const sym = fSymbol.trim().toUpperCase();
        if (!sym || baseSymbolOptions.some((o) => o.value === sym)) {
            return baseSymbolOptions;
        }
        return [
            {
                value: sym,
                label: `${sym} — ${t('manualInvest.symbolLegacy', 'Mevcut kayıt / listede yok')}`,
            },
            ...baseSymbolOptions,
        ];
    }, [baseSymbolOptions, fSymbol, t]);

    const createMut = useMutation({
        mutationFn: createManualPosition,
        onSuccess: async () => {
            setFormOpen(false);
            await invalidateManual();
        },
        onError: (err) => {
            const { code, message } = readFinanceApiError(err);
            if (code === 'BUY_PRICE_NOT_FOUND') {
                setBuySubmitMode('manual');
                setFormBanner(t('manualInvest.errBuyPrice', 'Seçilen tarih için otomatik fiyat bulunamadı. Lütfen alış fiyatını manuel girin.'));
            } else if (code === 'SELL_PRICE_NOT_FOUND') {
                setSellSubmitMode('manual');
                setFormBanner(t('manualInvest.errSellPrice', 'Seçilen tarih için satış fiyatı bulunamadı. Lütfen satış fiyatını manuel girin.'));
            } else {
                setFormBanner(message);
                alert(message);
            }
        },
    });

    const updateMut = useMutation({
        mutationFn: ({ id, body }: { id: number; body: Parameters<typeof updateManualPosition>[1] }) => updateManualPosition(id, body),
        onSuccess: async () => {
            setFormOpen(false);
            await invalidateManual();
        },
        onError: (err) => {
            const { code, message } = readFinanceApiError(err);
            if (code === 'BUY_PRICE_NOT_FOUND') {
                setBuySubmitMode('manual');
                setFormBanner(t('manualInvest.errBuyPrice', 'Seçilen tarih için otomatik fiyat bulunamadı. Lütfen alış fiyatını manuel girin.'));
            } else if (code === 'SELL_PRICE_NOT_FOUND') {
                setSellSubmitMode('manual');
                setFormBanner(t('manualInvest.errSellPrice', 'Seçilen tarih için satış fiyatı bulunamadı. Lütfen satış fiyatını manuel girin.'));
            } else {
                setFormBanner(message);
                alert(message);
            }
        },
    });

    const closeMut = useMutation({
        mutationFn: ({ id, body }: { id: number; body: Parameters<typeof closeManualPosition>[1] }) => closeManualPosition(id, body),
        onSuccess: async () => {
            setCloseOpen(false);
            setCloseId(null);
            await invalidateManual();
        },
        onError: (err) => {
            const { code, message } = readFinanceApiError(err);
            if (code === 'SELL_PRICE_NOT_FOUND') {
                setCSellSubmitMode('manual');
                setCloseBanner(t('manualInvest.errSellPrice', 'Seçilen tarih için satış fiyatı bulunamadı. Lütfen satış fiyatını manuel girin.'));
            } else {
                setCloseBanner(message);
                alert(message);
            }
        },
    });

    const deleteMut = useMutation({
        mutationFn: deleteManualPosition,
        onSuccess: async () => {
            await invalidateManual();
        },
        onError: (err) => alert(readFinanceApiError(err).message),
    });

    const runBuyResolve = async () => {
        const sym = fSymbol.trim().toUpperCase();
        if (!sym) {
            alert(t('manualInvest.needSymbol', 'Önce sembol girin.'));
            return;
        }
        setResolveBusy('buy');
        setFormBanner(null);
        try {
            const r = await resolveManualPrice(fType, sym, fBuyDate);
            setLastBuyResolve(r);
            if (r.found && r.price != null) {
                setFBuyPriceStr(String(r.price));
                setBuySubmitMode('auto');
            } else {
                setBuySubmitMode('manual');
                setFormBanner(
                    r.message ??
                        t('manualInvest.buyResolveFail', 'Seçilen tarih için otomatik fiyat bulunamadı. Lütfen alış fiyatını manuel girin.'),
                );
            }
        } catch (e) {
            alert(readFinanceApiError(e).message);
        } finally {
            setResolveBusy(null);
        }
    };

    const runSellResolve = async () => {
        const sym = fSymbol.trim().toUpperCase();
        if (!sym) {
            alert(t('manualInvest.needSymbol', 'Önce sembol girin.'));
            return;
        }
        setResolveBusy('sell');
        setFormBanner(null);
        try {
            const r = await resolveManualPrice(fType, sym, fSellDate);
            setLastSellResolve(r);
            if (r.found && r.price != null) {
                setFSellPriceStr(String(r.price));
                setSellSubmitMode('auto');
            } else {
                setSellSubmitMode('manual');
                setFormBanner(
                    r.message ??
                        t('manualInvest.sellResolveFail', 'Seçilen tarih için satış fiyatı bulunamadı. Lütfen satış fiyatını manuel girin.'),
                );
            }
        } catch (e) {
            alert(readFinanceApiError(e).message);
        } finally {
            setResolveBusy(null);
        }
    };

    const runCloseResolve = async () => {
        const row = positions.find((p) => p.id === closeId);
        if (!row) return;
        setCResolveBusy(true);
        setCloseBanner(null);
        try {
            const r = await resolveManualPrice(String(row.type), String(row.symbol), cSellDate);
            setCLastResolve(r);
            if (r.found && r.price != null) {
                setCSellPriceStr(String(r.price));
                setCSellSubmitMode('auto');
            } else {
                setCSellSubmitMode('manual');
                setCloseBanner(
                    r.message ??
                        t('manualInvest.sellResolveFail', 'Seçilen tarih için satış fiyatı bulunamadı. Lütfen satış fiyatını manuel girin.'),
                );
            }
        } catch (e) {
            alert(readFinanceApiError(e).message);
        } finally {
            setCResolveBusy(false);
        }
    };

    const buildPayload = (): Record<string, unknown> => {
        const sym = fSymbol.trim().toUpperCase();
        const qty = Number(fQty);
        const buyFee = Number(fBuyFee || 0);
        const sellFee = Number(fSellFee || 0);
        const body: Record<string, unknown> = {
            type: fType,
            symbol: sym,
            quantity: qty,
            buyDate: fBuyDate,
            buyFee,
            note: fNote.trim() || undefined,
        };
        if (buySubmitMode === 'manual') {
            const bp = Number(fBuyPriceStr);
            body.buyPrice = bp;
        }
        if (positionStatus === 'SOLD') {
            body.status = 'SOLD';
            body.sellDate = fSellDate;
            body.sellFee = sellFee;
            if (sellSubmitMode === 'manual') {
                body.sellPrice = Number(fSellPriceStr);
            }
        } else {
            body.status = 'OPEN';
        }
        return body;
    };

    const submitForm = async (e: FormEvent) => {
        e.preventDefault();
        setFormBanner(null);
        const sym = fSymbol.trim().toUpperCase();
        if (!sym) {
            alert(t('manualInvest.needSymbol', 'Önce sembol girin.'));
            return;
        }
        const qty = Number(fQty);
        if (!qty || qty <= 0) {
            alert(t('manualInvest.needQty', 'Miktar pozitif olmalıdır.'));
            return;
        }
        if (buySubmitMode === 'manual') {
            const bp = Number(fBuyPriceStr);
            if (!bp || bp <= 0) {
                alert(t('manualInvest.needBuyPrice', 'Alış fiyatı girin.'));
                return;
            }
        }
        if (positionStatus === 'SOLD') {
            if (sellSubmitMode === 'manual') {
                const sp = Number(fSellPriceStr);
                if (!sp || sp <= 0) {
                    alert(t('manualInvest.needSellPrice', 'Satış fiyatı girin.'));
                    return;
                }
            }
        }
        const payload = buildPayload() as Parameters<typeof createManualPosition>[0];
        if (formMode === 'create') {
            createMut.mutate(payload);
        } else if (editId != null) {
            updateMut.mutate({ id: editId, body: payload });
        }
    };

    const openClose = (id: number) => {
        setCloseId(id);
        setCSellDate(istanbulTodayYmd());
        setCSellFee('0');
        setCSellPriceStr('');
        setCSellSubmitMode('auto');
        setCLastResolve(null);
        setCloseBanner(null);
        setCloseOpen(true);
    };

    const submitClose = (e: FormEvent) => {
        e.preventDefault();
        setCloseBanner(null);
        if (closeId == null) return;
        if (cSellSubmitMode === 'manual') {
            const sp = Number(cSellPriceStr);
            if (!sp || sp <= 0) {
                alert(t('manualInvest.needSellPrice', 'Satış fiyatı girin.'));
                return;
            }
        }
        const body: Record<string, unknown> = {
            sellDate: cSellDate,
            sellFee: Number(cSellFee || 0),
        };
        if (cSellSubmitMode === 'manual') {
            body.sellPrice = Number(cSellPriceStr);
        }
        closeMut.mutate({ id: closeId, body: body as Parameters<typeof closeManualPosition>[1] });
    };

    const openAnalysis = (id: number) => {
        setAnalysisId(id);
        setAnalysisOpen(true);
    };

    useImperativeHandle(
        ref,
        () => ({
            openCreate: openFormCreate,
            openEdit: openFormEdit,
            openAnalysis,
            openSellEntry: () => {
                const first = positions.find((p) => statusOf(p) === 'OPEN');
                if (first) openClose(first.id);
                else alert(t('portfolio.sellNoOpen', 'Satış girişi için açık pozisyon bulunmuyor.'));
            },
        }),
        [openFormCreate, openFormEdit, positions, t],
    );

    const cardSurface: CSSProperties = {
        background: tokens.bgCard,
        border: `1px solid ${tokens.border}`,
        color: tokens.text,
        borderRadius: 12,
        padding: 14,
    };

    const emptyManual = positions.length === 0;

    useEffect(() => {
        if (surface === 'full' && positions.length > 0) {
            void qc.invalidateQueries({ queryKey: manualPortfolioKeys.insights() });
        }
    }, [positions.length, surface, qc]);

    const [chartMetric, setChartMetric] = useState<'value' | 'price'>('value');

    return (
        <>
        {surface === 'full' ? (
        <section className="mia-root portfolio-fade-in portfolio-fade-in--delay-1" style={{ marginTop: 18 }}>
            <div className="mia-header" style={{ display: 'flex', flexWrap: 'wrap', gap: 12, alignItems: 'flex-start', justifyContent: 'space-between' }}>
                <div>
                    <h2 style={{ margin: 0, fontSize: '1.15rem', fontWeight: 800 }}>{t('manualInvest.title', 'Geçmiş Yatırım Analizi')}</h2>
                    <p style={{ margin: '6px 0 0', fontSize: '0.82rem', color: tokens.textMuted, maxWidth: 720, lineHeight: 1.45 }}>
                        {t(
                            'manualInvest.subtitle',
                            'Geçmişte aldığınız veya sattığınız varlıkları ekleyin; gerçekleşen kârı, güncel değerini ve satmasaydınız oluşabilecek sonucu karşılaştırın.',
                        )}
                    </p>
                </div>
                <button type="button" className="pf-btn-submit-silver" style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }} onClick={openFormCreate}>
                    <Plus size={18} />
                    {t('manualInvest.addBtn', 'Yeni yatırım ekle')}
                </button>
            </div>

            <div className="mia-info-strip" style={{ ...cardSurface, marginTop: 14, display: 'grid', gap: 10 }}>
                <div style={{ fontSize: '0.78rem', fontWeight: 700, display: 'flex', alignItems: 'center', gap: 8 }}>
                    <BookOpen size={16} />
                    {t('manualInvest.finLiteracy', 'Finansal okuryazarlık')}
                </div>
                <ul style={{ margin: 0, paddingLeft: 18, color: tokens.textMuted, fontSize: '0.78rem', lineHeight: 1.5 }}>
                    <li title={t('manualInvest.tipNominal', '')}>{t('manualInvest.tipNominal', 'Nominal getiri para cinsinden görünen getiridir; enflasyon etkisini içermez.')}</li>
                    <li title={t('manualInvest.tipMissed', '')}>
                        {t('manualInvest.tipMissed', 'Satmasaydım farkı: satış geliri ile bugün hâlâ tutsaydınız oluşabilecek değer arasındaki farktır.')}
                    </li>
                    <li title={t('manualInvest.tipAuto', '')}>
                        {t(
                            'manualInvest.tipAuto',
                            'Otomatik fiyat: seçilen günde veri yoksa en yakın önceki kapanış kullanılabilir. Veri yoksa manuel fiyat girmeniz gerekir.',
                        )}
                    </li>
                </ul>
            </div>

            <div className="mia-summary-grid" style={{ marginTop: 14, display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(160px, 1fr))', gap: 10 }}>
                {(
                    [
                        ['inv', t('manualInvest.sumInvested', 'Toplam yatırılan'), formatCurrencyTry(locale, summary?.totalInvested ?? 0)],
                        ['real', t('manualInvest.sumRealized', 'Gerçekleşen K/Z'), formatCurrencyTry(locale, summary?.realizedProfit ?? 0)],
                        ['unr', t('manualInvest.sumUnrealized', 'Açık K/Z'), formatCurrencyTry(locale, summary?.unrealizedProfit ?? 0)],
                        ['miss', t('manualInvest.sumMissed', 'Satmasaydım farkı'), formatCurrencyTry(locale, summary?.missedProfit ?? 0)],
                        [
                            'nom',
                            t('manualInvest.sumNominal', 'Toplam nominal'),
                            `${formatCurrencyTry(locale, summary?.totalNominalProfit ?? 0)} · ${formatPercent(locale, summary?.totalNominalReturnPct ?? 0)}`,
                        ],
                        [
                            'big',
                            t('manualInvest.sumBigMiss', 'En büyük kaçan fırsat'),
                            summary?.biggestMissedOpportunitySymbol
                                ? `${summary.biggestMissedOpportunitySymbol} · ${formatCurrencyTry(locale, summary?.biggestMissedProfit)}`
                                : '—',
                        ],
                    ] as const
                ).map(([key, label, val]) => (
                    <div key={key} style={cardSurface}>
                        <div style={{ fontSize: '0.72rem', color: tokens.textMuted, marginBottom: 6 }}>{label}</div>
                        <div style={{ fontSize: '0.95rem', fontWeight: 800 }}>{emptyManual ? t('manualInvest.noRecordsShort', 'Henüz kayıt yok') : val}</div>
                    </div>
                ))}
            </div>

            <div style={{ marginTop: 14, display: 'flex', flexWrap: 'wrap', gap: 10, alignItems: 'center', justifyContent: 'space-between' }}>
                <h3 style={{ margin: 0, fontSize: '0.95rem', fontWeight: 800 }}>{t('portfolio.insightsTitle', 'Portföy içgörüleri')}</h3>
                <button
                    type="button"
                    className="pf-btn-submit-silver"
                    style={{ fontSize: '0.78rem', padding: '6px 12px' }}
                    disabled={evaluateAlertsMutation.isPending || emptyManual}
                    onClick={() => evaluateAlertsMutation.mutate()}
                >
                    {evaluateAlertsMutation.isPending
                        ? t('common.loading', 'Yükleniyor...')
                        : t('portfolio.evaluateSmartAlerts', 'Akıllı Uyarıları Değerlendir')}
                </button>
            </div>

            {insightsQuery.isLoading && !insights ? (
                <p className="mia-muted" style={{ marginTop: 8, fontSize: '0.78rem' }}>
                    {t('common.loading', 'Yükleniyor...')}
                </p>
            ) : null}

            <div
                className="mia-summary-grid mia-insights-grid"
                style={{ marginTop: 10, display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))', gap: 10 }}
            >
                <div style={cardSurface}>
                    <div style={{ fontSize: '0.72rem', color: tokens.textMuted, marginBottom: 6 }}>
                        {t('portfolio.kpiNominalPnl', 'Nominal Getiri')}
                    </div>
                    <div style={{ fontSize: '0.95rem', fontWeight: 800 }} className={pnlClass(insightSummary?.nominalReturn)}>
                        {emptyManual || !insightSummary
                            ? '—'
                            : `${formatCurrencyTry(locale, insightSummary.nominalReturn)} · ${formatPercent(locale, insightSummary.nominalReturnPct)}`}
                    </div>
                </div>
                <div style={cardSurface}>
                    <div style={{ fontSize: '0.72rem', color: tokens.textMuted, marginBottom: 6 }}>
                        {t('portfolio.estRealLabel', 'Reel Getiri')}
                    </div>
                    <div style={{ fontSize: '0.95rem', fontWeight: 800 }} className={pnlClass(insightSummary?.realReturn)}>
                        {emptyManual || !insightSummary
                            ? '—'
                            : insightSummary.realReturnAvailable
                              ? `${formatCurrencyTry(locale, insightSummary.realReturn)} · ${formatPercent(locale, insightSummary.realReturnPct)}`
                              : t(
                                    'portfolio.realReturnUnavailable',
                                    'TÜFE verisi bu dönem için çözümlenemediği için reel getiri hesaplanamadı.',
                                )}
                    </div>
                </div>
                <div style={cardSurface}>
                    <div style={{ fontSize: '0.72rem', color: tokens.textMuted, marginBottom: 6 }}>
                        {t('portfolio.healthScoreTitle', 'Portföy Sağlık Skoru')}
                    </div>
                    <div style={{ fontSize: '0.95rem', fontWeight: 800 }}>
                        {emptyManual || !insights?.healthScore
                            ? '—'
                            : `${insights.healthScore.score} · ${insights.healthScore.level}`}
                    </div>
                    {insights?.healthScore?.summary ? (
                        <p style={{ margin: '6px 0 0', fontSize: '0.72rem', color: tokens.textMuted, lineHeight: 1.35 }}>
                            {insights.healthScore.summary}
                        </p>
                    ) : null}
                </div>
                <div style={cardSurface}>
                    <div style={{ fontSize: '0.72rem', color: tokens.textMuted, marginBottom: 6 }}>
                        {t('portfolio.concentrationRiskTitle', 'Konsantrasyon Riski')}
                    </div>
                    <div
                        style={{ fontSize: '0.95rem', fontWeight: 800 }}
                        className={riskLevelClass(insights?.concentrationRisk?.riskLevel)}
                    >
                        {emptyManual || !insights?.concentrationRisk
                            ? '—'
                            : `${insights.concentrationRisk.riskLevel}${insights.concentrationRisk.topAssetSymbol ? ` · ${insights.concentrationRisk.topAssetSymbol}` : ''}`}
                    </div>
                    {insights?.concentrationRisk?.message ? (
                        <p style={{ margin: '6px 0 0', fontSize: '0.72rem', color: tokens.textMuted, lineHeight: 1.35 }}>
                            {insights.concentrationRisk.message}
                        </p>
                    ) : null}
                </div>
            </div>

            {evaluateAlertsMutation.isSuccess ? (
                <p className="mia-pos" style={{ marginTop: 8, fontSize: '0.78rem' }}>
                    {t('portfolio.alertsGenerated', 'Uyarılar değerlendirildi')}: {evaluateAlertsMutation.data?.generatedCount ?? 0}
                </p>
            ) : null}
            {evaluateAlertsMutation.isError ? (
                <p style={{ marginTop: 8, fontSize: '0.78rem', color: tokens.error }}>
                    {readFinanceApiError(evaluateAlertsMutation.error).message}
                </p>
            ) : null}

            <div className="mia-toolbar" style={{ marginTop: 14, display: 'flex', flexWrap: 'wrap', gap: 10, alignItems: 'center' }}>
                {(
                    [
                        ['ALL', t('manualInvest.fAll', 'Tümü')],
                        ['OPEN', t('manualInvest.fOpen', 'Açık')],
                        ['SOLD', t('manualInvest.fSold', 'Satılmış')],
                    ] as const
                ).map(([k, lab]) => (
                    <button
                        key={k}
                        type="button"
                        className={statusFilter === k ? 'pf-segment--on' : ''}
                        style={{ padding: '8px 12px', borderRadius: 10, border: `1px solid ${tokens.border}`, background: statusFilter === k ? tokens.bg : tokens.bgCard, color: tokens.text, fontWeight: 600, cursor: 'pointer' }}
                        onClick={() => setStatusFilter(k)}
                    >
                        {lab}
                    </button>
                ))}
                <select
                    value={typeFilter}
                    onChange={(e) => setTypeFilter(e.target.value as TypeFilter)}
                    style={{ padding: '8px 10px', borderRadius: 10, border: `1px solid ${tokens.border}`, background: tokens.bgCard, color: tokens.text }}
                >
                    <option value="ALL">{t('manualInvest.fTypeAll', 'Tüm türler')}</option>
                    {ASSET_TYPE_ORDER.map((at) => (
                        <option key={at} value={at}>
                            {manualAssetTypeLabel(at)}
                        </option>
                    ))}
                </select>
                <div style={{ flex: '1 1 200px', minWidth: 160, position: 'relative' }}>
                    <Search size={16} style={{ position: 'absolute', left: 10, top: '50%', transform: 'translateY(-50%)', opacity: 0.45 }} />
                    <input
                        value={search}
                        onChange={(e) => setSearch(e.target.value)}
                        placeholder={t('manualInvest.searchPh', 'Sembol veya not ara…')}
                        style={{ width: '100%', padding: '9px 10px 9px 34px', borderRadius: 10, border: `1px solid ${tokens.border}`, background: tokens.bgCard, color: tokens.text }}
                    />
                </div>
            </div>

            {emptyManual ? (
                <div className="mia-empty" style={{ ...cardSurface, marginTop: 14, textAlign: 'center', padding: '36px 20px' }}>
                    <LineChartIcon size={40} style={{ opacity: 0.35, marginBottom: 8 }} />
                    <div style={{ fontWeight: 800, fontSize: '1rem' }}>{t('manualInvest.emptyTitle', 'Henüz geçmiş yatırım kaydı yok')}</div>
                    <p style={{ color: tokens.textMuted, fontSize: '0.85rem', maxWidth: 480, margin: '10px auto 0', lineHeight: 1.45 }}>
                        {t(
                            'manualInvest.emptyBody',
                            'Geçmişte aldığınız veya sattığınız varlıkları ekleyerek gerçekleşen kârınızı ve satmasaydınız oluşabilecek sonucu analiz edebilirsiniz.',
                        )}
                    </p>
                    <button type="button" className="pf-btn-submit-silver" style={{ marginTop: 16 }} onClick={openFormCreate}>
                        {t('manualInvest.emptyCta', 'İlk yatırımı ekle')}
                    </button>
                </div>
            ) : (
                <div className="mia-table-wrap pf-table-scroll" style={{ marginTop: 12 }}>
                    <table className="pf-table">
                        <thead>
                            <tr>
                                <th>{t('manualInvest.colAsset', 'Varlık')}</th>
                                <th>{t('manualInvest.colStatus', 'Durum')}</th>
                                <th>{t('manualInvest.colBuyD', 'Alış tarihi')}</th>
                                <th>{t('manualInvest.colSellD', 'Satış tarihi')}</th>
                                <th className="pf-th-num">{t('portfolio.colQty', 'Miktar')}</th>
                                <th className="pf-th-num">{t('manualInvest.colBuyCost', 'Alış maliyeti')}</th>
                                <th className="pf-th-num">{t('manualInvest.colCurVal', 'Güncel değer')}</th>
                                <th className="pf-th-num">{t('manualInvest.colSellProc', 'Satış geliri')}</th>
                                <th className="pf-th-num">{t('manualInvest.colPnl', 'K/Z')}</th>
                                <th className="pf-th-num">{t('manualInvest.colPct', 'Getiri %')}</th>
                                <th className="pf-th-num">{t('manualInvest.colMissed', 'Satmasaydım')}</th>
                                <th className="pf-th-num">{t('portfolio.colActions', 'İşlem')}</th>
                            </tr>
                        </thead>
                        <tbody>
                            {pageSlice.map((p) => {
                                const st = statusOf(p);
                                const isOpen = st === 'OPEN';
                                const pnl = isOpen ? n(p.unrealizedProfit) : n(p.realizedProfit);
                                const pct = isOpen ? n(p.unrealizedReturnPct) : n(p.realizedReturnPct);
                                return (
                                    <tr key={p.id} className="mia-row-click" style={{ cursor: 'pointer' }} onClick={() => openAnalysis(p.id)}>
                                        <td>
                                            <span className="pf-num-strong">{p.symbol}</span>
                                            <span style={{ display: 'block', fontSize: '0.72rem', color: tokens.textMuted }}>
                                                {manualAssetTypeLabel(String(p.type).toUpperCase() as ManualAssetType)}
                                            </span>
                                        </td>
                                        <td>
                                            <span className={`mia-badge mia-badge--${isOpen ? 'open' : 'sold'}`}>{st}</span>
                                        </td>
                                        <td>{p.buyDate ? String(p.buyDate).slice(0, 10) : '—'}</td>
                                        <td>{p.sellDate ? String(p.sellDate).slice(0, 10) : '—'}</td>
                                        <td className="pf-num-strong" style={{ textAlign: 'right' }}>
                                            {n(p.quantity)?.toLocaleString(locale, { maximumFractionDigits: 8 }) ?? '—'}
                                        </td>
                                        <td className="pf-num-strong" style={{ textAlign: 'right' }}>
                                            {formatCurrencyTry(locale, p.buyCost)}
                                        </td>
                                        <td className="pf-num-strong" style={{ textAlign: 'right' }}>
                                            {isOpen ? formatCurrencyTry(locale, p.currentValue) : '—'}
                                        </td>
                                        <td className="pf-num-strong" style={{ textAlign: 'right' }}>
                                            {!isOpen ? formatCurrencyTry(locale, p.sellProceeds) : '—'}
                                        </td>
                                        <td className={`pf-num-strong ${pnlClass(pnl)}`} style={{ textAlign: 'right' }}>
                                            {pnl != null ? formatCurrencyTry(locale, pnl) : '—'}
                                        </td>
                                        <td className={`pf-num-strong ${pnlClass(pct)}`} style={{ textAlign: 'right' }}>
                                            {formatPercent(locale, pct)}
                                        </td>
                                        <td className={`pf-num-strong ${pnlClass(p.missedProfit)}`} style={{ textAlign: 'right' }}>
                                            {!isOpen ? formatCurrencyTry(locale, p.missedProfit) : '—'}
                                        </td>
                                        <td style={{ textAlign: 'right' }} onClick={(ev) => ev.stopPropagation()}>
                                            {isOpen ? (
                                                <button type="button" className="mia-btn-sm" onClick={() => openClose(p.id)}>
                                                    {t('manualInvest.soldBtn', 'Sattım')}
                                                </button>
                                            ) : null}{' '}
                                            <button type="button" className="mia-btn-sm" onClick={() => openFormEdit(p.id)}>
                                                {t('common.update', 'Düzenle')}
                                            </button>{' '}
                                            <button
                                                type="button"
                                                className="mia-btn-sm mia-btn-sm--danger"
                                                onClick={() => {
                                                    if (window.confirm(t('portfolio.confirmDelete', 'Bu manuel pozisyonu silmek istediğinize emin misiniz?'))) {
                                                        deleteMut.mutate(p.id);
                                                    }
                                                }}
                                            >
                                                {t('portfolio.delete', 'Sil')}
                                            </button>
                                        </td>
                                    </tr>
                                );
                            })}
                        </tbody>
                    </table>
                    {totalPages > 1 && (
                        <div className="pf-pagination" style={{ marginTop: 10 }}>
                            <button type="button" className="pf-page-btn" disabled={tablePage <= 0} onClick={() => setTablePage((x) => Math.max(0, x - 1))}>
                                {t('portfolio.prev', 'Önceki')}
                            </button>
                            <span style={{ fontSize: '0.78rem', color: tokens.textMuted }}>
                                {tablePage + 1}/{totalPages}
                            </span>
                            <button type="button" className="pf-page-btn" disabled={tablePage >= totalPages - 1} onClick={() => setTablePage((x) => Math.min(totalPages - 1, x + 1))}>
                                {t('portfolio.next', 'Sonraki')}
                            </button>
                        </div>
                    )}
                </div>
            )}
        </section>
        ) : null}

            {formOpen ? (
                <div className="mia-modal-backdrop" role="presentation" onMouseDown={() => setFormOpen(false)}>
                    <div className="mia-modal" role="dialog" aria-modal onMouseDown={(ev) => ev.stopPropagation()} style={{ ...cardSurface, maxWidth: 520, width: '100%', maxHeight: '90vh', overflow: 'auto' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
                            <h3 style={{ margin: 0, fontSize: '1rem' }}>
                                {formMode === 'create' ? t('manualInvest.modalCreate', 'Yeni yatırım') : t('manualInvest.modalEdit', 'Yatırımı düzenle')}
                            </h3>
                            <button type="button" className="mia-icon-btn" aria-label={t('market.drawerClose', 'Kapat')} onClick={() => setFormOpen(false)}>
                                <X size={18} />
                            </button>
                        </div>
                        {formBanner ? (
                            <div style={{ fontSize: '0.8rem', color: tokens.error, marginBottom: 10, padding: 8, borderRadius: 8, border: `1px solid ${tokens.border}` }}>{formBanner}</div>
                        ) : null}
                        <form onSubmit={submitForm} className="mia-form">
                            <label className="pf-field-label">
                                {t('portfolio.assetType', 'Varlık türü')}
                                <select
                                    className="pf-input"
                                    value={fType}
                                    onChange={(e) => {
                                        setFType(e.target.value as ManualAssetType);
                                        setFSymbol('');
                                    }}
                                >
                                    {ASSET_TYPE_ORDER.map((at) => (
                                        <option key={at} value={at}>
                                            {manualAssetTypeLabel(at)}
                                        </option>
                                    ))}
                                </select>
                            </label>
                            <label className="pf-field-label">
                                {t('portfolio.symbol', 'Sembol')}
                                <select
                                    className="pf-input"
                                    value={fSymbol}
                                    onChange={(e) => setFSymbol(e.target.value)}
                                >
                                    <option value="">{t('manualInvest.pickSymbol', 'Sembol seçin')}</option>
                                    {symbolSelectOptions.map((o) => (
                                        <option key={o.value} value={o.value}>
                                            {o.label}
                                        </option>
                                    ))}
                                </select>
                            </label>
                            <label className="pf-field-label">
                                {t('portfolio.quantity', 'Miktar')}
                                <input className="pf-input" type="number" step="any" value={fQty} onChange={(e) => setFQty(e.target.value)} />
                            </label>
                            <label className="pf-field-label">
                                {t('portfolio.buyDate', 'Alış tarihi')}
                                <input className="pf-input" type="date" value={fBuyDate} onChange={(e) => setFBuyDate(e.target.value)} />
                            </label>
                            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 10 }}>
                                <button type="button" className="mia-btn-sm" disabled={resolveBusy === 'buy'} onClick={() => void runBuyResolve()}>
                                    {resolveBusy === 'buy' ? '…' : t('manualInvest.resolveBuy', 'Alış fiyatını otomatik bul')}
                                </button>
                                {lastBuyResolve?.found ? (
                                    <span className="mia-muted" style={{ fontSize: '0.78rem', alignSelf: 'center' }}>
                                        {formatCurrencyTry(locale, lastBuyResolve.price)} · {priceSourceLabel(t, lastBuyResolve.source as string)}
                                        {lastBuyResolve.resolvedDate ? ` · ${String(lastBuyResolve.resolvedDate).slice(0, 10)}` : ''}
                                    </span>
                                ) : null}
                            </div>
                            <label className="pf-field-label">
                                {t('portfolio.buyPriceTry', 'Birim fiyat (TRY)')}
                                <input
                                    className="pf-input"
                                    type="number"
                                    step="any"
                                    value={fBuyPriceStr}
                                    onChange={(e) => {
                                        setFBuyPriceStr(e.target.value);
                                        setBuySubmitMode('manual');
                                    }}
                                    disabled={buySubmitMode === 'auto' && lastBuyResolve?.found === true}
                                />
                                {buySubmitMode === 'auto' && lastBuyResolve?.found ? (
                                    <button type="button" className="mia-link-btn" onClick={() => setBuySubmitMode('manual')}>
                                        {t('manualInvest.overrideManual', 'Manuel değiştir')}
                                    </button>
                                ) : null}
                            </label>
                            <label className="pf-field-label">
                                {t('manualInvest.buyFee', 'Alış masrafı')}
                                <input className="pf-input" type="number" step="any" value={fBuyFee} onChange={(e) => setFBuyFee(e.target.value)} />
                            </label>
                            <fieldset style={{ border: `1px solid ${tokens.border}`, borderRadius: 10, padding: 10 }}>
                                <legend style={{ fontSize: '0.78rem', color: tokens.textMuted }}>{t('manualInvest.holding', 'Durum')}</legend>
                                <label style={{ marginRight: 12 }}>
                                    <input type="radio" checked={positionStatus === 'OPEN'} onChange={() => setPositionStatus('OPEN')} /> {t('manualInvest.stOpen', 'Hâlâ elimde')}
                                </label>
                                <label>
                                    <input type="radio" checked={positionStatus === 'SOLD'} onChange={() => setPositionStatus('SOLD')} /> {t('manualInvest.stSold', 'Sattım')}
                                </label>
                            </fieldset>
                            {positionStatus === 'SOLD' ? (
                                <>
                                    <label className="pf-field-label">
                                        {t('manualInvest.sellDate', 'Satış tarihi')}
                                        <input className="pf-input" type="date" value={fSellDate} onChange={(e) => setFSellDate(e.target.value)} />
                                    </label>
                                    <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 10 }}>
                                        <button type="button" className="mia-btn-sm" disabled={resolveBusy === 'sell'} onClick={() => void runSellResolve()}>
                                            {resolveBusy === 'sell' ? '…' : t('manualInvest.resolveSell', 'Satış fiyatını otomatik bul')}
                                        </button>
                                        {lastSellResolve?.found ? (
                                            <span className="mia-muted" style={{ fontSize: '0.78rem', alignSelf: 'center' }}>
                                                {formatCurrencyTry(locale, lastSellResolve.price)} · {priceSourceLabel(t, lastSellResolve.source as string)}
                                                {lastSellResolve.resolvedDate ? ` · ${String(lastSellResolve.resolvedDate).slice(0, 10)}` : ''}
                                            </span>
                                        ) : null}
                                    </div>
                                    <label className="pf-field-label">
                                        {t('manualInvest.sellPrice', 'Satış fiyatı (TRY)')}
                                        <input
                                            className="pf-input"
                                            type="number"
                                            step="any"
                                            value={fSellPriceStr}
                                            onChange={(e) => {
                                                setFSellPriceStr(e.target.value);
                                                setSellSubmitMode('manual');
                                            }}
                                            disabled={sellSubmitMode === 'auto' && lastSellResolve?.found === true}
                                        />
                                        {sellSubmitMode === 'auto' && lastSellResolve?.found ? (
                                            <button type="button" className="mia-link-btn" onClick={() => setSellSubmitMode('manual')}>
                                                {t('manualInvest.overrideManual', 'Manuel değiştir')}
                                            </button>
                                        ) : null}
                                    </label>
                                    <label className="pf-field-label">
                                        {t('manualInvest.sellFee', 'Satış masrafı')}
                                        <input className="pf-input" type="number" step="any" value={fSellFee} onChange={(e) => setFSellFee(e.target.value)} />
                                    </label>
                                </>
                            ) : null}
                            <label className="pf-field-label">
                                {t('portfolio.note', 'Not')}
                                <textarea className="pf-input" rows={2} value={fNote} onChange={(e) => setFNote(e.target.value)} />
                            </label>
                            <div style={{ display: 'flex', gap: 8, marginTop: 8 }}>
                                <button type="submit" className="pf-btn-submit-silver" disabled={createMut.isPending || updateMut.isPending}>
                                    {formMode === 'create' ? t('portfolio.addPosition', 'Pozisyon ekle') : t('common.update', 'Güncelle')}
                                </button>
                                <button type="button" className="mia-btn-sm" onClick={() => setFormOpen(false)}>
                                    {t('common.cancel', 'İptal')}
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            ) : null}

            {closeOpen && closeId != null ? (
                <div className="mia-modal-backdrop" role="presentation" onMouseDown={() => setCloseOpen(false)}>
                    <div className="mia-modal" role="dialog" aria-modal onMouseDown={(ev) => ev.stopPropagation()} style={{ ...cardSurface, maxWidth: 420, width: '100%' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
                            <h3 style={{ margin: 0 }}>{t('manualInvest.closeTitle', 'Pozisyonu kapat (sattım)')}</h3>
                            <button type="button" className="mia-icon-btn" onClick={() => setCloseOpen(false)}>
                                <X size={18} />
                            </button>
                        </div>
                        {closeBanner ? <div style={{ color: tokens.error, fontSize: '0.8rem', marginBottom: 8 }}>{closeBanner}</div> : null}
                        <form onSubmit={submitClose}>
                            <label className="pf-field-label">
                                {t('manualInvest.sellDate', 'Satış tarihi')}
                                <input className="pf-input" type="date" value={cSellDate} onChange={(e) => setCSellDate(e.target.value)} />
                            </label>
                            <div style={{ display: 'flex', gap: 8, marginBottom: 10 }}>
                                <button type="button" className="mia-btn-sm" disabled={cResolveBusy} onClick={() => void runCloseResolve()}>
                                    {cResolveBusy ? '…' : t('manualInvest.resolveSell', 'Satış fiyatını otomatik bul')}
                                </button>
                            </div>
                            <label className="pf-field-label">
                                {t('manualInvest.sellPrice', 'Satış fiyatı (TRY)')}
                                <input
                                    className="pf-input"
                                    type="number"
                                    step="any"
                                    value={cSellPriceStr}
                                    onChange={(e) => {
                                        setCSellPriceStr(e.target.value);
                                        setCSellSubmitMode('manual');
                                    }}
                                    disabled={cSellSubmitMode === 'auto' && cLastResolve?.found === true}
                                />
                            </label>
                            <label className="pf-field-label">
                                {t('manualInvest.sellFee', 'Satış masrafı')}
                                <input className="pf-input" type="number" step="any" value={cSellFee} onChange={(e) => setCSellFee(e.target.value)} />
                            </label>
                            <button type="submit" className="pf-btn-submit-silver" style={{ marginTop: 10 }} disabled={closeMut.isPending}>
                                {t('manualInvest.confirmClose', 'Kapat ve kaydet')}
                            </button>
                        </form>
                    </div>
                </div>
            ) : null}

            {analysisOpen && analysisId != null ? (
                <div className="mia-drawer-backdrop" onMouseDown={() => setAnalysisOpen(false)}>
                    <aside className="mia-drawer" onMouseDown={(e) => e.stopPropagation()} style={{ background: tokens.bgCard, color: tokens.text, borderLeft: `1px solid ${tokens.border}` }}>
                        <div className="mia-drawer-head" style={{ borderBottom: `1px solid ${tokens.border}` }}>
                            <div>
                                <div style={{ fontWeight: 800, fontSize: '1.05rem' }}>{analysisQuery.data?.position.symbol ?? '…'}</div>
                                <div style={{ fontSize: '0.78rem', color: tokens.textMuted }}>
                                    {analysisQuery.data?.position.type} · <span className={`mia-badge mia-badge--${statusOf(analysisQuery.data?.position as ManualPortfolioView) === 'OPEN' ? 'open' : 'sold'}`}>{statusOf(analysisQuery.data?.position as ManualPortfolioView)}</span>
                                </div>
                            </div>
                            <button type="button" className="mia-icon-btn" onClick={() => setAnalysisOpen(false)}>
                                <X size={18} />
                            </button>
                        </div>
                        <div className="mia-drawer-body" style={{ padding: 12 }}>
                            {analysisQuery.isLoading ? (
                                <p style={{ color: tokens.textMuted }}>{t('common.loading', 'Yükleniyor…')}</p>
                            ) : analysisQuery.error ? (
                                <p style={{ color: tokens.error }}>{readFinanceApiError(analysisQuery.error).message}</p>
                            ) : analysisQuery.data ? (
                                <AnalysisBody data={analysisQuery.data} tokens={tokens} locale={locale} t={t} chartMetric={chartMetric} setChartMetric={setChartMetric} />
                            ) : null}
                        </div>
                        {analysisQuery.data ? (
                            <div
                                className="mia-drawer-footer"
                                style={{
                                    padding: '12px 14px',
                                    borderTop: `1px solid ${tokens.border}`,
                                    display: 'flex',
                                    flexWrap: 'wrap',
                                    gap: 8,
                                }}
                            >
                                <button
                                    type="button"
                                    className="mia-btn-sm"
                                    onClick={() => {
                                        const id = analysisId;
                                        if (id == null) return;
                                        setAnalysisOpen(false);
                                        openFormEdit(id);
                                    }}
                                >
                                    {t('common.update', 'Düzenle')}
                                </button>
                                {statusOf(analysisQuery.data.position) === 'OPEN' ? (
                                    <button
                                        type="button"
                                        className="mia-btn-sm"
                                        onClick={() => {
                                            const id = analysisId;
                                            if (id == null) return;
                                            setAnalysisOpen(false);
                                            openClose(id);
                                        }}
                                    >
                                        {t('portfolio.drawerSell', 'Satış gir')}
                                    </button>
                                ) : null}
                                <button
                                    type="button"
                                    className="mia-btn-sm mia-btn-sm--danger"
                                    onClick={() => {
                                        const id = analysisId;
                                        if (id == null) return;
                                        if (window.confirm(t('portfolio.confirmDelete', 'Bu manuel pozisyonu silmek istediğinize emin misiniz?'))) {
                                            deleteMut.mutate(id);
                                            setAnalysisOpen(false);
                                        }
                                    }}
                                >
                                    {t('portfolio.delete', 'Sil')}
                                </button>
                            </div>
                        ) : null}
                    </aside>
                </div>
            ) : null}
        </>
    );
});

ManualInvestmentAnalysisSection.displayName = 'ManualInvestmentAnalysisSection';

function AnalysisBody({
    data,
    tokens,
    locale,
    t,
    chartMetric,
    setChartMetric,
}: {
    data: ManualPortfolioAnalysis;
    tokens: Props['tokens'];
    locale: string;
    t: (k: string, d: string) => string;
    chartMetric: 'value' | 'price';
    setChartMetric: (m: 'value' | 'price') => void;
}) {
    const p = data.position;
    const isOpen = statusOf(p) === 'OPEN';
    const chartRows = useMemo(() => {
        return (data.chartSeries ?? [])
            .map((pt) => ({
                name: String(pt.date).slice(0, 10),
                price: n(pt.price),
                value: n(pt.value),
                y: chartMetric === 'price' ? n(pt.price) : n(pt.value),
            }))
            .filter((r) => r.y != null && Number.isFinite(r.y));
    }, [data.chartSeries, chartMetric]);

    const fallbackDateNote =
        p.buyPriceSource === 'MARKET_HISTORY_PREVIOUS_CLOSE'
        || p.sellPriceSource === 'MARKET_HISTORY_PREVIOUS_CLOSE'
        || p.buyPriceSource === 'MARKET_HISTORY_NEXT_CLOSE'
        || p.sellPriceSource === 'MARKET_HISTORY_NEXT_CLOSE';
    const sameDayHourlyNote =
        p.buyPriceSource === 'MARKET_HISTORY_SAME_DAY_HOURLY' || p.sellPriceSource === 'MARKET_HISTORY_SAME_DAY_HOURLY';

    return (
        <div>
            <p style={{ fontSize: '0.78rem', color: tokens.textMuted, marginTop: 0 }}>
                {t('manualInvest.buyD', 'Alış')}: {p.buyDate ? String(p.buyDate).slice(0, 10) : '—'}
                {p.sellDate ? ` · ${t('manualInvest.sellD', 'Satış')}: ${String(p.sellDate).slice(0, 10)}` : ''}
            </p>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(140px, 1fr))', gap: 8, marginBottom: 12 }}>
                {isOpen ? (
                    <>
                        <MetricMini label={t('manualInvest.colBuyCost', 'Alış maliyeti')} value={formatCurrencyTry(locale, p.buyCost)} />
                        <MetricMini label={t('manualInvest.colCurVal', 'Güncel değer')} value={formatCurrencyTry(locale, p.currentValue)} />
                        <MetricMini label={t('manualInvest.colPnl', 'K/Z')} value={formatCurrencyTry(locale, p.unrealizedProfit)} valueClass={pnlClass(p.unrealizedProfit)} />
                        <MetricMini label={t('manualInvest.colPct', 'Getiri %')} value={formatPercent(locale, p.unrealizedReturnPct)} valueClass={pnlClass(p.unrealizedReturnPct)} />
                    </>
                ) : (
                    <>
                        <MetricMini label={t('manualInvest.colBuyCost', 'Alış maliyeti')} value={formatCurrencyTry(locale, p.buyCost)} />
                        <MetricMini label={t('manualInvest.colSellProc', 'Satış geliri')} value={formatCurrencyTry(locale, p.sellProceeds)} />
                        <MetricMini label={t('manualInvest.realizedPnl', 'Gerçekleşen K/Z')} value={formatCurrencyTry(locale, p.realizedProfit)} valueClass={pnlClass(p.realizedProfit)} />
                        <MetricMini label={t('manualInvest.holdToday', 'Satmasaydım bugün')} value={formatCurrencyTry(locale, p.holdValueToday)} />
                        <MetricMini label={t('manualInvest.colMissed', 'Satmasaydım farkı')} value={formatCurrencyTry(locale, p.missedProfit)} valueClass={pnlClass(p.missedProfit)} />
                        <MetricMini label={t('manualInvest.missedPct', 'Satmasaydım getiri %')} value={formatPercent(locale, p.missedReturnPct)} valueClass={pnlClass(p.missedReturnPct)} />
                    </>
                )}
            </div>

            <div style={{ fontSize: '0.75rem', color: tokens.textMuted, marginBottom: 8 }}>
                <strong>{t('manualInvest.priceSources', 'Fiyat kaynakları')}:</strong> {t('manualInvest.buy', 'Alış')}: {priceSourceLabel(t, p.buyPriceSource)}{' '}
                {p.sellPriceSource ? `· ${t('manualInvest.sell', 'Satış')}: ${priceSourceLabel(t, p.sellPriceSource)}` : ''}
                {fallbackDateNote ? (
                    <span>
                        {' '}
                        <Info size={14} style={{ verticalAlign: 'text-bottom', marginLeft: 4 }} aria-hidden />
                        {t('manualInvest.prevCloseHint', 'Seçilen tarihte günlük fiyat bulunamadığı için en yakın önceki veya sonraki kapanış kullanılmış olabilir.')}
                    </span>
                ) : null}
                {sameDayHourlyNote ? (
                    <span>
                        {' '}
                        <Info size={14} style={{ verticalAlign: 'text-bottom', marginLeft: 4 }} aria-hidden />
                        {t('manualInvest.sameDayHourlyHint', 'Seçilen gün için günlük kayıt yerine aynı günün son saatlik fiyatı kullanılmış olabilir.')}
                    </span>
                ) : null}
            </div>

            <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 8 }}>
                <span style={{ fontSize: '0.78rem', fontWeight: 700 }}>{t('manualInvest.chart', 'Grafik')}</span>
                <button type="button" className={chartMetric === 'value' ? 'mia-btn-sm' : 'mia-btn-ghost'} onClick={() => setChartMetric('value')}>
                    {t('manualInvest.chartVal', 'Değer')}
                </button>
                <button type="button" className={chartMetric === 'price' ? 'mia-btn-sm' : 'mia-btn-ghost'} onClick={() => setChartMetric('price')}>
                    {t('manualInvest.chartPrice', 'Fiyat')}
                </button>
            </div>

            {chartRows.length === 0 ? (
                <p style={{ fontSize: '0.8rem', color: tokens.textMuted, lineHeight: 1.45 }}>
                    {t(
                        'manualInvest.chartEmpty',
                        'Bu varlık için seçilen aralıkta grafik verisi bulunamadı. Hesaplanan metrikler mevcut fiyat/veri durumuna göre gösteriliyor.',
                    )}
                </p>
            ) : (
                <div style={{ width: '100%', height: 220 }}>
                    <ResponsiveContainer>
                        <LineChart data={chartRows} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
                            <CartesianGrid strokeDasharray="3 3" stroke={tokens.border} opacity={0.45} />
                            <XAxis dataKey="name" tick={{ fontSize: 10, fill: tokens.textMuted }} />
                            <YAxis tick={{ fontSize: 10, fill: tokens.textMuted }} domain={['auto', 'auto']} />
                            <Tooltip
                                contentStyle={{ background: tokens.bgCard, border: `1px solid ${tokens.border}`, borderRadius: 8 }}
                                formatter={(v: number | undefined) => (v == null ? '' : Number(v).toLocaleString(locale, { maximumFractionDigits: 6 }))}
                            />
                            <Line type="monotone" dataKey="y" stroke="#60a5fa" dot={false} strokeWidth={2} isAnimationActive={false} name={chartMetric === 'price' ? 'Price' : 'Value'} />
                            <Legend />
                        </LineChart>
                    </ResponsiveContainer>
                </div>
            )}

            <div style={{ marginTop: 12 }}>
                <div style={{ fontSize: '0.78rem', fontWeight: 700, marginBottom: 6 }}>{t('manualInvest.markers', 'Önemli noktalar')}</div>
                <ul style={{ margin: 0, paddingLeft: 16, fontSize: '0.78rem', color: tokens.textMuted, lineHeight: 1.55 }}>
                    {(data.markers ?? []).map((m, i) => (
                        <li key={`${m.type}-${i}`}>
                            <strong>{m.type}</strong>: {String(m.date).slice(0, 10)} · {formatCurrencyTry(locale, m.price)} · {formatCurrencyTry(locale, m.value)}
                        </li>
                    ))}
                </ul>
            </div>
        </div>
    );
}

function MetricMini({ label, value, valueClass }: { label: string; value: string; valueClass?: string }) {
    return (
        <div style={{ borderRadius: 10, border: '1px solid var(--app-surface-border)', padding: 8 }}>
            <div style={{ fontSize: '0.68rem', opacity: 0.85 }}>{label}</div>
            <div className={valueClass} style={{ fontWeight: 800, fontSize: '0.88rem' }}>
                {value}
            </div>
        </div>
    );
}
