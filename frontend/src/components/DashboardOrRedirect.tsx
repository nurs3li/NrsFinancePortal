import { Navigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { Dashboard } from '../pages/Dashboard';

export function DashboardOrRedirect() {
    const { role } = useAuth();
    if (role === 'ADMIN') return <Navigate to="/admin" replace />;
    if (role === 'FINANCE_MANAGER') return <Navigate to="/fm/tasks" replace />;
    return <Dashboard />;
}