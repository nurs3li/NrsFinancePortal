import { useEffect, useMemo, useState } from 'react';
import { financeClient } from '../api/client';
import { useTheme } from '../theme/ThemeContext';

type AssetType = 'CRYPTO' | 'FX' | 'FUND' | 'METAL' | 'STOCK';

type SimulationResponse = {
    type: string;
    symbol: string;
    buyDate: string | null;
    inputAmountTry: number;
    historicalPriceTry: number;
    currentPriceTry: number;
    unitsBought: number;
    currentValueTry: number;
    pnlTry: number;
    pnlPct: number;
    message: string;
};

function unwrapData<T>(res: any): T {
    return (res?.data?.data ?? res?.data) as T;
}

type MarketOverview = {
    doviz?: Record<string, { buyPrice?: number; sellPrice?: number; source?: string }>;
    metals?: Record<string, { buyPrice?: number; source?: string }>;
    crypto?: Record<string, { buyPrice?: number; source?: string }>;
    funds?: Record<string, { buyPrice?: number; source?: string }>;
    stocks?: Record<string, { buyPrice?: number; source?: string }>;
    timestamp?: string;
};

function getOverviewKey(type: AssetType): keyof MarketOverview {
    switch (type) {
        case 'CRYPTO': return 'crypto';
        case 'FX': return 'doviz';
        case 'METAL': return 'metals';
        case 'FUND': return 'funds';
        case 'STOCK': return 'stocks';
        default: return 'crypto';
    }
}

