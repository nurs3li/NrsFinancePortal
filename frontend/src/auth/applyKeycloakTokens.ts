import keycloak from './keycloak';

export type PortalTokens = {
    accessToken: string;
    refreshToken?: string | null;
    expiresIn?: number | null;
};

function parseJwt(token: string): Record<string, unknown> | undefined {
    try {
        const payload = token.split('.')[1];
        if (!payload) return undefined;
        const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
        return JSON.parse(json) as Record<string, unknown>;
    } catch {
        return undefined;
    }
}

/** Keycloak redirect olmadan portal içi girişten gelen token'ları keycloak-js oturumuna yazar. */
export function applyKeycloakTokens(tokens: PortalTokens): void {
    const parsed = parseJwt(tokens.accessToken);
    keycloak.token = tokens.accessToken;
    keycloak.refreshToken = tokens.refreshToken ?? undefined;
    keycloak.tokenParsed = parsed;
    keycloak.refreshTokenParsed = tokens.refreshToken ? parseJwt(tokens.refreshToken) : undefined;
    keycloak.authenticated = true;
    if (tokens.expiresIn != null) {
        keycloak.tokenParsed = {
            ...(parsed ?? {}),
            exp: Math.floor(Date.now() / 1000) + tokens.expiresIn,
        };
    }
    keycloak.onAuthSuccess?.();
    keycloak.onAuthRefreshSuccess?.();
}
