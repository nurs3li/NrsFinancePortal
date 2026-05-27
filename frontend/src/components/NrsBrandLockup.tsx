import { useId } from 'react';
import { useLanguage } from '../i18n/LanguageContext';
import './NrsBrandLockup.css';

export function NrsBrandMark({ className }: { className?: string }) {
    const reactId = useId();
    const gid = `nrs-brand-grad-${reactId.replace(/:/g, '')}`;
    return (
        <svg
            className={className ? `nrs-brand__mark ${className}` : 'nrs-brand__mark'}
            width="54"
            height="48"
            viewBox="0 0 54 48"
            aria-hidden
        >
            <defs>
                <linearGradient id={gid} x1="0%" y1="100%" x2="100%" y2="0%">
                    <stop offset="0%" stopColor="#1d4ed8" />
                    <stop offset="55%" stopColor="#38bdf8" />
                    <stop offset="100%" stopColor="#7dd3fc" />
                </linearGradient>
            </defs>
            <g className="nrs-brand__mark-bars">
                <rect x="2" y="30" width="3.5" height="12" rx="0.8" fill={`url(#${gid})`} />
                <rect x="7.5" y="26" width="3.5" height="16" rx="0.8" fill={`url(#${gid})`} />
                <rect x="13" y="22" width="3.5" height="20" rx="0.8" fill={`url(#${gid})`} />
            </g>
            <text
                x="19"
                y="27.5"
                fill="currentColor"
                fontSize="14"
                fontWeight="800"
                fontFamily="Inter, system-ui, sans-serif"
                letterSpacing="0.04em"
            >
                NRS
            </text>
            <path
                fill="none"
                stroke={`url(#${gid})`}
                strokeWidth="2.2"
                strokeLinecap="round"
                strokeLinejoin="round"
                d="M 17 33 Q 26 22 38 14 L 44 9"
            />
            <path fill={`url(#${gid})`} d="M 40.5 7.5 L 46 8.8 L 42.8 13.5 Z" />
        </svg>
    );
}

/** Landing + app shell: monogram + NRS FINANCE / PORTAL lockup (i18n). */
export function NrsBrandLockup() {
    const { t } = useLanguage();
    return (
        <>
            <NrsBrandMark />
            <div className="nrs-brand__text">
                <span className="nrs-brand__main">{t('landing.brandLockupMain', 'NRS FINANCE')}</span>
                <div className="nrs-brand__portal-row">
                    <span className="nrs-brand__line" aria-hidden />
                    <span className="nrs-brand__portal">{t('landing.brandLockupPortal', 'PORTAL')}</span>
                    <span className="nrs-brand__line" aria-hidden />
                </div>
            </div>
        </>
    );
}
