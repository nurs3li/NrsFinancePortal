import { useLanguage } from '../../i18n/LanguageContext';
import { fmtMoney, fmtNativeAmount } from './formatViopBond';

type Props = {
    riskExposureTry?: number | null;
    riskExposureNative?: number | null;
    quoteCurrency?: string | null;
    missingFxRate?: boolean;
    locale: string;
};

export function ViopExposureCell({
    riskExposureTry,
    riskExposureNative,
    quoteCurrency,
    missingFxRate,
    locale,
}: Props) {
    const { t } = useLanguage();
    const cur = (quoteCurrency ?? 'TRY').toUpperCase();
    const isForeign = cur === 'USD' || cur === 'EUR';

    if (missingFxRate && isForeign) {
        return (
            <div>
                <div>{fmtNativeAmount(riskExposureNative, cur, locale)}</div>
                <div className="vb-cell-sub vb-cell-sub--warn">
                    {t('viopBond.exposureTryMissing', 'TRY karşılığı: — · Kur verisi yok')}
                </div>
            </div>
        );
    }

    return (
        <div>
            <div>{fmtMoney(riskExposureTry, locale)} ₺</div>
            {isForeign && riskExposureNative != null ? (
                <div className="vb-cell-sub">{fmtNativeAmount(riskExposureNative, cur, locale)}</div>
            ) : null}
        </div>
    );
}
