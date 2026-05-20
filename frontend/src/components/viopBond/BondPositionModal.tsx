import { useEffect, useState, type FormEvent } from 'react';
import { useLanguage } from '../../i18n/LanguageContext';
import type { BondType, CouponFrequency, ManualBondPosition, ManualBondPositionCreatePayload } from '../../types/bondPosition';

type Props = {
    open: boolean;
    onClose: () => void;
    initial?: ManualBondPosition | null;
    onSubmit: (payload: ManualBondPositionCreatePayload) => Promise<void>;
    tokens: { bgCard: string; border: string };
};

const BOND_TYPES: BondType[] = ['GOVERNMENT_BOND', 'TREASURY_BILL', 'EUROBOND', 'CORPORATE_BOND'];
const CURRENCIES = ['TRY', 'USD', 'EUR'];
const FREQUENCIES: CouponFrequency[] = ['NONE', 'ANNUAL', 'SEMI_ANNUAL', 'QUARTERLY'];

export function BondPositionModal({ open, onClose, initial, onSubmit, tokens }: Props) {
    const { t } = useLanguage();
    const [symbol, setSymbol] = useState('');
    const [displayName, setDisplayName] = useState('');
    const [bondType, setBondType] = useState<BondType>('GOVERNMENT_BOND');
    const [currency, setCurrency] = useState('TRY');
    const [nominalValue, setNominalValue] = useState('');
    const [buyPrice, setBuyPrice] = useState('');
    const [buyDate, setBuyDate] = useState('');
    const [currentPrice, setCurrentPrice] = useState('');
    const [maturityDate, setMaturityDate] = useState('');
    const [couponRate, setCouponRate] = useState('');
    const [couponFrequency, setCouponFrequency] = useState<CouponFrequency>('NONE');
    const [note, setNote] = useState('');
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        if (!open) return;
        setSymbol(initial?.symbol ?? '');
        setDisplayName(initial?.displayName ?? '');
        setBondType(initial?.bondType ?? 'GOVERNMENT_BOND');
        setCurrency(initial?.currency ?? 'TRY');
        setNominalValue(initial?.nominalValue != null ? String(initial.nominalValue) : '');
        setBuyPrice(initial?.buyPrice != null ? String(initial.buyPrice) : '');
        setBuyDate(initial?.buyDate ?? new Date().toISOString().slice(0, 10));
        setCurrentPrice(initial?.currentPrice != null ? String(initial.currentPrice) : '');
        setMaturityDate(initial?.maturityDate ?? '');
        setCouponRate(initial?.couponRate != null ? String(initial.couponRate) : '');
        setCouponFrequency(initial?.couponFrequency ?? 'NONE');
        setNote(initial?.note ?? '');
        setError(null);
    }, [open, initial]);

    if (!open) return null;

    const handleSubmit = async (e: FormEvent) => {
        e.preventDefault();
        setSaving(true);
        setError(null);
        try {
            await onSubmit({
                symbol: symbol.trim().toUpperCase(),
                displayName: displayName.trim() || undefined,
                bondType,
                currency,
                nominalValue: Number(nominalValue),
                buyPrice: Number(buyPrice),
                buyDate,
                currentPrice: currentPrice ? Number(currentPrice) : undefined,
                maturityDate: maturityDate || undefined,
                couponRate: couponRate ? Number(couponRate) : undefined,
                couponFrequency,
                note: note.trim() || undefined,
            });
            onClose();
        } catch (err) {
            setError(err instanceof Error ? err.message : t('viopBond.saveFailed', 'Kayıt başarısız'));
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
                <h3>{initial ? t('viopBond.editBond', 'Tahvil Güncelle') : t('viopBond.addBond', 'Tahvil Ekle')}</h3>
                <form className="vb-form-grid" onSubmit={handleSubmit}>
                    <label>
                        {t('viopBond.colInstrument', 'Enstrüman')}
                        <input value={symbol} onChange={(e) => setSymbol(e.target.value)} required />
                    </label>
                    <label>
                        {t('viopBond.colDisplayName', 'Görünen ad')}
                        <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} />
                    </label>
                    <label>
                        {t('viopBond.colBondType', 'Tür')}
                        <select value={bondType} onChange={(e) => setBondType(e.target.value as BondType)}>
                            {BOND_TYPES.map((b) => (
                                <option key={b} value={b}>
                                    {b}
                                </option>
                            ))}
                        </select>
                    </label>
                    <label>
                        {t('viopBond.colCurrency', 'Para Birimi')}
                        <select value={currency} onChange={(e) => setCurrency(e.target.value)}>
                            {CURRENCIES.map((c) => (
                                <option key={c} value={c}>
                                    {c}
                                </option>
                            ))}
                        </select>
                    </label>
                    <label>
                        {t('viopBond.colNominal', 'Nominal Değer')}
                        <input type="number" min="0.000001" step="any" value={nominalValue} onChange={(e) => setNominalValue(e.target.value)} required />
                    </label>
                    <label>
                        {t('viopBond.colBuyPrice', 'Alış Fiyatı')}
                        <input type="number" min="0.000001" step="any" value={buyPrice} onChange={(e) => setBuyPrice(e.target.value)} required />
                    </label>
                    <label>
                        {t('viopBond.colBuyDate', 'Alış Tarihi')}
                        <input type="date" value={buyDate} onChange={(e) => setBuyDate(e.target.value)} required />
                    </label>
                    <label>
                        {t('viopBond.colCurrentPrice', 'Güncel Fiyat')}
                        <input type="number" min="0.000001" step="any" value={currentPrice} onChange={(e) => setCurrentPrice(e.target.value)} />
                    </label>
                    <label>
                        {t('viopBond.colMaturity', 'Vade Tarihi')}
                        <input type="date" value={maturityDate} onChange={(e) => setMaturityDate(e.target.value)} />
                    </label>
                    <label>
                        {t('viopBond.colCouponRate', 'Kupon Oranı')}
                        <input type="number" min="0" step="any" value={couponRate} onChange={(e) => setCouponRate(e.target.value)} />
                    </label>
                    <label>
                        {t('viopBond.colCouponFreq', 'Kupon Sıklığı')}
                        <select value={couponFrequency} onChange={(e) => setCouponFrequency(e.target.value as CouponFrequency)}>
                            {FREQUENCIES.map((f) => (
                                <option key={f} value={f}>
                                    {f}
                                </option>
                            ))}
                        </select>
                    </label>
                    <label>
                        {t('viopBond.colNote', 'Not')}
                        <textarea rows={2} value={note} onChange={(e) => setNote(e.target.value)} />
                    </label>
                    {error ? <p style={{ color: '#f87171', margin: 0 }}>{error}</p> : null}
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
