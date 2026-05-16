import { useState, useRef, useEffect } from 'react';
import { HelpCircle } from 'lucide-react';

type SimTermInfoProps = {
    termKey: string;
    title: string;
    body: string;
    mutedColor: string;
};

export function SimTermInfo({ termKey, title, body, mutedColor }: SimTermInfoProps) {
    const [open, setOpen] = useState(false);
    const ref = useRef<HTMLSpanElement>(null);

    useEffect(() => {
        if (!open) return;
        const onDoc = (e: MouseEvent) => {
            if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
        };
        document.addEventListener('mousedown', onDoc);
        return () => document.removeEventListener('mousedown', onDoc);
    }, [open]);

    return (
        <span className="sim-term-info" ref={ref}>
            <button
                type="button"
                className="sim-term-info__btn"
                aria-label={title}
                aria-expanded={open}
                aria-describedby={open ? `sim-term-${termKey}` : undefined}
                onClick={() => setOpen((o) => !o)}
            >
                <HelpCircle size={14} aria-hidden />
            </button>
            {open ? (
                <span id={`sim-term-${termKey}`} className="sim-term-info__popover" role="tooltip">
                    <strong className="sim-term-info__title">{title}</strong>
                    <span className="sim-term-info__body" style={{ color: mutedColor }}>
                        {body}
                    </span>
                </span>
            ) : null}
        </span>
    );
}
