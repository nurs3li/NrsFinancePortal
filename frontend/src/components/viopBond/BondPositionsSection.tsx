import { useMemo, useState } from 'react';
import { MoreHorizontal, Plus } from 'lucide-react';
import { PositionSearchFilterBar } from './PositionSearchFilterBar';
import { useLanguage } from '../../i18n/LanguageContext';
import type { BondPositionStatus, ManualBondPosition } from '../../types/bondPosition';
import { bondTypeLabel } from './bondPositionLabels';
import { bondPeriodRealReturnPercent, maturityBucket, maturityBucketLabel } from './bondAnalysisHelpers';
import { BondRealReturnHeader, bondRealReturnMissingTitle } from './BondRealReturnHeader';
import { fmtDate, fmtMoney, fmtPct } from './formatViopBond';
import { pctClass, pnlClass } from './vbTabShared';

type Props = {
    rows: ManualBondPosition[];
    loading: boolean;
    tokens: { border: string; bgCard: string; textMuted: string };
    onManualAdd: () => void;
    onEdit: (row: ManualBondPosition) => void;
    onSell: (row: ManualBondPosition) => void;
    onDelete: (id: number) => void;
    onDetail: (row: ManualBondPosition) => void;
    onAlert: (row: ManualBondPosition) => void;
};

type StatusFilter = 'ALL' | BondPositionStatus;
type CurrencyFilter = 'ALL' | 'TRY' | 'USD' | 'EUR';

