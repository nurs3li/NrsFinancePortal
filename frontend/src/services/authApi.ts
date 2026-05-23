import { financeClient } from '../api/client';
import type { PortalTokens } from '../auth/applyKeycloakTokens';

export type LoginRequest = {
    usernameOrEmail: string;
    password: string;
    otp?: string;
    rememberMe: boolean;
};

export type LoginResponse = {
    otpRequired: boolean;
    message?: string | null;
    accessToken?: string | null;
    refreshToken?: string | null;
    expiresIn?: number | null;
    refreshExpiresIn?: number | null;
    userId?: number | null;
    username?: string | null;
    email?: string | null;
    role?: string | null;
};

function unwrapData<T>(res: { data?: { data?: T; success?: boolean } }): T | undefined {
    const root = res.data;
    if (root && typeof root === 'object' && 'data' in root && root.success === true) {
        return root.data as T;
    }
    return root as T | undefined;
}

export async function portalLogin(body: LoginRequest): Promise<LoginResponse> {
    const res = await financeClient.post('/api/public/login', body);
    return unwrapData<LoginResponse>(res) ?? { otpRequired: false };
}

export async function portalRefreshToken(refreshToken: string): Promise<LoginResponse> {
    const res = await financeClient.post('/api/public/token/refresh', { refreshToken });
    return unwrapData<LoginResponse>(res) ?? { otpRequired: false };
}

export function loginResponseToTokens(data: LoginResponse): PortalTokens | null {
    if (!data.accessToken) return null;
    return {
        accessToken: data.accessToken,
        refreshToken: data.refreshToken,
        expiresIn: data.expiresIn ?? undefined,
    };
}
