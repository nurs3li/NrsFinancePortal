import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from './AuthContext';
import type { UserRole } from './AuthContext';

export function RoleProtectedRoute({
                                       children,
                                       allowedRoles,
                                   }: {
    children: React.ReactNode;
    allowedRoles: UserRole[];
}) {
    const { isAuthenticated, ready, role } = useAuth();
    const location = useLocation();

    if (!ready) {
        return <div style={{ padding: 20 }}>Yükleniyor...</div>;
    }

    if (!isAuthenticated) {
        return <Navigate to="/login" state={{ from: location }} replace />;
    }

    if (role == null || !allowedRoles.includes(role)) {
        return <Navigate to="/dashboard" replace />;
    }

    return <>{children}</>;
}