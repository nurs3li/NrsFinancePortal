import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import { financeClient, readFinanceBinaryErrorMessage } from '../api/client';
import { useTheme } from '../theme/ThemeContext';
import { useRefetchOnFocus } from '../hooks/useRefetchOnFocus';
import { usePolling } from '../hooks/usePolling';
import { useLanguage } from '../i18n/LanguageContext';
import './Wallet.css';

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
    sourceBankName?: string;
    depositIban?: string;
    systemIbanId?: string;
    destinationIban?: string;
    destinationAccountHolder?: string;
    destinationBankName?: string;
};

const SOURCE_BANK_OPTIONS = [
    'A Bank',
    'Akbank',
    'Aktif Bank',
    'Albaraka',
    'Anadolubank',
    'Bank Asya',
    'Burgan Bank',
    'DenizBank',
    'Fibabanka',
    'QNB Finansbank',
    'Garanti BBVA',
    'Halkbank',
    'HSBC',
    'ICBC Turkey',
    'ING',
    'Kuveyt Turk',
    'Odeabank',
    'Sekerbank',
    'Ziraat Bankasi',
    'TEB',
    'Turkish Bank',
    'Turkiye Is Bankasi',
    'Turkiye Finans',
    'T-Bank',
    'VakifBank',
    'Vakıf Katilim',
    'Yapi Kredi',
    'Ziraat Katilim',
] as const;

function unwrapData<T>(res: any): T {
    return (res?.data?.data ?? res?.data) as T;
}

function useAnimatedNumber(target: number) {
    const [display, setDisplay] = useState(0);
    const displayRef = useRef(0);

    useEffect(() => {
        const from = displayRef.current;
        let raf = 0;
        const t0 = performance.now();
        const dur = 680;
        const step = (now: number) => {
            const p = Math.min(1, (now - t0) / dur);
            const eased = 1 - (1 - p) ** 3;
            const next = from + (target - from) * eased;
            displayRef.current = next;
            setDisplay(next);
            if (p < 1) raf = requestAnimationFrame(step);
        };
        raf = requestAnimationFrame(step);
        return () => cancelAnimationFrame(raf);
    }, [target]);

    return display;
}

function WalletMetricTry({
    value,
    label,
    valueClassName,
    locale,
}: {
    value: number;
    label: string;
    valueClassName?: string;
    locale: string;
}) {
    const v = useAnimatedNumber(value);
    return (
        <div className="wallet-card-premium">
            <div className="wallet-metric-label">{label}</div>
            <div className={`wallet-metric-value ${valueClassName ?? ''}`.trim()}>
                ₺{v.toLocaleString(locale, { maximumFractionDigits: 2 })}
            </div>
        </div>
    );
}

function statusPillClass(s: FundRequestStatus): string {
    switch (s) {
        case 'APPROVED':
            return 'wallet-pill wallet-pill--approved';
        case 'PENDING':
            return 'wallet-pill wallet-pill--pending';
        case 'REJECTED':
            return 'wallet-pill wallet-pill--rejected';
        case 'CANCELLED':
            return 'wallet-pill wallet-pill--cancelled';
        default:
            return 'wallet-pill';
    }
}

