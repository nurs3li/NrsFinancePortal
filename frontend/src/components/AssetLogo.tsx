import { useState } from 'react';
import type { LucideIcon } from 'lucide-react';

type Props = {
    src: string | null;
    alt: string;
    fallbackIcon: LucideIcon;
    fallbackColor: string;
    size?: number;
};

export function AssetLogo({ src, alt, fallbackIcon: FallbackIcon, fallbackColor, size = 24 }: Props) {
    const [hasError, setHasError] = useState(false);

    if (!src || hasError) {
        return (
            <span
                style={{
                    width: size,
                    height: size,
                    borderRadius: 999,
                    background: 'rgba(148, 163, 184, 0.15)',
                    display: 'inline-flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    flexShrink: 0,
                }}
            >
                <FallbackIcon size={Math.round(size * 0.66)} color={fallbackColor} />
            </span>
        );
    }

    return (
        <img
            src={src}
            alt={alt}
            width={size}
            height={size}
            loading="lazy"
            decoding="async"
            style={{
                width: size,
                height: size,
                objectFit: 'contain',
                borderRadius: 999,
                flexShrink: 0,
            }}
            onError={() => setHasError(true)}
        />
    );
}
