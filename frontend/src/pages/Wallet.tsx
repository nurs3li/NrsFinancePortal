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

type WalletSummary = {
    accountId: number;
    currentBalance: number;
    availableBalance: number;
    pendingDeposit: number;
    pendingWithdrawal: number;
};

type DepositInstructions = {
    iban: string;
    recipientName: string;
    bankName: string;
    userReferenceCode: string;
    systemIbanId: string;
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
    receiptFileId: string | null;
    referenceNo: string | null;
    sourceBankName: string | null;
    depositIban: string | null;
    systemIbanId: string | null;
    destinationIban: string | null;
    destinationAccountHolder: string | null;
    destinationBankName: string | null;
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
    receiptFileUrl?: string;
    receiptFileId?: string;
    referenceNo?: string;
    externalReferenceNo?: string;
    sourceBankName?: string;
    depositIban?: string;
    systemIbanId?: string;
    destinationIban?: string;
    destinationAccountHolder?: string;
    destinationBankName?: string;
};

function unwrapData<T>(res: any): T {
    return (res?.data?.data ?? res?.data) as T;
}

export function Wallet() {
    const { tokens } = useTheme();

    const [balance, setBalance] = useState<BalanceView | null>(null);
    const [summary, setSummary] = useState<WalletSummary | null>(null);
    const [depositInstructions, setDepositInstructions] = useState<DepositInstructions | null>(null);
    const [items, setItems] = useState<FundRequestView[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const [submitting, setSubmitting] = useState(false);

    const [type, setType] = useState<FundRequestType>('DEPOSIT');
    const [amount, setAmount] = useState('1000');
    const [currency, setCurrency] = useState('TRY');
    const [destinationIban, setDestinationIban] = useState('');
    const [destinationAccountHolder, setDestinationAccountHolder] = useState('');
    const [destinationBankName, setDestinationBankName] = useState('');
    const [sourceBankName, setSourceBankName] = useState('');
    const [externalReferenceNo, setExternalReferenceNo] = useState('');
    const [receiptFileUrl, setReceiptFileUrl] = useState<string | null>(null);
    const [receiptFileId, setReceiptFileId] = useState<string | null>(null);
    const [receiptFileName, setReceiptFileName] = useState<string | null>(null);
    const [uploadingReceipt, setUploadingReceipt] = useState(false);
    const [requestNote, setRequestNote] = useState('');

    const [successMsg, setSuccessMsg] = useState<string | null>(null);

    const fetchAll = useCallback(() => {
        setLoading(true);
        setError(null);

        Promise.all([
            financeClient.get('/api/balance/me'),
            financeClient.get('/api/wallet/summary'),
            financeClient.get('/api/wallet/deposit-instructions'),
            financeClient.get('/api/fund-requests/me'),
        ])
            .then(([balRes, summaryRes, instructionsRes, reqRes]) => {
                setBalance(unwrapData<BalanceView>(balRes));
                setSummary(unwrapData<WalletSummary>(summaryRes));
                setDepositInstructions(unwrapData<DepositInstructions>(instructionsRes));
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

    useEffect(() => {
        if (type === 'DEPOSIT') {
            setDestinationIban('');
            setDestinationAccountHolder('');
            setDestinationBankName('');
            setAmount('1000');
            return;
        }
        setReceiptFileId(null);
        setReceiptFileUrl(null);
        setReceiptFileName(null);
        setSourceBankName('');
        setExternalReferenceNo('');
        setAmount('1000');
    }, [type]);

    const uploadReceipt = async (file: File) => {
        const ext = file.name.toLowerCase().split('.').pop() ?? '';
        if (!['pdf', 'jpg', 'jpeg', 'png'].includes(ext)) {
            alert('Dekont dosyası PDF/JPG/PNG olmalı.');
            return;
        }
        const fd = new FormData();
        fd.append('file', file);
        try {
            setUploadingReceipt(true);
            const res = await financeClient.post('/api/fund-requests/receipts', fd, {
                headers: { 'Content-Type': 'multipart/form-data' },
            });
            const data = unwrapData<{ receiptFileId: string; receiptFileUrl: string; originalFileName: string }>(res);
            setReceiptFileId(data.receiptFileId);
            setReceiptFileUrl(data.receiptFileUrl);
            setReceiptFileName(data.originalFileName);
        } catch (err: any) {
            alert(
                err?.response?.data?.errors?.error ??
                    err?.response?.data?.message ??
                    err?.message ??
                    'Dekont yüklenemedi'
            );
        } finally {
            setUploadingReceipt(false);
        }
    };

    const submit = async (e: React.FormEvent) => {
        e.preventDefault();
        setSuccessMsg(null);

        const parsedAmount = Number(amount);
        if (!parsedAmount || parsedAmount <= 0) {
            alert('Tutar sıfırdan büyük olmalı.');
            return;
        }

        if (type === 'WITHDRAWAL' && parsedAmount > Number(summary?.availableBalance ?? 0)) {
            alert('Çekim tutarı kullanılabilir bakiyeden fazla olamaz.');
            return;
        }

        if (type === 'DEPOSIT' && !receiptFileId && !receiptFileUrl) {
            alert('Yatırım için dekont yüklemek zorunludur.');
            return;
        }

        if (type === 'WITHDRAWAL' && !destinationIban.trim()) {
            alert('Çekim için alıcı IBAN zorunludur.');
            return;
        }
        if (type === 'WITHDRAWAL' && !destinationAccountHolder.trim()) {
            alert('Çekim için alıcı ad soyad zorunludur.');
            return;
        }
        if (type === 'WITHDRAWAL' && !destinationBankName.trim()) {
            alert('Çekim için banka adı zorunludur.');
            return;
        }

        const payload: CreateFundRequestPayload = {
            type,
            amount: parsedAmount,
            currency: currency?.trim() || 'TRY',
            requestNote: requestNote?.trim() || undefined,
            sourceBankName: type === 'DEPOSIT' ? sourceBankName?.trim() || undefined : undefined,
            externalReferenceNo: type === 'DEPOSIT' ? externalReferenceNo?.trim() || undefined : undefined,
            receiptFileUrl: type === 'DEPOSIT' ? receiptFileUrl ?? undefined : undefined,
            receiptFileId: type === 'DEPOSIT' ? receiptFileId ?? undefined : undefined,
            depositIban: type === 'DEPOSIT' ? depositInstructions?.iban : undefined,
            systemIbanId: type === 'DEPOSIT' ? depositInstructions?.systemIbanId : undefined,
            destinationIban: type === 'WITHDRAWAL' ? destinationIban?.trim() || undefined : undefined,
            destinationAccountHolder: type === 'WITHDRAWAL' ? destinationAccountHolder?.trim() || undefined : undefined,
            destinationBankName: type === 'WITHDRAWAL' ? destinationBankName?.trim() || undefined : undefined,
        };

        try {
            setSubmitting(true);
            await financeClient.post('/api/fund-requests', payload);
            setSuccessMsg('Talebiniz başarıyla oluşturuldu.');
            setRequestNote('');
            if (type === 'DEPOSIT') {
                setReceiptFileId(null);
                setReceiptFileUrl(null);
                setReceiptFileName(null);
                setExternalReferenceNo('');
                setSourceBankName('');
            } else {
                setDestinationIban('');
                setDestinationAccountHolder('');
                setDestinationBankName('');
            }
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
        if (status === 'APPROVED') return { color: '#166534', background: '#dcfce7', borderRadius: 999, padding: '2px 8px', fontWeight: 700 };
        if (status === 'REJECTED') return { color: '#991b1b', background: '#fee2e2', borderRadius: 999, padding: '2px 8px', fontWeight: 700 };
        if (status === 'PENDING') return { color: '#92400e', background: '#fef3c7', borderRadius: 999, padding: '2px 8px', fontWeight: 700 };
        return { color: tokens.textMuted, background: tokens.inputBg, borderRadius: 999, padding: '2px 8px', fontWeight: 700 };
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
                            display: 'grid',
                            gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
                            gap: 12,
                        }}
                    >
                        <div>
                            <div style={{ fontSize: '0.8125rem', color: tokens.textMuted }}>Mevcut Bakiye</div>
                            <div style={{ fontSize: '1.5rem', fontWeight: 700 }}>
                                {fmtMoney(summary?.currentBalance ?? balance?.currentAmount ?? 0)}
                            </div>
                        </div>
                        <div>
                            <div style={{ fontSize: '0.8125rem', color: tokens.textMuted }}>Kullanılabilir Bakiye</div>
                            <div style={{ fontSize: '1.1rem', fontWeight: 700 }}>
                                {fmtMoney(summary?.availableBalance ?? balance?.currentAmount ?? 0)}
                            </div>
                        </div>
                        <div>
                            <div style={{ fontSize: '0.8125rem', color: tokens.textMuted }}>Bekleyen Yatırma</div>
                            <div style={{ fontSize: '1.1rem', fontWeight: 700, color: '#22c55e' }}>
                                {fmtMoney(summary?.pendingDeposit ?? 0)}
                            </div>
                        </div>
                        <div>
                            <div style={{ fontSize: '0.8125rem', color: tokens.textMuted }}>Bekleyen Çekim</div>
                            <div style={{ fontSize: '1.1rem', fontWeight: 700, color: '#f59e0b' }}>
                                {fmtMoney(summary?.pendingWithdrawal ?? 0)}
                            </div>
                        </div>
                        <div style={{ color: tokens.textMuted, fontSize: '0.8125rem' }}>
                            accountId: {summary?.accountId ?? balance?.accountId ?? '-'}
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
                                    <option value="DEPOSIT">Yatırım</option>
                                    <option value="WITHDRAWAL">Çekim</option>
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

                            {type === 'DEPOSIT' ? (
                                <>
                                    <div
                                        style={{
                                            gridColumn: '1 / -1',
                                            border: `1px solid ${tokens.border}`,
                                            borderRadius: 10,
                                            padding: 12,
                                            background: tokens.inputBg,
                                        }}
                                    >
                                        <div style={{ fontWeight: 700, marginBottom: 8 }}>Yatırım Talimatı (Sistem Hesabı)</div>
                                        <div style={{ fontSize: '0.875rem', color: tokens.textMuted }}>
                                            IBAN: <strong style={{ color: tokens.text }}>{depositInstructions?.iban ?? '-'}</strong>
                                        </div>
                                        <div style={{ fontSize: '0.875rem', color: tokens.textMuted }}>
                                            Alıcı: <strong style={{ color: tokens.text }}>{depositInstructions?.recipientName ?? '-'}</strong>
                                        </div>
                                        <div style={{ fontSize: '0.875rem', color: tokens.textMuted }}>
                                            Banka: <strong style={{ color: tokens.text }}>{depositInstructions?.bankName ?? '-'}</strong>
                                        </div>
                                        <div style={{ fontSize: '0.875rem', color: tokens.textMuted }}>
                                            Kullanıcı Referansı:{' '}
                                            <strong style={{ color: tokens.text }}>{depositInstructions?.userReferenceCode ?? '-'}</strong>
                                        </div>
                                    </div>

                                    <label style={{ fontSize: '0.875rem' }}>
                                        Kaynak Banka (opsiyonel)
                                        <input
                                            value={sourceBankName}
                                            onChange={(e) => setSourceBankName(e.target.value)}
                                            style={inputStyle}
                                            placeholder="Garanti BBVA"
                                        />
                                    </label>

                                    <label style={{ fontSize: '0.875rem' }}>
                                        Referans No (opsiyonel)
                                        <input
                                            value={externalReferenceNo}
                                            onChange={(e) => setExternalReferenceNo(e.target.value)}
                                            style={inputStyle}
                                            placeholder="EFT-..."
                                        />
                                    </label>

                                    <label style={{ gridColumn: '1 / -1', fontSize: '0.875rem' }}>
                                        Dekont Dosyası (PDF/JPG/PNG) *
                                        <input
                                            type="file"
                                            accept=".pdf,.jpg,.jpeg,.png"
                                            style={inputStyle}
                                            onChange={(e) => {
                                                const file = e.target.files?.[0];
                                                if (file) {
                                                    void uploadReceipt(file);
                                                }
                                            }}
                                        />
                                        <div style={{ marginTop: 6, fontSize: '0.8125rem', color: tokens.textMuted }}>
                                            {uploadingReceipt
                                                ? 'Dekont yükleniyor...'
                                                : receiptFileName
                                                  ? `Yüklendi: ${receiptFileName}`
                                                  : 'Henüz dekont yüklenmedi'}
                                        </div>
                                    </label>
                                </>
                            ) : (
                                <>
                                    <label style={{ fontSize: '0.875rem' }}>
                                        Alıcı IBAN
                                        <input
                                            value={destinationIban}
                                            onChange={(e) => setDestinationIban(e.target.value)}
                                            style={inputStyle}
                                            placeholder="TR..."
                                            required
                                        />
                                    </label>

                                    <label style={{ fontSize: '0.875rem' }}>
                                        Alıcı Ad Soyad
                                        <input
                                            value={destinationAccountHolder}
                                            onChange={(e) => setDestinationAccountHolder(e.target.value)}
                                            style={inputStyle}
                                            placeholder="Ad Soyad"
                                            required
                                        />
                                    </label>

                                    <label style={{ fontSize: '0.875rem' }}>
                                        Banka Adı
                                        <input
                                            value={destinationBankName}
                                            onChange={(e) => setDestinationBankName(e.target.value)}
                                            style={inputStyle}
                                            placeholder="Banka"
                                            required
                                        />
                                    </label>
                                </>
                            )}

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
                                    {submitting ? 'Gönderiliyor...' : 'Talep oluştur'}
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
                                        <th style={{ textAlign: 'left', padding: 8, borderBottom: `2px solid ${tokens.border}` }}>Detay</th>
                                        <th style={{ textAlign: 'left', padding: 8, borderBottom: `2px solid ${tokens.border}` }}>İnceleme notu</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {items.length === 0 ? (
                                        <tr>
                                            <td
                                                colSpan={7}
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
                                                <td style={{ padding: 8, borderBottom: `1px solid ${tokens.tableBorder}`, fontSize: '0.8125rem' }}>
                                                    {r.type === 'DEPOSIT' ? (
                                                        <div>
                                                            <div>Yatırım IBAN: {r.depositIban || r.bankAccountIban || '-'}</div>
                                                            <div>Ref: {r.referenceNo || '-'}</div>
                                                            {r.receiptFileUrl ? (
                                                                <a href={r.receiptFileUrl} target="_blank" rel="noreferrer" style={{ color: '#60a5fa' }}>
                                                                    Dekont
                                                                </a>
                                                            ) : (
                                                                <div>Dekont: -</div>
                                                            )}
                                                        </div>
                                                    ) : (
                                                        <div>
                                                            <div>Alıcı IBAN: {r.destinationIban || '-'}</div>
                                                            <div>Alıcı: {r.destinationAccountHolder || '-'}</div>
                                                            <div>Banka: {r.destinationBankName || '-'}</div>
                                                        </div>
                                                    )}
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
