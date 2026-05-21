import keycloak from '../auth/keycloak';

export function readKeycloakDisplayName(): string | null {
    const parsed = keycloak.tokenParsed as Record<string, unknown> | undefined;
    if (!parsed) return null;

    const name = parsed.name;
    if (typeof name === 'string' && name.trim()) return name.trim();

    const given = parsed.given_name;
    const family = parsed.family_name;
    const parts = [given, family]
        .filter((v): v is string => typeof v === 'string' && v.trim().length > 0)
        .map((v) => v.trim());
    if (parts.length > 0) return parts.join(' ');

    const preferred = parsed.preferred_username;
    if (typeof preferred === 'string' && preferred.trim()) return preferred.trim();

    return null;
}

export function openKeycloakAccountConsole(): void {
    const url = keycloak.createAccountUrl?.();
    if (url) {
        window.location.href = url;
        return;
    }
    if (typeof keycloak.accountManagement === 'function') {
        void keycloak.accountManagement();
    }
}
