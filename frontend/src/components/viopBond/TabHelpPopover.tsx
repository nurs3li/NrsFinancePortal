import { useEffect, useId, useLayoutEffect, useRef, useState } from 'react';
import { Info } from 'lucide-react';

type Props = {
    title: string;
    body: string;
    tokens: { border: string; bgCard: string; textMuted: string };
};

export function TabHelpPopover({ title, body, tokens }: Props) {
    const [open, setOpen] = useState(false);
    const [flipEnd, setFlipEnd] = useState(false);
    const wrapRef = useRef<HTMLDivElement>(null);
    const panelRef = useRef<HTMLDivElement>(null);
    const panelId = useId();

    useLayoutEffect(() => {
        if (!open || !wrapRef.current || !panelRef.current) return;
        const wrap = wrapRef.current.getBoundingClientRect();
        const panelW = panelRef.current.offsetWidth;
        const margin = 12;
        const overflowsRight = wrap.left + panelW > window.innerWidth - margin;
        const overflowsLeft = wrap.right - panelW < margin;
        setFlipEnd(overflowsRight && !overflowsLeft);
    }, [open, title, body]);

    useEffect(() => {
        if (!open) return;
        const onDoc = (e: MouseEvent) => {
            if (wrapRef.current && !wrapRef.current.contains(e.target as Node)) {
                setOpen(false);
            }
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

    const cardStyle = { borderColor: tokens.border, background: tokens.bgCard };

    return (
        <div
            ref={wrapRef}
            className={`vb-tab-help-wrap${open ? ' is-open' : ''}${flipEnd ? ' vb-tab-help-wrap--flip-end' : ''}`}
            onMouseEnter={() => setOpen(true)}
            onMouseLeave={() => setOpen(false)}
        >
            <button
                type="button"
                className="vb-tab-help-trigger"
                aria-expanded={open}
                aria-controls={panelId}
                aria-label={title}
                onClick={(e) => {
                    e.stopPropagation();
                    setOpen((v) => !v);
                }}
            >
                <Info size={15} aria-hidden />
            </button>
            <div
                ref={panelRef}
                id={panelId}
                className="vb-tab-help-popover"
                style={cardStyle}
                role="tooltip"
                onClick={(e) => e.stopPropagation()}
            >
                <strong className="vb-tab-help-popover__title">{title}</strong>
                <p className="vb-tab-help-popover__body" style={{ color: tokens.textMuted }}>
                    {body}
                </p>
            </div>
        </div>
    );
}
