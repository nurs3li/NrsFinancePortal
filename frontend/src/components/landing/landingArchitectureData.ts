import type { LucideIcon } from 'lucide-react';
import { Activity, Cpu, ShieldCheck } from 'lucide-react';

export type ArchLang = 'tr' | 'en';

export type ArchCardId = 'microservices' | 'identity' | 'events';

export type ArchCardDef = {
    id: ArchCardId;
    icon: LucideIcon;
    title: Record<ArchLang, string>;
    body: Record<ArchLang, string>;
};

export const ARCH_SECTION_TITLE: Record<ArchLang, string> = {
    tr: 'Güçlü Altyapı ve Sistem Mimarisi',
    en: 'Robust Infrastructure & System Architecture',
};

export const ARCH_SECTION_INTRO: Record<ArchLang, string> = {
    tr: 'NRS Finans Portal, veri yoğun finansal süreçleri ve anlık sinyalleri milisaniyeler düzeyinde işleyen, modern ve ölçeklenebilir bir yazılım mimarisi üzerine inşa edilmiştir.',
    en: 'NRS Finance Portal is built on a modern, scalable software architecture designed to process data-intensive financial workflows and instant signals at millisecond levels.',
};

export const ARCH_DOCS_LABEL: Record<ArchLang, string> = {
    tr: 'Proje Teknik Dokümantasyonu (Docs)',
    en: 'Project Technical Documentation (Docs)',
};

/** GitHub docs klasörü — depo kökündeki /docs */
export const ARCH_DOCS_URL = 'https://github.com/nurs3li/NrsFinancePortal/tree/process/docs';

export const ARCH_CARDS: ArchCardDef[] = [
    {
        id: 'microservices',
        icon: Cpu,
        title: {
            tr: 'Mikroservis Mimarisi',
            en: 'Microservices Architecture',
        },
        body: {
            tr: 'Piyasa Verisi, Finans Çekirdeği ve Bildirim servisleri bağımsız mikroservisler olarak ayrıştırılmıştır. Yüksek trafik altında yatayda esnekçe ölçeklenir.',
            en: 'Market Data, Finance Core, and Notification services are decoupled into independent microservices, enabling flexible horizontal scaling under high traffic.',
        },
    },
    {
        id: 'identity',
        icon: ShieldCheck,
        title: {
            tr: 'Güvenlik, Kimlik & Yetkilendirme',
            en: 'Identity & Access Management',
        },
        body: {
            tr: 'Keycloak tabanlı güvenli kimlik doğrulama altyapısı. Rol Tabanlı Erişim Kontrolü (RBAC) ile kullanıcı ve admin yetkileri ayrıştırılır; JWT ve OTP ile uç noktalar korunur.',
            en: 'Keycloak-powered secure authentication framework. Role-Based Access Control (RBAC) isolates user and admin privileges; protected via JWT and OTP security.',
        },
    },
    {
        id: 'events',
        icon: Activity,
        title: {
            tr: 'Event-Driven Sinyal Hattı & Gözlemlenebilirlik',
            en: 'Event-Driven Pipeline & Observability',
        },
        body: {
            tr: 'Anlık fiyat alarmları Apache Kafka mesaj kuyruğu üzerinden gecikmesiz iletilir. Sistem izlenebilirliği Grafana ve OpenSearch destekli Audit Hub ile anlık kayıt altındadır.',
            en: 'Real-time price alerts are processed instantly via Apache Kafka. System tracing and logs are monitored through a Grafana and OpenSearch backed Audit Hub.',
        },
    },
];
