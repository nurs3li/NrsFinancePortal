import { useEffect, useRef } from 'react';
import { createPortal } from 'react-dom';
import { X } from 'lucide-react';
import type { MacroEducationTerm } from '../../../content/macroEducationTerms';
import type { MacroTheme } from '../MacroTheme';

type Props = {
    open: boolean;
    onClose: () => void;
    term: MacroEducationTerm;
    tokens: MacroTheme;
};

export function InfoModal({ open, onClose, term, tokens }: Props) {
    const dialogRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        if (!open) return;
        const onKey = (e: KeyboardEvent) => {
            if (e.key === 'Escape') onClose();
        };
        document.addEventListener('keydown', onKey);
        document.body.style.overflow = 'hidden';
        return () => {
            document.removeEventListener('keydown', onKey);
            document.body.style.overflow = '';
        };
    }, [open, onClose]);

    if (!open) return null;

    return createPortal(
        <div
            className="macro-info-modal__backdrop"
            role="presentation"
            onClick={(e) => {
                if (e.target === e.currentTarget) onClose();
            }}
        >
            <div
                ref={dialogRef}
                className="macro-info-modal__dialog"
                role="dialog"
                aria-modal="true"
                aria-labelledby="macro-info-title"
                style={{ borderColor: tokens.border, background: tokens.bgCard, color: tokens.text }}
            >
                <div className="macro-info-modal__header">
                    <h2 id="macro-info-title" className="macro-info-modal__title">
                        {term.title}
                    </h2>
                    <button type="button" className="macro-info-modal__close" onClick={onClose} aria-label="Kapat">
                        <X size={18} />
                    </button>
                </div>
                <p className="macro-info-modal__short" style={{ color: tokens.textMuted }}>
                    {term.short}
                </p>
                <p className="macro-info-modal__detail">{term.detail}</p>
                {term.formula ? (
                    <div className="macro-info-modal__block">
                        <div className="macro-info-modal__label" style={{ color: tokens.textMuted }}>
                            Formül
                        </div>
                        <code className="macro-info-modal__formula">{term.formula}</code>
                    </div>
                ) : null}
                {term.example ? (
                    <div className="macro-info-modal__block">
                        <div className="macro-info-modal__label" style={{ color: tokens.textMuted }}>
                            Örnek
                        </div>
                        <p className="macro-info-modal__detail">{term.example}</p>
                    </div>
                ) : null}
                {term.whyItMatters ? (
                    <div className="macro-info-modal__block macro-info-modal__block--highlight">
                        <div className="macro-info-modal__label" style={{ color: tokens.textMuted }}>
                            Neden önemli?
                        </div>
                        <p className="macro-info-modal__detail">{term.whyItMatters}</p>
                    </div>
                ) : null}
            </div>
        </div>,
        document.body,
    );
}
