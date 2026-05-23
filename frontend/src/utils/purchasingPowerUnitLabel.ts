import type { MarketCategory } from '../components/market/marketTypes';
import type { MetalsSubmarket } from '../constants/preciousMetalsUsd';
import type { FundSubmarket } from '../services/marketTerminalListApi';

export type EquitySubmarket = 'US' | 'BIST';

export type PurchasingPowerUnitContext = {
    category: MarketCategory;
    equitySubmarket?: EquitySubmarket;
    fundSubmarket?: FundSubmarket;
    metalsSubmarket?: MetalsSubmarket;
};

type TranslateFn = (key: string, fallback: string) => string;

/** TL karşılaştırma kartı / grafik başlıkları için birim (ör. "1 adet", "1 lot"). */
export function resolvePurchasingPowerUnitLabel(ctx: PurchasingPowerUnitContext, t: TranslateFn): string {
    switch (ctx.category) {
        case 'EQUITY':
            return ctx.equitySubmarket === 'BIST'
                ? t('market.ppUnit.lot', '1 lot')
                : t('market.ppUnit.share', '1 adet');
        case 'CRYPTO':
            return t('market.ppUnit.coin', '1 coin');
        case 'FX':
            return t('market.ppUnit.fxUnit', '1 birim');
        case 'METALS':
            return ctx.metalsSubmarket === 'OUNCE'
                ? t('market.ppUnit.ounce', '1 ons')
                : t('market.ppUnit.gram', '1 gram');
        case 'FUNDS':
            return t('market.ppUnit.fundShare', '1 pay');
        default:
            return t('market.ppUnit.lot', '1 lot');
    }
}
