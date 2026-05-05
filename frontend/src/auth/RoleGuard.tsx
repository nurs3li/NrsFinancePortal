import type { ReactNode } from 'react';
import { useAuth } from './AuthContext';
import type { UserRole } from './AuthContext';

type RoleGuardProps = {
    /** En az biri yeterliyse children gösterilir */
    roles: UserRole[];
    children: ReactNode;
    fallback?: ReactNode;
};

export function RoleGuard({ roles, children, fallback = null }: RoleGuardProps) {
    const { role } = useAuth();
    if (!role || !roles.includes(role)) {
        return <>{fallback}</>;
    }
    return <>{children}</>;
}