export function Wallet() {
    const { tokens } = useTheme();
    const { t, lang } = useLanguage();
    const locale = lang === 'en' ? 'en-US' : 'tr-TR';

    const [balance, setBalance] = useState<BalanceView | null>(null);
    const [summary, setSummary] = useState<WalletSummary | null>(null);
    const [depositInstructions, setDepositInstructions] = useState<DepositInstructions | null>(null);
    const [items, setItems] = useState<FundRequestView[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const [submitting, setSubmitting] = useState(false);

    const [modalMode, setModalMode] = useState<FundRequestType | null>(null);
    const [type, setType] = useState<FundRequestType>('DEPOSIT');
    const [amount, setAmount] = useState('1000');
    const [currency, setCurrency] = useState('TRY');
    const [destinationIban, setDestinationIban] = useState('');
    const [destinationAccountHolder, setDestinationAccountHolder] = useState('');
    const [destinationBankName, setDestinationBankName] = useState('');
    const [sourceBankName, setSourceBankName] = useState('');
    const [receiptFileUrl, setReceiptFileUrl] = useState<string | null>(null);
    const [receiptFileId, setReceiptFileId] = useState<string | null>(null);
    const [receiptFileName, setReceiptFileName] = useState<string | null>(null);
    const [uploadingReceipt, setUploadingReceipt] = useState(false);
    const [requestNote, setRequestNote] = useState('');
    const [dropActive, setDropActive] = useState(false);

    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);

    const [successMsg, setSuccessMsg] = useState<string | null>(null);

    const receiptInputRef = useRef<HTMLInputElement>(null);

    const openReceipt = async (receiptFileId?: string | null, receiptFileUrl?: string | null) => {
        const idFromUrl = receiptFileUrl?.split('/receipts/')[1]?.split('?')[0]?.trim() || null;
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
                    t('wallet.dataLoadFailed', 'Veriler alınamadı');
                setError(msg);
            })
            .finally(() => setLoading(false));
    }, [t]);

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
        setAmount('1000');
    }, [type]);

    const totalPages = Math.max(1, Math.ceil(items.length / pageSize));

    useEffect(() => {
        setPage((p) => Math.min(Math.max(1, p), totalPages));
    }, [items.length, pageSize, totalPages]);

    const paginatedItems = useMemo(() => {
        const start = (page - 1) * pageSize;
        return items.slice(start, start + pageSize);
    }, [items, page, pageSize]);

    const uploadReceipt = async (file: File) => {
        const ext = file.name.toLowerCase().split('.').pop() ?? '';
        if (!['pdf', 'jpg', 'jpeg', 'png'].includes(ext)) {
            alert(t('wallet.receiptTypeError', 'Dekont dosyası PDF/JPG/PNG olmalı.'));
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
                    t('wallet.receiptUploadFailed', 'Dekont yüklenemedi')
            );
        } finally {
            setUploadingReceipt(false);
        }
    };

    const submit = async (e: FormEvent) => {
        e.preventDefault();
        setSuccessMsg(null);

        const parsedAmount = Number(amount);
        if (!parsedAmount || parsedAmount <= 0) {
            alert(t('wallet.amountPositive', 'Tutar sıfırdan büyük olmalı.'));
            return;
        }

        if (type === 'WITHDRAWAL' && parsedAmount > Number(summary?.availableBalance ?? 0)) {
            alert(t('wallet.withdrawLimit', 'Çekim tutarı kullanılabilir bakiyeden fazla olamaz.'));
            return;
        }

        if (type === 'DEPOSIT' && !receiptFileId && !receiptFileUrl) {
            alert(t('wallet.receiptRequiredDeposit', 'Yatırım için dekont yüklemek zorunludur.'));
            return;
        }

        if (type === 'WITHDRAWAL' && !destinationIban.trim()) {
            alert(t('wallet.ibanRequired', 'Çekim için alıcı IBAN zorunludur.'));
            return;
        }
        let normalizedIban = destinationIban;
        if (type === 'WITHDRAWAL') {
            const iban = destinationIban.replace(/\s+/g, '').toUpperCase();
            if (!/^TR\d{24}$/.test(iban)) {
                alert(t('wallet.invalidTrIban', 'Geçersiz IBAN. TR ile başlamalı ve 24 rakam içermelidir.'));
                return;
            }
            normalizedIban = iban;
            setDestinationIban(iban);
        }
        if (type === 'WITHDRAWAL' && !destinationAccountHolder.trim()) {
            alert(t('wallet.holderRequired', 'Çekim için alıcı ad soyad zorunludur.'));
            return;
        }
        if (type === 'WITHDRAWAL' && !destinationBankName.trim()) {
            alert(t('wallet.bankRequired', 'Çekim için banka adı zorunludur.'));
            return;
        }

        const payload: CreateFundRequestPayload = {
            type,
            amount: parsedAmount,
            currency: currency?.trim() || 'TRY',
            requestNote: requestNote?.trim() || undefined,
            sourceBankName: type === 'DEPOSIT' ? sourceBankName?.trim() || undefined : undefined,
            receiptFileUrl: type === 'DEPOSIT' ? receiptFileUrl ?? undefined : undefined,
            receiptFileId: type === 'DEPOSIT' ? receiptFileId ?? undefined : undefined,
            depositIban: type === 'DEPOSIT' ? depositInstructions?.iban : undefined,
            systemIbanId: type === 'DEPOSIT' ? depositInstructions?.systemIbanId : undefined,
            destinationIban: type === 'WITHDRAWAL' ? normalizedIban?.trim() || undefined : undefined,
            destinationAccountHolder: type === 'WITHDRAWAL' ? destinationAccountHolder?.trim() || undefined : undefined,
            destinationBankName: type === 'WITHDRAWAL' ? destinationBankName?.trim() || undefined : undefined,
        };

        try {
            setSubmitting(true);
            await financeClient.post('/api/fund-requests', payload);
            setSuccessMsg(t('wallet.requestCreated', 'Talebiniz başarıyla oluşturuldu.'));
            setRequestNote('');
            if (type === 'DEPOSIT') {
                setReceiptFileId(null);
                setReceiptFileUrl(null);
                setReceiptFileName(null);
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
                    t('wallet.requestCreateFailed', 'Talep oluşturulamadı')
            );
        } finally {
            setSubmitting(false);
        }
    };

    const closeModal = useCallback(() => {
        setModalMode(null);
        setSuccessMsg(null);
    }, []);

    const openDepositModal = () => {
        setModalMode('DEPOSIT');
        setType('DEPOSIT');
        setSuccessMsg(null);
    };

    const openWithdrawModal = () => {
        setModalMode('WITHDRAWAL');
        setType('WITHDRAWAL');
        setSuccessMsg(null);
    };

    useEffect(() => {
        if (!modalMode) return;
        const onKey = (ev: KeyboardEvent) => {
            if (ev.key === 'Escape') closeModal();
        };
        window.addEventListener('keydown', onKey);
        return () => window.removeEventListener('keydown', onKey);
    }, [modalMode, closeModal]);

    const statusLabel = (s: FundRequestStatus) => {
        switch (s) {
            case 'APPROVED':
                return t('wallet.statusApproved', 'Onaylandı');
            case 'PENDING':
                return t('wallet.statusPending', 'Beklemede');
            case 'REJECTED':
                return t('wallet.statusRejected', 'Reddedildi');
            case 'CANCELLED':
                return t('wallet.statusCancelled', 'İptal');
            default:
                return s;
        }
    };

    const typeLabel = (ft: FundRequestType) =>
        ft === 'DEPOSIT' ? t('wallet.deposit', 'Yatırım') : t('wallet.withdrawal', 'Çekim');

    const currentBal = summary?.currentBalance ?? balance?.currentAmount ?? 0;
    const availBal = summary?.availableBalance ?? balance?.currentAmount ?? 0;
    const pendDep = summary?.pendingDeposit ?? 0;
    const pendWdr = summary?.pendingWithdrawal ?? 0;

    return (
        <div className="wallet-page" style={{ background: tokens.bg, color: tokens.text }}>
            <div className="wallet-container">
                <h1 style={{ fontSize: '1.65rem', fontWeight: 800, marginBottom: 6 }}>{t('nav.wallet', 'Cüzdan')}</h1>
                <p style={{ color: tokens.textMuted, fontSize: '0.875rem', marginBottom: 20 }}>
                    {t('wallet.subtitle', 'Para yatırma/çekme talebi oluştur ve durumunu takip et.')}
                </p>

                {loading && <p style={{ color: tokens.textMuted }}>{t('common.loading', 'Yükleniyor...')}</p>}
                {error && (
                    <p style={{ color: tokens.error }}>
                        {t('news.errorPrefix', 'Hata')}: {error}
                    </p>
                )}

                {!loading && !error && (
                    <>
                        <div className="wallet-metrics-grid">
                            <WalletMetricTry
                                value={currentBal}
                                label={t('wallet.currentBalance', 'Mevcut Bakiye')}
                                locale={locale}
                            />
                            <WalletMetricTry
                                value={availBal}
                                label={t('wallet.availableBalance', 'Kullanılabilir Bakiye')}
                                locale={locale}
                            />
                            <WalletMetricTry
                                value={pendDep}
                                label={t('wallet.pendingDeposit', 'Bekleyen Yatırma')}
                                valueClassName="wallet-metric-value--accent-green"
                                locale={locale}
                            />
                            <WalletMetricTry
                                value={pendWdr}
                                label={t('wallet.pendingWithdrawal', 'Bekleyen Çekim')}
                                valueClassName="wallet-metric-value--accent-amber"
                                locale={locale}
                            />
                        </div>

                        <div className="wallet-main-split">
                            <aside className="wallet-sidebar">
                                <div className="wallet-card-premium wallet-actions">
                                    <h2 className="wallet-actions-title">
                                        {t('wallet.quickActions', 'Hızlı İşlemler')}
                                    </h2>
                                    <div className="wallet-cta-row">
                                        <button type="button" className="wallet-cta wallet-cta--deposit" onClick={openDepositModal}>
                                            {t('wallet.depositCta', 'Para Yatır')}
                                        </button>
                                        <button type="button" className="wallet-cta wallet-cta--withdraw" onClick={openWithdrawModal}>
                                            {t('wallet.withdrawCta', 'Para Çek')}
                                        </button>
                                    </div>
                                </div>
                            </aside>

                            <section className="wallet-card-premium wallet-history-card">
                                <h2
                                    style={{
                                        marginTop: 0,
                                        marginBottom: 14,
                                        fontSize: '0.95rem',
                                        fontWeight: 800,
                                        letterSpacing: '0.04em',
                                        textTransform: 'uppercase',
                                        color: tokens.textMuted,
                                    }}
                                >
                                    {t('wallet.requestHistory', 'Talep Geçmişim')}
                                </h2>

                                <div className="wallet-table-wrap">
                                    <table className="wallet-table">
                                        <thead>
                                            <tr>
                                                <th>{t('wallet.tableId', 'ID')}</th>
                                                <th>{t('wallet.tableType', 'Tip')}</th>
                                                <th>{t('transactions.amount', 'Tutar')}</th>
                                                <th>{t('wallet.status', 'Durum')}</th>
                                                <th>{t('news.date', 'Tarih')}</th>
                                                <th>{t('wallet.detail', 'Detay')}</th>
                                                <th>{t('wallet.reviewNote', 'İnceleme notu')}</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            {items.length === 0 ? (
                                                <tr>
                                                    <td
                                                        colSpan={7}
                                                        style={{
                                                            padding: 14,
                                                            color: tokens.textMuted,
                                                            textAlign: 'center',
                                                        }}
                                                    >
                                                        {t('wallet.noRequests', 'Henüz talep yok.')}
                                                    </td>
                                                </tr>
                                            ) : (
                                                paginatedItems.map((r) => (
                                                    <tr key={r.id} className="wallet-table-row">
                                                        <td>#{r.id}</td>
                                                        <td>{typeLabel(r.type)}</td>
                                                        <td>
                                                            {Number(r.amount).toLocaleString(locale, { maximumFractionDigits: 2 })}{' '}
                                                            {r.currency}
                                                        </td>
                                                        <td>
                                                            <span className={statusPillClass(r.status)}>{statusLabel(r.status)}</span>
                                                        </td>
                                                        <td>{new Date(r.createdAt).toLocaleString(locale)}</td>
                                                        <td style={{ fontSize: '0.78rem' }}>
                                                            {r.type === 'DEPOSIT' ? (
                                                                <div>
                                                                    <div>
                                                                        {t('wallet.depositIbanShort', 'Yatırım IBAN')}:{' '}
                                                                        {r.depositIban || r.bankAccountIban || '-'}
                                                                    </div>
                                                                    {r.receiptFileUrl ? (
                                                                        <button
                                                                            type="button"
                                                                            onClick={() => void openReceipt(r.receiptFileId, r.receiptFileUrl)}
                                                                            style={{
                                                                                border: 'none',
                                                                                background: 'transparent',
                                                                                color: '#00d4ff',
                                                                                padding: 0,
                                                                                cursor: 'pointer',
                                                                                marginTop: 4,
                                                                            }}
                                                                        >
                                                                            {t('wallet.receipt', 'Dekont')}
                                                                        </button>
                                                                    ) : (
                                                                        <div style={{ marginTop: 4 }}>
                                                                            {t('wallet.receipt', 'Dekont')}: —
                                                                        </div>
                                                                    )}
                                                                </div>
                                                            ) : (
                                                                <div>
                                                                    <div>
                                                                        {t('wallet.recipientIbanShort', 'Alıcı IBAN')}:{' '}
                                                                        {r.destinationIban || '-'}
                                                                    </div>
                                                                    <div>
                                                                        {t('wallet.recipientNameShort', 'Alıcı')}:{' '}
                                                                        {r.destinationAccountHolder || '-'}
                                                                    </div>
                                                                    <div>
                                                                        {t('wallet.bankNameShort', 'Banka')}: {r.destinationBankName || '-'}
                                                                    </div>
                                                                </div>
                                                            )}
                                                        </td>
                                                        <td>{r.reviewNote || '—'}</td>
                                                    </tr>
                                                ))
                                            )}
                                        </tbody>
                                    </table>
                                </div>

                                {items.length > 0 && (
                                    <div className="wallet-pagination">
                                        <span>
                                            {t('wallet.pageSizeLabel', 'Sayfa başına')}
                                            :{' '}
                                        </span>
                                        <select
                                            className="wallet-input"
                                            style={{ width: 'auto', padding: '6px 10px', fontSize: '0.78rem' }}
                                            value={pageSize}
                                            onChange={(e) => {
                                                setPageSize(Number(e.target.value));
                                                setPage(1);
                                            }}
                                        >
                                            <option value={5}>5</option>
                                            <option value={10}>10</option>
                                        </select>
                                        <span>
                                            {t('wallet.paginationSep', '·')} {page} / {totalPages}
                                        </span>
                                        <button type="button" disabled={page <= 1} onClick={() => setPage((p) => Math.max(1, p - 1))}>
                                            {t('wallet.prev', 'Önceki')}
                                        </button>
                                        <button
                                            type="button"
                                            disabled={page >= totalPages}
                                            onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
                                        >
                                            {t('wallet.next', 'Sonraki')}
                                        </button>
                                    </div>
                                )}
                            </section>
                        </div>
                    </>
                )}
            </div>

            {modalMode && (
                <div
                    className="wallet-modal-overlay"
                    role="presentation"
                    onClick={closeModal}
                    onKeyDown={(e) => e.key === 'Escape' && closeModal()}
                >
                    <div
                        className="wallet-modal"
                        role="dialog"
                        aria-modal="true"
                        aria-labelledby="wallet-modal-title"
                        onClick={(e) => e.stopPropagation()}
                    >
                        <div className="wallet-modal-header">
                            <h2 id="wallet-modal-title" className="wallet-modal-title">
                                {modalMode === 'DEPOSIT'
                                    ? t('wallet.modalDepositTitle', 'Para yatırma talebi')
                                    : t('wallet.modalWithdrawTitle', 'Para çekme talebi')}
                            </h2>
                            <button type="button" className="wallet-modal-close" aria-label="Close" onClick={closeModal}>
                                ×
                            </button>
                        </div>

                        <form onSubmit={submit} className="wallet-form-grid">
                            <label style={{ fontSize: '0.875rem' }}>
                                {t('wallet.amount', 'Tutar')}
                                <input
                                    type="number"
                                    step="0.01"
                                    value={amount}
                                    onChange={(e) => setAmount(e.target.value)}
                                    className="wallet-input"
                                    required
                                />
                            </label>

                            <label style={{ fontSize: '0.875rem' }}>
                                {t('wallet.currency', 'Para Birimi')}
                                <input
                                    value={currency}
                                    onChange={(e) => setCurrency(e.target.value)}
                                    className="wallet-input"
                                    placeholder="TRY"
                                />
                            </label>

                            {modalMode === 'DEPOSIT' ? (
                                <>
                                    <div
                                        style={{
                                            border: `1px solid rgba(192,192,192,0.18)`,
                                            borderRadius: 12,
                                            padding: 12,
                                            background: 'rgba(8, 14, 28, 0.35)',
                                        }}
                                    >
                                        <div style={{ fontWeight: 700, marginBottom: 8 }}>
                                            {t('wallet.depositInstruction', 'Yatırım Talimatı (Sistem Hesabı)')}
                                        </div>
                                        <div style={{ fontSize: '0.875rem', color: 'rgba(184, 193, 204, 0.9)' }}>
                                            IBAN: <strong style={{ color: 'inherit' }}>{depositInstructions?.iban ?? '—'}</strong>
                                        </div>
                                        <div style={{ fontSize: '0.875rem', color: 'rgba(184, 193, 204, 0.9)' }}>
                                            {t('wallet.recipientNameShort', 'Alıcı')}:{' '}
                                            <strong style={{ color: 'inherit' }}>{depositInstructions?.recipientName ?? '—'}</strong>
                                        </div>
                                        <div style={{ fontSize: '0.875rem', color: 'rgba(184, 193, 204, 0.9)' }}>
                                            {t('wallet.bankNameShort', 'Banka')}:{' '}
                                            <strong style={{ color: 'inherit' }}>{depositInstructions?.bankName ?? '—'}</strong>
                                        </div>
                                        <div style={{ fontSize: '0.875rem', color: 'rgba(184, 193, 204, 0.9)' }}>
                                            {t('wallet.userReference', 'Kullanıcı Referansı')}:{' '}
                                            <strong style={{ color: 'inherit' }}>{depositInstructions?.userReferenceCode ?? '—'}</strong>
                                        </div>
                                    </div>

                                    <label style={{ fontSize: '0.875rem' }}>
                                        {t('wallet.sourceBankOptional', 'Kaynak Banka (opsiyonel)')}
                                        <select
                                            value={sourceBankName}
                                            onChange={(e) => setSourceBankName(e.target.value)}
                                            className="wallet-input"
                                        >
                                            <option value="">{t('wallet.selectSourceBank', 'Banka seçin')}</option>
                                            {SOURCE_BANK_OPTIONS.map((bank) => (
                                                <option key={bank} value={bank}>
                                                    {bank}
                                                </option>
                                            ))}
                                        </select>
                                    </label>

                                    <div>
                                        <div style={{ fontSize: '0.875rem', marginBottom: 8 }}>{t('wallet.receipt', 'Dekont')} *</div>
                                        <div
                                            className={`wallet-dropzone ${dropActive ? 'wallet-dropzone--active' : ''}`}
                                            onClick={() => receiptInputRef.current?.click()}
                                            onDragEnter={(e) => {
                                                e.preventDefault();
                                                setDropActive(true);
                                            }}
                                            onDragOver={(e) => {
                                                e.preventDefault();
                                                setDropActive(true);
                                            }}
                                            onDragLeave={() => setDropActive(false)}
                                            onDrop={(e) => {
                                                e.preventDefault();
                                                setDropActive(false);
                                                const file = e.dataTransfer.files?.[0];
                                                if (file) void uploadReceipt(file);
                                            }}
                                        >
                                            <input
                                                ref={receiptInputRef}
                                                type="file"
                                                accept=".pdf,.jpg,.jpeg,.png"
                                                onChange={(e) => {
                                                    const file = e.target.files?.[0];
                                                    if (file) void uploadReceipt(file);
                                                }}
                                            />
                                            <div style={{ fontWeight: 700, marginBottom: 4 }}>
                                                {t('wallet.dropzoneTitle', 'Dekontu sürükleyip bırakın')}
                                            </div>
                                            <div style={{ fontSize: '0.8125rem', color: 'rgba(184, 193, 204, 0.85)' }}>
                                                {t('wallet.dropzoneHint', 'veya tıklayarak seçin · PDF, JPG, PNG')}
                                            </div>
                                        </div>
                                        <div style={{ marginTop: 8, fontSize: '0.8125rem', color: 'rgba(184, 193, 204, 0.85)' }}>
                                            {uploadingReceipt
                                                ? t('wallet.uploadingReceipt', 'Dekont yükleniyor...')
                                                : receiptFileName
                                                  ? `${t('wallet.receiptUploadedPrefix', 'Yüklendi')}: ${receiptFileName}`
                                                  : t('wallet.receiptNotUploaded', 'Henüz dekont yüklenmedi')}
                                        </div>
                                        {receiptFileId && (
                                            <button
                                                type="button"
                                                onClick={() => void openReceipt(receiptFileId, receiptFileUrl)}
                                                className="wallet-cta wallet-cta--withdraw"
                                                style={{ marginTop: 10, width: '100%' }}
                                            >
                                                {t('wallet.viewUploadedReceipt', 'Yüklenen dekontu görüntüle')}
                                            </button>
                                        )}
                                    </div>
                                </>
                            ) : (
                                <>
                                    <label style={{ fontSize: '0.875rem' }}>
                                        {t('wallet.recipientIbanShort', 'Alıcı IBAN')}
                                        <input
                                            value={destinationIban}
                                            onChange={(e) => setDestinationIban(e.target.value)}
                                            className="wallet-input"
                                            placeholder="TR..."
                                            required
                                        />
                                    </label>

                                    <label style={{ fontSize: '0.875rem' }}>
                                        {t('wallet.recipientNameFormLabel', 'Alıcı Ad Soyad')}
                                        <input
                                            value={destinationAccountHolder}
                                            onChange={(e) => setDestinationAccountHolder(e.target.value)}
                                            className="wallet-input"
                                            placeholder={t('wallet.recipientNamePlaceholder', 'Ad Soyad')}
                                            required
                                        />
                                    </label>

                                    <label style={{ fontSize: '0.875rem' }}>
                                        {t('wallet.bankNameFormLabel', 'Banka Adı')}
                                        <input
                                            value={destinationBankName}
                                            onChange={(e) => setDestinationBankName(e.target.value)}
                                            className="wallet-input"
                                            placeholder={t('wallet.bankNamePlaceholder', 'Banka')}
                                            required
                                        />
                                    </label>
                                </>
                            )}

                            <label style={{ fontSize: '0.875rem' }}>
                                {t('wallet.noteLabel', 'Not')}
                                <textarea
                                    value={requestNote}
                                    onChange={(e) => setRequestNote(e.target.value)}
                                    rows={3}
                                    className="wallet-input"
                                    style={{ resize: 'vertical', minHeight: 72 }}
                                    placeholder={t('wallet.notePlaceholder', 'Kısa açıklama...')}
                                />
                            </label>

                            {successMsg && (
                                <div style={{ color: '#39ff14', fontSize: '0.875rem', fontWeight: 600 }}>{successMsg}</div>
                            )}

                            <button type="submit" className="wallet-submit" disabled={submitting}>
                                {submitting ? t('common.submitting', 'Gönderiliyor...') : t('wallet.createRequest', 'Talep oluştur')}
                            </button>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
}
