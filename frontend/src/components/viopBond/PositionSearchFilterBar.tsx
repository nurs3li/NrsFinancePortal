import { useEffect, useId, useRef, useState } from 'react';
import { Filter } from 'lucide-react';
import { useLanguage } from '../../i18n/LanguageContext';

export type PositionFilterGroup = {
    id: string;
    label: string;
    options: { value: string; label: string }[];
    value: string;
    onChange: (value: string) => void;
};

type Props = {
    search: string;
    onSearchChange: (value: string) => void;
    searchPlaceholder: string;
    groups: PositionFilterGroup[];
    tokens: { border: string; bgCard: string; textMuted: string };
};

export function PositionSearchFilterBar({
    search,
    onSearchChange,
    searchPlaceholder,
    groups,
    tokens,
}: Props) {
    const { t } = useLanguage();
    const [open, setOpen] = useState(false);
    const wrapRef = useRef<HTMLDivElement>(null);
    const panelId = useId();

    const activeCount = groups.filter((g) => g.value !== 'ALL').length;

    useEffect(() => {
        if (!open) return;
        const onDoc = (e: MouseEvent) => {
            if (wrapRef.current && !wrapRef.current.contains(e.target as Node)) {
                setOpen(false);
            }
        };
        const onKey = (e: KeyboardEvent) => {
            if (e.key === 'Escape') setOpen(false);
        };
        document.addEventListener('mousedown', onDoc);
        document.addEventListener('keydown', onKey);
        return () => {
            document.removeEventListener('mousedown', onDoc);
            document.removeEventListener('keydown', onKey);
        };
    }, [open]);

    const cardStyle = { borderColor: tokens.border, background: tokens.bgCard };

    return (
        <div className="vb-search-filter-bar" ref={wrapRef}>
            <div className="vb-search-filter-bar__input-wrap">
                <input
                    className="vb-search vb-search--with-filter"
                    placeholder={searchPlaceholder}
                    value={search}
                    onChange={(e) => onSearchChange(e.target.value)}
                />
                <button
                    type="button"
                    className={`vb-filter-trigger${open ? ' is-open' : ''}${activeCount > 0 ? ' has-active' : ''}`}
                    style={cardStyle}
                    aria-expanded={open}
                    aria-controls={panelId}
                    aria-label={t('viopBond.filters', 'Filtreler')}
                    onClick={() => setOpen((v) => !v)}
                >
                    <Filter size={16} />
                    {activeCount > 0 ? <span className="vb-filter-trigger__badge">{activeCount}</span> : null}
                </button>
            </div>
            {open ? (
                <div id={panelId} className="vb-filter-panel" style={cardStyle} role="dialog" aria-label={t('viopBond.filters', 'Filtreler')}>
                    {groups.map((group) => (
                        <div key={group.id} className="vb-filter-panel__group">
                            <span className="vb-filter-panel__label" style={{ color: tokens.textMuted }}>
                                {group.label}
                            </span>
                            <div className="vb-filter-chips">
                                {group.options.map((opt) => (
                                    <button
                                        key={opt.value}
                                        type="button"
                                        className={`vb-filter-chip${group.value === opt.value ? ' is-active' : ''}`}
                                        onClick={() => group.onChange(opt.value)}
                                    >
                                        {opt.label}
                                    </button>
                                ))}
                            </div>
                        </div>
                    ))}
                    <div className="vb-filter-panel__footer">
                        <button
                            type="button"
                            className="pf-dash-btn pf-dash-btn--compact"
                            onClick={() => {
                                for (const g of groups) {
                                    g.onChange('ALL');
                                }
                            }}
                        >
                            {t('viopBond.clearFilters', 'Filtreleri temizle')}
                        </button>
                        <button type="button" className="pf-dash-btn pf-dash-btn--compact pf-dash-btn--primary" onClick={() => setOpen(false)}>
                            {t('viopBond.applyFilters', 'Uygula')}
                        </button>
                    </div>
                </div>
            ) : null}
        </div>
    );
}
