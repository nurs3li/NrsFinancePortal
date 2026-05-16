import { AlertTriangle } from 'lucide-react';
import { useLanguage } from '../../i18n/LanguageContext';

type SimulationHeaderProps = {
    showUsdNotice: boolean;
    mutedColor: string;
};

export function SimulationHeader({ showUsdNotice, mutedColor }: SimulationHeaderProps) {
    const { t } = useLanguage();

    return (
        <header className="sim-header">
            <div className="sim-header__titles">
                <h1 className="sim-header__title">{t('simulation.title', 'Yatırım Simülasyonu')}</h1>
                <p className="sim-lead sim-header__lead">
                    {t(
                        'simulation.lead',
                        'Geçmişte yaptığın bir harcamayı yatırıma dönüştürseydin bugün ne olurdu? Bir tarih, tutar ve varlık seç; sistem geçmiş fiyatı bulur, bugünkü değerini ve getirisini hesaplar.',
                    )}
                </p>
            </div>
            <div className="sim-header__alerts">
                <div className="sim-info-alert" style={{ color: mutedColor }}>
                    <span className="sim-info-alert__icon" aria-hidden>
                        ℹ
                    </span>
                    <span>
                        {t(
                            'simulation.tryBasisInfo',
                            'Simülasyon hesapları TRY bazındadır. Hisse ve kripto için geçmiş USD fiyatlar, ilgili güne kadarki USDTRY günlük serisi ile çevrilir; güncel birim fiyat ise canlı USDTRY (spot) ile TRY’ye alınır.',
                        )}
                    </span>
                </div>
                {showUsdNotice ? (
                    <div className="sim-info-alert sim-info-alert--warn" style={{ color: mutedColor, borderColor: 'rgba(56,189,248,0.45)' }}>
                        <AlertTriangle size={16} className="sim-info-alert__warn-icon" aria-hidden />
                        <span>
                            {t(
                                'simulation.approximationNotice',
                                'Sonuçlar yaklaşıktır: ABD hisse, kripto ve fon geçmiş performans serisi tarihsel USDTRY ile; bugünkü değer ve birim fiyat güncel kur ile hesaplanmıştır. Kesin yatırım tavsiyesi değildir.',
                            )}
                        </span>
                    </div>
                ) : null}
            </div>
        </header>
    );
}
