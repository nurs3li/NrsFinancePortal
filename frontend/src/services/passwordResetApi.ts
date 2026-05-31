import { financeClient } from '../api/client';

export type PasswordResetCompleteResponse = {
    username: string;
    message: string;
};

function unwrapData<T>(res: { data?: { data?: T; success?: boolean } }): T | undefined {
    const root = res.data;
    if (root && typeof root === 'object' && 'data' in root && root.success === true) {
        return root.data as T;
    }
    return root as T | undefined;
}

export async function requestPasswordResetCode(email: string): Promise<string> {
    const res = await financeClient.post('/api/public/password-reset/request-code', { email });
    return unwrapData<string>(res) ?? 'Doğrulama kodu gönderildi.';
}

export async function verifyPasswordResetCode(email: string, code: string): Promise<string> {
    const res = await financeClient.post('/api/public/password-reset/verify-code', { email, code });
    return unwrapData<string>(res) ?? 'Doğrulama kodu onaylandı.';
}

export async function completePasswordReset(
    email: string,
    newPassword: string,
    confirmPassword: string
): Promise<PasswordResetCompleteResponse> {
    const res = await financeClient.post('/api/public/password-reset/complete', {
        email,
        newPassword,
        confirmPassword,
    });
    return unwrapData<PasswordResetCompleteResponse>(res) ?? { username: email, message: 'Şifreniz güncellendi.' };
}
