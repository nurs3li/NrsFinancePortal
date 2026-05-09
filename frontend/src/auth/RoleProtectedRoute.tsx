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
        return <Navigate to="/" state={{ from: location }} replace />;
    }

    if (role == null || !allowedRoles.includes(role)) {
        if (role === 'USER') return <Navigate to="/dashboard" replace />;
        if (role === 'FINANCE_MANAGER') return <Navigate to="/fm/tasks" replace />;
        if (role === 'ADMIN') return <Navigate to="/admin" replace />;
        return <Navigate to="/" replace />;
    }

    return <>{children}</>;
}