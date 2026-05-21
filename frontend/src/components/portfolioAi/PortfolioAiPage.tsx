import { useCallback, useEffect, useMemo, useState, type CSSProperties } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import axios from 'axios';
import { Info, Sparkles, Trash2 } from 'lucide-react';
import { useLanguage } from '../../i18n/LanguageContext';
import { useTheme } from '../../theme/ThemeContext';
import { manualPortfolioKeys } from '../../queries/manualPortfolioKeys';
import { getManualPositions } from '../../services/manualPortfolioApi';
import {
    deletePortfolioAiAnalysis,
    getAiAnalysisById,
    getAiAnalysisHistory,
    getLatestPortfolioAiAnalysis,
    getPortfolioContextSnapshot,
    requestCurrentPortfolioAiAnalysis,
} from '../../services/portfolioAiApi';
import { readFinanceApiError } from '../../services/manualPortfolioApi';
import type {
    AiAnalysisHistoryFilters,
    AiAnalysisResult,
    AiDetailLevel,
    AiRiskProfile,
    CurrentPortfolioAiAnalysisRequest,
    PortfolioAnalysisType,
} from '../../types/portfolioAi';
import { PortfolioAiAssetDetail } from './PortfolioAiAssetDetail';
import { PortfolioAiGenerationBadge } from './PortfolioAiGenerationBadge';
import { buildAssetRows } from './portfolioAiAssetRows';
import { PortfolioAiAssetTable } from './PortfolioAiAssetTable';
import { PortfolioAiConfigCard } from './PortfolioAiConfigCard';
import { PortfolioAiEmailDeliveryPanel } from './PortfolioAiEmailDeliveryPanel';
import { PortfolioAiResultsBlock } from './PortfolioAiResultsBlock';
import { PortfolioAiSummaryCard } from './PortfolioAiSummaryCard';
import { DIST_COLORS, openDistributionByType } from './portfolioAiDistribution';
import { fmtDate } from './portfolioAiFormat';
import '../../pages/Portfolio.css';
import '../../pages/PortfolioAiAnalysis.css';

