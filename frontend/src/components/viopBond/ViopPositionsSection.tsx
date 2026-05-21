import { useMemo, useState } from 'react';
import { MoreHorizontal } from 'lucide-react';
import { PositionSearchFilterBar } from './PositionSearchFilterBar';
import { useLanguage } from '../../i18n/LanguageContext';
import type { ViopCategory } from '../../types/viopPosition';
import type { ManualViopPosition, ViopDirection, ViopPositionStatus } from '../../types/viopPosition';
import { viopCategoryLabel, viopDirectionLabel, viopStatusLabel } from './viopPositionLabels';
import { resolveViopExpiry } from './viopContractMeta';
import { fmtMoney } from './formatViopBond';
import { pnlClass } from './vbTabShared';

type Props = {
    rows: ManualViopPosition[];
    loading: boolean;
    tokens: { border: string; bgCard: string; textMuted: string };
    onEdit: (row: ManualViopPosition) => void;
    onClose: (row: ManualViopPosition) => void;
    onDelete: (id: number) => void;
    onDetail: (row: ManualViopPosition) => void;
    onAlert: (row: ManualViopPosition) => void;
};

type StatusFilter = 'ALL' | ViopPositionStatus;
type DirectionFilter = 'ALL' | ViopDirection;
type CategoryFilter = 'ALL' | ViopCategory;

