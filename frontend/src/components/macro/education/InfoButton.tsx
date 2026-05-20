import { CircleHelp } from 'lucide-react';
import type { MacroTermId } from '../../../content/macroEducationTerms';
import { useInfoTerm } from './InfoTermProvider';

type Props = {
    termId: MacroTermId;
    ariaLabel: string;
    className?: string;
};

export function InfoButton({ termId, ariaLabel, className }: Props) {
    const { openTerm } = useInfoTerm();
    return (
        <button
            type="button"
            className={className ?? 'macro-info-btn'}
            onClick={() => openTerm(termId)}
            aria-label={ariaLabel}
        >
            <CircleHelp size={14} strokeWidth={2} aria-hidden />
        </button>
    );
}
