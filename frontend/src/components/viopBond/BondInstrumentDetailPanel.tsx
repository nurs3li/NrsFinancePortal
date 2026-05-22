import type { ReactNode } from 'react';
import { Bell, Plus } from 'lucide-react';
import { useLanguage } from '../../i18n/LanguageContext';
import type { ManualBondPosition } from '../../types/bondPosition';
import type { TerminalListInstrumentVm } from '../../utils/marketTerminalListVm';
import {
    approxRealReturnPct,
    bondRiskTag,
    displayBondType,
    maturityBucket,
    maturityBucketLabel,
} from './bondAnalysisHelpers';
import { fmtDate, fmtMoney, fmtPct } from './formatViopBond';
import { pctClass, pnlClass } from './vbTabShared';

type Props = {
    instrument: TerminalListInstrumentVm;
    matchedPosition: ManualBondPosition | null;
    cpiYoY: number | null;
    tokens: { border: string; bgCard: string; textMuted: string; text?: string };
    onAddPosition: () => void;
    onSetAlert: () => void;
    onClose?: () => void;
};

export function BondInstrumentDetailPanel({
    instrument,
    matchedPosition,
    cpiYoY,
    tokens,
    onAddPosition,
    onSetAlert,
    onClose,
}: Props) {
    const { t, lang } = useLanguage();
    const locale = lang === 'en' ? 'en-US' : 'tr-TR';
    const bondType = displayBondType(instrument.symbol, instrument.displayName, undefined, t);
    const days = instrument.daysToMaturity;
    const bucket = maturityBucket(days);
    const yieldPct = instrument.yieldToMaturity;
    const realReturn = matchedPosition ? approxRealReturnPct(matchedPosition.returnPct, cpiYoY) : null;

    const section = (title: string, children: ReactNode) => (
        <div className="vb-detail-section">
            <h4 className="vb-detail-section-title" style={{ color: tokens.textMuted }}>
                {title}
            </h4>
            {children}
        </div>
    );

    const row = (label: string, value: ReactNode, hint?: string) => (
        <div key={label} className="vb-detail-row">
            <span style={{ color: tokens.textMuted }} title={hint}>
                {label}
            </span>
            <strong>{value}</strong>
        </div>
    );

    return (
        <aside className="vb-drawer pf-card-premium vb-bond-detail-panel" style={{ borderColor: tokens.border, background: tokens.bgCard }}>
            <div className="vb-detail-panel-head">
                <h3>{t('viopBond.marketDetailTitle', 'Enstrüman detayı')}</h3>
                {onClose ? (
                    <button type="button" className="pf-dash-btn pf-dash-btn--compact" onClick={onClose}>
                        {t('viopBond.close', 'Kapat')}
                    </button>
                ) : null}
            </div>
            <p className="vb-detail-symbol">{instrument.symbol}</p>
            <p className="vb-detail-sub" style={{ color: tokens.textMuted }}>
                {instrument.displayName}
            </p>

            {section(
                t('viopBond.detailBasics', 'Temel bilgiler'),
                <>
                    {row(t('viopBond.colBondType', 'Tür'), bondType)}
                    {row(t('viopBond.colCurrency', 'Döviz'), instrument.currency ?? 'TRY')}
                    {row(
                        t('viopBond.colMaturity', 'Vade'),
                        instrument.maturityDate ? (
                            <>
                                {fmtDate(instrument.maturityDate, locale)}
                                {days != null ? (
                                    <span className="vb-detail-muted"> · {days} {t('viopBond.days', 'gün')}</span>
                                ) : null}
                            </>
                        ) : (
                            '—'
                        ),
                    )}
                    {bucket
                        ? row(t('viopBond.maturityGroup', 'Vade grubu'), maturityBucketLabel(bucket, t))
                        : null}
                    {row(
                        t('viopBond.colCouponRate', 'Kupon oranı'),
                        instrument.couponRate != null ? fmtPct(instrument.couponRate, locale) : '—',
                        t('viopBond.couponHint', 'Nominal üzerinden yıllık kupon oranı; piyasa getirisi değildir.'),
                    )}
                    {instrument.couponFrequencyLabel
                        ? row(t('market.bondCouponFreq', 'Kupon sıklığı'), instrument.couponFrequencyLabel)
                        : null}
                </>,
            )}

            {section(
                t('viopBond.detailMarket', 'Piyasa verisi'),
                <>
                    {row(
                        t('viopBond.colCurrentPrice', 'Güncel fiyat'),
                        fmtMoney(instrument.price, locale),
                        t('viopBond.priceDirtyHint', 'Debt snapshot dirty price; nominal 100 üzerinden gösterim.'),
                    )}
                    {row(
                        t('viopBond.colYield', 'Piyasa getirisi'),
                        yieldPct != null ? fmtPct(yieldPct, locale) : '—',
                        yieldPct == null
                            ? t('viopBond.yieldMissing', 'Piyasa getirisi verisi bulunamadı.')
                            : undefined,
                    )}
                    {row(t('viopBond.colDay', 'Gün (%)'), fmtPct(instrument.pctDay, locale))}
                    {row(
                        t('viopBond.colSource', 'Kaynak'),
                        <span className="vb-badge vb-badge--market">{t('viopBond.sourceMarket', 'Piyasa verisi')}</span>,
                    )}
                </>,
            )}

            {section(
                t('viopBond.detailRisk', 'Risk yorumu'),
                <p className="vb-risk-comment" style={{ color: tokens.textMuted, margin: 0, fontSize: '0.85rem' }}>
                    {bondRiskTag(days, instrument.currency ?? 'TRY', t)}
                    <br />
                    <span style={{ fontSize: '0.8rem' }}>
                        {t(
                            'viopBond.rateRiskNote',
                            'Uzun vadeli tahviller faiz artışında daha hassas olabilir; kısa vadede etki sınırlıdır.',
                        )}
                    </span>
                </p>,
            )}

            {matchedPosition
                ? section(
                      t('viopBond.detailMyPosition', 'Benim pozisyonum'),
                      <>
                          {row(t('viopBond.colNominal', 'Nominal'), fmtMoney(matchedPosition.nominalValue, locale))}
                          {row(t('viopBond.colBuyPrice', 'Alış fiyatı'), fmtMoney(matchedPosition.buyPrice, locale))}
                          {row(t('viopBond.colCurrentPrice', 'Güncel fiyat'), fmtMoney(matchedPosition.currentPrice, locale))}
                          {row(
                              t('viopBond.colPnl', 'Fiyat K/Z'),
                              <span className={pnlClass(matchedPosition.totalReturn ?? matchedPosition.pnl)}>
                                  {fmtMoney(matchedPosition.totalReturn ?? matchedPosition.pnl, locale)}
                              </span>,
                          )}
                          {row(
                              t('viopBond.colReturn', 'Nominal getiri'),
                              <span className={pctClass(matchedPosition.returnPct)}>
                                  {fmtPct(matchedPosition.returnPct, locale)}
                              </span>,
                          )}
                          {row(
                              t('viopBond.colRealReturn', 'Reel getiri'),
                              realReturn != null ? (
                                  <span className={pctClass(realReturn)}>{fmtPct(realReturn, locale)}</span>
                              ) : (
                                  <span title={t('viopBond.realMissing', 'TÜFE verisi yok')}>—</span>
                              ),
                              cpiYoY != null
                                  ? t('viopBond.realHint', 'Nominal getiri − TÜFE YoY ({cpi}%)').replace('{cpi}', String(cpiYoY.toFixed(1)))
                                  : undefined,
                          )}
                          {row(
                              t('viopBond.annualCouponEst', 'Yıllık kupon tahmini'),
                              fmtMoney(matchedPosition.annualCoupon, locale),
                          )}
                          <span className="vb-badge vb-badge--manual">{t('viopBond.sourceManual', 'Manuel fiyat')}</span>
                      </>,
                  )
                : null}

            <div className="vb-drawer-actions">
                <button type="button" className="pf-dash-btn" onClick={onSetAlert}>
                    <Bell size={14} />
                    {t('viopBond.setAlert', 'Alarm Kur')}
                </button>
                <button type="button" className="pf-dash-btn pf-dash-btn--primary" onClick={onAddPosition}>
                    <Plus size={14} />
                    {t('viopBond.addToPosition', 'Pozisyona Ekle')}
                </button>
            </div>
        </aside>
    );
}
