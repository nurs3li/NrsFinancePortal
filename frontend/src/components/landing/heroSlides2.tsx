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

export function HeroSlideHeatmap() {
    const { t } = useLanguage();
    const tiles = [
        { sym: 'THYAO', pct: '+2.1%', up: true },
        { sym: 'ASELS', pct: '+1.4%', up: true },
        { sym: 'GARAN', pct: '-0.8%', up: false },
        { sym: 'TUPRS', pct: '+0.6%', up: true },
        { sym: 'BIMAS', pct: '-0.3%', up: false },
        { sym: 'KCHOL', pct: '+0.9%', up: true },
    ];
    const macros = [
        { k: 'landing.slide2.m1', f: 'TCMB Politika Faizi', v: '%45,00' },
        { k: 'landing.slide2.m2', f: 'TÜFE Yıllık', v: '%69,8' },
        { k: 'landing.slide2.m3', f: 'Reel Faiz', v: '-%14,6' },
        { k: 'landing.slide2.m4', f: '2Y Tahvil', v: '%42,1' },
        { k: 'landing.slide2.m5', f: '10Y Tahvil', v: '%31,4' },
        { k: 'landing.slide2.m6', f: 'USD/TRY', v: '32,85' },
    ];
    return (
        <div className="hero-slide-body">
            <SlideHead
                titleKey="landing.slide2.title"
                subKey="landing.slide2.sub"
                titleFallback="BIST Isı Haritası & Makro Panel"
                subFallback="Sektörel hareketleri ve makro göstergeleri tek ekranda izleyin"
            />
            <div className="hero-slide-split">
                <div className="hero-panel">
                    <p className="hero-mini-label">{t('landing.slide2.sectors', 'Sektör ısı haritası')}</p>
                    <div className="hero-heatmap">
                        {tiles.map((tile) => (
                            <div key={tile.sym} className={`hero-heat-tile ${tile.up ? 'up' : 'down'}`}>
                                {tile.sym}
                                <br />
                                {tile.pct}
                            </div>
                        ))}
                    </div>
                </div>
                <div className="hero-panel">
                    <p className="hero-mini-label">{t('landing.slide2.macro', 'Makro göstergeler')}</p>
                    <div className="hero-macro-grid">
                        {macros.map((m) => (
                            <div key={m.k} className="hero-macro-card">
                                <strong>{t(m.k, m.f)}</strong>
                                <span>{m.v}</span>
                            </div>
                        ))}
                    </div>
                </div>
            </div>
        </div>
    );
}

