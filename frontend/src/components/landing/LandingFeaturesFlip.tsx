import type { ReactNode } from 'react';
import { useLanguage } from '../../i18n/LanguageContext';
import {
    FEATURE_CARDS,
    FEATURE_CATEGORIES,
    FEATURES_SECTION_TITLE,
    type FeatureCardId,
    type FeatureLang,
} from './landingFeaturesFlipData';
import './LandingFeaturesFlip.css';

function Kpi({ label, value, tone }: { label: string; value: string; tone?: 'pos' | 'neg' | 'neutral' }) {
    return (
        <div className="lff-kpi">
            <span className="lff-kpi__label">{label}</span>
            <span className={`lff-kpi__value${tone ? ` ${tone}` : ''}`}>{value}</span>
        </div>
    );
}

function BackRealReturn({ lang }: { lang: FeatureLang }) {
    const L = lang === 'tr';
    return (
        <>
            <p className="lff-flip-card__back-label">{L ? 'Nominal vs Reel' : 'Nominal vs Real'}</p>
            <svg className="lff-mini-chart" viewBox="0 0 200 42" aria-hidden>
                <polyline points="8,32 50,22 95,14 140,10 192,6" fill="none" stroke="#4ade80" strokeWidth="2" />
                <polyline points="8,32 50,28 95,24 140,22 192,20" fill="none" stroke="#38bdf8" strokeWidth="1.5" />
            </svg>
            <div className="lff-kpi-grid">
                <Kpi label={L ? 'Nominal' : 'Nominal'} value={L ? '+₺42.180' : '+₺42,180'} tone="pos" />
                <Kpi label={L ? 'Reel' : 'Real'} value={L ? '+₺18.760' : '+₺18,760'} tone="pos" />
                <Kpi label={L ? 'Enflasyon Etkisi' : 'Inflation Impact'} value={L ? '-₺12.420' : '-₺12,420'} tone="neg" />
                <Kpi label={L ? 'Sağlık Skoru' : 'Health Score'} value="78/100" />
            </div>
        </>
    );
}

function BackMissedOpportunity({ lang }: { lang: FeatureLang }) {
    const L = lang === 'tr';
    return (
        <>
            <p className="lff-flip-card__back-label">{L ? 'Fırsat maliyeti' : 'Opportunity cost'}</p>
            <div className="lff-kpi-grid">
                <Kpi
                    label={L ? 'Toplam Kaçırılan' : 'Total Missed'}
                    value={L ? '₺14.500' : '₺14,500'}
                    tone="neg"
                />
                <Kpi label={L ? 'En Büyük Fırsat' : 'Top Opportunity'} value="AAPL +%45" tone="pos" />
            </div>
            <div className="lff-tag-row">
                <span className="lff-tag info">{L ? 'Fırsat Skoru: %72' : 'Opportunity Score: 72%'}</span>
                <span className="lff-tag">{L ? 'Orta-Yüksek' : 'Medium-High'}</span>
            </div>
        </>
    );
}

function BackHeatmapTerminal({ lang }: { lang: FeatureLang }) {
    const L = lang === 'tr';
    const rows = [
        { sym: 'THYAO', chg: '+2.35%', up: true },
        { sym: 'F_XU030', chg: '+0.84%', up: true },
        { sym: 'AAPL', chg: '-0.42%', up: false },
        { sym: 'BTC/USDT', chg: '+1.18%', up: true },
    ];
    return (
        <>
            <p className="lff-flip-card__back-label">{L ? 'Canlı terminal' : 'Live terminal'}</p>
            {rows.map((r) => (
                <div key={r.sym} className="lff-terminal-row">
                    <span className="sym">{r.sym}</span>
                    <span className={r.up ? 'lff-kpi__value pos' : 'lff-kpi__value neg'}>{r.chg}</span>
                </div>
            ))}
            <div className="lff-tag-row">
                <span className="lff-tag">{L ? 'RSI: 62 Nötr' : 'RSI: 62 Neutral'}</span>
                <span className="lff-tag pos">{L ? 'Trend: Pozitif' : 'Trend: Positive'}</span>
            </div>
        </>
    );
}

function BackMacro({ lang }: { lang: FeatureLang }) {
    const L = lang === 'tr';
    const items = L
        ? [
              ['TCMB Politika Faizi', '%45,00'],
              ['TÜFE Yıllık', '%69,8'],
              ['Reel Faiz', '-%14,6'],
              ['2Y Tahvil', '%42,1'],
          ]
        : [
              ['CBRT Policy Rate', '45.00%'],
              ['CPI YoY', '69.8%'],
              ['Real Rate', '-14.6%'],
              ['2Y Bond', '42.1%'],
          ];
    return (
        <>
            <p className="lff-flip-card__back-label">{L ? 'Makro panel' : 'Macro panel'}</p>
            <div className="lff-kpi-grid">
                {items.map(([label, value]) => (
                    <Kpi key={label} label={label} value={value} tone={value.startsWith('-') ? 'neg' : 'neutral'} />
                ))}
            </div>
        </>
    );
}

