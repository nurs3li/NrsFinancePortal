import { useState } from 'react';
import { bankBrand, bankLogoSrc } from '../../utils/bankLogos';

type Props = {
    bankName: string;
    size?: 'sm' | 'md';
    className?: string;
};

export function BankLogo({ bankName, size = 'md', className = '' }: Props) {
    const [failed, setFailed] = useState(false);
    const src = bankLogoSrc(bankName);
    const brand = bankBrand(bankName);
    const sizeClass = size === 'sm' ? 'br-logo--sm' : 'br-logo--md';

    if (src && !failed) {
        return (
            <img
                src={src}
                alt=""
                className={`br-logo br-logo--img ${sizeClass} ${className}`.trim()}
                onError={() => setFailed(true)}
            />
        );
    }

    return (
        <span
            className={`br-logo br-logo--fallback ${sizeClass} ${className}`.trim()}
            style={{ background: brand.color }}
            aria-hidden
        >
            {brand.abbr}
        </span>
    );
}
