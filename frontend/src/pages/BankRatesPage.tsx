import { useQuery } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { Info, RefreshCw, Settings } from 'lucide-react';
import { useTheme } from '../theme/ThemeContext';
import { useLanguage } from '../i18n/LanguageContext';
import { fetchBankRatesBoard } from '../services/bankRatesApi';
import {
    type BankSortMode,
    computeKpis,
    enrichBankRows,
    sortBankRows,
} from '../utils/bankRatesVm';
import { BankRatesKpiStrip } from '../components/bankRates/BankRatesKpiStrip';
import { BankRatesCardGrid } from '../components/bankRates/BankRatesCardGrid';
import { BankRatesComparisonTable } from '../components/bankRates/BankRatesComparisonTable';
import './BankRatesPage.css';

const CURRENCIES = ['USD', 'EUR'] as const;
type ViewMode = 'card' | 'table';

export function BankRatesPage() {
    const { tokens } = useTheme();
    const { t, lang } = useLanguage();
    const locale = lang === 'en' ? 'en-GB' : 'tr-TR';
    const [currency, setCurrency] = useState<(typeof CURRENCIES)[number]>('USD');
    const [view, setView] = useState<ViewMode>('card');
    const [sort, setSort] = useState<BankSortMode>('spread_asc');

    const q = useQuery({
        queryKey: ['bank-rates', 'board', currency],
        queryFn: ({ signal }) => fetchBankRatesBoard(currency, signal),
        staleTime: 120_000,
    });

    const enriched = useMemo(() => enrichBankRows(q.data), [q.data]);
    const sorted = useMemo(() => sortBankRows(enriched, sort), [enriched, sort]);
    const kpis = useMemo(() => computeKpis(enriched), [enriched]);

    const fetchedLabel = useMemo(() => {
        const at = q.data?.fetchedAt;
        if (!at) return null;
        try {
            return new Date(at).toLocaleString(locale, { dateStyle: 'medium', timeStyle: 'short' });
        } catch {
            return at;
        }
    }, [q.data?.fetchedAt, locale]);

    const pageStyle = {
        '--br-bg': tokens.bg,
        '--br-card': tokens.bgCard,
        '--br-border': tokens.border,
        '--br-text': tokens.text,
        '--br-muted': tokens.textMuted,
        '--br-accent': tokens.accent,
    } as React.CSSProperties;

    const themeSlice = {
        bgCard: tokens.bgCard,
        border: tokens.border,
        text: tokens.text,
        textMuted: tokens.textMuted,
        accent: tokens.accent,
    };

    return (
        <div className="bank-rates-dashboard" style={pageStyle}>
            <header className="br-dash-header">
                <div className="br-dash-header__title">
                    <h1>
                        {t('bankRates.title', 'Banka Kurları')}
                        <Info size={16} className="br-dash-header__info" title={t('bankRates.subtitle', '')} />
                    </h1>
                    <p className="br-dash-header__sub">
                        {t(
                            'bankRates.dashboardLead',
                            'Bankalar arası döviz alış/satış ve makas karşılaştırması — veriler 2 saatte bir güncellenir.',
                        )}
                    </p>
                </div>
                <div className="br-dash-header__actions">
                    {fetchedLabel ? (
                        <span className={`br-dash-header__time${q.data?.stale ? ' is-stale' : ''}`}>
                            {t('bankRates.lastUpdate', 'Son güncelleme')}: {fetchedLabel}
                        </span>
                    ) : null}
                    <button
                        type="button"
                        className="br-btn br-btn--primary"
                        onClick={() => q.refetch()}
                        disabled={q.isFetching}
                    >
                        <RefreshCw size={16} className={q.isFetching ? 'br-spin' : ''} />
                        {t('bankRates.refresh', 'Yenile')}
                    </button>
                    <a
                        className="br-btn br-btn--icon"
                        href="https://dovizborsa.com/banka/"
                        target="_blank"
                        rel="noopener noreferrer"
                        title={t('bankRates.source', 'Kaynak')}
                    >
                        <Settings size={18} />
                    </a>
                </div>
            </header>

            <div className="br-controls">
                <div className="br-controls__group">
                    <span className="br-controls__label">{t('bankRates.currency', 'Para Birimi')}</span>
                    <div className="br-seg">
                        {CURRENCIES.map((ccy) => (
                            <button
                                key={ccy}
                                type="button"
                                className={`br-seg__btn${currency === ccy ? ' is-on' : ''}`}
                                onClick={() => setCurrency(ccy)}
                            >
                                {ccy}
                            </button>
                        ))}
                    </div>
                </div>
                <div className="br-controls__group">
                    <span className="br-controls__label">{t('bankRates.view', 'Görünüm')}</span>
                    <div className="br-seg">
                        <button
                            type="button"
                            className={`br-seg__btn${view === 'card' ? ' is-on' : ''}`}
                            onClick={() => setView('card')}
                        >
                            {t('bankRates.viewCard', 'Kart')}
                        </button>
                        <button
                            type="button"
                            className={`br-seg__btn${view === 'table' ? ' is-on' : ''}`}
                            onClick={() => setView('table')}
                        >
                            {t('bankRates.viewTable', 'Tablo')}
                        </button>
                    </div>
                </div>
                <div className="br-controls__group">
                    <span className="br-controls__label">{t('bankRates.sort', 'Sıralama')}</span>
                    <select
                        className="br-select"
                        value={sort}
                        onChange={(e) => setSort(e.target.value as BankSortMode)}
                    >
                        <option value="spread_asc">{t('bankRates.sortSpreadAsc', 'Makas (En Düşük)')}</option>
                        <option value="spread_desc">{t('bankRates.sortSpreadDesc', 'Makas (En Yüksek)')}</option>
                        <option value="buy_desc">{t('bankRates.sortBuyDesc', 'Alış (En Yüksek)')}</option>
                        <option value="sell_asc">{t('bankRates.sortSellAsc', 'Satış (En Düşük)')}</option>
                        <option value="name_asc">{t('bankRates.sortName', 'Banka Adı')}</option>
                        <option value="change_desc">{t('bankRates.sortChange', 'Değişim %')}</option>
                    </select>
                </div>
                <div className="br-controls__notice">
                    <Info size={14} />
                    <span>
                        {t(
                            'bankRates.notice',
                            'Kaynak: dovizborsa.com özet verisi. Resmi banka gişe kuru değildir.',
                        )}
                    </span>
                </div>
            </div>

            {q.isPending ? (
                <p className="br-empty">{t('common.loading', 'Yükleniyor…')}</p>
            ) : q.isError ? (
                <p className="br-empty">{t('bankRates.loadError', 'Banka kurları yüklenemedi.')}</p>
            ) : !sorted.length ? (
                <p className="br-empty">{t('bankRates.empty', 'Henüz kayıt yok.')}</p>
            ) : (
                <>
                    <BankRatesKpiStrip kpis={kpis} tokens={themeSlice} locale={locale} t={t} />

                    {view === 'card' ? (
                        <BankRatesCardGrid
                            rows={sorted}
                            currency={currency}
                            locale={locale}
                            tokens={themeSlice}
                            t={t}
                        />
                    ) : null}

                    <BankRatesComparisonTable
                        rows={sorted}
                        currency={currency}
                        locale={locale}
                        tokens={themeSlice}
                        t={t}
                    />
                </>
            )}
        </div>
    );
}
