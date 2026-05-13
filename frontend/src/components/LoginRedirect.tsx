import { useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth, type UserRole } from '../auth/AuthContext';
import '../pages/LandingPage.css';

function destinationForUser(role: UserRole | null, fromPathname: string | undefined): string {
    if (fromPathname && fromPathname !== '/' && fromPathname !== '/dashboard') {
        return fromPathname;
    }
    if (role === 'ADMIN') return '/admin';
    if (role === 'USER') return '/dashboard';
    return '/dashboard';
}

/**
 * Keeps /login as a stable Keycloak redirect URI while sending users to the landing experience.
 * Waits for Keycloak init so OAuth query/hash on this URL is processed before moving to /.
 */
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
        const qs = location.search ?? '';
        const hash = location.hash ?? '';
        navigate(`/${qs}${hash}`, { replace: true });
    }, [ready, isAuthenticated, role, from, location.search, location.hash, navigate]);

    return (
        <div className="landing-loading-screen">
            {!ready ? 'Yükleniyor…' : 'Yönlendiriliyor…'}
        </div>
    );
}