export function HeroSlideMissedOpportunity() {
    const { t } = useLanguage();
    return (
        <div className="hero-slide-body">
            <SlideHead
                titleKey="landing.slide3.title"
                subKey="landing.slide3.sub"
                titleFallback="Satmasaydım Ne Olurdu? Analizi"
                subFallback="Satılan pozisyonların fırsat maliyetini günlük TRY değeriyle görün"
            />
            <div className="hero-slide-split">
                <div className="hero-panel">
                    <p className="hero-mini-label">{t('landing.slide3.chartTitle', 'Satış Sonrası Performans')}</p>
                    <svg className="hero-chart-svg" viewBox="0 0 200 100" aria-hidden>
                        <line x1="0" y1="90" x2="200" y2="90" stroke="var(--landing-chart-grid)" strokeWidth="0.5" />
                        <polyline points="10,72 50,70 90,68 130,68 170,68 190,68" fill="none" stroke="#60a5fa" strokeWidth="2" />
                        <polyline points="10,72 50,65 90,52 130,38 170,28 190,22" fill="none" stroke="#4ade80" strokeWidth="2" />
                        <line x1="90" y1="12" x2="90" y2="88" stroke="var(--landing-chart-dim)" strokeWidth="1" strokeDasharray="3 2" />
                        <text x="92" y="18" fill="var(--landing-chart-dim)" fontSize="7">
                            {t('landing.slide3.sellDate', 'Satış Tarihi')}
                        </text>
                        <text x="100" y="48" fill="#4ade80" fontSize="7">
                            {t('landing.slide3.missed', 'Fırsat Maliyeti: ₺14.500')}
                        </text>
                    </svg>
                </div>
                <div className="hero-panel">
                    <div className="hero-metric-stack">
                        <div className="hero-metric-card">
                            <strong>{t('landing.slide3.m1', 'Toplam Kaçırılan Getiri')}</strong>
                            <span className="pos">₺14.500</span>
                        </div>
                        <div className="hero-metric-card">
                            <strong>{t('landing.slide3.m2', 'En Büyük Fırsat')}</strong>
                            <span className="pos">THYAO +%38</span>
                        </div>
                        <div className="hero-metric-card">
                            <strong>{t('landing.slide3.m3', 'Fırsat Skoru')}</strong>
                            <span>{t('landing.slide3.m3v', 'Orta-Yüksek')}</span>
                        </div>
                        <div className="hero-metric-card">
                            <strong>{t('landing.slide3.m4', 'Portföy Etkisi')}</strong>
                            <span>{t('landing.slide3.m4v', 'Orta')}</span>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}

export function HeroSlideRealReturn() {
    const { t } = useLanguage();
    return (
        <div className="hero-slide-body">
            <SlideHead
                titleKey="landing.slide4.title"
                subKey="landing.slide4.sub"
                titleFallback="Enflasyondan Arındırılmış Reel Getiri"
                subFallback="Nominal kazanç ile gerçek alım gücü performansını ayırın"
            />
            <div className="hero-slide-split">
                <div className="hero-panel">
                    <p className="hero-mini-label">{t('landing.slide4.chartTitle', 'Nominal vs Reel Getiri')}</p>
                    <svg className="hero-chart-svg" viewBox="0 0 200 100" aria-hidden>
                        <rect x="60" y="25" width="120" height="55" fill="rgba(239,68,68,0.08)" />
                        <text x="62" y="22" fill="#f87171" fontSize="6">
                            {t('landing.slide4.cpi', 'TÜFE Etkisi')}
                        </text>
                        <polyline points="10,80 50,65 90,48 130,32 170,18 190,12" fill="none" stroke="#4ade80" strokeWidth="2" />
                        <polyline points="10,80 50,72 90,62 130,52 170,45 190,40" fill="none" stroke="#38bdf8" strokeWidth="2" />
                        <text x="120" y="58" fill="#f87171" fontSize="7">
                            {t('landing.slide4.cpiAmt', 'Enflasyon Etkisi: -₺12.420')}
                        </text>
                    </svg>
                </div>
                <div className="hero-panel">
                    <div className="hero-metric-stack">
                        <div className="hero-metric-card">
                            <strong>{t('landing.slide4.m1', 'Nominal Getiri')}</strong>
                            <span className="pos">+₺42.180</span>
                        </div>
                        <div className="hero-metric-card">
                            <strong>{t('landing.slide4.m2', 'Reel Getiri')}</strong>
                            <span className="pos">+₺18.760</span>
                        </div>
                        <div className="hero-metric-card">
                            <strong>{t('landing.slide4.m3', 'Enflasyon Etkisi')}</strong>
                            <span className="neg">-₺12.420</span>
                        </div>
                        <div className="hero-metric-card">
                            <strong>{t('landing.slide4.m4', 'Sağlık Skoru')}</strong>
                            <span>78/100</span>
                        </div>
                    </div>
                    <div className="hero-conc-bar" aria-hidden>
                        <span style={{ width: '45%', background: '#3b82f6' }} />
                        <span style={{ width: '20%', background: '#eab308' }} />
                        <span style={{ width: '15%', background: '#22c55e' }} />
                        <span style={{ width: '10%', background: '#f97316' }} />
                        <span style={{ width: '10%', background: '#8b5cf6' }} />
                    </div>
                    <p className="hero-chart-note">
                        BIST %45 · {t('landing.slide4.gold', 'Altın')} %20 · FX %15 · {t('landing.slide4.crypto', 'Kripto')} %10
                    </p>
                </div>
            </div>
        </div>
    );
}

export function HeroSlideTimeMachine() {
    const { t } = useLanguage();
    return (
        <div className="hero-slide-body">
            <SlideHead
                titleKey="landing.slide5.title"
                subKey="landing.slide5.sub"
                titleFallback="Zaman Makinesi Simülatörü"
                subFallback="Geçmiş harcamalarınızı yatırım senaryolarıyla karşılaştırın"
            />
            <div className="hero-chip-strip">
                <span className="hero-chip">{t('landing.slide5.chip1', 'Geçmiş Harcama: ₺10.000')}</span>
                <span className="hero-chip">{t('landing.slide5.chip2', 'Tarih: Mayıs 2024')}</span>
                <span className="hero-chip">{t('landing.slide5.chip3', 'Kategori: Telefon')}</span>
            </div>
            <div className="hero-slide-split">
                <div className="hero-panel">
                    <p className="hero-mini-label">{t('landing.slide5.chartTitle', 'Bugünkü Değer Karşılaştırması')}</p>
                    <svg className="hero-chart-svg" viewBox="0 0 200 90" aria-hidden>
                        <polyline points="10,70 60,68 110,55 160,35 190,25" fill="none" stroke="#eab308" strokeWidth="2" />
                        <polyline points="10,70 60,62 110,48 160,38 190,32" fill="none" stroke="#4ade80" strokeWidth="1.5" />
                        <polyline points="10,70 60,65 110,58 160,50 190,45" fill="none" stroke="#38bdf8" strokeWidth="1.5" />
                        <polyline points="10,70 60,70 110,71 160,72 190,73" fill="none" stroke="var(--landing-chart-dim)" strokeWidth="1.5" />
                    </svg>
                </div>
                <div className="hero-panel">
                    <div className="hero-metric-stack">
                        <div className="hero-metric-card">
                            <strong>{t('landing.slide5.m1', 'Harcama Tutarı')}</strong>
                            <span>₺10.000</span>
                        </div>
                        <div className="hero-metric-card">
                            <strong>{t('landing.slide5.m2', 'Bugünkü En İyi Senaryo')}</strong>
                            <span className="pos">{t('landing.slide5.m2v', 'Altın ₺15.870')}</span>
                        </div>
                        <div className="hero-metric-card">
                            <strong>{t('landing.slide5.m3', 'Alternatif Kazanç')}</strong>
                            <span className="pos">+₺5.870</span>
                        </div>
                    </div>
                    <span className="hero-badge">{t('landing.slide5.best', 'En iyi senaryo: Altın')}</span>
                </div>
            </div>
            <div className="hero-bar-compare">
                <div className="hero-bar-col muted">
                    <div className="bar" />
                    {t('landing.slide5.b1', 'Harcasaydım')}
                </div>
                <div className="hero-bar-col">
                    <div className="bar" style={{ height: '28px' }} />
                    BIST100
                </div>
                <div className="hero-bar-col gold">
                    <div className="bar" />
                    {t('landing.slide5.b3', 'Altın')}
                </div>
                <div className="hero-bar-col">
                    <div className="bar" style={{ height: '24px' }} />
                    USD/TRY
                </div>
            </div>
        </div>
    );
}

export function HeroSlideAlerts() {
    const { t } = useLanguage();
    return (
        <div className="hero-slide-body">
            <SlideHead
                titleKey="landing.slide6.title"
                subKey="landing.slide6.sub"
                titleFallback="Akıllı İçgörüler & Fiyat Alarmları"
                subFallback="Reel getiri, fiyat eşiği ve risk sinyallerinde anlık uyarılar alın"
            />
            <div className="hero-slide-split">
                <div className="hero-panel">
                    <p className="hero-mini-label">{t('landing.slide6.chartTitle', 'Alarm Tetikleme Grafiği')}</p>
                    <svg className="hero-chart-svg" viewBox="0 0 200 100" aria-hidden>
                        <rect x="0" y="75" width="200" height="20" fill="rgba(239,68,68,0.1)" />
                        <text x="4" y="88" fill="#f87171" fontSize="6">
                            {t('landing.slide6.riskZone', 'Reel Risk Bölgesi')}
                        </text>
                        <polyline points="10,70 50,58 90,45 130,32 170,22 190,18" fill="none" stroke="#4ade80" strokeWidth="2" />
                        <line x1="0" y1="38" x2="200" y2="38" stroke="#eab308" strokeWidth="1" strokeDasharray="4 3" />
                        <text x="4" y="35" fill="#eab308" fontSize="6">
                            {t('landing.slide6.threshold', 'Fiyat Eşiği: ₺312,40')}
                        </text>
                        <circle cx="130" cy="32" r="3" fill="#38bdf8" />
                        <text x="132" y="28" fill="#38bdf8" fontSize="6">
                            {t('landing.slide6.crossed', 'Eşik Aşıldı')}
                        </text>
                    </svg>
                </div>
                <div className="hero-panel">
                    <div className="hero-alert-card warn">
                        <strong>{t('landing.slide6.a1t', 'Fiyat Eşiği Aşıldı')}</strong>
                        <span>{t('landing.slide6.a1b', 'THYAO ₺312,40 seviyesini geçti')}</span>
                    </div>
                    <div className="hero-alert-card risk">
                        <strong>{t('landing.slide6.a2t', 'Reel Getiri Negatif')}</strong>
                        <span>{t('landing.slide6.a2b', 'TÜFE sonrası performans -%2,5')}</span>
                    </div>
                    <div className="hero-alert-card">
                        <strong>{t('landing.slide6.a3t', 'Portföy Yoğunlaşma Riski')}</strong>
                        <span>{t('landing.slide6.a3b', 'BIST ağırlığı %45')}</span>
                    </div>
                    <div className="hero-alert-card">
                        <strong>{t('landing.slide6.a4t', 'Cooldown Aktif')}</strong>
                        <span>{t('landing.slide6.a4b', 'Aynı alarm 1 saat içinde tekrar gönderilmez')}</span>
                    </div>
                    <div className="hero-channel-strip">
                        <span className="hero-channel-chip">{t('landing.slide6.ch1', 'Uygulama içi: Aktif')}</span>
                        <span className="hero-channel-chip">{t('landing.slide6.ch2', 'E-posta: Aktif')}</span>
                    </div>
                </div>
            </div>
        </div>
    );
}