export function BondPositionsSection({
    rows,
    loading,
    tokens,
    onManualAdd,
    onEdit,
    onSell,
    onDelete,
    onDetail,
    onAlert,
}: Props) {
    const { t, lang } = useLanguage();
    const locale = lang === 'en' ? 'en-US' : 'tr-TR';
    const [search, setSearch] = useState('');
    const [statusFilter, setStatusFilter] = useState<StatusFilter>('OPEN');
    const [currencyFilter, setCurrencyFilter] = useState<CurrencyFilter>('ALL');
    const [menuOpenId, setMenuOpenId] = useState<number | null>(null);

    const filtered = useMemo(() => {
        const q = search.trim().toLowerCase();
        return rows.filter((r) => {
            if (statusFilter !== 'ALL' && r.status !== statusFilter) return false;
            if (currencyFilter !== 'ALL' && r.currency !== currencyFilter) return false;
            if (!q) return true;
            return (
                r.symbol.toLowerCase().includes(q) ||
                (r.displayName ?? '').toLowerCase().includes(q)
            );
        });
    }, [rows, search, statusFilter, currencyFilter]);

    const statusLabel = (s: BondPositionStatus) => {
        if (s === 'OPEN') return t('viopBond.statusOpen', 'Açık');
        if (s === 'SOLD') return t('viopBond.statusSold', 'Satıldı');
        return s;
    };

    const cardStyle = { borderColor: tokens.border, background: tokens.bgCard };

    const renderActions = (row: ManualBondPosition, compact?: boolean) => (
        <div className={`vb-actions${compact ? ' vb-actions--wrap' : ''}`}>
            <button type="button" className="pf-dash-btn pf-dash-btn--compact" onClick={() => onDetail(row)}>
                {t('viopBond.detail', 'Detay')}
            </button>
            {row.status === 'OPEN' ? (
                <button type="button" className="pf-dash-btn pf-dash-btn--compact" onClick={() => onEdit(row)}>
                    {t('viopBond.edit', 'Güncelle')}
                </button>
            ) : null}
            {row.status === 'OPEN' ? (
                <button type="button" className="pf-dash-btn pf-dash-btn--compact" onClick={() => onSell(row)}>
                    {t('viopBond.sold', 'Satıldı')}
                </button>
            ) : null}
            <div className="vb-action-menu-wrap">
                <button
                    type="button"
                    className="pf-dash-btn pf-dash-btn--compact"
                    aria-label={t('viopBond.moreActions', 'Diğer işlemler')}
                    onClick={() => setMenuOpenId(menuOpenId === row.id ? null : row.id)}
                >
                    <MoreHorizontal size={14} />
                </button>
                {menuOpenId === row.id ? (
                    <div className="vb-action-menu" style={cardStyle}>
                        <button type="button" onClick={() => { onAlert(row); setMenuOpenId(null); }}>
                            {t('viopBond.alert', 'Alarm')}
                        </button>
                        <button
                            type="button"
                            className="vb-action-menu--danger"
                            onClick={() => {
                                if (window.confirm(t('viopBond.confirmDelete', 'Silinsin mi?'))) {
                                    onDelete(row.id);
                                }
                                setMenuOpenId(null);
                            }}
                        >
                            {t('viopBond.delete', 'Sil')}
                        </button>
                    </div>
                ) : null}
            </div>
        </div>
    );

    const positionCard = (row: ManualBondPosition) => {
        const real = bondPeriodRealReturnPercent(row);
        const bucket = maturityBucket(row.daysToMaturity);
        return (
            <article key={row.id} className="vb-position-card pf-card-premium" style={cardStyle}>
                <div className="vb-position-card__head">
                    <div>
                        <strong>{row.displayName?.trim() || row.symbol}</strong>
                        <div className="vb-cell-sub">{row.symbol}</div>
                    </div>
                    <span className="vb-badge">{statusLabel(row.status)}</span>
                </div>
                <div className="vb-position-card__grid">
                    <div>
                        <span style={{ color: tokens.textMuted }}>{t('viopBond.colNominal', 'Nominal')}</span>
                        <div>{fmtMoney(row.nominalValue, locale)}</div>
                    </div>
                    <div>
                        <span style={{ color: tokens.textMuted }}>{t('viopBond.colBuyDate', 'Alış tarihi')}</span>
                        <div>{fmtDate(row.buyDate, locale)}</div>
                    </div>
                    <div>
                        <span style={{ color: tokens.textMuted }}>{t('viopBond.colValue', 'Güncel değer')}</span>
                        <div>{fmtMoney(row.currentValue, locale)}</div>
                    </div>
                    <div>
                        <span style={{ color: tokens.textMuted }}>{t('viopBond.bondPricePnl', 'Fiyat K/Z')}</span>
                        <div className={pnlClass(row.pricePnl ?? row.pnl)}>
                            {fmtMoney(row.pricePnl ?? row.pnl, locale)}
                        </div>
                    </div>
                    <div>
                        <span style={{ color: tokens.textMuted }}>{t('viopBond.bondTotalReturn', 'Toplam getiri')}</span>
                        <div className={pnlClass(row.totalReturn ?? row.pnl)}>
                            {fmtMoney(row.totalReturn ?? row.pnl, locale)}
                        </div>
                    </div>
                    <div>
                        <span style={{ color: tokens.textMuted }}>{t('viopBond.colReturn', 'Nom. getiri')}</span>
                        <div className={pctClass(row.totalReturnPercent ?? row.returnPct)}>
                            {fmtPct(row.totalReturnPercent ?? row.returnPct, locale)}
                        </div>
                    </div>
                    <div>
                        <span style={{ color: tokens.textMuted }}>
                            {t('viopBond.colRealReturnPeriod', 'Dönemsel reel getiri')}
                        </span>
                        <div className={pctClass(real)} title={real == null ? bondRealReturnMissingTitle(t) : undefined}>
                            {real != null ? fmtPct(real, locale) : '—'}
                        </div>
                    </div>
                    {row.maturityDate ? (
                        <div>
                            <span style={{ color: tokens.textMuted }}>{t('viopBond.colMaturity', 'Vade')}</span>
                            <div>
                                {fmtDate(row.maturityDate, locale)}
                                {row.daysToMaturity != null ? ` · ${row.daysToMaturity}g` : ''}
                                {bucket ? ` · ${maturityBucketLabel(bucket, t)}` : ''}
                            </div>
                        </div>
                    ) : null}
                </div>
                {renderActions(row, true)}
            </article>
        );
    };

    return (
        <section className="vb-section vb-section--priority">
            <div className="vb-section-head-row">
                <h3 className="vb-section-title">{t('viopBond.myBondPositions', 'Benim Tahvil Pozisyonlarım')}</h3>
                <button type="button" className="pf-dash-btn pf-dash-btn--primary" onClick={onManualAdd}>
                    <Plus size={14} />
                    {t('viopBond.addBondManual', 'Manuel Tahvil Ekle')}
                </button>
            </div>
            <div className="vb-toolbar vb-toolbar--filters">
                <PositionSearchFilterBar
                    search={search}
                    onSearchChange={setSearch}
                    searchPlaceholder={t('viopBond.searchPosition', 'Pozisyon ara…')}
                    tokens={tokens}
                    groups={[
                        {
                            id: 'status',
                            label: t('viopBond.filterStatus', 'Durum'),
                            value: statusFilter,
                            onChange: (v) => setStatusFilter(v as StatusFilter),
                            options: (['ALL', 'OPEN', 'SOLD'] as const).map((f) => ({
                                value: f,
                                label: f === 'ALL' ? t('viopBond.filterAll', 'Tümü') : statusLabel(f),
                            })),
                        },
                        {
                            id: 'currency',
                            label: t('viopBond.filterCurrency', 'Para birimi'),
                            value: currencyFilter,
                            onChange: (v) => setCurrencyFilter(v as CurrencyFilter),
                            options: (['ALL', 'TRY', 'USD', 'EUR'] as const).map((c) => ({
                                value: c,
                                label: c === 'ALL' ? t('viopBond.filterAll', 'Tümü') : c,
                            })),
                        },
                    ]}
                />
            </div>
            {loading ? (
                <p className="vb-pad-muted" style={{ color: tokens.textMuted }}>
                    {t('viopBond.loading', 'Yükleniyor…')}
                </p>
            ) : filtered.length === 0 ? (
                <div className="vb-empty-state">
                    <p>
                        {t(
                            'viopBond.bondEmpty',
                            'Henüz tahvil veya bono pozisyonunuz yok. Piyasa listesinden ekleyebilir veya manuel kayıt açabilirsiniz.',
                        )}
                    </p>
                    <button type="button" className="pf-dash-btn pf-dash-btn--primary" onClick={onManualAdd}>
                        {t('viopBond.addBondManual', 'Manuel Tahvil Ekle')}
                    </button>
                </div>
            ) : (
                <>
                    <div className="vb-positions-cards">{filtered.map(positionCard)}</div>
                    <div className="pf-card-premium vb-table-wrap vb-positions-table" style={{ ...cardStyle, padding: '0.5rem' }}>
                        <table className="vb-table">
                            <thead>
                                <tr>
                                    <th>{t('viopBond.colInstrument', 'Enstrüman')}</th>
                                    <th>{t('viopBond.colBondType', 'Tür')}</th>
                                    <th>{t('viopBond.colCurrency', 'Döviz')}</th>
                                    <th>{t('viopBond.colBuyDate', 'Alış tarihi')}</th>
                                    <th>{t('viopBond.colNominal', 'Nominal')}</th>
                                    <th>{t('viopBond.colValue', 'Güncel değer')}</th>
                                    <th>{t('viopBond.bondPricePnl', 'Fiyat K/Z')}</th>
                                    <th>{t('viopBond.bondCouponIncome', 'Tahsil kupon')}</th>
                                    <th>{t('viopBond.bondTotalReturn', 'Toplam getiri')}</th>
                                    <th>{t('viopBond.colReturn', 'Nom. getiri')}</th>
                                    <th>
                                        <BondRealReturnHeader tokens={tokens} />
                                    </th>
                                    <th>{t('viopBond.colMaturity', 'Vade')}</th>
                                    <th>{t('viopBond.colStatus', 'Durum')}</th>
                                    <th />
                                </tr>
                            </thead>
                            <tbody>
                                {filtered.map((row) => {
                                    const real = bondPeriodRealReturnPercent(row);
                                    const bucket = maturityBucket(row.daysToMaturity);
                                    return (
                                        <tr key={row.id}>
                                            <td>
                                                <strong>{row.displayName?.trim() || row.symbol}</strong>
                                                <div className="vb-cell-sub">{row.symbol}</div>
                                            </td>
                                            <td>{bondTypeLabel(row.bondType, t)}</td>
                                            <td>{row.currency}</td>
                                            <td>{fmtDate(row.buyDate, locale)}</td>
                                            <td>{fmtMoney(row.nominalValue, locale)}</td>
                                            <td>{fmtMoney(row.currentValue, locale)}</td>
                                            <td className={pnlClass(row.pricePnl ?? row.pnl)}>
                                                {fmtMoney(row.pricePnl ?? row.pnl, locale)}
                                            </td>
                                            <td>{fmtMoney(row.collectedCoupon ?? 0, locale)}</td>
                                            <td className={pnlClass(row.totalReturn ?? row.pnl)}>
                                                {fmtMoney(row.totalReturn ?? row.pnl, locale)}
                                            </td>
                                            <td className={pctClass(row.totalReturnPercent ?? row.returnPct)}>
                                                {fmtPct(row.totalReturnPercent ?? row.returnPct, locale)}
                                            </td>
                                            <td
                                                className={pctClass(real)}
                                                title={real == null ? bondRealReturnMissingTitle(t) : undefined}
                                            >
                                                {real != null ? fmtPct(real, locale) : '—'}
                                            </td>
                                            <td>
                                                {row.maturityDate ? (
                                                    <>
                                                        {fmtDate(row.maturityDate, locale)}
                                                        {row.daysToMaturity != null ? (
                                                            <div className="vb-cell-sub">
                                                                {row.daysToMaturity} {t('viopBond.days', 'gün')}
                                                                {bucket ? ` · ${maturityBucketLabel(bucket, t)}` : ''}
                                                            </div>
                                                        ) : null}
                                                    </>
                                                ) : (
                                                    '—'
                                                )}
                                            </td>
                                            <td>{statusLabel(row.status)}</td>
                                            <td>{renderActions(row)}</td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>
                    </div>
                </>
            )}
        </section>
    );
}
