import { useQuery } from '@tanstack/react-query';
import { useLanguage } from '../../i18n/LanguageContext';
import {
    fetchDepositRatesLatest,
    fetchInflationLatest,
    fetchInterestInflationMacroPanel,
} from '../../services/marketDataService';
import { formatPercent2, lastObservation, selectMacroSeries } from '../../utils/macroPanelSeries';

type Props = {
    tokens: { bgCard: string; border: string; text: string; textMuted: string };
    /** İlk açılışta grafik öncelikli; makro sorguları geciktirilebilir */
    queriesEnabled?: boolean;
};

export function MarketMacroInfoCard({ tokens, queriesEnabled = true }: Props) {
    const { t, lang } = useLanguage();
    const locale = lang === 'en' ? 'en-US' : 'tr-TR';

    const { data: inflation, isLoading: loadingInf } = useQuery({
        queryKey: ['market', 'macro', 'inflation-latest'],
        queryFn: ({ signal }) => fetchInflationLatest(signal),
        enabled: queriesEnabled,
        staleTime: 300_000,
    });

    const { data: panel, isLoading: loadingPanel } = useQuery({
        queryKey: ['market', 'macro', 'interest-inflation-panel'],
        queryFn: ({ signal }) => fetchInterestInflationMacroPanel(signal),
        enabled: queriesEnabled,
        staleTime: 300_000,
    });

    const { data: depositLatest } = useQuery({
        queryKey: ['market', 'macro', 'deposit-latest'],
        queryFn: ({ signal }) => fetchDepositRatesLatest(signal),
        enabled: queriesEnabled,
        staleTime: 60_000,
    });

    const dep1m = depositLatest?.find(
        (r) => String(r.currency).toUpperCase() === 'TRY' && String(r.term).toUpperCase() === '1M',
    );
    const dep1mPanel = lastObservation(selectMacroSeries(panel?.series, 'DEPOSIT_RATE_TRY_1M_WEEKLY'));

    const loading = loadingInf || loadingPanel;

    return (
        <section className="terminal-card terminal-macro-info-card" style={{ borderColor: tokens.border }}>
            <h3 className="terminal-macro-card__title" style={{ color: tokens.text }}>
                {t('market.macro.sidebarTitle', 'Türkiye makro özeti')}
            </h3>
            {loading ? (
                <p className="terminal-macro-card__muted" style={{ color: tokens.textMuted }}>
                    {t('market.loading', 'Yükleniyor...')}
                </p>
            ) : (
                <dl className="terminal-macro-info-grid">
                    <div>
                        <dt style={{ color: tokens.textMuted }}>{t('market.macro.cpiYoY', 'TÜFE (yıllık)')}</dt>
                        <dd style={{ color: tokens.text }}>{formatPercent2(inflation?.cpi?.annualChangePercent ?? null, locale)}</dd>
                    </div>
                    <div>
                        <dt style={{ color: tokens.textMuted }}>{t('market.macro.cpiMoM', 'TÜFE (aylık)')}</dt>
                        <dd style={{ color: tokens.text }}>{formatPercent2(inflation?.cpi?.monthlyChangePercent ?? null, locale)}</dd>
                    </div>
                    <div>
                        <dt style={{ color: tokens.textMuted }}>{t('market.macro.ppiYoY', 'Yİ-ÜFE (yıllık)')}</dt>
                        <dd style={{ color: tokens.text }}>{formatPercent2(inflation?.ppi?.annualChangePercent ?? null, locale)}</dd>
                    </div>
                    <div>
                        <dt style={{ color: tokens.textMuted }}>{t('market.macro.depositTry1m', 'TL mevduat 1A')}</dt>
                        <dd style={{ color: tokens.text }}>
                            {dep1m?.ratePercent != null
                                ? `${Number(dep1m.ratePercent).toLocaleString(locale, { maximumFractionDigits: 2 })}%`
                                : dep1mPanel
                                  ? `${dep1mPanel.value.toLocaleString(locale, { maximumFractionDigits: 2 })}%`
                                  : '—'}
                        </dd>
                    </div>
                    {panel?.derived?.realDepositRate != null ? (
                        <div className="terminal-macro-info-grid__wide">
                            <dt style={{ color: tokens.textMuted }}>{t('market.macro.realDeposit', 'Reel mevduat (yaklaşık)')}</dt>
                            <dd style={{ color: tokens.text }}>{formatPercent2(panel.derived.realDepositRate, locale)}</dd>
                        </div>
                    ) : null}
                </dl>
            )}
            <p className="terminal-macro-card__footnote" style={{ color: tokens.textMuted }}>
                {t(
                    'market.macro.sidebarFootnote',
                    'EVDS akım verileri; grafik altındaki panelde tarihsel enflasyon ve mevduat faizi eğrileri yer alır.',
                )}
            </p>
        </section>
    );
}
