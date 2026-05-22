import { bankBrand } from './bankRatesVm';

/** Banka adı → logo dosya kökü (public/bank-logos/{slug}.png|.svg) */
const BANK_LOGO_SLUG: Record<string, string> = {
    Akbank: 'akbank',
    Albaraka: 'albaraka',
    Denizbank: 'denizbank',
    Finansbank: 'finansbank',
    'QNB Finansbank': 'finansbank',
    Garanti: 'garanti',
    'Garanti BBVA': 'garanti',
    Halkbank: 'halkbank',
    HSBC: 'hsbc',
    'ING Bank': 'ing',
    'İş Bankası': 'isbank',
    Kuveyttürk: 'kuveytturk',
    Şekerbank: 'sekerbank',
    TEB: 'teb',
    ICBC: 'icbc',
    Vakıfbank: 'vakifbank',
    'Yapı Kredi': 'yapikredi',
    Ziraat: 'ziraat',
};

export function bankLogoSlug(bankName: string): string | null {
    const direct = BANK_LOGO_SLUG[bankName];
    if (direct) return direct;
    for (const [key, slug] of Object.entries(BANK_LOGO_SLUG)) {
        if (bankName.includes(key) || key.includes(bankName)) return slug;
    }
    return null;
}

/** İlk bulunan uzantı; yoksa null (fallback rozeti kullanılır). */
export function bankLogoSrc(bankName: string): string | null {
    const slug = bankLogoSlug(bankName);
    if (!slug) return null;
    return `/bank-logos/${slug}.png`;
}

export { bankBrand };
