import { Eye, Search } from 'lucide-react';
import type { PortfolioAiAssetRow } from './portfolioAiAssetRows';
import { fmtPct } from './portfolioAiFormat';
import type { TranslateFn } from './portfolioAiUiTypes';

type Props = {
    t: TranslateFn;
    locale: string;
    rows: PortfolioAiAssetRow[];
    search: string;
    onSearchChange: (v: string) => void;
    selectedSymbol: string | null;
    onSelect: (symbol: string) => void;
};

export function PortfolioAiAssetTable({
    t,
    locale,
    rows,
    search,
    onSearchChange,
    selectedSymbol,
    onSelect,
}: Props) {
    const q = search.trim().toUpperCase();
    const filtered = q ? rows.filter((r) => r.symbol.includes(q)) : rows;

    return (
        <section className="pf-ai-dash-card pf-ai-dash-card--fill">
            <h2 className="pf-ai-dash-card__title">{t('portfolioAi.assetTableTitle', 'Varlık Bazlı AI Analizi')}</h2>
            <div className="pf-ai-search-wrap">
                <Search size={14} aria-hidden />
                <input
                    type="search"
                    value={search}
                    onChange={(e) => onSearchChange(e.target.value)}
                    placeholder={t('portfolioAi.assetSearchPh', 'Sembol ara (THYAO, VOO…)')}
                />
            </div>
            {filtered.length === 0 ? (
                <p className="pf-ai-muted">{t('portfolioAi.assetTableEmpty', 'Açık pozisyon yok veya eşleşme bulunamadı.')}</p>
            ) : (
                <div className="pf-ai-table-scroll">
                    <table className="pf-ai-table pf-ai-table--dense">
                        <thead>
                            <tr>
                                <th>{t('portfolioAi.colAsset', 'Varlık')}</th>
                                <th>{t('portfolioAi.colWeight', 'Ağırlık')}</th>
                                <th>{t('portfolioAi.colReturn', 'Getiri')}</th>
                                <th>{t('portfolioAi.colAssetScore', 'Puan')}</th>
                                <th>{t('portfolioAi.colRiskScore', 'Risk')}</th>
                                <th />
                            </tr>
                        </thead>
                        <tbody>
                            {filtered.map((r) => (
                                <tr
                                    key={r.symbol}
                                    className={selectedSymbol === r.symbol ? 'is-selected' : ''}
                                    onClick={() => onSelect(r.symbol)}
                                >
                                    <td>
                                        <strong>{r.symbol}</strong>
                                    </td>
                                    <td>{fmtPct(r.weightPct, locale)}</td>
                                    <td className={r.returnPct != null && r.returnPct >= 0 ? 'pos' : 'neg'}>
                                        {r.returnPct != null
                                            ? `${r.returnPct >= 0 ? '+' : ''}${fmtPct(r.returnPct, locale)}`
                                            : '—'}
                                    </td>
                                    <td>{r.assetScore}</td>
                                    <td>{r.riskScore}</td>
                                    <td>
                                        <button
                                            type="button"
                                            className="pf-ai-icon-btn"
                                            aria-label={t('portfolioAi.view', 'Görüntüle')}
                                            onClick={(e) => {
                                                e.stopPropagation();
                                                onSelect(r.symbol);
                                            }}
                                        >
                                            <Eye size={12} />
                                        </button>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            )}
        </section>
    );
}
