import { readApiError } from '../api/envelope';
import { financeClient } from '../api/client';

export type UserMeDto = {
    id: number;
    username: string;
    email: string;
    firstName?: string | null;
    lastName?: string | null;
    role: string;
    loginSuspended: boolean;
    createdAt?: string | null;
};

function unwrap<T>(res: { data?: { data?: T } | T }): T {
    const body = res.data as { data?: T } | T;
    if (body && typeof body === 'object' && 'data' in body && (body as { data?: T }).data !== undefined) {
        return (body as { data: T }).data;
    }
    return body as T;
}

export function readProfileApiError(err: unknown, fallback: string): string {
    const raw = readApiError(err).message || fallback;

    if (raw.includes('Internal server error') && raw.includes('email/send')) {
        return 'E-posta servisi şu an yanıt vermiyor. Lütfen kısa süre sonra tekrar deneyin.';
    }
    if (raw.includes('Doğrulama maili gönderilemedi') && raw.length > 120) {
        const short = raw.split(':').pop()?.trim();
        if (short && short.length < 120 && !short.startsWith('{')) return short;
    }
    return raw.length > 200 ? fallback : raw;
}

export async function fetchCurrentUser(): Promise<UserMeDto> {
    const res = await financeClient.get('/api/users/me');
    return unwrap<UserMeDto>(res);
}

export async function updateUsername(username: string): Promise<UserMeDto> {
    const res = await financeClient.patch('/api/users/me/username', { username });
    return unwrap<UserMeDto>(res);
}

export async function updateProfileNames(firstName: string, lastName: string): Promise<UserMeDto> {
    const res = await financeClient.patch('/api/users/me/profile', { firstName, lastName });
    return unwrap<UserMeDto>(res);
}

export async function requestEmailChangeCode(email: string): Promise<string> {
    const res = await financeClient.post('/api/users/me/email/request-code', { email });
    return unwrap<string>(res);
}

export async function confirmEmailChange(email: string, code: string): Promise<UserMeDto> {
    const res = await financeClient.post('/api/users/me/email/confirm', { email, code });
    return unwrap<UserMeDto>(res);
}

export async function changePassword(currentPassword: string, newPassword: string): Promise<string> {
    const res = await financeClient.post('/api/users/me/password', { currentPassword, newPassword });
    return unwrap<string>(res);
}

export function formatUserFullName(user: UserMeDto | null | undefined): string | null {
    if (!user) return null;
    const parts = [user.firstName, user.lastName]
        .filter((v): v is string => typeof v === 'string' && v.trim().length > 0)
        .map((v) => v.trim());
    return parts.length > 0 ? parts.join(' ') : null;
}