export function Simulation() {
    const { tokens } = useTheme();

    const [type, setType] = useState<AssetType>('CRYPTO');
    const [symbol, setSymbol] = useState('BTCUSDT');
    const [amount, setAmount] = useState('5000'); // toplam yatırım (TRY)
    const [buyPrice, setBuyPrice] = useState('100000'); // kullanıcı girişi
    const [buyDate, setBuyDate] = useState(new Date().toISOString().slice(0, 10)); // bilgi amaçlı
    const [overview, setOverview] = useState<MarketOverview | null>(null);
    const [overviewLoading, setOverviewLoading] = useState(true);

    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [result, setResult] = useState<SimulationResponse | null>(null);

    useEffect(() => {
        setOverviewLoading(true);
        financeClient
            .get('/api/market/overview')
            .then((res) => setOverview(unwrapData<MarketOverview>(res)))
            .catch(() => setOverview(null))
            .finally(() => setOverviewLoading(false));
    }, []);

    const symbolOptions = useMemo(() => {
        if (!overview) return [] as string[];
        const key = getOverviewKey(type);
        const map = overview[key];
        if (!map || typeof map !== 'object') return [] as string[];
        return Object.keys(map).filter((k) => map[k] != null);
    }, [overview, type]);

    useEffect(() => {
        if (symbolOptions.length > 0 && !symbolOptions.includes(symbol)) {
            setSymbol(symbolOptions[0]);
        } else if (symbolOptions.length === 0) {
            setSymbol('');
        }
    }, [symbolOptions, symbol]);

    const runSimulation = async (e: React.FormEvent) => {
        e.preventDefault();
        setError(null);
        setResult(null);

        const parsedAmount = Number(amount);
        const parsedBuyPrice = Number(buyPrice);
        if (!parsedAmount || parsedAmount <= 0) {
            setError('Tutar 0’dan büyük olmalı.');
            return;
        }
        if (!parsedBuyPrice || parsedBuyPrice <= 0) {
            setError('Alış fiyatı 0’dan büyük olmalı.');
            return;
        }
        if (!symbol.trim()) {
            setError('Sembol seçmelisin.');
            return;
        }

        const key = getOverviewKey(type);
        const currentPrice =
            overview?.[key] && typeof overview[key] === 'object'
                ? Number((overview[key] as Record<string, { buyPrice?: number }>)[symbol]?.buyPrice ?? 0)
                : 0;
        if (!currentPrice || currentPrice <= 0) {
            setError('Bu sembol için güncel fiyat bulunamadı.');
            return;
        }

        try {
            setLoading(true);
            // Geçmiş veri yoksa manuel alış fiyatına göre frontend hesap
            const units = parsedAmount / parsedBuyPrice;
            const currentValue = units * currentPrice;
            const pnl = currentValue - parsedAmount;
            const pnlPct = parsedAmount === 0 ? 0 : pnl / parsedAmount;

            const computed: SimulationResponse = {
                type,
                symbol: symbol.trim().toUpperCase(),
                buyDate: buyDate || null,
                inputAmountTry: parsedAmount,
                historicalPriceTry: parsedBuyPrice,
                currentPriceTry: currentPrice,
                unitsBought: units,
                currentValueTry: currentValue,
                pnlTry: pnl,
                pnlPct,
                message: `${buyDate} tarihinde birim alış fiyatını ₺${parsedBuyPrice.toLocaleString('tr-TR')} kabul ederek hesaplandı.`,
            };
            setResult(computed);
        } catch (err: any) {
            const msg =
                err?.response?.data?.errors?.error ??
                err?.response?.data?.message ??
                err?.message ??
                'Simülasyon hatası';
            setError(msg);
        } finally {
            setLoading(false);
        }
    };

    const fmtMoney = (v: number) => `₺${Number(v).toLocaleString('tr-TR', { maximumFractionDigits: 2 })}`;
    const pnlPositive = (result?.pnlTry ?? 0) >= 0;

    const pageStyle: React.CSSProperties = {
        padding: 24,
        background: tokens.bg,
        color: tokens.text,
        minHeight: '100%',
    };

    const cardStyle: React.CSSProperties = {
        background: tokens.bgCard,
        border: `1px solid ${tokens.border}`,
        borderRadius: 12,
        padding: 16,
    };

    const inputStyle: React.CSSProperties = {
        width: '100%',
        padding: 8,
        borderRadius: 8,
        border: `1px solid ${tokens.border}`,
        background: tokens.inputBg,
        color: tokens.text,
        fontSize: '0.9375rem',
    };

    return (
        <div style={pageStyle}>
            <h1 style={{ fontSize: '1.75rem', fontWeight: 700, marginBottom: 6 }}>What-If Simülasyon</h1>
            <p style={{ color: tokens.textMuted, fontSize: '0.875rem', marginBottom: 16 }}>
                Geçmiş veri zorunlu değil. Alış fiyatını sen giriyorsun, sistem güncel fiyatla kar/zarar hesaplıyor.
            </p>

            <div style={{ ...cardStyle, marginBottom: 16 }}>
                <form
                    onSubmit={runSimulation}
                    style={{
                        display: 'grid',
                        gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
                        gap: 12,
                    }}
                >
                    <label style={{ fontSize: '0.875rem' }}>
                        Varlık Türü
                        <select value={type} onChange={(e) => setType(e.target.value as AssetType)} style={inputStyle}>
                            <option value="CRYPTO">CRYPTO</option>
                            <option value="FX">FX</option>
                            <option value="METAL">METAL</option>
                            <option value="FUND">FUND</option>
                            <option value="STOCK">STOCK</option>
                        </select>
                    </label>

                    <label style={{ fontSize: '0.875rem' }}>
                        Sembol
                        {overviewLoading ? (
                            <div style={{ ...inputStyle, color: tokens.textMuted }}>Yükleniyor...</div>
                        ) : symbolOptions.length > 0 ? (
                            <select value={symbol} onChange={(e) => setSymbol(e.target.value)} style={inputStyle}>
                                {symbolOptions.map((s) => (
                                    <option key={s} value={s}>{s}</option>
                                ))}
                            </select>
                        ) : (
                            <input value={symbol} onChange={(e) => setSymbol(e.target.value)} style={inputStyle} />
                        )}
                    </label>

                    <label style={{ fontSize: '0.875rem' }}>
                        Yatırılan Tutar (TRY)
                        <input type="number" step="0.01" value={amount} onChange={(e) => setAmount(e.target.value)} style={inputStyle} />
                    </label>

                    <label style={{ fontSize: '0.875rem' }}>
                        Alış Fiyatı (TRY / birim)
                        <input type="number" step="0.00000001" value={buyPrice} onChange={(e) => setBuyPrice(e.target.value)} style={inputStyle} />
                    </label>

                    <label style={{ fontSize: '0.875rem' }}>
                        Alım Tarihi
                        <input type="date" value={buyDate} onChange={(e) => setBuyDate(e.target.value)} style={inputStyle} />
                    </label>

                    <div style={{ gridColumn: '1 / -1' }}>
                        <button
                            type="submit"
                            disabled={loading}
                            style={{
                                padding: '8px 14px',
                                borderRadius: 8,
                                border: 'none',
                                background: tokens.accentGradient,
                                color: '#fff',
                                fontWeight: 600,
                                cursor: loading ? 'default' : 'pointer',
                                opacity: loading ? 0.7 : 1,
                            }}
                        >
                            {loading ? 'Hesaplanıyor...' : 'Simüle Et'}
                        </button>
                    </div>
                </form>
            </div>

            {error && (
                <div style={{ ...cardStyle, borderColor: tokens.error, color: tokens.error }}>
                    Hata: {error}
                </div>
            )}

            {result && (
                <div style={cardStyle}>
                    <h2 style={{ marginTop: 0, marginBottom: 12, fontSize: '1rem' }}>Sonuç</h2>

                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 12 }}>
                        <div>
                            <div style={{ color: tokens.textMuted, fontSize: '0.8125rem' }}>Varlık</div>
                            <div>{result.type} / {result.symbol}</div>
                        </div>
                        <div>
                            <div style={{ color: tokens.textMuted, fontSize: '0.8125rem' }}>Alım Tarihi</div>
                            <div>{result.buyDate ? new Date(result.buyDate).toLocaleDateString('tr-TR') : '-'}</div>
                        </div>
                        <div>
                            <div style={{ color: tokens.textMuted, fontSize: '0.8125rem' }}>Yatırılan</div>
                            <div>{fmtMoney(result.inputAmountTry)}</div>
                        </div>
                        <div>
                            <div style={{ color: tokens.textMuted, fontSize: '0.8125rem' }}>Bugünkü Değer</div>
                            <div>{fmtMoney(result.currentValueTry)}</div>
                        </div>
                        <div>
                            <div style={{ color: tokens.textMuted, fontSize: '0.8125rem' }}>PNL</div>
                            <div style={{ color: pnlPositive ? '#22c55e' : '#ef4444', fontWeight: 700 }}>
                                {fmtMoney(result.pnlTry)} ({Number(result.pnlPct).toLocaleString('tr-TR', { maximumFractionDigits: 2 })}%)
                            </div>
                        </div>
                        <div>
                            <div style={{ color: tokens.textMuted, fontSize: '0.8125rem' }}>Birim</div>
                            <div>{Number(result.unitsBought).toLocaleString('tr-TR', { maximumFractionDigits: 8 })}</div>
                        </div>
                    </div>

                    {result.message && (
                        <p style={{ marginTop: 12, color: tokens.textMuted, fontSize: '0.875rem' }}>
                            {result.message}
                        </p>
                    )}
                </div>
            )}
        </div>
    );
}
