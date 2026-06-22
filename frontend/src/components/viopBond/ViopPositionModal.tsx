import { useEffect, useState, type FormEvent } from 'react';
import { readApiError } from '../../api/envelope';
import { useLanguage } from '../../i18n/LanguageContext';
import type { ManualViopPosition, ManualViopPositionCreatePayload, ViopCategory, ViopDirection } from '../../types/viopPosition';

type Props = {
    open: boolean;
    onClose: () => void;
    initial?: ManualViopPosition | null;
    onSubmit: (payload: ManualViopPositionCreatePayload) => Promise<void>;
    tokens: { bgCard: string; border: string };
};

const CATEGORIES: ViopCategory[] = ['FX', 'INDEX', 'COMMODITY', 'EQUITY'];
const DIRECTIONS: ViopDirection[] = ['LONG', 'SHORT'];

export function ViopPositionModal({ open, onClose, initial, onSubmit, tokens }: Props) {
    const { t } = useLanguage();
    const [symbol, setSymbol] = useState('');
    const [displayName, setDisplayName] = useState('');
    const [viopCategory, setViopCategory] = useState<ViopCategory>('FX');
    const [underlyingSymbol, setUnderlyingSymbol] = useState('');
    const [direction, setDirection] = useState<ViopDirection>('LONG');
    const [contractCount, setContractCount] = useState('1');
    const [entryPrice, setEntryPrice] = useState('');
    const [entryDate, setEntryDate] = useState('');
    const [currentPrice, setCurrentPrice] = useState('');
    const [contractMultiplier, setContractMultiplier] = useState('1');
    const [initialMargin, setInitialMargin] = useState('');
    const [expiryDate, setExpiryDate] = useState('');
    const [note, setNote] = useState('');
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        if (!open) return;
        setSymbol(initial?.symbol ?? '');
        setDisplayName(initial?.displayName ?? '');
        setViopCategory(initial?.viopCategory ?? 'FX');
        setUnderlyingSymbol(initial?.underlyingSymbol ?? '');
        setDirection(initial?.direction ?? 'LONG');
        setContractCount(String(initial?.contractCount ?? 1));
        setEntryPrice(initial?.entryPrice != null ? String(initial.entryPrice) : '');
        setEntryDate(initial?.entryDate ?? new Date().toISOString().slice(0, 10));
        setCurrentPrice(initial?.currentPrice != null ? String(initial.currentPrice) : '');
        setContractMultiplier(String(initial?.contractMultiplier ?? 1));
        setInitialMargin(initial?.initialMargin != null ? String(initial.initialMargin) : '');
        setExpiryDate(initial?.expiryDate ?? '');
        setNote(initial?.note ?? '');
        setError(null);
    }, [open, initial]);

    if (!open) return null;

    const handleSubmit = async (e: FormEvent) => {
        e.preventDefault();
        setSaving(true);
        setError(null);
        try {
            const payload: ManualViopPositionCreatePayload = {
                symbol: symbol.trim().toUpperCase(),
                displayName: displayName.trim() || undefined,
                viopCategory,
                underlyingSymbol: underlyingSymbol.trim() || undefined,
                direction,
                contractCount: Number(contractCount),
                entryPrice: Number(entryPrice),
                entryDate,
                currentPrice: currentPrice ? Number(currentPrice) : undefined,
                contractMultiplier: Number(contractMultiplier),
                initialMargin: initialMargin ? Number(initialMargin) : undefined,
                expiryDate: expiryDate || undefined,
                note: note.trim() || undefined,
            };
            await onSubmit(payload);
            onClose();
        } catch (err) {
            setError(readApiError(err).message || t('viopBond.saveFailed', 'Kayıt başarısız'));
        } finally {
            setSaving(false);
        }
    };

    return (
        <div className="vb-modal-backdrop" onClick={onClose} role="presentation">
            <div
                className="vb-modal pf-card-premium"
                style={{ background: tokens.bgCard, borderColor: tokens.border }}
                onClick={(e) => e.stopPropagation()}
                role="dialog"
            >
                <h3>{initial ? t('viopBond.editViop', 'VİOP Pozisyonu Güncelle') : t('viopBond.addViop', 'VİOP Pozisyonu Ekle')}</h3>
                <form className="vb-form-grid" onSubmit={handleSubmit}>
                    <label>
                        {t('viopBond.colContract', 'Kontrat')}
                        <input value={symbol} onChange={(e) => setSymbol(e.target.value)} required />
                    </label>
                    <label>
                        {t('viopBond.colDisplayName', 'Görünen ad')}
                        <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} />
                    </label>
                    <label>
                        {t('viopBond.colViopType', 'VİOP Türü')}
                        <select value={viopCategory} onChange={(e) => setViopCategory(e.target.value as ViopCategory)}>
                            {CATEGORIES.map((c) => (
                                <option key={c} value={c}>
                                    {c}
                                </option>
                            ))}
                        </select>
                    </label>
                    <label>
                        {t('viopBond.colUnderlying', 'Dayanak Varlık')}
                        <input value={underlyingSymbol} onChange={(e) => setUnderlyingSymbol(e.target.value)} />
                    </label>
                    <label>
                        {t('viopBond.colDirection', 'Yön')}
                        <select value={direction} onChange={(e) => setDirection(e.target.value as ViopDirection)}>
                            {DIRECTIONS.map((d) => (
                                <option key={d} value={d}>
                                    {d}
                                </option>
                            ))}
                        </select>
                    </label>
                    <label>
                        {t('viopBond.colCount', 'Kontrat Adedi')}
                        <input type="number" min="0.000001" step="any" value={contractCount} onChange={(e) => setContractCount(e.target.value)} required />
                    </label>
                    <label>
                        {t('viopBond.colEntryPrice', 'Giriş Fiyatı')}
                        <input type="number" min="0.000001" step="any" value={entryPrice} onChange={(e) => setEntryPrice(e.target.value)} required />
                    </label>
                    <label>
                        {t('viopBond.colEntryDate', 'Giriş Tarihi')}
                        <input type="date" value={entryDate} onChange={(e) => setEntryDate(e.target.value)} required />
                    </label>
                    <label>
                        {t('viopBond.colCurrentPrice', 'Güncel Fiyat')}
                        <input type="number" min="0.000001" step="any" value={currentPrice} onChange={(e) => setCurrentPrice(e.target.value)} />
                    </label>
                    <label>
                        {t('viopBond.colMultiplier', 'Kontrat Çarpanı')}
                        <input type="number" min="0.000001" step="any" value={contractMultiplier} onChange={(e) => setContractMultiplier(e.target.value)} required />
                    </label>
                    <label>
                        {t('viopBond.colMargin', 'Başlangıç Teminatı')}
                        <input type="number" min="0" step="any" value={initialMargin} onChange={(e) => setInitialMargin(e.target.value)} />
                    </label>
                    <label>
                        {t('viopBond.colExpiry', 'Vade Tarihi')}
                        <input type="date" value={expiryDate} onChange={(e) => setExpiryDate(e.target.value)} />
                    </label>
                    <label>
                        {t('viopBond.colNote', 'Not')}
                        <textarea rows={2} value={note} onChange={(e) => setNote(e.target.value)} />
                    </label>
                    {error ? <p style={{ color: '#991B1B', margin: 0 }}>{error}</p> : null}
                    <div className="vb-toolbar">
                        <button type="button" className="vb-btn-sm" onClick={onClose}>
                            {t('viopBond.cancel', 'İptal')}
                        </button>
                        <button type="submit" className="vb-btn-sm" disabled={saving}>
                            {saving ? '…' : t('viopBond.save', 'Kaydet')}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
}
