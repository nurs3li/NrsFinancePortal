import { useMemo, useState } from 'react';
import { useLanguage } from '../../i18n/LanguageContext';
import { viopCategoryFor } from '../../constants/ViopWhitelist';
import { MarketSparkline } from '../market/MarketSparkline';
import type { ManualViopPosition } from '../../types/viopPosition';
import type { TerminalListInstrumentVm } from '../../utils/marketTerminalListVm';
import {
    filterViopByCategory,
    viopCategoryLabel,
    viopFilterLabel,
    type ViopMarketFilter,
} from './viopBondMarket';
import { daysToExpiryFromIso } from './viopAnalysisHelpers';
import { resolveViopExpiry } from './viopContractMeta';
import { fmtMoney, fmtPct } from './formatViopBond';
import { pctClass } from './vbTabShared';
import { ViopInstrumentDetailPanel } from './ViopInstrumentDetailPanel';

const VIOP_FILTERS: ViopMarketFilter[] = ['ALL', 'FX', 'INDEX', 'COMMODITY', 'EQUITY'];

type Props = {
    rows: TerminalListInstrumentVm[];
    loading: boolean;
    positions: ManualViopPosition[];
    tokens: { border: string; bgCard: string; textMuted: string; text: string };
    selectedSymbol: string | null;
    onSelect: (symbol: string) => void;
    onAdd: (row: TerminalListInstrumentVm) => void;
    onAlert: (row: TerminalListInstrumentVm) => void;
    onPositionDetail: (row: ManualViopPosition) => void;
};

