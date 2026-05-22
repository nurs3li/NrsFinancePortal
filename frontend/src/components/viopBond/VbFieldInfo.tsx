import { useEffect, useId, useRef, useState } from 'react';
import { Info } from 'lucide-react';

type Props = {
    text: string;
    label?: string;
};

export function VbFieldInfo({ text, label = 'Bilgi' }: Props) {
    const [open, setOpen] = useState(false);
    const wrapRef = useRef<HTMLSpanElement>(null);
    const panelId = useId();

    useEffect(() => {
        if (!open) return;
        const onDoc = (e: MouseEvent) => {
            if (wrapRef.current && !wrapRef.current.contains(e.target as Node)) setOpen(false);
        };
        const onKey = (e: KeyboardEvent) => {
            if (e.key === 'Escape') setOpen(false);
        };
        document.addEventListener('mousedown', onDoc);
        document.addEventListener('keydown', onKey);
        return () => {
            document.removeEventListener('mousedown', onDoc);
            document.removeEventListener('keydown', onKey);
        };
    }, [open]);

    return (
        <span ref={wrapRef} className={`vb-field-info${open ? ' is-open' : ''}`}>
            <button
                type="button"
                className="vb-field-info-btn"
                aria-label={label}
                aria-expanded={open}
                aria-controls={panelId}
                onClick={() => setOpen((v) => !v)}
            >
                <Info size={14} />
            </button>
            {open ? (
                <span id={panelId} className="vb-field-info-panel" role="tooltip">
                    {text}
                </span>
            ) : null}
        </span>
    );
}
