import type { ReactNode } from 'react';
import type { MacroTermId } from '../../../content/macroEducationTerms';
import { InfoButton } from './InfoButton';

type Props = {
    children: ReactNode;
    termId?: MacroTermId;
    infoAriaLabel?: string;
    as?: 'span' | 'h2' | 'h3' | 'h4';
    className?: string;
    style?: React.CSSProperties;
};

export function TermLabel({ children, termId, infoAriaLabel, as: Tag = 'span', className, style }: Props) {
    return (
        <Tag className={className ?? 'macro-term-label'} style={style}>
            {children}
            {termId && infoAriaLabel ? <InfoButton termId={termId} ariaLabel={infoAriaLabel} /> : null}
        </Tag>
    );
}
