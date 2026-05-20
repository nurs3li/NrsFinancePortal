import type { ReactNode } from 'react';

type Props = {
    term: string;
    children: ReactNode;
    className?: string;
};

/** Finansal okuryazarlık help-card entegrasyonu için tıklanabilir terim işareti. */
export function PfHelpTerm({ term, children, className }: Props) {
    return (
        <span data-help-term={term} className={className} style={{ cursor: 'help' }}>
            {children}
        </span>
    );
}
