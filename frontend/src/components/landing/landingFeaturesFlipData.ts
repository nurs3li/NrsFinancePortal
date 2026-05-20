import type { LucideIcon } from 'lucide-react';
import { Bell, Hourglass, LayoutGrid, LineChart, ShieldAlert, TrendingUp } from 'lucide-react';

export type FeatureLang = 'tr' | 'en';

export type FeatureCardId =
    | 'real-return'
    | 'missed-opportunity'
    | 'heatmap-terminal'
    | 'macro'
    | 'time-machine'
    | 'alerts';

export type FeatureCategoryId = 'analytics' | 'market' | 'tools';

export type FeatureCardDef = {
    id: FeatureCardId;
    icon: LucideIcon;
    title: Record<FeatureLang, string>;
    description: Record<FeatureLang, string>;
};

export type FeatureCategoryDef = {
    id: FeatureCategoryId;
    title: Record<FeatureLang, string>;
    cardIds: FeatureCardId[];
};

export const FEATURES_SECTION_TITLE: Record<FeatureLang, string> = {
    tr: 'Özellikler',
    en: 'Features',
};

export const FEATURE_CATEGORIES: FeatureCategoryDef[] = [
    {
        id: 'analytics',
        title: {
            tr: 'Gelişmiş Analitik & Portföy',
            en: 'Advanced Analytics & Portfolio',
        },
        cardIds: ['real-return', 'missed-opportunity'],
    },
    {
        id: 'market',
        title: {
            tr: 'Çok Varlıklı Piyasa Terminali',
            en: 'Multi-Asset Market Terminal',
        },
        cardIds: ['heatmap-terminal', 'macro'],
    },
    {
        id: 'tools',
        title: {
            tr: 'Akıllı Araçlar & Uyarılar',
            en: 'Smart Tools & Alerts',
        },
        cardIds: ['time-machine', 'alerts'],
    },
];

export const FEATURE_CARDS: Record<FeatureCardId, FeatureCardDef> = {
    'real-return': {
        id: 'real-return',
        icon: TrendingUp,
        title: {
            tr: 'TÜFE Tabanlı Reel Getiri Analizi',
            en: 'Inflation-Adjusted Real Return Analysis',
        },
        description: {
            tr: 'Nominal kâr/zarar oranlarınızı TÜFE verileriyle enflasyondan arındırın; paranızın gerçek satın alma gücünü izleyin.',
            en: 'Isolate nominal returns using CPI data; track the true purchasing power and net growth of your capital.',
        },
    },
    'missed-opportunity': {
        id: 'missed-opportunity',
        icon: ShieldAlert,
        title: {
            tr: '“Satmasaydım Ne Olurdu?” Analizi',
            en: '"What If I Didn\'t Sell?" Analysis',
        },
        description: {
            tr: 'Kapatılan pozisyonların satış sonrası yaşam döngüsünü ve güncel değerini takip ederek kaçan fırsatları analiz edin.',
            en: 'Track the post-sale lifecycle and current TRY value of closed positions to measure missed opportunity costs.',
        },
    },
    'heatmap-terminal': {
        id: 'heatmap-terminal',
        icon: LayoutGrid,
        title: {
            tr: 'Canlı Sektörel Isı Haritası & Terminal',
            en: 'Live Sectoral Heatmap & Terminal',
        },
        description: {
            tr: 'BIST, VİOP, Kripto ve ABD hisselerini tek ekranda; RSI/MA göstergeleri ve trend çizgileriyle anlık izleyin.',
            en: 'Monitor BIST, VIOP, Crypto, and US Stocks on a single screen with live RSI/MA indicators and sparklines.',
        },
    },
    macro: {
        id: 'macro',
        icon: LineChart,
        title: {
            tr: 'Makroekonomik Göstergeler & Kıyas',
            en: 'Macroeconomic Indicators & Comparison',
        },
        description: {
            tr: 'TCMB faiz, TÜFE ve tahvil verilerini takip edin; varlıkları enflasyon ve mevduat getirisiyle kıyaslayın.',
            en: 'Follow CBRT rates, CPI, and bonds; compare asset performances directly against inflation and deposit yields.',
        },
    },
    'time-machine': {
        id: 'time-machine',
        icon: Hourglass,
        title: {
            tr: 'Zaman Makinesi Simülasyonu',
            en: 'Time Machine Investment Simulator',
        },
        description: {
            tr: 'Geçmiş harcamalarınızı yatırıma dönüştürün; “O gün alsaydım bugün ne olurdu?” senaryosunu grafiklerle yarıştırın.',
            en: 'Convert past expenses into investments; simulate “what if I bought back then” scenarios with multi-asset charts.',
        },
    },
    alerts: {
        id: 'alerts',
        icon: Bell,
        title: {
            tr: 'Akıllı Fiyat Alarmları & İçgörüler',
            en: 'Smart Price Alerts & Insights',
        },
        description: {
            tr: 'Fiyat eşikleri ve yüzde değişimler için anlık alarmlar kurun; portföy risklerine dair akıllı bildirimler alın.',
            en: 'Set instant alerts for price thresholds and % changes; receive intelligent notifications regarding portfolio risks.',
        },
    },
};
