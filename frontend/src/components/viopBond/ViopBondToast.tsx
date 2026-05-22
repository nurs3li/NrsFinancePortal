import { useEffect } from 'react';

type Props = {
    message: string;
    variant: 'success' | 'error';
    onDismiss: () => void;
};

export function ViopBondToast({ message, variant, onDismiss }: Props) {
    useEffect(() => {
        const id = window.setTimeout(onDismiss, 4500);
        return () => window.clearTimeout(id);
    }, [message, onDismiss]);

    return (
        <div className={`vb-toast vb-toast--${variant}`} role="status">
            <span>{message}</span>
            <button type="button" className="vb-toast-close" onClick={onDismiss} aria-label="Kapat">
                ×
            </button>
        </div>
    );
}
