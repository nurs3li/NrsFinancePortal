import { useCallback, useEffect, useState } from 'react';
import { financeClient } from '../api/client';
import { useTheme } from '../theme/ThemeContext';
import { useRefetchOnFocus } from '../hooks/useRefetchOnFocus';
import { usePolling } from '../hooks/usePolling';

type FundRequestType = 'DEPOSIT' | 'WITHDRAWAL';
type FundRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';

type BalanceView = {
    accountId: number;
    currentAmount: number;
};

type FundRequestView = {
    id: number;
    userId: number;
    accountId: number;
    type: FundRequestType;
    status: FundRequestStatus;
    amount: number;
    currency: string;
    requestNote: string | null;
    reviewNote: string | null;
    bankAccountIban: string | null;
    receiptFileUrl: string | null;
    referenceNo: string | null;
    sourceBankName: string | null;
    approvedByUserId: number | null;
    approvedAt: string | null;
    rejectedAt: string | null;
    createdAt: string;
};

type CreateFundRequestPayload = {
    type: FundRequestType;
    amount: number;
    currency?: string;
    requestNote?: string;
    bankAccountIban?: string;
    receiptFileUrl?: string;
    referenceNo?: string;
    sourceBankName?: string;
};

function unwrapData<T>(res: any): T {
    return (res?.data?.data ?? res?.data) as T;
}

