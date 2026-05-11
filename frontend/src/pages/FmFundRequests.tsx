import { useCallback, useEffect, useRef, useState } from 'react';
import { financeClient, readFinanceBinaryErrorMessage } from '../api/client';
import keycloak from '../auth/keycloak';
import { useTheme } from '../theme/ThemeContext';
import { useRefetchOnFocus } from '../hooks/useRefetchOnFocus';
import { usePolling } from '../hooks/usePolling';
import { useLanguage } from '../i18n/LanguageContext';

const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8085';

type FundRequestType = 'DEPOSIT' | 'WITHDRAWAL';
type FundRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';

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
    sourceBankName: string | null;
    depositIban: string | null;
    systemIbanId: string | null;
    destinationIban: string | null;
    destinationAccountHolder: string | null;
    destinationBankName: string | null;
    approvedByUserId: number | null;
    approvedAt: string | null;
    rejectedAt: string | null;
    assignedFmKeycloakId: string | null;
    claimedAt: string | null;
    createdAt: string;
};

function unwrapData<T>(res: any): T {
    return (res?.data?.data ?? res?.data) as T;
}

export function FmFundRequests() {
    const { tokens } = useTheme();
    const { t, lang } = useLanguage();

    const [items, setItems] = useState<FundRequestView[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const [actionLoadingId, setActionLoadingId] = useState<number | null>(null);
    const [claimingId, setClaimingId] = useState<number | null>(null);

    const [rejectModalOpen, setRejectModalOpen] = useState(false);
    const [rejectTargetId, setRejectTargetId] = useState<number | null>(null);
    const [rejectNote, setRejectNote] = useState('');

    const openReceipt = async (receiptFileId?: string | null, receiptFileUrl?: string | null) => {
        const idFromUrl =
            receiptFileUrl?.split('/receipts/')[1]?.split('?')[0]?.trim() || null;
        const receiptId = (receiptFileId || idFromUrl || '').trim();
        if (!receiptId) return;
        try {
            const res = await financeClient.get<ArrayBuffer>(`/api/fund-requests/receipts/${receiptId}`, {
                responseType: 'arraybuffer',
            });
            const ct = (res.headers?.['content-type'] as string) || 'application/octet-stream';
            const blob = new Blob([res.data], { type: ct });
            const objectUrl = URL.createObjectURL(blob);
            window.open(objectUrl, '_blank', 'noopener,noreferrer');
            setTimeout(() => URL.revokeObjectURL(objectUrl), 60_000);
        } catch (err: any) {
            const status = err?.response?.status as number | undefined;
            const fromApi = readFinanceBinaryErrorMessage(err);
            if (status === 404) {
                alert(
                    t(
                        'wallet.receiptMissing',
                        'Dekont dosyası sunucuda yok. Eski kayıtlar geçici klasörde tutulduysa silinmiş olabilir; servis artık kalıcı dizin kullanıyor. Yeni yüklemeler korunur.'
                    ) + (fromApi ? `\n\n(${fromApi})` : '')
                );
                return;
            }
            alert(
                fromApi ??
                    err?.response?.data?.errors?.error ??
                    err?.response?.data?.message ??
                    err?.message ??
                    t('wallet.receiptOpenFailed', 'Dekont görüntülenemedi')
            );
        }
    };

    const sseAbortRef = useRef<AbortController | null>(null);

    const fetchPending = useCallback(() => {
        setLoading(true);
        setError(null);

        financeClient
            .get('/api/admin/fund-requests/pending')
            .then((res) => {
                const list = unwrapData<FundRequestView[]>(res) ?? [];
                setItems(list);
            })
            .catch((err) => {
                const msg =
                    err.response?.data?.errors?.error ??
                    err.response?.data?.message ??
                    err.message ??
                    t('admin.listLoadFailed', 'Liste alınamadı');
                setError(msg);
            })
            .finally(() => setLoading(false));
    }, []);

    useEffect(() => {
        fetchPending();
    }, [fetchPending]);

    useRefetchOnFocus(fetchPending);
    usePolling(fetchPending, 30_000);

    useEffect(() => {
        sseAbortRef.current?.abort();
        if (!keycloak.token) return;
        const ac = new AbortController();
        sseAbortRef.current = ac;
        const run = async () => {
            try {
                const res = await fetch(`${API_BASE}/api/tasks/sse/fm`, {
                    method: 'GET',
                    headers: {
                        Authorization: `Bearer ${keycloak.token}`,
                        Accept: 'text/event-stream',
                    },
                    signal: ac.signal,
                });
                if (!res.ok || !res.body) return;
                const reader = res.body.getReader();
                const dec = new TextDecoder();
                let buf = '';
                for (;;) {
                    const { done, value } = await reader.read();
                    if (done) break;
                    buf += dec.decode(value, { stream: true });
                    if (buf.includes('FUND_REQUEST_CLAIMED')) {
                        buf = '';
                        void fetchPending();
                    }
                    if (buf.length > 64_000) buf = buf.slice(-32_000);
                }
            } catch {
                /* abort / network */
            }
        };
        void run();
        return () => ac.abort();
    }, [fetchPending, keycloak.token]);

    const approve = async (id: number) => {
        setActionLoadingId(id);
        try {
            await financeClient.post(`/api/admin/fund-requests/${id}/approve`, {
                reviewNote: t('fm.approvedFromFrontend', 'Frontend üzerinden onaylandı'),
            });
            await fetchPending();
        } catch (err: any) {
            alert(
                err?.response?.data?.errors?.error ??
                    err?.response?.data?.message ??
                    err?.message ??
                    t('fm.approveError', 'Onay sırasında hata')
            );
        } finally {
            setActionLoadingId(null);
        }
    };

    const claim = async (id: number) => {
        setClaimingId(id);
        try {
            await financeClient.post(`/api/admin/fund-requests/${id}/claim`);
            await fetchPending();
        } catch (err: any) {
            alert(
                err?.response?.data?.errors?.error ??
                err?.response?.data?.message ??
                err?.message ??
                t('fm.claimError', 'Üzerime alma sırasında hata'),
            );
        } finally {
            setClaimingId(null);
        }
    };

    const openRejectModal = (id: number) => {
        setRejectTargetId(id);
        setRejectNote('');
        setRejectModalOpen(true);
    };

    const reject = async () => {
        if (!rejectTargetId) return;
        setActionLoadingId(rejectTargetId);

        try {
            await financeClient.post(`/api/admin/fund-requests/${rejectTargetId}/reject`, {
                reviewNote: rejectNote?.trim() || t('fm.rejectedFromFrontend', 'Frontend üzerinden reddedildi'),
            });
            setRejectModalOpen(false);
            setRejectTargetId(null);
            setRejectNote('');
            await fetchPending();
        } catch (err: any) {
            alert(
                err?.response?.data?.errors?.error ??
                    err?.response?.data?.message ??
                    err?.message ??
                    t('fm.rejectError', 'Red sırasında hata')
            );
        } finally {
            setActionLoadingId(null);
        }
    };

    const fmtMoney = (v: number, ccy: string) =>
        `${Number(v).toLocaleString('tr-TR', { maximumFractionDigits: 2 })} ${ccy || 'TRY'}`;

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

    const badgeStyle = (type: FundRequestType): React.CSSProperties => ({
        display: 'inline-block',
        borderRadius: 999,
        padding: '2px 10px',
        fontSize: '0.75rem',
        fontWeight: 600,
        color: '#fff',
        background: type === 'DEPOSIT' ? tokens.accentGradient : '#f59e0b',
    });

    const statusBadgeStyle = (status: FundRequestStatus): React.CSSProperties => {
        if (status === 'APPROVED') return { color: '#fff', background: tokens.success };
        if (status === 'REJECTED') return { color: '#fff', background: tokens.error };
        if (status === 'PENDING') return { color: '#111827', background: '#facc15' };
        return { color: tokens.textMuted, background: tokens.inputBg };
    };

    return (
        <div style={pageStyle}>
            <h1 style={{ fontSize: '1.75rem', fontWeight: 700, marginBottom: 6 }}>Para Talepleri</h1>
            <p style={{ color: tokens.textMuted, fontSize: '0.875rem', marginBottom: 16 }}>
                {t('fm.fundRequestsSubtitle', 'Finance Manager onay/reddet ekranı. Yalnızca PENDING talepler listelenir.')}
            </p>

            <div style={cardStyle}>
                {loading && <p style={{ color: tokens.textMuted }}>{t('common.loading', 'Yükleniyor...')}</p>}
                {error && <p style={{ color: tokens.error }}>{t('news.errorPrefix', 'Hata')}: {error}</p>}

                {!loading && !error && (
                    <div>
                        {items.length === 0 ? (
                            <p style={{ color: tokens.textMuted, margin: 0 }}>Bekleyen talep yok.</p>
                        ) : (
                            <div style={{ display: 'grid', gap: 12 }}>
                                {items.map((r) => (
                                    <div
                                        key={r.id}
                                        style={{
                                            border: `1px solid ${tokens.border}`,
                                            borderRadius: 10,
                                            padding: 12,
                                            background: tokens.inputBg,
                                        }}
                                    >
                                        <div style={{ display: 'flex', justifyContent: 'space-between', gap: 10, flexWrap: 'wrap' }}>
                                            <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
                                                <strong>#{r.id}</strong>
                                                <span style={{ color: tokens.textMuted, fontSize: '0.8rem' }}>userId: {r.userId}</span>
                                                <span style={{ color: tokens.textMuted, fontSize: '0.8rem' }}>accountId: {r.accountId}</span>
                                                <span style={badgeStyle(r.type)}>{r.type}</span>
                                                <span
                                                    style={{
                                                        ...statusBadgeStyle(r.status),
                                                        borderRadius: 999,
                                                        padding: '2px 8px',
                                                        fontSize: '0.75rem',
                                                        fontWeight: 700,
                                                    }}
                                                >
                                                    {r.status}
                                                </span>
                                                {r.claimedAt && (
                                                    <span
                                                        style={{
                                                            borderRadius: 999,
                                                            padding: '2px 8px',
                                                            fontSize: '0.75rem',
                                                            fontWeight: 700,
                                                            color: tokens.text,
                                                            background: tokens.bg,
                                                        }}
                                                    >
                                                        CLAIMED
                                                    </span>
                                                )}
                                            </div>
                                            <div style={{ fontWeight: 700 }}>{fmtMoney(r.amount, r.currency)}</div>
                                        </div>

                                        <div
                                            style={{
                                                marginTop: 10,
                                                display: 'grid',
                                                gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
                                                gap: 10,
                                                fontSize: '0.85rem',
                                            }}
                                        >
                                            {r.type === 'DEPOSIT' ? (
                                                <>
                                                    <div>
                                                        <div style={{ color: tokens.textMuted }}>Sistem IBAN</div>
                                                        <div>{r.depositIban || r.bankAccountIban || '-'}</div>
                                                    </div>
                                                    <div>
                                                        <div style={{ color: tokens.textMuted }}>Sistem IBAN ID</div>
                                                        <div>{r.systemIbanId || '-'}</div>
                                                    </div>
                                                    <div>
                                                        <div style={{ color: tokens.textMuted }}>Kaynak Banka</div>
                                                        <div>{r.sourceBankName || '-'}</div>
                                                    </div>
                                                    <div>
                                                        <div style={{ color: tokens.textMuted }}>Dekont</div>
                                                        {r.receiptFileUrl ? (
                                                            <button
                                                                type="button"
                                                                onClick={() => void openReceipt(r.receiptFileId, r.receiptFileUrl)}
                                                                style={{
                                                                    border: 'none',
                                                                    background: 'transparent',
                                                                    color: tokens.accent,
                                                                    padding: 0,
                                                                    cursor: 'pointer',
                                                                }}
                                                            >
                                                                Dekontu Aç
                                                            </button>
                                                        ) : (
                                                            <span>-</span>
                                                        )}
                                                    </div>
                                                </>
                                            ) : (
                                                <>
                                                    <div>
                                                        <div style={{ color: tokens.textMuted }}>Alıcı IBAN</div>
                                                        <div>{r.destinationIban || r.bankAccountIban || '-'}</div>
                                                    </div>
                                                    <div>
                                                        <div style={{ color: tokens.textMuted }}>Alıcı Ad Soyad</div>
                                                        <div>{r.destinationAccountHolder || '-'}</div>
                                                    </div>
                                                    <div>
                                                        <div style={{ color: tokens.textMuted }}>Banka Adı</div>
                                                        <div>{r.destinationBankName || r.sourceBankName || '-'}</div>
                                                    </div>
                                                </>
                                            )}
                                            <div style={{ gridColumn: '1 / -1' }}>
                                                <div style={{ color: tokens.textMuted }}>Not</div>
                                                <div>{r.requestNote || '-'}</div>
                                            </div>
                                            <div style={{ gridColumn: '1 / -1', color: tokens.textMuted, fontSize: '0.8rem' }}>
                                                {t('admin.createdAt', 'Oluşturulma')}: {new Date(r.createdAt).toLocaleString(lang === 'en' ? 'en-US' : 'tr-TR')}
                                            </div>
                                        </div>

                                        <div style={{ display: 'flex', gap: 8, marginTop: 12 }}>
                                            {r.claimedAt ? (
                                                <>
                                                    <button
                                                        type="button"
                                                        disabled={actionLoadingId === r.id}
                                                        onClick={() => approve(r.id)}
                                                        style={{
                                                            padding: '6px 10px',
                                                            borderRadius: 8,
                                                            border: 'none',
                                                            cursor: 'pointer',
                                                            color: '#fff',
                                                            background: tokens.accentGradient,
                                                            opacity: actionLoadingId === r.id ? 0.7 : 1,
                                                        }}
                                                    >
                                                        Onayla
                                                    </button>
                                                    <button
                                                        type="button"
                                                        disabled={actionLoadingId === r.id}
                                                        onClick={() => openRejectModal(r.id)}
                                                        style={{
                                                            padding: '6px 10px',
                                                            borderRadius: 8,
                                                            border: 'none',
                                                            cursor: 'pointer',
                                                            color: '#fff',
                                                            background: tokens.error,
                                                            opacity: actionLoadingId === r.id ? 0.7 : 1,
                                                        }}
                                                    >
                                                        Reddet
                                                    </button>
                                                </>
                                            ) : (
                                                <button
                                                    type="button"
                                                    disabled={claimingId === r.id}
                                                    onClick={() => claim(r.id)}
                                                    style={{
                                                        padding: '6px 10px',
                                                        borderRadius: 8,
                                                        border: 'none',
                                                        cursor: 'pointer',
                                                        color: '#fff',
                                                        background: tokens.accentGradient,
                                                        opacity: claimingId === r.id ? 0.7 : 1,
                                                    }}
                                                >
                                                    {claimingId === r.id ? '...' : t('fm.claimMine', 'Üzerime Al')}
                                                </button>
                                            )}
                                        </div>
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>
                )}
            </div>

            {rejectModalOpen && (
                <div
                    style={{
                        position: 'fixed',
                        inset: 0,
                        background: 'rgba(0,0,0,0.35)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        zIndex: 9999,
                        padding: 16,
                    }}
                >
                    <div
                        style={{
                            width: 'min(560px, 100%)',
                            background: tokens.bgCard,
                            border: `1px solid ${tokens.border}`,
                            borderRadius: 12,
                            padding: 16,
                        }}
                    >
                        <h3 style={{ margin: 0, marginBottom: 8 }}>Talebi Reddet</h3>
                        <p style={{ color: tokens.textMuted, fontSize: '0.875rem', marginTop: 0 }}>
                            Talep ID: {rejectTargetId}
                        </p>
                        <textarea
                            value={rejectNote}
                            onChange={(e) => setRejectNote(e.target.value)}
                            rows={4}
                            placeholder="Red sebebi (review note)"
                            style={{
                                width: '100%',
                                padding: 10,
                                borderRadius: 8,
                                border: `1px solid ${tokens.border}`,
                                background: tokens.inputBg,
                                color: tokens.text,
                                resize: 'vertical',
                            }}
                        />
                        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 12 }}>
                            <button
                                type="button"
                                onClick={() => setRejectModalOpen(false)}
                                style={{
                                    padding: '8px 12px',
                                    borderRadius: 8,
                                    border: `1px solid ${tokens.border}`,
                                    background: tokens.bgCard,
                                    color: tokens.text,
                                    cursor: 'pointer',
                                }}
                            >
                                Vazgeç
                            </button>
                            <button
                                type="button"
                                onClick={reject}
                                style={{
                                    padding: '8px 12px',
                                    borderRadius: 8,
                                    border: 'none',
                                    background: tokens.error,
                                    color: '#fff',
                                    cursor: 'pointer',
                                }}
                            >
                                Reddet
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
