import type { ReactNode } from 'react';
import { useLanguage } from '../../i18n/LanguageContext';
import type { ManualBondPosition } from '../../types/bondPosition';
import { approxRealReturnPct, maturityBucket, maturityBucketLabel } from './bondAnalysisHelpers';
import { bondTypeLabel } from './bondPositionLabels';
import { fmtDate, fmtMoney, fmtPct } from './formatViopBond';
import { pctClass, pnlClass } from './vbTabShared';

type Props = {
    position: ManualBondPosition | null;
    cpiYoY?: number | null;
    tokens: { border: string; bgCard: string; textMuted: string };
    onClose?: () => void;
};

export function BondPositionDetailDrawer({ position, cpiYoY = null, tokens, onClose }: Props) {
    const { t, lang } = useLanguage();
    const locale = lang === 'en' ? 'en-US' : 'tr-TR';

    if (!position) {
        return (
            <div className="vb-drawer pf-card-premium" style={{ borderColor: tokens.border, background: tokens.bgCard }}>
                <p style={{ color: tokens.textMuted, margin: 0 }}>
                    {t('viopBond.selectRow', 'Detay için tablodan bir pozisyon seçin.')}
                </p>
            </div>
        );
    }

    const realReturn = approxRealReturnPct(position.returnPct, cpiYoY);
    const bucket = maturityBucket(position.daysToMaturity);
    const statusLabel =
        position.status === 'OPEN'
            ? t('viopBond.statusOpen', 'Açık')
            : position.status === 'SOLD'
              ? t('viopBond.statusSold', 'Satıldı')
              : position.status;

    const row = (label: string, value: ReactNode) => (
        <div key={label} className="vb-detail-row">
            <span style={{ color: tokens.textMuted }}>{label}</span>
            <strong>{value}</strong>
        </div>
    );

    return (
        <div className="vb-drawer pf-card-premium vb-bond-detail-panel" style={{ borderColor: tokens.border, background: tokens.bgCard }}>
            <div className="vb-detail-panel-head">
                <h3>{position.displayName?.trim() || position.symbol}</h3>
                {onClose ? (
                    <button type="button" className="pf-dash-btn pf-dash-btn--compact" onClick={onClose}>
                        {t('viopBond.close', 'Kapat')}
                    </button>
                ) : null}
            </div>
            <p className="vb-detail-sub" style={{ color: tokens.textMuted }}>
                {position.symbol} · {bondTypeLabel(position.bondType, t)}
            </p>

            <div className="vb-detail-section">
                <h4 className="vb-detail-section-title" style={{ color: tokens.textMuted }}>
                    {t('viopBond.detailMyPosition', 'Benim pozisyonum')}
                </h4>
                {row(t('viopBond.colCurrency', 'Döviz'), position.currency)}
                {row(t('viopBond.colNominal', 'Nominal'), fmtMoney(position.nominalValue, locale))}
                {row(t('viopBond.colBuyPrice', 'Alış fiyatı'), fmtMoney(position.buyPrice, locale))}
                {row(t('viopBond.colCurrentPrice', 'Güncel fiyat'), fmtMoney(position.currentPrice, locale))}
                {row(
                    t('viopBond.colPricePnl', 'Fiyat K/Z'),
                    <span className={pnlClass(position.pricePnl ?? position.pnl)}>
                        {fmtMoney(position.pricePnl ?? position.pnl, locale)}
                    </span>,
                )}
                {row(
                    t('viopBond.colCollectedCoupon', 'Tahmini tahsil edilen kupon'),
                    fmtMoney(position.collectedCoupon, locale),
                )}
                {row(
                    t('viopBond.colTotalReturn', 'Toplam getiri'),
                    <span className={pnlClass(position.totalReturn)}>
                        {fmtMoney(position.totalReturn, locale)}
                    </span>,
                )}
                {row(
                    t('viopBond.colTotalReturnPct', 'Toplam getiri %'),
                    <span className={pctClass(position.totalReturnPercent)}>{fmtPct(position.totalReturnPercent, locale)}</span>,
                )}
                {row(
                    t('viopBond.colReturn', 'Fiyat getiri %'),
                    <span className={pctClass(position.returnPct)}>{fmtPct(position.returnPct, locale)}</span>,
                )}
                {row(
                    t('viopBond.colRealReturn', 'Reel getiri'),
                    realReturn != null ? (
                        <span className={pctClass(realReturn)}>{fmtPct(realReturn, locale)}</span>
                    ) : (
                        <span title={t('viopBond.realMissing', 'TÜFE verisi yok')}>—</span>
                    ),
                )}
                {row(
                    t('viopBond.colMaturity', 'Vade'),
                    position.maturityDate ? (
                        <>
                            {fmtDate(position.maturityDate, locale)}
                            {position.daysToMaturity != null ? ` · ${position.daysToMaturity}g` : ''}
                            {bucket ? ` · ${maturityBucketLabel(bucket, t)}` : ''}
                        </>
                    ) : (
                        '—'
                    ),
                )}
                {row(
                    t('viopBond.colCouponRate', 'Kupon'),
                    position.couponRate != null ? fmtPct(position.couponRate, locale) : '—',
                )}
                {row(t('viopBond.colStatus', 'Durum'), statusLabel)}
            </div>

            <p className="vb-risk-note" style={{ marginTop: '0.75rem', color: tokens.textMuted, fontSize: '0.8rem' }}>
                {t(
                    'viopBond.bondNote',
                    'Fiyat K/Z alış ile güncel fiyat farkından hesaplanır. Kupon oranı ile piyasa getirisi aynı şey değildir.',
                )}
            </p>
        </div>
    );
}
