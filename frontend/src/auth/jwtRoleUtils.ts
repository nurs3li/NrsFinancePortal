import type { UserRole } from './AuthContext';

export function readRealmRolesFromTokenParsed(parsed: unknown): string[] {
    if (!parsed || typeof parsed !== 'object') return [];
    const ra = (parsed as { realm_access?: { roles?: unknown } }).realm_access?.roles;
    if (!Array.isArray(ra)) return [];
    return ra.filter((x): x is string => typeof x === 'string' && x.length > 0);
}

/** Keycloak realm_access.roles ile backend JwtIdentityReader aynı öncelik */
export function effectiveRoleFromRealmRoles(roles: string[]): UserRole | null {
    if (roles.includes('ADMIN')) return 'ADMIN';
    if (roles.includes('FINANCE_MANAGER')) return 'FINANCE_MANAGER';
    if (roles.includes('USER')) return 'USER';
    return null;
}
