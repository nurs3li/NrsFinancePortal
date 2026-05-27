import axios from 'axios';

export type ApiErrorDetail = {
    code?: string;
    message?: string;
    error?: string;
};

export type ApiEnvelope<T> = {
    success?: boolean;
    data?: T;
    errors?: ApiErrorDetail | null;
    meta?: unknown;
};

function envelopeErrors(data: unknown): ApiErrorDetail | undefined {
    if (!data || typeof data !== 'object') return undefined;
    const errors = (data as ApiEnvelope<unknown>).errors;
    if (errors && typeof errors === 'object') {
        return errors as ApiErrorDetail;
    }
    return undefined;
}

/**
 * Standart API hata zarfından kullanıcıya gösterilecek mesaj ve kodu okur.
 */
export function readApiError(err: unknown): { code?: string; message: string } {
    if (axios.isAxiosError(err)) {
        const data = err.response?.data;
        const e = envelopeErrors(data);
        const legacyMessage =
            data && typeof data === 'object' && 'message' in data
                ? (data as { message?: string }).message
                : undefined;
        const msg = e?.message ?? e?.error ?? legacyMessage ?? err.message ?? 'Request failed';
        return { code: typeof e?.code === 'string' ? e.code : undefined, message: msg };
    }
    return { message: err instanceof Error ? err.message : 'Request failed' };
}

/**
 * Başarılı envelope gövdesinden {@code data} alanını çıkarır.
 */
export function unwrapApiSuccess<T>(data: unknown): T {
    if (!data || typeof data !== 'object') {
        throw new Error('Unexpected API response shape');
    }
    const body = data as ApiEnvelope<T>;
    if (body.success === true && body.data !== undefined) {
        return body.data as T;
    }
    throw new Error('Unexpected API response shape');
}
