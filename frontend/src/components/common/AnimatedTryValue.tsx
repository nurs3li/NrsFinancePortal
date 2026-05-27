import type { CSSProperties } from 'react';
import { useAnimatedNumber } from '../simulation/useAnimatedNumber';

function fmtTry(locale: string, v: number) {
    return new Intl.NumberFormat(locale, { style: 'currency', currency: 'TRY', maximumFractionDigits: 0 }).format(v);
}

function fmtTrySigned(locale: string, v: number) {
    const s = v >= 0 ? '+' : '';
    return `${s}${fmtTry(locale, v)}`;
}

type Props = {
    value: number;
    locale: string;
    signed?: boolean;
    className?: string;
    style?: CSSProperties;
};

export function AnimatedTryValue({ value, locale, signed = false, className, style }: Props) {
    const display = useAnimatedNumber(value);
    const text = signed ? fmtTrySigned(locale, display) : fmtTry(locale, display);
    return (
        <span className={className} style={style}>
            {text}
        </span>
    );
}
