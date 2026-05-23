import type { LucideIcon } from 'lucide-react';
import {
    Briefcase,
    Calculator,
    CandlestickChart,
    Building2,
    Landmark,
    LayoutDashboard,
    LineChart,
    Newspaper,
    ScrollText,
    Users,
} from 'lucide-react';

export type LayoutNavItem = {
    key: string;
    to: string;
    label: string;
    mobileLabel: string;
    icon: LucideIcon;
    show: boolean;
};

type BuildNavArgs = {
    isAdmin: boolean;
    t: (key: string, fallback?: string) => string;
};

export function buildLayoutNavItems({ isAdmin, t }: BuildNavArgs): LayoutNavItem[] {
    if (isAdmin) {
        return [
            {
                key: 'market',
                to: '/market',
                label: t('nav.market', 'Piyasa'),
                mobileLabel: t('nav.market', 'Piyasa'),
                icon: LineChart,
                show: true,
            },
            {
                key: 'marketMacro',
                to: '/market/macro',
                label: t('nav.marketMacro', 'Makro Finans Paneli'),
                mobileLabel: t('nav.marketMacroShort', 'Makro'),
                icon: Landmark,
                show: true,
            },
            {
                key: 'bankRates',
                to: '/market/bank-rates',
                label: t('nav.bankRates', 'Banka Kurları'),
                mobileLabel: t('nav.bankRatesShort', 'Banka'),
                icon: Building2,
                show: true,
            },
            {
                key: 'news',
                to: '/news',
                label: t('nav.news', 'Haberler'),
                mobileLabel: t('nav.news', 'Haberler'),
                icon: Newspaper,
                show: true,
            },
            {
                key: 'admin-users',
                to: '/admin/users',
                label: t('nav.userManagement', 'Kullanıcı Yönetimi'),
                mobileLabel: t('nav.adminShort', 'Admin'),
                icon: Users,
                show: true,
            },
            {
                key: 'admin-audit',
                to: '/admin/audit',
                label: t('nav.auditLogs', 'Audit Logs'),
                mobileLabel: t('nav.auditShort', 'Audit'),
                icon: ScrollText,
                show: true,
            },
        ];
    }
    return [
        {
            key: 'dashboard',
            to: '/dashboard',
            label: t('nav.dashboard', 'Dashboard'),
            mobileLabel: t('nav.dashboard', 'Dashboard'),
            icon: LayoutDashboard,
            show: true,
        },
        {
            key: 'market',
            to: '/market',
            label: t('nav.market', 'Piyasa'),
            mobileLabel: t('nav.market', 'Piyasa'),
            icon: LineChart,
            show: true,
        },
        {
            key: 'news',
            to: '/news',
            label: t('nav.news', 'Haberler'),
            mobileLabel: t('nav.news', 'Haberler'),
            icon: Newspaper,
            show: true,
        },
        {
            key: 'portfolio',
            to: '/portfolio',
            label: t('nav.portfolio', 'Spot Portföyüm'),
            mobileLabel: t('nav.portfolioShort', 'Spot'),
            icon: Briefcase,
            show: true,
        },
        {
            key: 'viop-bond',
            to: '/viop-bond-analysis',
            label: t('nav.viopBond', 'Vadeli Portföyüm'),
            mobileLabel: t('nav.viopBondShort', 'Vadeli'),
            icon: CandlestickChart,
            show: true,
        },
        {
            key: 'simulation',
            to: '/simulation',
            label: t('nav.investmentSimulation', 'Yatırım Simülasyonu'),
            mobileLabel: t('nav.simulationShort', 'Simülasyon'),
            icon: Calculator,
            show: true,
        },
        {
            key: 'marketMacro',
            to: '/market/macro',
            label: t('nav.marketMacro', 'Makro Finans Paneli'),
            mobileLabel: t('nav.marketMacroShort', 'Makro'),
            icon: Landmark,
            show: true,
        },
        {
            key: 'bankRates',
            to: '/market/bank-rates',
            label: t('nav.bankRates', 'Banka Kurları'),
            mobileLabel: t('nav.bankRatesShort', 'Banka'),
            icon: Building2,
            show: true,
        },
    ];
}

export function resolveActiveNavItem(pathname: string, items: LayoutNavItem[]): LayoutNavItem | undefined {
    let best: LayoutNavItem | undefined;
    for (const item of items) {
        if (!item.show) continue;
        if (pathname.startsWith(item.to)) {
            if (!best || item.to.length > best.to.length) best = item;
        }
    }
    return best;
}
