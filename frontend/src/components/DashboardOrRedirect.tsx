import { Navigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { Dashboard } from '../pages/Dashboard';

// NOT: Onceden Dashboard'i lazy yukluyorduk; Suspense pending stuck nedeniyle navigation
// kirildi (bkz App.tsx aciklamasi), o yuzden eager import'a donduk.

export function DashboardOrRedirect() {
    const { role } = useAuth();
    if (role === 'ADMIN') return <Navigate to="/admin" replace />;
    return <Dashboard />;
}