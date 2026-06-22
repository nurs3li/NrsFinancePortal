import { useLanguage } from '../../i18n/LanguageContext';

function SlideHead({
    titleKey,
    subKey,
    titleFallback,
    subFallback,
}: {
    titleKey: string;
    subKey: string;
    titleFallback: string;
    subFallback: string;
}) {
    const { t } = useLanguage();
    return (
        <div className="hero-slide-head">
            <h3 className="hero-slide-title">{t(titleKey, titleFallback)}</h3>
            <p className="hero-slide-sub">{t(subKey, subFallback)}</p>
        </div>
    );
}

function SparkUp() {
    return (
        <svg className="hero-spark" viewBox="0 0 56 18" aria-hidden>
            <polyline points="0,14 12,10 24,12 36,6 48,8 56,4" fill="none" stroke="#166534" strokeWidth="1.5" />
        </svg>
    );
}

function SparkDown() {
    return (
        <svg className="hero-spark" viewBox="0 0 56 18" aria-hidden>
            <polyline points="0,6 14,8 28,10 42,12 56,14" fill="none" stroke="#991B1B" strokeWidth="1.5" />
        </svg>
    );
}

export function HeroSlideMultiAsset() {
    const { t } = useLanguage();
    const rows = [
        { label: 'BIST', sym: 'THYAO', chg: '+2.35%', up: true },
        { label: 'VİOP', sym: 'F_XU030', chg: '+0.84%', up: true },
        { label: t('landing.slide1.abd', 'ABD'), sym: 'AAPL', chg: '-0.42%', up: false },
        { label: t('landing.slide1.crypto', 'Kripto'), sym: 'BTC/USDT', chg: '+1.18%', up: true },
        { label: 'FX', sym: 'USD/TRY', chg: '+0.31%', up: true },
    ];
    return (
        <div className="hero-slide-body">
            <SlideHead
                titleKey="landing.slide1.title"
                subKey="landing.slide1.sub"
                titleFallback="Çok Varlıklı Canlı Terminal"
                subFallback="BIST • VİOP • ABD Hisseleri • Kripto • FX"
            />
            <div className="hero-slide-split">
                <div className="hero-panel">
                    {rows.map((r) => (
                        <div key={r.sym} className="hero-terminal-row" style={{ marginBottom: '0.28rem' }}>
                            <span className="hero-terminal-label">{r.label}</span>
                            <span className="sym">{r.sym}</span>
                            {r.up ? <SparkUp /> : <SparkDown />}
                            <span className={`chg ${r.up ? 'up' : 'down'}`}>{r.chg}</span>
                        </div>
                    ))}
                </div>
                <div className="hero-panel">
                    <p className="hero-mini-label">{t('landing.slide1.indicators', 'Teknik göstergeler')}</p>
                    <div className="hero-ind-grid">
                        <div className="hero-ind-item">
                            <span>RSI</span>
                            <span>62 · {t('landing.slide1.rsi', 'Nötr')}</span>
                        </div>
                        <div className="hero-ind-item">
                            <span>MA20</span>
                            <span>{t('landing.slide1.ma20', 'Üstünde')}</span>
                        </div>
                        <div className="hero-ind-item">
                            <span>{t('landing.slide1.trend', 'Trend')}</span>
                            <span>{t('landing.slide1.trendPos', 'Pozitif')}</span>
                        </div>
                        <div className="hero-ind-item">
                            <span>{t('landing.slide1.vol', 'Volatilite')}</span>
                            <span>{t('landing.slide1.volMid', 'Orta')}</span>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}