export function Wallet() {
    const { tokens } = useTheme();

    const [balance, setBalance] = useState<BalanceView | null>(null);
    const [items, setItems] = useState<FundRequestView[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const [submitting, setSubmitting] = useState(false);

    const [type, setType] = useState<FundRequestType>('DEPOSIT');
    const [amount, setAmount] = useState('1000');
    const [currency, setCurrency] = useState('TRY');
    const [bankAccountIban, setBankAccountIban] = useState('');
    const [sourceBankName, setSourceBankName] = useState('');
    const [referenceNo, setReferenceNo] = useState('');
    const [receiptFileUrl, setReceiptFileUrl] = useState('');
    const [requestNote, setRequestNote] = useState('');

    const [successMsg, setSuccessMsg] = useState<string | null>(null);

    const fetchAll = useCallback(() => {
        setLoading(true);
        setError(null);

        Promise.all([
            financeClient.get('/api/balance/me'),
            financeClient.get('/api/fund-requests/me'),
        ])
            .then(([balRes, reqRes]) => {
                setBalance(unwrapData<BalanceView>(balRes));
                setItems(unwrapData<FundRequestView[]>(reqRes) ?? []);
            })
            .catch((err) => {
                const msg =
                    err.response?.data?.errors?.error ??
                    err.response?.data?.message ??
                    err.message ??
                    'Veriler alınamadı';
                setError(msg);
            })
            .finally(() => setLoading(false));
    }, []);

    useEffect(() => {
        fetchAll();
    }, [fetchAll]);

    useRefetchOnFocus(fetchAll);
    usePolling(fetchAll, 30_000);

    const submit = async (e: React.FormEvent) => {
        e.preventDefault();
        setSuccessMsg(null);

        const parsedAmount = Number(amount);
        if (!parsedAmount || parsedAmount <= 0) {
            alert('Tutar 0’dan büyük olmalı.');
            return;
        }

        if (type === 'DEPOSIT' && !bankAccountIban.trim()) {
            alert('Deposit için IBAN zorunlu.');
            return;
        }

        const payload: CreateFundRequestPayload = {
            type,
            amount: parsedAmount,
            currency: currency?.trim() || 'TRY',
            requestNote: requestNote?.trim() || undefined,
            bankAccountIban: bankAccountIban?.trim() || undefined,
            sourceBankName: sourceBankName?.trim() || undefined,
            referenceNo: referenceNo?.trim() || undefined,
            receiptFileUrl: receiptFileUrl?.trim() || undefined,
        };

        try {
            setSubmitting(true);
            await financeClient.post('/api/fund-requests', payload);
            setSuccessMsg('Talebiniz başarıyla oluşturuldu.');
            await fetchAll();
        } catch (err: any) {
            alert(
                err?.response?.data?.errors?.error ??
                    err?.response?.data?.message ??
                    err?.message ??
                    'Talep oluşturulamadı'
            );
        } finally {
            setSubmitting(false);
        }
    };

    const fmtMoney = (v: number) => `₺${Number(v).toLocaleString('tr-TR', { maximumFractionDigits: 2 })}`;

    const statusStyle = (status: FundRequestStatus): React.CSSProperties => {
        if (status === 'APPROVED') return { color: '#22c55e', fontWeight: 600 };
        if (status === 'REJECTED') return { color: '#ef4444', fontWeight: 600 };
        if (status === 'PENDING') return { color: '#f59e0b', fontWeight: 600 };
        return { color: tokens.textMuted, fontWeight: 600 };
    };

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
            <h1 style={{ fontSize: '1.75rem', fontWeight: 700, marginBottom: 6 }}>Cüzdan</h1>
            <p style={{ color: tokens.textMuted, fontSize: '0.875rem', marginBottom: 16 }}>
                Para yatırma/çekme talebi oluştur ve durumunu takip et.
            </p>

            {loading && <p style={{ color: tokens.textMuted }}>Yükleniyor...</p>}
            {error && <p style={{ color: tokens.error }}>Hata: {error}</p>}

            {!loading && !error && (
                <>
                    <div
                        style={{
                            ...cardStyle,
                            marginBottom: 16,
                            display: 'flex',
                            justifyContent: 'space-between',
                            alignItems: 'center',
                        }}
                    >
                        <div>
                            <div style={{ fontSize: '0.8125rem', color: tokens.textMuted }}>Mevcut Bakiye</div>
                            <div style={{ fontSize: '1.5rem', fontWeight: 700 }}>
                                {fmtMoney(balance?.currentAmount ?? 0)}
                            </div>
                        </div>
                        <div style={{ color: tokens.textMuted, fontSize: '0.8125rem' }}>
                            accountId: {balance?.accountId ?? '-'}
                        </div>
                    </div>

                    <div style={{ ...cardStyle, marginBottom: 16 }}>
                        <h2 style={{ marginTop: 0, marginBottom: 12, fontSize: '1rem' }}>Yeni Talep</h2>

                        <form
                            onSubmit={submit}
                            style={{
                                display: 'grid',
                                gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
                                gap: 12,
                            }}
                        >
                            <label style={{ fontSize: '0.875rem' }}>
                                İşlem Tipi
                                <select value={type} onChange={(e) => setType(e.target.value as FundRequestType)} style={inputStyle}>
                                    <option value="DEPOSIT">DEPOSIT</option>
                                    <option value="WITHDRAWAL">WITHDRAWAL</option>
                                </select>
                            </label>

                            <label style={{ fontSize: '0.875rem' }}>
                                Tutar
                                <input
                                    type="number"
                                    step="0.01"
                                    value={amount}
                                    onChange={(e) => setAmount(e.target.value)}
                                    style={inputStyle}
                                    required
                                />
                            </label>

                            <label style={{ fontSize: '0.875rem' }}>
                                Para Birimi
                                <input
                                    value={currency}
                                    onChange={(e) => setCurrency(e.target.value)}
                                    style={inputStyle}
                                    placeholder="TRY"
                                />
                            </label>

                            <label style={{ fontSize: '0.875rem' }}>
                                IBAN
                                <input
                                    value={bankAccountIban}
                                    onChange={(e) => setBankAccountIban(e.target.value)}
                                    style={inputStyle}
                                    placeholder="TR..."
                                />
                            </label>

                            <label style={{ fontSize: '0.875rem' }}>
                                Kaynak Banka
                                <input
                                    value={sourceBankName}
                                    onChange={(e) => setSourceBankName(e.target.value)}
                                    style={inputStyle}
                                    placeholder="Garanti BBVA"
                                />
                            </label>

                            <label style={{ fontSize: '0.875rem' }}>
                                Referans No
                                <input
                                    value={referenceNo}
                                    onChange={(e) => setReferenceNo(e.target.value)}
                                    style={inputStyle}
                                    placeholder="EFT-..."
                                />
                            </label>

                            <label style={{ gridColumn: '1 / -1', fontSize: '0.875rem' }}>
                                Dekont URL
                                <input
                                    value={receiptFileUrl}
                                    onChange={(e) => setReceiptFileUrl(e.target.value)}
                                    style={inputStyle}
                                    placeholder="https://..."
                                />
                            </label>

                            <label style={{ gridColumn: '1 / -1', fontSize: '0.875rem' }}>
                                Not
                                <textarea
                                    value={requestNote}
                                    onChange={(e) => setRequestNote(e.target.value)}
                                    rows={3}
                                    style={{ ...inputStyle, resize: 'vertical' }}
                                    placeholder="Kısa açıklama..."
                                />
                            </label>

                            <div style={{ gridColumn: '1 / -1', display: 'flex', gap: 8, alignItems: 'center' }}>
                                <button
                                    type="submit"
                                    disabled={submitting}
                                    style={{
                                        padding: '8px 14px',
                                        borderRadius: 8,
                                        border: 'none',
                                        background: tokens.accentGradient,
                                        color: '#fff',
                                        fontWeight: 600,
                                        cursor: submitting ? 'default' : 'pointer',
                                        opacity: submitting ? 0.7 : 1,
                                    }}
                                >
                                    {submitting ? 'Gönderiliyor...' : 'Talep Oluştur'}
                                </button>

                                {successMsg && <span style={{ color: '#22c55e', fontSize: '0.875rem' }}>{successMsg}</span>}
                            </div>
                        </form>
                    </div>

                    <div style={cardStyle}>
                        <h2 style={{ marginTop: 0, marginBottom: 12, fontSize: '1rem' }}>Talep Geçmişim</h2>

                        <div style={{ overflowX: 'auto' }}>
                            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.875rem' }}>
                                <thead>
                                    <tr>
                                        <th style={{ textAlign: 'left', padding: 8, borderBottom: `2px solid ${tokens.border}` }}>ID</th>
                                        <th style={{ textAlign: 'left', padding: 8, borderBottom: `2px solid ${tokens.border}` }}>Tip</th>
                                        <th style={{ textAlign: 'left', padding: 8, borderBottom: `2px solid ${tokens.border}` }}>Tutar</th>
                                        <th style={{ textAlign: 'left', padding: 8, borderBottom: `2px solid ${tokens.border}` }}>Durum</th>
                                        <th style={{ textAlign: 'left', padding: 8, borderBottom: `2px solid ${tokens.border}` }}>Tarih</th>
                                        <th style={{ textAlign: 'left', padding: 8, borderBottom: `2px solid ${tokens.border}` }}>Review Note</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {items.length === 0 ? (
                                        <tr>
                                            <td
                                                colSpan={6}
                                                style={{
                                                    padding: 10,
                                                    borderBottom: `1px solid ${tokens.tableBorder}`,
                                                    color: tokens.textMuted,
                                                    textAlign: 'center',
                                                }}
                                            >
                                                Henüz talep yok.
                                            </td>
                                        </tr>
                                    ) : (
                                        items.map((r) => (
                                            <tr key={r.id}>
                                                <td style={{ padding: 8, borderBottom: `1px solid ${tokens.tableBorder}` }}>#{r.id}</td>
                                                <td style={{ padding: 8, borderBottom: `1px solid ${tokens.tableBorder}` }}>{r.type}</td>
                                                <td style={{ padding: 8, borderBottom: `1px solid ${tokens.tableBorder}` }}>
                                                    {Number(r.amount).toLocaleString('tr-TR', { maximumFractionDigits: 2 })} {r.currency}
                                                </td>
                                                <td style={{ padding: 8, borderBottom: `1px solid ${tokens.tableBorder}` }}>
                                                    <span style={statusStyle(r.status)}>{r.status}</span>
                                                </td>
                                                <td style={{ padding: 8, borderBottom: `1px solid ${tokens.tableBorder}` }}>
                                                    {new Date(r.createdAt).toLocaleString('tr-TR')}
                                                </td>
                                                <td style={{ padding: 8, borderBottom: `1px solid ${tokens.tableBorder}` }}>
                                                    {r.reviewNote || '-'}
                                                </td>
                                            </tr>
                                        ))
                                    )}
                                </tbody>
                            </table>
                        </div>
                    </div>
                </>
            )}
        </div>
    );
}
