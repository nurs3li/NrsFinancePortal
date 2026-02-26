import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

export function HomeRedirect() {
    const { isAuthenticated, role } = useAuth();
    const location = useLocation();
    const from = (location.state as { from?: { pathname?: string } })?.from?.pathname;

    if (!isAuthenticated) {
        return <Navigate to="/login" replace state={{ from: location }} />;
    }
    if (from && from !== '/' && from !== '/dashboard') {
        return <Navigate to={from} replace />;
    }
    if (role === 'ADMIN') return <Navigate to="/admin/metrics" replace />;
    if (role === 'FINANCE_MANAGER') return <Navigate to="/fm/tasks" replace />;
    return <Navigate to="/dashboard" replace />;
}