export function PortfolioAiPage() {
    const { t, lang } = useLanguage();
    const { tokens } = useTheme();
    const locale = lang === 'en' ? 'en-GB' : 'tr-TR';

    const pageStyle = {
        '--tp-bg': tokens.bg,
        '--tp-card': tokens.bgCard,
        '--tp-border': tokens.border,
        '--tp-text': tokens.text,
        '--tp-muted': tokens.textMuted,
    } as CSSProperties;

    const [displayResult, setDisplayResult] = useState<AiAnalysisResult | null>(null);
    const [viewingHistoryId, setViewingHistoryId] = useState<string | null>(null);
    const [errorMsg, setErrorMsg] = useState<string | null>(null);
    const [titleError, setTitleError] = useState<string | null>(null);
    const [analysisTitle, setAnalysisTitle] = useState('');
    const [pfType, setPfType] = useState<PortfolioAnalysisType>('GENERAL');
    const [risk, setRisk] = useState<AiRiskProfile>('BALANCED');
    const [detail, setDetail] = useState<AiDetailLevel>('DETAILED');
    const [incNews, setIncNews] = useState(true);
    const [incMacro, setIncMacro] = useState(true);
    const [incInflation, setIncInflation] = useState(true);
    const incAlloc = true;
    const [assetSearch, setAssetSearch] = useState('');
    const [selectedSymbol, setSelectedSymbol] = useState<string | null>(null);
    const [historyFilters, setHistoryFilters] = useState<AiAnalysisHistoryFilters>({
        kind: 'ALL',
        dateRange: '30D',
        symbolSearch: '',
    });

    const { data: ctx, isLoading: ctxLoading } = useQuery({
        queryKey: ['portfolio-ai', 'context'],
        queryFn: getPortfolioContextSnapshot,
    });
    const { data: positions = [] } = useQuery({
        queryKey: manualPortfolioKeys.positions(),
        queryFn: getManualPositions,
    });
    const { data: history = [], refetch: refetchHistory } = useQuery({
        queryKey: ['portfolio-ai', 'history', historyFilters],
        queryFn: () => getAiAnalysisHistory(historyFilters),
    });

    const typeLabel = useCallback(
        (k: string) => t(`portfolio.typeLabel.${String(k).toUpperCase()}`, String(k).toUpperCase()),
        [t],
    );
    const distribution = useMemo(
        () => openDistributionByType(positions, typeLabel),
        [positions, typeLabel],
    );
    const pieData = useMemo(
        () => distribution.map((d, i) => ({ name: d.name, value: d.pct, fill: DIST_COLORS[i % DIST_COLORS.length] })),
        [distribution],
    );

    const portfolioResult = displayResult?.kind === 'PORTFOLIO' ? displayResult : null;

    const assetRows = useMemo(() => {
        if (!portfolioResult) return buildAssetRows(positions, []);
        return buildAssetRows(positions, portfolioResult.assetComments);
    }, [positions, portfolioResult]);

    const selectedRow = useMemo(
        () => assetRows.find((r) => r.symbol === selectedSymbol) ?? assetRows[0] ?? null,
        [assetRows, selectedSymbol],
    );

    useEffect(() => {
        if (assetRows.length > 0 && !selectedSymbol) {
            setSelectedSymbol(assetRows[0].symbol);
        }
    }, [assetRows, selectedSymbol]);

    const portfolioPayload = (): CurrentPortfolioAiAnalysisRequest => ({
        title: analysisTitle.trim(),
        analysisType: pfType,
        riskProfile: risk,
        detailLevel: detail,
        includeNews: incNews,
        includeMacro: incMacro,
        includeInflation: incInflation,
        includeAllocation: incAlloc,
    });

    const { data: latestAnalysis, refetch: refetchLatest } = useQuery({
        queryKey: ['portfolio-ai', 'latest'],
        queryFn: getLatestPortfolioAiAnalysis,
        staleTime: 30_000,
    });

    useEffect(() => {
        if (latestAnalysis && !displayResult && !viewingHistoryId) {
            setDisplayResult(latestAnalysis);
        }
    }, [latestAnalysis, displayResult, viewingHistoryId]);

    const portfolioMutation = useMutation({
        mutationFn: requestCurrentPortfolioAiAnalysis,
        onSuccess: (r) => {
            setDisplayResult(r);
            setViewingHistoryId(null);
            setErrorMsg(null);
            setSelectedSymbol(null);
            void refetchHistory();
            void refetchLatest();
        },
        onError: (err) => {
            const { message } = readFinanceApiError(err);
            const timedOut = axios.isAxiosError(err) && err.code === 'ECONNABORTED';
            if (timedOut) {
                setErrorMsg(
                    t(
                        'portfolioAi.analysisTimeout',
                        'Analiz beklenenden uzun sürdü (zaman aşımı). Birkaç saniye sonra sayfayı yenileyin; sonuç kaydedilmiş olabilir.',
                    ),
                );
                void refetchLatest().then(({ data: latest }) => {
                    if (latest?.kind === 'PORTFOLIO') {
                        setDisplayResult(latest);
                        setErrorMsg(null);
                    }
                });
                return;
            }
            setErrorMsg(message || t('portfolioAi.analysisFailed', 'Analiz oluşturulamadı.'));
        },
    });

    const deleteMutation = useMutation({
        mutationFn: deletePortfolioAiAnalysis,
        onSuccess: async (_data, deletedId) => {
            const wasViewing =
                viewingHistoryId === deletedId || displayResult?.id === deletedId;
            void refetchHistory();
            if (wasViewing) {
                setViewingHistoryId(null);
                setDisplayResult(null);
                setSelectedSymbol(null);
                const { data: latest } = await refetchLatest();
                if (latest?.kind === 'PORTFOLIO') {
                    setDisplayResult(latest);
                }
            }
        },
        onError: () => {
            setErrorMsg(t('portfolioAi.deleteFailed', 'Analiz silinemedi.'));
        },
    });

    /** Yalnızca DB kaydı (GET); OpenAI çağrısı yok. */
    const loadHistoryAnalysis = (id: string) => {
        void getAiAnalysisById(id).then((r) => {
            if (r?.kind === 'PORTFOLIO') {
                setDisplayResult(r);
                setViewingHistoryId(id);
                setSelectedSymbol(null);
            }
        });
    };

    const backToLatestAnalysis = () => {
        setViewingHistoryId(null);
        setErrorMsg(null);
        void refetchLatest().then(({ data: latest }) => {
            if (latest?.kind === 'PORTFOLIO') {
                setDisplayResult(latest);
            } else {
                setDisplayResult(null);
            }
            setSelectedSymbol(null);
        });
    };

    const handleAnalyze = () => {
        const trimmed = analysisTitle.trim();
        if (!trimmed) {
            setTitleError(t('portfolioAi.titleRequired', 'Başlık zorunludur.'));
            return;
        }
        setTitleError(null);
        setErrorMsg(null);
        portfolioMutation.mutate(portfolioPayload());
    };

    const handleDeleteHistory = (id: string, title: string) => {
        const msg = t(
            'portfolioAi.deleteConfirm',
            '“{title}” analizini silmek istiyor musunuz?',
        ).replace('{title}', title);
        if (!window.confirm(msg)) return;
        setErrorMsg(null);
        deleteMutation.mutate(id);
    };

    return (
        <div className="portfolio-page pf-ai-page" style={pageStyle}>
            <header className="pf-ai-hero portfolio-fade-in">
                <div className="pf-ai-hero__row">
                    <div className="pf-ai-hero__main">
                        <h1 className="pf-ai-hero__title">
                            <Sparkles size={24} aria-hidden />
                            {t('portfolioAi.pageTitle', 'AI Analizi')}
                        </h1>
                        <p className="pf-ai-hero__sub">
                            {t('portfolioAi.heroTagline', 'Yapay zeka ile portföyünüzü daha akıllı yönetin')}
                        </p>
                    </div>
                    <PortfolioAiEmailDeliveryPanel
                        t={t}
                        locale={locale}
                        portfolioResult={portfolioResult}
                    />
                </div>
            </header>

            <div className="pf-ai-dashboard">
                <div className="pf-ai-dashboard__row pf-ai-dashboard__row--top">
                    <PortfolioAiConfigCard
                        t={t}
                        title={analysisTitle}
                        setTitle={(v) => {
                            setAnalysisTitle(v);
                            if (titleError && v.trim()) {
                                setTitleError(null);
                            }
                        }}
                        titleError={titleError}
                        pfType={pfType}
                        setPfType={setPfType}
                        risk={risk}
                        setRisk={setRisk}
                        detail={detail}
                        setDetail={setDetail}
                        incNews={incNews}
                        setIncNews={setIncNews}
                        incMacro={incMacro}
                        setIncMacro={setIncMacro}
                        incInflation={incInflation}
                        setIncInflation={setIncInflation}
                        pending={portfolioMutation.isPending}
                        errorMsg={errorMsg}
                        onAnalyze={handleAnalyze}
                    />
                    <PortfolioAiSummaryCard
                        t={t}
                        locale={locale}
                        ctx={ctx}
                        loading={ctxLoading}
                        pieData={pieData}
                    />
                </div>

                <div className="pf-ai-dashboard__row pf-ai-dashboard__row--mid">
                    <section className="pf-ai-dash-card pf-ai-dash-card--last-result">
                            <div className="pf-ai-dash-card__head pf-ai-dash-card__head--last-result">
                                <div className="pf-ai-last-result-head">
                                    <h2 className="pf-ai-dash-card__title">
                                        {t('portfolioAi.lastResultTitle', 'Son AI Analizi Sonucu')}
                                    </h2>
                                    {portfolioResult ? (
                                        <div className="pf-ai-last-result-meta">
                                            <PortfolioAiGenerationBadge
                                                t={t}
                                                source={portfolioResult.source}
                                                model={portfolioResult.model}
                                            />
                                            <time
                                                className="pf-ai-summary-panel__date"
                                                dateTime={portfolioResult.createdAt}
                                            >
                                                {fmtDate(portfolioResult.createdAt, locale)}
                                            </time>
                                        </div>
                                    ) : null}
                                </div>
                                <div className="pf-ai-dash-card__actions">
                                    {viewingHistoryId ? (
                                        <div className="pf-ai-history-view-bar">
                                            <span className="pf-ai-history-badge">
                                                {t(
                                                    'portfolioAi.viewingHistory',
                                                    'Geçmiş analiz görüntüleniyor',
                                                )}
                                            </span>
                                            <button
                                                type="button"
                                                className="pf-ai-btn-text"
                                                onClick={backToLatestAnalysis}
                                            >
                                                {t('portfolioAi.backToLatest', 'Son analize dön')}
                                            </button>
                                        </div>
                                    ) : null}
                                </div>
                            </div>
                        {portfolioResult ? (
                            <PortfolioAiResultsBlock
                                result={portfolioResult}
                                t={t}
                                showDisclaimer={false}
                                variant="summaryCard"
                            />
                        ) : (
                            <p className="pf-ai-muted">
                                {t(
                                    'portfolioAi.noSummaryYet',
                                    'Henüz AI analizi oluşturulmadı. Mevcut portföyünüzü analiz ederek başlayın.',
                                )}
                            </p>
                        )}
                    </section>
                    <PortfolioAiAssetTable
                        t={t}
                        locale={locale}
                        rows={assetRows}
                        search={assetSearch}
                        onSearchChange={setAssetSearch}
                        selectedSymbol={selectedRow?.symbol ?? null}
                        onSelect={setSelectedSymbol}
                    />
                </div>

                <div className="pf-ai-dashboard__row pf-ai-dashboard__row--bottom">
                    <section className="pf-ai-dash-card pf-ai-dash-card--wide">
                        <h2 className="pf-ai-dash-card__title">{t('portfolioAi.historyTitle', 'Son Analiz Geçmişi')}</h2>
                        <div className="pf-ai-filters">
                            <select
                                value={historyFilters.dateRange ?? '30D'}
                                onChange={(e) =>
                                    setHistoryFilters((f) => ({
                                        ...f,
                                        dateRange: e.target.value as AiAnalysisHistoryFilters['dateRange'],
                                    }))
                                }
                            >
                                <option value="7D">{t('portfolioAi.filter7d', 'Son 7 gün')}</option>
                                <option value="30D">{t('portfolioAi.filter30d', 'Son 30 gün')}</option>
                                <option value="ALL">{t('portfolioAi.filterAllTime', 'Tümü')}</option>
                            </select>
                            <input
                                type="search"
                                placeholder={t('portfolioAi.filterSymbol', 'Sembol veya başlık ara')}
                                value={historyFilters.symbolSearch ?? ''}
                                onChange={(e) =>
                                    setHistoryFilters((f) => ({ ...f, symbolSearch: e.target.value }))
                                }
                            />
                        </div>
                        {history.length === 0 ? (
                            <p className="pf-ai-muted">{t('portfolioAi.historyEmpty', 'Kayıt yok.')}</p>
                        ) : (
                            <div className="pf-ai-table-scroll">
                                <table className="pf-ai-table pf-ai-table--dense">
                                    <thead>
                                        <tr>
                                            <th>{t('portfolioAi.colDate', 'Tarih')}</th>
                                            <th>{t('portfolioAi.colType', 'Tür')}</th>
                                            <th>{t('portfolioAi.colTitle', 'Analiz başlığı')}</th>
                                            <th>{t('portfolioAi.colScore', 'Portföy puanı')}</th>
                                            <th>{t('portfolioAi.colRiskScore', 'Risk puanı')}</th>
                                            <th>{t('portfolioAi.colSnippet', 'Kısa özet')}</th>
                                            <th>{t('portfolioAi.colActions', 'İşlem')}</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {history.map((h) => (
                                            <tr
                                                key={h.id}
                                                className={`pf-ai-table-row--click${viewingHistoryId === h.id ? ' is-selected' : ''}`}
                                                onClick={() => loadHistoryAnalysis(h.id)}
                                            >
                                                <td>{fmtDate(h.createdAt, locale)}</td>
                                                <td>{h.kind}</td>
                                                <td>{h.title}</td>
                                                <td>{h.portfolioScore}</td>
                                                <td>{h.riskScore}</td>
                                                <td className="pf-ai-table__snippet">{h.summarySnippet}</td>
                                                <td className="pf-ai-table__actions">
                                                    <button
                                                        type="button"
                                                        className="pf-ai-icon-btn"
                                                        title={t('portfolioAi.deleteAnalysis', 'Sil')}
                                                        aria-label={t('portfolioAi.deleteAnalysis', 'Sil')}
                                                        disabled={deleteMutation.isPending}
                                                        onClick={(e) => {
                                                            e.stopPropagation();
                                                            handleDeleteHistory(h.id, h.title);
                                                        }}
                                                    >
                                                        <Trash2 size={16} aria-hidden />
                                                    </button>
                                                </td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        )}
                    </section>
                    <PortfolioAiAssetDetail t={t} row={selectedRow} locale={locale} />
                </div>
            </div>

            <footer className="pf-ai-footer-banner">
                <Info size={16} aria-hidden />
                <span>
                    {t('portfolioAi.disclaimer', 'Bu analiz bilgilendirme amaçlıdır; yatırım tavsiyesi değildir.')}
                </span>
            </footer>
        </div>
    );
}
