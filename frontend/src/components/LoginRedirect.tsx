import { useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth, type UserRole } from '../auth/AuthContext';
import '../pages/LandingPage.css';

function destinationForUser(role: UserRole | null, fromPathname: string | undefined): string {
    if (fromPathname && fromPathname !== '/' && fromPathname !== '/dashboard') {
        return fromPathname;
    }
    if (role === 'ADMIN') return '/market';
    if (role === 'USER') return '/dashboard';
    return '/dashboard';
}

/** Eski /login URL → ana sayfa giriş paneli (Keycloak UI yok). */
export function LoginRedirect() {
    const { ready, isAuthenticated, role } = useAuth();
    const navigate = useNavigate();
    const location = useLocation();
    const from = (location.state as { from?: { pathname?: string } })?.from?.pathname;

    useEffect(() => {
        if (!ready) return;
        if (isAuthenticated) {
            navigate(destinationForUser(role, from), { replace: true });
            return;
        }
        const params = new URLSearchParams(location.search);
        if (!params.has('signin')) {
            params.set('signin', '1');
        }
        const qs = params.toString() ? `?${params.toString()}` : '?signin=1';
        navigate(`/${qs}`, { replace: true });
    }, [ready, isAuthenticated, role, from, location.search, navigate]);

    return (
        <div className="landing-loading-screen">
            {!ready ? 'Yükleniyor…' : 'Yönlendiriliyor…'}
        </div>
    );
}