export function ViopPositionsSection({
    rows,
    loading,
    tokens,
    onEdit,
    onClose,
    onDelete,
    onDetail,
    onAlert,
}: Props) {
    const { t, lang } = useLanguage();
    const locale = lang === 'en' ? 'en-US' : 'tr-TR';
    const [search, setSearch] = useState('');
    const [statusFilter, setStatusFilter] = useState<StatusFilter>('OPEN');
    const [directionFilter, setDirectionFilter] = useState<DirectionFilter>('ALL');
    const [categoryFilter, setCategoryFilter] = useState<CategoryFilter>('ALL');
    const [menuOpenId, setMenuOpenId] = useState<number | null>(null);

    const filtered = useMemo(() => {
        const q = search.trim().toLowerCase();
        return rows.filter((r) => {
            if (statusFilter !== 'ALL' && r.status !== statusFilter) return false;
            if (directionFilter !== 'ALL' && r.direction !== directionFilter) return false;
            if (categoryFilter !== 'ALL' && r.viopCategory !== categoryFilter) return false;
            if (!q) return true;
            return (
                r.symbol.toLowerCase().includes(q) ||
                (r.displayName ?? '').toLowerCase().includes(q)
            );
        });
    }, [rows, search, statusFilter, directionFilter, categoryFilter]);

    const cardStyle = { borderColor: tokens.border, background: tokens.bgCard };

    const renderActions = (row: ManualViopPosition) => (
        <div className="vb-actions">
            <button type="button" className="pf-dash-btn pf-dash-btn--compact" onClick={() => onDetail(row)}>
                {t('viopBond.detail', 'Detay')}
            </button>
            {row.status === 'OPEN' ? (
                <>
                    <button type="button" className="pf-dash-btn pf-dash-btn--compact" onClick={() => onEdit(row)}>
                        {t('viopBond.edit', 'Güncelle')}
                    </button>
                    <button type="button" className="pf-dash-btn pf-dash-btn--compact" onClick={() => onClose(row)}>
                        {t('viopBond.closePosition', 'Kapat')}
                    </button>
                </>
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
                        <button
                            type="button"
                            onClick={() => {
                                onAlert(row);
                                setMenuOpenId(null);
                            }}
                        >
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

    const maturityCell = (row: ManualViopPosition) => {
        const exp = resolveViopExpiry(row.symbol, locale, { expiryDate: row.expiryDate ?? undefined });
        return (
            <>
                {exp?.displayLong ?? row.expiryDate ?? '—'}
                {row.daysToExpiry != null ? (
                    <div className="vb-cell-sub">
                        {row.daysToExpiry} {t('viopBond.days', 'gün')}
                    </div>
                ) : null}
            </>
        );
    };

    const positionCard = (row: ManualViopPosition) => (
        <article key={row.id} className="vb-position-card pf-card-premium" style={cardStyle}>
            <div className="vb-position-card__head">
                <div>
                    <strong>{row.displayName?.trim() || row.symbol}</strong>
                    <div className="vb-cell-sub">{row.symbol}</div>
                </div>
                <span className="vb-badge">{viopStatusLabel(row.status, t)}</span>
            </div>
            <div className="vb-position-card__sub">
                {viopDirectionLabel(row.direction, t)} · {viopCategoryLabel(row.viopCategory, t)} ·{' '}
                {row.contractCount} {t('viopBond.contracts', 'adet')}
            </div>
            <div className="vb-position-card__grid">
                <div>
                    <span style={{ color: tokens.textMuted }}>{t('viopBond.colEntryPrice', 'Giriş')}</span>
                    <div>{fmtMoney(row.entryPrice, locale)}</div>
                </div>
                <div>
                    <span style={{ color: tokens.textMuted }}>{t('viopBond.colCurrentPrice', 'Güncel')}</span>
                    <div>{fmtMoney(row.currentPrice, locale)}</div>
                </div>
                <div>
                    <span style={{ color: tokens.textMuted }}>{t('viopBond.colPnl', 'K/Z')}</span>
                    <div className={pnlClass(row.unrealizedPnl)}>{fmtMoney(row.unrealizedPnl, locale)}</div>
                </div>
                <div>
                    <span style={{ color: tokens.textMuted }}>{t('viopBond.colExposure', 'Maruziyet')}</span>
                    <div>{fmtMoney(row.riskExposure, locale)}</div>
                </div>
                <div>
                    <span style={{ color: tokens.textMuted }}>{t('viopBond.colMargin', 'Teminat')}</span>
                    <div>{fmtMoney(row.initialMargin, locale)}</div>
                </div>
                <div>
                    <span style={{ color: tokens.textMuted }}>{t('viopBond.colMaturity', 'Vade')}</span>
                    <div>{maturityCell(row)}</div>
                </div>
            </div>
            {renderActions(row)}
        </article>
    );

    return (
        <section className="vb-section vb-section--priority">
            <h3 className="vb-section-title">{t('viopBond.myViopPositions', 'Benim VİOP Pozisyonlarım')}</h3>
            <p className="vb-section-lead" style={{ color: tokens.textMuted }}>
                {t(
                    'viopBond.myViopPositionsLead',
                    'Açık vadeli işlem pozisyonlarınızı teminat, K/Z, maruziyet ve vade bazında takip edin.',
                )}
            </p>
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
                            options: (['ALL', 'OPEN', 'CLOSED'] as const).map((f) => ({
                                value: f,
                                label: f === 'ALL' ? t('viopBond.filterAll', 'Tümü') : viopStatusLabel(f, t),
                            })),
                        },
                        {
                            id: 'direction',
                            label: t('viopBond.filterDirection', 'Yön'),
                            value: directionFilter,
                            onChange: (v) => setDirectionFilter(v as DirectionFilter),
                            options: (['ALL', 'LONG', 'SHORT'] as const).map((f) => ({
                                value: f,
                                label: f === 'ALL' ? t('viopBond.filterAll', 'Tümü') : viopDirectionLabel(f, t),
                            })),
                        },
                        {
                            id: 'category',
                            label: t('viopBond.filterCategory', 'Kategori'),
                            value: categoryFilter,
                            onChange: (v) => setCategoryFilter(v as CategoryFilter),
                            options: (['ALL', 'FX', 'INDEX', 'COMMODITY', 'EQUITY'] as const).map((f) => ({
                                value: f,
                                label: f === 'ALL' ? t('viopBond.filterAll', 'Tümü') : viopCategoryLabel(f, t),
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
                            'viopBond.viopEmpty',
                            'Henüz VİOP pozisyonunuz yok. Piyasa kontratlarından pozisyon ekleyebilirsiniz.',
                        )}
                    </p>
                </div>
            ) : (
                <>
                    <div className="vb-positions-cards">{filtered.map(positionCard)}</div>
                    <div className="pf-card-premium vb-table-wrap vb-positions-table" style={{ ...cardStyle, padding: '0.5rem' }}>
                        <table className="vb-table">
                            <thead>
                                <tr>
                                    <th>{t('viopBond.colContract', 'Kontrat')}</th>
                                    <th>{t('viopBond.colDirection', 'Yön')}</th>
                                    <th>{t('viopBond.colViopType', 'Kategori')}</th>
                                    <th>{t('viopBond.colCount', 'Adet')}</th>
                                    <th>{t('viopBond.colEntryPrice', 'Giriş')}</th>
                                    <th>{t('viopBond.colCurrentPrice', 'Güncel')}</th>
                                    <th>{t('viopBond.colPnl', 'K/Z')}</th>
                                    <th>{t('viopBond.colMargin', 'Teminat')}</th>
                                    <th>{t('viopBond.colExposure', 'Maruziyet')}</th>
                                    <th>{t('viopBond.colMaturity', 'Vade')}</th>
                                    <th>{t('viopBond.colStatus', 'Durum')}</th>
                                    <th />
                                </tr>
                            </thead>
                            <tbody>
                                {filtered.map((row) => (
                                    <tr key={row.id}>
                                        <td>
                                            <strong>{row.displayName?.trim() || row.symbol}</strong>
                                            <div className="vb-cell-sub">{row.symbol}</div>
                                        </td>
                                        <td>
                                            <span
                                                className={`vb-badge${row.direction === 'LONG' ? ' vb-badge--long' : ' vb-badge--short'}`}
                                            >
                                                {viopDirectionLabel(row.direction, t)}
                                            </span>
                                        </td>
                                        <td>{viopCategoryLabel(row.viopCategory, t)}</td>
                                        <td>{row.contractCount}</td>
                                        <td>{fmtMoney(row.entryPrice, locale)}</td>
                                        <td>{fmtMoney(row.currentPrice, locale)}</td>
                                        <td className={pnlClass(row.unrealizedPnl)}>
                                            {fmtMoney(row.unrealizedPnl, locale)}
                                        </td>
                                        <td>{fmtMoney(row.initialMargin, locale)}</td>
                                        <td>{fmtMoney(row.riskExposure, locale)}</td>
                                        <td>{maturityCell(row)}</td>
                                        <td>{viopStatusLabel(row.status, t)}</td>
                                        <td>{renderActions(row)}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </>
            )}
        </section>
    );
}