export function ViopMarketSection({
    rows,
    loading,
    positions,
    tokens,
    selectedSymbol,
    onSelect,
    onAdd,
    onAlert,
    onPositionDetail,
}: Props) {
    const { t, lang } = useLanguage();
    const locale = lang === 'en' ? 'en-US' : 'tr-TR';
    const [search, setSearch] = useState('');
    const [typeFilter, setTypeFilter] = useState<ViopMarketFilter>('ALL');
    const [mobileDetailOpen, setMobileDetailOpen] = useState(false);

    const filtered = useMemo(() => {
        let list = filterViopByCategory(rows, typeFilter);
        const q = search.trim().toLowerCase();
        if (q) {
            list = list.filter(
                (i) => i.symbol.toLowerCase().includes(q) || i.displayName.toLowerCase().includes(q),
            );
        }
        return list;
    }, [rows, search, typeFilter]);

    const selected = filtered.find((i) => i.symbol === selectedSymbol) ?? filtered[0] ?? null;

    const cardStyle = { borderColor: tokens.border, background: tokens.bgCard };

    const openDetail = (symbol: string) => {
        onSelect(symbol);
        if (typeof window !== 'undefined' && window.matchMedia('(max-width: 900px)').matches) {
            setMobileDetailOpen(true);
        }
    };

    const expiryLabel = (row: TerminalListInstrumentVm) => {
        const exp = resolveViopExpiry(row.symbol, locale, { contractMonth: row.contractMonth });
        const days = exp?.expiryDate ? daysToExpiryFromIso(exp.expiryDate) : null;
        return (
            <>
                {exp?.displayLong ?? row.contractMonth ?? '—'}
                {days != null ? (
                    <div className="vb-cell-sub">
                        {days} {t('viopBond.days', 'gün')}
                    </div>
                ) : null}
            </>
        );
    };

    return (
        <section className="vb-section">
            <h3 className="vb-section-title">{t('viopBond.marketViopTitle', 'Piyasa VİOP Kontratları')}</h3>
            <p className="vb-section-lead" style={{ color: tokens.textMuted }}>
                {t(
                    'viopBond.marketViopLead',
                    'Vadeli işlem kontratlarını izleyin, alarm kurun veya pozisyona ekleyin.',
                )}
            </p>
            <div className="vb-toolbar vb-toolbar--filters">
                <input
                    className="vb-search"
                    placeholder={t('viopBond.searchMarket', 'Kontrat ara…')}
                    value={search}
                    onChange={(e) => setSearch(e.target.value)}
                />
                <div className="vb-filter-chips vb-filter-chips--wrap">
                    {VIOP_FILTERS.map((f) => (
                        <button
                            key={f}
                            type="button"
                            className={`vb-filter-chip${typeFilter === f ? ' is-active' : ''}`}
                            onClick={() => setTypeFilter(f)}
                        >
                            {viopFilterLabel(f, t)}
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
                            <p>{t('viopBond.marketEmpty', 'Piyasa kontratı bulunamadı.')}</p>
                        </div>
                    ) : (
                        <>
                            <div className="vb-market-cards">
                                {filtered.map((row) => {
                                    const cat = viopCategoryFor(row.symbol);
                                    return (
                                        <article
                                            key={row.symbol}
                                            className={`vb-market-card${selected?.symbol === row.symbol ? ' is-selected' : ''}`}
                                            style={cardStyle}
                                            onClick={() => openDetail(row.symbol)}
                                        >
                                            <div className="vb-market-card__head">
                                                <strong>{row.symbol}</strong>
                                                <span className="vb-badge">{cat ? viopCategoryLabel(cat, t) : '—'}</span>
                                            </div>
                                            <div className="vb-market-card__sub">{row.displayName}</div>
                                            <div className="vb-market-card__metrics">
                                                <span>{fmtMoney(row.price, locale)}</span>
                                                <span className={pctClass(row.pctDay)}>{fmtPct(row.pctDay, locale)}</span>
                                                <span>{expiryLabel(row)}</span>
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
                                    );
                                })}
                            </div>
                            <table className="vb-table vb-market-table">
                                <thead>
                                    <tr>
                                        <th>{t('viopBond.colContract', 'Kontrat')}</th>
                                        <th>{t('viopBond.colViopType', 'Tür')}</th>
                                        <th>{t('viopBond.colCurrentPrice', 'Son fiyat')}</th>
                                        <th>{t('viopBond.colDay', 'Gün')}</th>
                                        <th>{t('viopBond.colWeek', 'Hafta')}</th>
                                        <th>{t('viopBond.colMonth', 'Ay')}</th>
                                        <th>{t('viopBond.colYear', 'Yıl')}</th>
                                        <th>{t('viopBond.colMaturity', 'Vade')}</th>
                                        <th>{t('viopBond.colMargin', 'Teminat')}</th>
                                        <th>{t('viopBond.colTrend', 'Trend')}</th>
                                        <th />
                                    </tr>
                                </thead>
                                <tbody>
                                    {filtered.map((row) => {
                                        const cat = viopCategoryFor(row.symbol);
                                        return (
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
                                                <td>{cat ? viopCategoryLabel(cat, t) : '—'}</td>
                                                <td>{fmtMoney(row.price, locale)}</td>
                                                <td className={pctClass(row.pctDay)}>{fmtPct(row.pctDay, locale)}</td>
                                                <td className={pctClass(row.pctWeek)}>{fmtPct(row.pctWeek, locale)}</td>
                                                <td className={pctClass(row.pctMonth)}>{fmtPct(row.pctMonth, locale)}</td>
                                                <td className={pctClass(row.pctYear)}>{fmtPct(row.pctYear, locale)}</td>
                                                <td>{expiryLabel(row)}</td>
                                                <td>
                                                    {row.marginRequirement != null
                                                        ? fmtMoney(row.marginRequirement, locale)
                                                        : '—'}
                                                </td>
                                                <td>
                                                    <MarketSparkline
                                                        closes={row.sparkline}
                                                        bgColor={tokens.bgCard}
                                                        lineColor={row.trend === 'UP' ? '#22c55e' : '#ef4444'}
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
                                        );
                                    })}
                                </tbody>
                            </table>
                        </>
                    )}
                </div>
                {selected ? (
                    <div className="vb-detail-panel-desktop">
                        <ViopInstrumentDetailPanel
                            instrument={selected}
                            positions={positions}
                            tokens={tokens}
                            onAddPosition={() => onAdd(selected)}
                            onSetAlert={() => onAlert(selected)}
                            onPositionDetail={onPositionDetail}
                        />
                    </div>
                ) : null}
            </div>
            {selected && mobileDetailOpen ? (
                <div className="vb-modal-backdrop" onClick={() => setMobileDetailOpen(false)} role="presentation">
                    <div className="vb-detail-drawer-mobile" onClick={(e) => e.stopPropagation()}>
                        <ViopInstrumentDetailPanel
                            instrument={selected}
                            positions={positions}
                            tokens={tokens}
                            onAddPosition={() => onAdd(selected)}
                            onSetAlert={() => onAlert(selected)}
                            onPositionDetail={onPositionDetail}
                            onClose={() => setMobileDetailOpen(false)}
                        />
                    </div>
                </div>
            ) : null}
        </section>
    );
}
