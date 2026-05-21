import type { ReactNode } from 'react';
import { Bell, Plus } from 'lucide-react';
import { useLanguage } from '../../i18n/LanguageContext';
import { viopCategoryFor } from '../../constants/ViopWhitelist';
import type { ManualViopPosition } from '../../types/viopPosition';
import type { TerminalListInstrumentVm } from '../../utils/marketTerminalListVm';
import { findOpenViopPosition, viopCategoryRiskNote, daysToExpiryFromIso } from './viopAnalysisHelpers';
import { viopCategoryLabel, viopDirectionLabel } from './viopPositionLabels';
import { formatViopUnderlyingDisplay, resolveViopExpiry } from './viopContractMeta';
import { fmtMoney, fmtPct } from './formatViopBond';
import { pnlClass } from './vbTabShared';

type Props = {
    instrument: TerminalListInstrumentVm;
    positions: ManualViopPosition[];
    tokens: { border: string; bgCard: string; textMuted: string };
    onAddPosition: () => void;
    onSetAlert: () => void;
    onPositionDetail?: (position: ManualViopPosition) => void;
    onClose?: () => void;
};

export function ViopInstrumentDetailPanel({
    instrument,
    positions,
    tokens,
    onAddPosition,
    onSetAlert,
    onPositionDetail,
    onClose,
}: Props) {
    const { t, lang } = useLanguage();
    const locale = lang === 'en' ? 'en-US' : 'tr-TR';
    const cat = viopCategoryFor(instrument.symbol);
    const expiry = resolveViopExpiry(instrument.symbol, locale, {
        contractMonth: instrument.contractMonth,
    });
    const daysLeft = expiry?.expiryDate ? daysToExpiryFromIso(expiry.expiryDate) : null;
    const matched = findOpenViopPosition(positions, instrument.symbol);

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
        <aside
            className="vb-drawer pf-card-premium vb-bond-detail-panel"
            style={{ borderColor: tokens.border, background: tokens.bgCard }}
        >
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
                    {row(t('viopBond.colViopType', 'Tür'), cat ? viopCategoryLabel(cat, t) : '—')}
                    {row(t('viopBond.colUnderlying', 'Dayanak'), formatViopUnderlyingDisplay(instrument.symbol))}
                    {row(
                        t('viopBond.colContractMonth', 'Kontrat ayı'),
                        instrument.contractMonth ?? expiry?.displayLong ?? '—',
                    )}
                    {row(
                        t('viopBond.colExpiry', 'Vade'),
                        expiry ? (
                            <>
                                {expiry.displayLong}
                                {daysLeft != null ? (
                                    <span className="vb-detail-muted">
                                        {' '}
                                        · {daysLeft} {t('viopBond.days', 'gün')}
                                    </span>
                                ) : null}
                            </>
                        ) : (
                            '—'
                        ),
                    )}
                </>,
            )}

            {section(
                t('viopBond.detailMarket', 'Piyasa verisi'),
                <>
                    {row(t('viopBond.colCurrentPrice', 'Son fiyat'), fmtMoney(instrument.price, locale))}
                    {row(t('viopBond.colDay', 'Gün'), fmtPct(instrument.pctDay, locale))}
                    {row(t('viopBond.colWeek', 'Hafta'), fmtPct(instrument.pctWeek, locale))}
                    {row(t('viopBond.colMonth', 'Ay'), fmtPct(instrument.pctMonth, locale))}
                    {row(t('viopBond.colYear', 'Yıl'), fmtPct(instrument.pctYear, locale))}
                    {row(
                        t('viopBond.colMargin', 'Piyasa teminatı'),
                        instrument.marginRequirement != null
                            ? fmtMoney(instrument.marginRequirement, locale)
                            : '—',
                    )}
                    {row(
                        t('viopBond.colSource', 'Kaynak'),
                        <span className="vb-badge vb-badge--market">
                            {instrument.source?.trim() || t('viopBond.sourceMarket', 'Piyasa verisi')}
                        </span>,
                    )}
                </>,
            )}

            {section(
                t('viopBond.detailRisk', 'Risk yorumu'),
                <p className="vb-risk-comment" style={{ color: tokens.textMuted, margin: 0, fontSize: '0.85rem' }}>
                    {viopCategoryRiskNote(cat, t)}
                    <br />
                    <span style={{ fontSize: '0.8rem' }}>
                        {t(
                            'viopBond.viopRiskLeverage',
                            'Kaldıraç etkisi nedeniyle küçük fiyat hareketleri K/Z’yi büyütebilir.',
                        )}
                    </span>
                    {daysLeft != null && daysLeft <= 14 ? (
                        <>
                            <br />
                            <span style={{ fontSize: '0.8rem', color: '#fbbf24' }}>
                                {t('viopBond.viopExpiryWarn', 'Vade yaklaşıyor; pozisyon planınızı gözden geçirin.')}
                            </span>
                        </>
                    ) : null}
                </p>,
            )}

            {matched
                ? section(
                      t('viopBond.detailMyPosition', 'Benim pozisyonum'),
                      <>
                          {row(t('viopBond.colDirection', 'Yön'), viopDirectionLabel(matched.direction, t))}
                          {row(t('viopBond.colCount', 'Adet'), String(matched.contractCount))}
                          {row(t('viopBond.colEntryPrice', 'Giriş'), fmtMoney(matched.entryPrice, locale))}
                          {row(t('viopBond.colCurrentPrice', 'Güncel'), fmtMoney(matched.currentPrice, locale))}
                          {row(
                              t('viopBond.colPnl', 'Açık K/Z'),
                              <span className={pnlClass(matched.unrealizedPnl)}>
                                  {fmtMoney(matched.unrealizedPnl, locale)}
                              </span>,
                          )}
                          {row(t('viopBond.colMargin', 'Teminat'), fmtMoney(matched.initialMargin, locale))}
                          {row(t('viopBond.colExposure', 'Maruziyet'), fmtMoney(matched.riskExposure, locale))}
                      </>,
                  )
                : section(
                      t('viopBond.detailMyPosition', 'Benim pozisyonum'),
                      <p style={{ color: tokens.textMuted, margin: 0, fontSize: '0.82rem' }}>
                          {t('viopBond.viopNoOpenPosition', 'Bu kontratta açık pozisyonunuz yok.')}
                      </p>,
                  )}

            <div className="vb-drawer-actions">
                <button type="button" className="pf-dash-btn" onClick={onSetAlert}>
                    <Bell size={14} />
                    {t('viopBond.setAlert', 'Alarm Kur')}
                </button>
                <button type="button" className="pf-dash-btn pf-dash-btn--primary" onClick={onAddPosition}>
                    <Plus size={14} />
                    {t('viopBond.addToPosition', 'Pozisyona Ekle')}
                </button>
                {matched && onPositionDetail ? (
                    <button type="button" className="pf-dash-btn" onClick={() => onPositionDetail(matched)}>
                        {t('viopBond.positionDetail', 'Pozisyon Detayı')}
                    </button>
                ) : null}
            </div>
        </aside>
    );
}
