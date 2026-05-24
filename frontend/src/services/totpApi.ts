import { financeClient } from '../api/client';

export type TotpStatus = {
    enabled: boolean;
    setupPending: boolean;
};

export type TotpSetup = {
    secret: string;
    otpauthUrl: string;
    issuer: string;
    accountName: string;
};

export async function fetchTotpStatus(): Promise<TotpStatus> {
    const res = await financeClient.get('/api/users/me/totp');
    const raw = res.data?.data ?? res.data;
    return {
        enabled: Boolean(raw?.enabled),
        setupPending: Boolean(raw?.setupPending),
    };
}

export async function beginTotpSetup(): Promise<TotpSetup> {
    const res = await financeClient.post('/api/users/me/totp/setup');
    const raw = res.data?.data ?? res.data;
    return {
        secret: String(raw?.secret ?? ''),
        otpauthUrl: String(raw?.otpauthUrl ?? ''),
        issuer: String(raw?.issuer ?? 'NRS Finance'),
        accountName: String(raw?.accountName ?? ''),
    };
}

export async function confirmTotpSetup(code: string): Promise<string> {
    const res = await financeClient.post('/api/users/me/totp/confirm', { code });
    return res.data?.data ?? 'İki aşamalı doğrulama etkinleştirildi.';
}

export async function cancelTotpSetup(): Promise<void> {
    await financeClient.post('/api/users/me/totp/cancel');
}

export async function disableTotp(): Promise<string> {
    const res = await financeClient.delete('/api/users/me/totp');
    return res.data?.data ?? 'İki aşamalı doğrulama devre dışı.';
}
