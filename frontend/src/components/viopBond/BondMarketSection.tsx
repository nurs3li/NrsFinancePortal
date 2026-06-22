import { useMemo, useState } from 'react';
import { useLanguage } from '../../i18n/LanguageContext';
import { MarketSparkline } from '../market/MarketSparkline';
import type { ManualBondPosition } from '../../types/bondPosition';
import type { TerminalListInstrumentVm } from '../../utils/marketTerminalListVm';
import {
    bondRiskTag,
    bondTypeFilterLabel,
    displayBondType,
    filterMarketByBondType,
    findPositionForSymbol,
    type BondTypeFilter,
} from './bondAnalysisHelpers';
import { isEurobondMarketInstrument } from './viopBondMarket';
import { fmtDate, fmtMoney, fmtPct } from './formatViopBond';
import { BondInstrumentDetailPanel } from './BondInstrumentDetailPanel';

type Props = {
    rows: TerminalListInstrumentVm[];
    loading: boolean;
    positions: ManualBondPosition[];
    tokens: { border: string; bgCard: string; textMuted: string; text: string };
    onSelect: (symbol: string) => void;
    selectedSymbol: string | null;
    onAdd: (row: TerminalListInstrumentVm) => void;
    onAlert: (row: TerminalListInstrumentVm) => void;
};

const TYPE_FILTERS: BondTypeFilter[] = ['ALL', 'GOVERNMENT_BOND', 'TREASURY_BILL', 'CORPORATE_BOND'];

