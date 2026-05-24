import type { ReactNode } from 'react';
import { useLanguage } from '../../i18n/LanguageContext';
import type { ManualViopPosition } from '../../types/viopPosition';
import { resolveViopExpiry } from './viopContractMeta';
import { viopCategoryLabel, viopDirectionLabel, viopStatusLabel } from './viopPositionLabels';
import { fmtDate, fmtLeverageX, fmtMoney, fmtNativeAmount, fmtRatioPercent } from './formatViopBond';
import { ViopExposureCell } from './ViopExposureCell';
import { pnlClass } from './vbTabShared';

type Props = {
    position: ManualViopPosition | null;
    tokens: { border: string; bgCard: string; textMuted: string };
    onClose?: () => void;
};

export function ViopPositionDetailDrawer({ position, tokens, onClose }: Props) {
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

    const expiry = resolveViopExpiry(position.symbol, locale, { expiryDate: position.expiryDate ?? undefined });
    const quote = (position.quoteCurrency ?? 'TRY').toUpperCase();
    const marginLabel =
        position.initialMargin != null
            ? t('viopBond.estimatedMargin', 'Tahmini teminat')
            : t('viopBond.colMargin', 'Teminat');

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
                {position.symbol} · {viopCategoryLabel(position.viopCategory, t)}
            </p>

            <div className="vb-detail-section">
                <h4 className="vb-detail-section-title" style={{ color: tokens.textMuted }}>
                    {t('viopBond.detailMyPosition', 'Benim pozisyonum')}
                </h4>
                {row(t('viopBond.colDirection', 'Yön'), viopDirectionLabel(position.direction, t))}
                {row(t('viopBond.colCount', 'Adet'), String(position.contractCount))}
                {row(t('viopBond.colMultiplier', 'Kontrat çarpanı'), String(position.contractMultiplier ?? 1))}
                {row(t('viopBond.colCurrency', 'Para birimi'), quote)}
                {row(t('viopBond.colEntryDate', 'Alış tarihi'), fmtDate(position.entryDate, locale))}
                {row(
                    t('viopBond.colEntryPrice', 'Giriş fiyatı'),
                    fmtNativeAmount(position.entryPrice, quote, locale),
                )}
                {row(
                    t('viopBond.colCurrentPrice', 'Güncel fiyat'),
                    fmtNativeAmount(position.currentPrice, quote, locale),
                )}
                {row(
                    t('viopBond.colPnl', 'Açık K/Z'),
                    <span className={pnlClass(position.unrealizedPnl)}>
                        {fmtMoney(position.unrealizedPnl, locale)} ₺
                        {position.unrealizedPnlNative != null && quote !== 'TRY' ? (
                            <span className="vb-cell-sub" style={{ display: 'block', fontWeight: 400 }}>
                                {fmtNativeAmount(position.unrealizedPnlNative, quote, locale)}
                            </span>
                        ) : null}
                    </span>,
                )}
                {row(marginLabel, `${fmtMoney(position.initialMargin, locale)} ₺`)}
                {row(
                    t('viopBond.colExposure', 'Maruziyet'),
                    <ViopExposureCell
                        riskExposureTry={position.riskExposure}
                        riskExposureNative={position.riskExposureNative}
                        quoteCurrency={position.quoteCurrency}
                        missingFxRate={position.missingFxRate}
                        locale={locale}
                    />,
                )}
                {row(t('viopBond.colLeverage', 'Kaldıraç'), fmtLeverageX(position.leverage, locale))}
                {row(t('viopBond.colPnlMargin', 'K/Z / Teminat'), fmtRatioPercent(position.pnlToMarginRatio, locale))}
                {row(
                    t('viopBond.marginRatioShort', 'Teminat oranı'),
                    fmtRatioPercent(position.marginRatio, locale),
                )}
                {row(
                    t('viopBond.colMaturity', 'Vade'),
                    expiry
                        ? `${expiry.displayLong}${position.daysToExpiry != null ? ` · ${position.daysToExpiry}g` : ''}`
                        : '—',
                )}
                {row(t('viopBond.colStatus', 'Durum'), viopStatusLabel(position.status, t))}
            </div>

            <p className="vb-risk-note" style={{ marginTop: '0.75rem', color: tokens.textMuted, fontSize: '0.8rem' }}>
                {t(
                    'viopBond.viopPositionNote',
                    'Net finansal etki = teminat + açık K/Z. Maruziyet, kaldıraçlı sözleşme büyüklüğüdür (TRY karşılığı).',
                )}
            </p>
        </div>
    );
}
