import type { BondType, CouponFrequency } from '../../types/bondPosition';

export function bondTypeLabel(type: BondType, t: (k: string, d: string) => string): string {
    switch (type) {
        case 'GOVERNMENT_BOND':
            return t('viopBond.bondTypeGov', 'Devlet Tahvili');
        case 'TREASURY_BILL':
            return t('viopBond.bondTypeBill', 'Hazine Bonosu');
        case 'EUROBOND':
            return t('viopBond.bondTypeEuro', 'Eurobond');
        case 'CORPORATE_BOND':
        default:
            return t('viopBond.bondTypeCorp', 'Özel Sektör Tahvili');
    }
}

export function couponFrequencyLabel(freq: CouponFrequency, t: (k: string, d: string) => string): string {
    switch (freq) {
        case 'ANNUAL':
            return t('viopBond.couponAnnual', 'Yıllık');
        case 'SEMI_ANNUAL':
            return t('viopBond.couponSemi', '6 ayda bir');
        case 'QUARTERLY':
            return t('viopBond.couponQuarter', '3 ayda bir');
        case 'NONE':
        default:
            return t('viopBond.couponNone', 'Kuponsuz');
    }
}