export function BondMarketSection({
    rows,
    loading,
    positions,
    tokens,
    onSelect,
    selectedSymbol,
    onAdd,
    onAlert,
}: Props) {
    const { t, lang } = useLanguage();
    const locale = lang === 'en' ? 'en-US' : 'tr-TR';
    const [search, setSearch] = useState('');
    const [typeFilter, setTypeFilter] = useState<BondTypeFilter>('ALL');
    const [mobileDetailOpen, setMobileDetailOpen] = useState(false);

    const filtered = useMemo(() => {
        const domestic = rows.filter((r) => !isEurobondMarketInstrument(r.symbol, r.displayName));
        const byType = filterMarketByBondType(domestic, typeFilter);
        const q = search.trim().toLowerCase();
        if (!q) return byType;
        return byType.filter(
            (i) => i.symbol.toLowerCase().includes(q) || i.displayName.toLowerCase().includes(q),
        );
    }, [rows, search, typeFilter]);

    const selected =
        filtered.find((i) => i.symbol === selectedSymbol) ?? filtered[0] ?? null;

    const matchedPosition = selected ? findPositionForSymbol(positions, selected.symbol) : null;

    const cardStyle = { borderColor: tokens.border, background: tokens.bgCard };

    const openDetail = (symbol: string) => {
        onSelect(symbol);
        if (typeof window !== 'undefined' && window.matchMedia('(max-width: 900px)').matches) {
            setMobileDetailOpen(true);
        }
    };

    return (
        <section className="vb-section">
            <h3 className="vb-section-title">{t('viopBond.marketBondTitle', 'Piyasa Tahvil & Bono')}</h3>
            <p className="vb-section-lead" style={{ color: tokens.textMuted }}>
                {t(
                    'viopBond.marketBondLead',
                    'TCMB DİBS snapshot verisi. Devlet tahvili ve hazine bonosu ISIN’leri listelenir.',
                )}
            </p>
            <div className="vb-toolbar vb-toolbar--filters">
                <input
                    className="vb-search"
                    placeholder={t('viopBond.searchMarket', 'Enstrüman ara…')}
                    value={search}
                    onChange={(e) => setSearch(e.target.value)}
                />
                <div className="vb-filter-chips vb-filter-chips--wrap">
                    {TYPE_FILTERS.map((f) => (
                        <button
                            key={f}
                            type="button"
                            className={`vb-filter-chip${typeFilter === f ? ' is-active' : ''}`}
                            onClick={() => setTypeFilter(f)}
                        >
                            {bondTypeFilterLabel(f, t)}
                        </button>
                    ))}
                </div>
            </div>
            <div className="vb-layout vb-layout--split vb-layout--bond-market">
                <div className="pf-card-premium vb-table-wrap" style={{ ...cardStyle, padding: '0.5rem' }}>
                    {loading ? (
                        <p className="vb-pad-muted" style={{ color: tokens.textMuted }}>
                            {t('viopBond.loading', 'Yükleniyor…')}
                        </p>
                    ) : filtered.length === 0 ? (
                        <div className="vb-empty-state">
                            <p>{t('viopBond.bondMarketEmpty', 'Piyasa tahvil verisi bulunamadı.')}</p>
                        </div>
                    ) : (
                        <>
                            <div className="vb-market-cards">
                                {filtered.map((row) => (
                                    <article
                                        key={row.symbol}
                                        className={`vb-market-card${selected?.symbol === row.symbol ? ' is-selected' : ''}`}
                                        style={cardStyle}
                                        onClick={() => openDetail(row.symbol)}
                                    >
                                        <div className="vb-market-card__head">
                                            <strong>{row.symbol}</strong>
                                            <span className="vb-badge">{displayBondType(row.symbol, row.displayName, undefined, t)}</span>
                                        </div>
                                        <div className="vb-market-card__sub">{row.displayName}</div>
                                        <div className="vb-market-card__metrics">
                                            <span>{fmtMoney(row.price, locale)}</span>
                                            <span>
                                                {row.yieldToMaturity != null
                                                    ? fmtPct(row.yieldToMaturity, locale)
                                                    : '—'}{' '}
                                                {t('viopBond.yieldShort', 'getiri')}
                                            </span>
                                            <span>
                                                {row.couponRate != null ? fmtPct(row.couponRate, locale) : '—'}{' '}
                                                {t('viopBond.couponShort', 'kupon')}
                                            </span>
                                        </div>
                                        <div className="vb-market-card__actions" onClick={(e) => e.stopPropagation()}>
                                            <button
                                                type="button"
                                                className="pf-dash-btn pf-dash-btn--compact pf-dash-btn--primary"
                                                onClick={() => onAdd(row)}
                                            >
                                                {t('viopBond.addToPosition', 'Pozisyona Ekle')}
                                            </button>
                                        </div>
                                    </article>
                                ))}
                            </div>
                            <table className="vb-table vb-market-table">
                                <thead>
                                    <tr>
                                        <th>{t('viopBond.colInstrument', 'Enstrüman')}</th>
                                        <th>{t('viopBond.colBondType', 'Tür')}</th>
                                        <th>{t('viopBond.colCurrency', 'Döviz')}</th>
                                        <th>{t('viopBond.colCurrentPrice', 'Fiyat')}</th>
                                        <th>{t('viopBond.colYield', 'Getiri')}</th>
                                        <th>{t('viopBond.colCouponRate', 'Kupon')}</th>
                                        <th>{t('viopBond.colMaturity', 'Vade / Kalan')}</th>
                                        <th>{t('viopBond.colRisk', 'Risk')}</th>
                                        <th>{t('viopBond.colTrend', 'Trend')}</th>
                                        <th />
                                    </tr>
                                </thead>
                                <tbody>
                                    {filtered.map((row) => (
                                        <tr
                                            key={row.symbol}
                                            className={selected?.symbol === row.symbol ? 'vb-row--selected' : ''}
                                            onClick={() => openDetail(row.symbol)}
                                            style={{ cursor: 'pointer' }}
                                        >
                                            <td>
                                                <strong>{row.symbol}</strong>
                                                <div className="vb-cell-sub">{row.displayName}</div>
                                            </td>
                                            <td>{displayBondType(row.symbol, row.displayName, undefined, t)}</td>
                                            <td>{row.currency ?? 'TRY'}</td>
                                            <td>{fmtMoney(row.price, locale)}</td>
                                            <td title={row.yieldToMaturity == null ? t('viopBond.yieldMissing', '') : undefined}>
                                                {row.yieldToMaturity != null ? fmtPct(row.yieldToMaturity, locale) : '—'}
                                            </td>
                                            <td>{row.couponRate != null ? fmtPct(row.couponRate, locale) : '—'}</td>
                                            <td>
                                                {row.maturityDate ? (
                                                    <>
                                                        {fmtDate(row.maturityDate, locale)}
                                                        {row.daysToMaturity != null ? (
                                                            <div className="vb-cell-sub">
                                                                {row.daysToMaturity} {t('viopBond.days', 'gün')}
                                                            </div>
                                                        ) : null}
                                                    </>
                                                ) : (
                                                    '—'
                                                )}
                                            </td>
                                            <td>
                                                <span className="vb-badge vb-badge--muted">
                                                    {bondRiskTag(row.daysToMaturity, row.currency ?? 'TRY', t)}
                                                </span>
                                            </td>
                                            <td>
                                                <MarketSparkline
                                                    closes={row.sparkline}
                                                    bgColor={tokens.bgCard}
                                                    lineColor={row.trend === 'UP' ? '#166534' : '#991B1B'}
                                                />
                                            </td>
                                            <td onClick={(e) => e.stopPropagation()}>
                                                <div className="vb-actions">
                                                    <button
                                                        type="button"
                                                        className="pf-dash-btn pf-dash-btn--compact"
                                                        onClick={() => onAlert(row)}
                                                    >
                                                        {t('viopBond.alert', 'Alarm')}
                                                    </button>
                                                    <button
                                                        type="button"
                                                        className="pf-dash-btn pf-dash-btn--compact pf-dash-btn--primary"
                                                        onClick={() => onAdd(row)}
                                                    >
                                                        {t('viopBond.addToPosition', 'Ekle')}
                                                    </button>
                                                </div>
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </>
                    )}
                </div>
                {selected ? (
                    <div className="vb-detail-panel-desktop">
                        <BondInstrumentDetailPanel
                            instrument={selected}
                            matchedPosition={matchedPosition}
                            tokens={tokens}
                            onAddPosition={() => onAdd(selected)}
                            onSetAlert={() => onAlert(selected)}
                        />
                    </div>
                ) : null}
            </div>
            {selected && mobileDetailOpen ? (
                <div className="vb-modal-backdrop" onClick={() => setMobileDetailOpen(false)} role="presentation">
                    <div className="vb-detail-drawer-mobile" onClick={(e) => e.stopPropagation()}>
                        <BondInstrumentDetailPanel
                            instrument={selected}
                            matchedPosition={matchedPosition}
                            tokens={tokens}
                            onAddPosition={() => onAdd(selected)}
                            onSetAlert={() => onAlert(selected)}
                            onClose={() => setMobileDetailOpen(false)}
                        />
                    </div>
                </div>
            ) : null}
        </section>
    );
}
