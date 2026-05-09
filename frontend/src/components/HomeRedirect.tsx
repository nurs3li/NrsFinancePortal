import { useEffect } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { LandingPage } from '../pages/LandingPage';
import '../pages/LandingPage.css';

export function HomeRedirect() {
    const { isAuthenticated, role, ready } = useAuth();
    const navigate = useNavigate();
    const location = useLocation();
    const from = (location.state as { from?: { pathname?: string } })?.from?.pathname;

    useEffect(() => {
        if (!ready || !isAuthenticated) return;
        if (from && from !== '/' && from !== '/dashboard') {
            navigate(from, { replace: true });
            return;
        }
        if (role === 'ADMIN') {
            navigate('/admin', { replace: true });
            return;
        }
        if (role === 'FINANCE_MANAGER') {
            navigate('/fm/tasks', { replace: true });
            return;
        }
        navigate('/dashboard', { replace: true });
    }, [ready, isAuthenticated, role, from, navigate]);

    if (!ready || isAuthenticated) {
        return (
            <div className="landing-loading-screen">
                {isAuthenticated ? 'Yönlendiriliyor…' : 'Yükleniyor…'}
            </div>
        );
    }
    return <LandingPage />;
}