function BackTimeMachine({ lang }: { lang: FeatureLang }) {
    const L = lang === 'tr';
    return (
        <>
            <p className="lff-flip-card__back-label">{L ? 'Simülasyon çıktısı' : 'Simulation output'}</p>
            <div className="lff-tag-row" style={{ marginTop: 0 }}>
                <span className="lff-tag">{L ? 'Harcama: ₺10.000' : 'Spend: ₺10,000'}</span>
                <span className="lff-tag">{L ? 'Mayıs 2024' : 'May 2024'}</span>
            </div>
            <div className="lff-kpi-grid">
                <Kpi
                    label={L ? 'En İyi Senaryo' : 'Best Scenario'}
                    value={L ? 'Altın ₺15.870' : 'Gold ₺15,870'}
                    tone="pos"
                />
                <Kpi label={L ? 'Alternatif Kazanç' : 'Alt. Gain'} value={L ? '+₺5.870' : '+₺5,870'} tone="pos" />
            </div>
            <span className="lff-tag pos" style={{ alignSelf: 'flex-start' }}>
                {L ? 'En iyi: Altın' : 'Best: Gold'}
            </span>
        </>
    );
}

function BackAlerts({ lang }: { lang: FeatureLang }) {
    const L = lang === 'tr';
    const items = L
        ? [
              { t: 'Fiyat Eşiği Aşıldı', b: 'THYAO ₺312,40', c: 'warn' as const },
              { t: 'Reel Getiri Negatif', b: 'TÜFE sonrası -%2,5', c: 'risk' as const },
              { t: 'Yoğunlaşma Riski', b: 'BIST ağırlığı %45', c: 'info' as const },
          ]
        : [
              { t: 'Price Threshold Crossed', b: 'THYAO ₺312.40', c: 'warn' as const },
              { t: 'Negative Real Return', b: 'Post-CPI -2.5%', c: 'risk' as const },
              { t: 'Concentration Risk', b: 'BIST weight 45%', c: 'info' as const },
          ];
    return (
        <>
            <p className="lff-flip-card__back-label">{L ? 'Bildirim akışı' : 'Notification feed'}</p>
            {items.map((item) => (
                <div key={item.t} className={`lff-alert-item ${item.c}`}>
                    <strong>{item.t}</strong>
                    <span>{item.b}</span>
                </div>
            ))}
        </>
    );
}

const BACK_RENDERERS: Record<FeatureCardId, (lang: FeatureLang) => ReactNode> = {
    'real-return': (lang) => <BackRealReturn lang={lang} />,
    'missed-opportunity': (lang) => <BackMissedOpportunity lang={lang} />,
    'heatmap-terminal': (lang) => <BackHeatmapTerminal lang={lang} />,
    macro: (lang) => <BackMacro lang={lang} />,
    'time-machine': (lang) => <BackTimeMachine lang={lang} />,
    alerts: (lang) => <BackAlerts lang={lang} />,
};

function FlipCard({ cardId, lang }: { cardId: FeatureCardId; lang: FeatureLang }) {
    const card = FEATURE_CARDS[cardId];
    const Icon = card.icon;
    const greenIcon = cardId === 'real-return' || cardId === 'heatmap-terminal';

    return (
        <article className="lff-flip-card" tabIndex={0}>
            <div className="lff-flip-card__inner">
                <div className="lff-flip-card__face lff-flip-card__face--front">
                    <div className={`lff-flip-card__icon-wrap${greenIcon ? ' lff-flip-card__icon-wrap--green' : ''}`}>
                        <Icon size={20} strokeWidth={1.35} aria-hidden />
                    </div>
                    <h4 className="lff-flip-card__card-title">{card.title[lang]}</h4>
                    <p className="lff-flip-card__card-desc">{card.description[lang]}</p>
                </div>
                <div className="lff-flip-card__face lff-flip-card__face--back">{BACK_RENDERERS[cardId](lang)}</div>
            </div>
        </article>
    );
}

export function LandingFeaturesFlip() {
    const { lang } = useLanguage();
    const featureLang: FeatureLang = lang === 'en' ? 'en' : 'tr';

    return (
        <section id="features" className="landing-features-flip landing-reveal">
            <h2 className="landing-features-flip__title">{FEATURES_SECTION_TITLE[featureLang]}</h2>
            <div className="landing-features-flip__grid">
                {FEATURE_CATEGORIES.map((category) => (
                    <div key={category.id}>
                        <h3 className="landing-features-flip__col-title">{category.title[featureLang]}</h3>
                        <div className="landing-features-flip__col-cards">
                            {category.cardIds.map((cardId) => (
                                <FlipCard key={cardId} cardId={cardId} lang={featureLang} />
                            ))}
                        </div>
                    </div>
                ))}
            </div>
        </section>
    );
}
