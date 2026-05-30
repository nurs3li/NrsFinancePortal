import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './auth/AuthContext';
import { ThemeProvider } from './theme/ThemeContext';
import { LanguageProvider } from './i18n/LanguageContext';
import { QueryProvider } from './providers/QueryProvider';
import { ErrorBoundary } from './components/ErrorBoundary';
import { ProtectedRoute } from './auth/ProtectedRoute';
import { RoleProtectedRoute } from './auth/RoleProtectedRoute';
import { Layout } from './components/Layout';
import { DashboardOrRedirect } from './components/DashboardOrRedirect';
import { HomeRedirect } from './components/HomeRedirect';
import { LoginRedirect } from './components/LoginRedirect';
import { News } from './pages/News';
import { Market } from './pages/Market';
import { MarketMacroPage } from './pages/MarketMacroPage';
import { MarketHeatmap } from './pages/MarketHeatmap';
import { BankRatesPage } from './pages/BankRatesPage';
import { Portfolio } from './pages/Portfolio';
import { PortfolioAiAnalysis } from './pages/PortfolioAiAnalysis';
import { Simulation } from './pages/Simulation';
import { ViopBondAnalysis } from './pages/ViopBondAnalysis';
import { AdminUsersAndAccounts } from './pages/AdminUsersAndAccounts';
import { Notifications } from './pages/Notifications';
import { AdminAudit } from './pages/AdminAudit';
import { UserSettings } from './pages/UserSettings';

// NOT: Onceden agir sayfalari React.lazy ile sarmaliyorduk. Ancak React 18 Suspense'in
// "pending sirasinda eski UI'yi tut" davranisi nedeniyle VIOP sekmesindeki yogun render
// commit'leri pending state'i takip ediyor ve Outlet asla yeni sayfaya gecmiyordu — URL
// degisse bile DOM eski Market sayfasinda kaliyordu (defaultPrevented=false ile teshis).
// Eager import'a donduk: bundle birazcik buyuyor ama navigation deterministik calisiyor.
//
// React Router v7 varsayilan olarak tum navigasyonlari startTransition icine alir;
// Dashboard/Portfolio gibi agir sayfalarda eski icerik uzun sure ekranda kalir (URL
// degisir, Outlet guncellenmez gibi gorunur). unstable_useTransitions={false} ile senkron
// rota gecisi saglanir; Layout'ta Outlet key={pathname} ile eski sayfa unmount edilir.

function App() {
    return (
        <LanguageProvider>
            <ThemeProvider>
                <ErrorBoundary>
                    <AuthProvider>
                        <QueryProvider>
                        <BrowserRouter unstable_useTransitions={false}>
                        <Routes>
                            <Route path="/" element={<HomeRedirect />} />
                            <Route path="/login" element={<LoginRedirect />} />
                            <Route element={<Layout />}>
                                <Route
                                    path="/dashboard"
                                    element={
                                        <ProtectedRoute>
                                            <DashboardOrRedirect />
                                        </ProtectedRoute>
                                    }
                                />
                                <Route
                                    path="/news"
                                    element={
                                        <ProtectedRoute>
                                            <News />
                                        </ProtectedRoute>
                                    }
                                />
                                <Route
                                    path="/market"
                                    element={
                                        <ProtectedRoute>
                                            <Market />
                                        </ProtectedRoute>
                                    }
                                />
                                <Route
                                    path="/market/heatmap"
                                    element={
                                        <ProtectedRoute>
                                            <MarketHeatmap />
                                        </ProtectedRoute>
                                    }
                                />
                                <Route
                                    path="/market/macro"
                                    element={
                                        <ProtectedRoute>
                                            <MarketMacroPage />
                                        </ProtectedRoute>
                                    }
                                />
                                <Route
                                    path="/market/bank-rates"
                                    element={
                                        <ProtectedRoute>
                                            <BankRatesPage />
                                        </ProtectedRoute>
                                    }
                                />
                                <Route
                                    path="/market/advanced"
                                    element={
                                        <ProtectedRoute>
                                            <Navigate to="/market" replace />
                                        </ProtectedRoute>
                                    }
                                />
                                <Route
                                    path="/portfolio"
                                    element={
                                        <ProtectedRoute>
                                            <Portfolio />
                                        </ProtectedRoute>
                                    }
                                />
                                <Route
                                    path="/portfolio/ai-analysis"
                                    element={
                                        <ProtectedRoute>
                                            <PortfolioAiAnalysis />
                                        </ProtectedRoute>
                                    }
                                />
                                <Route
                                    path="/viop-bond-analysis"
                                    element={
                                        <ProtectedRoute>
                                            <ViopBondAnalysis />
                                        </ProtectedRoute>
                                    }
                                />
                                <Route
                                    path="/trade"
                                    element={
                                        <ProtectedRoute>
                                            <Navigate to="/portfolio" replace />
                                        </ProtectedRoute>
                                    }
                                />
                                <Route
                                    path="/transactions"
                                    element={
                                        <ProtectedRoute>
                                            <Navigate to="/portfolio" replace />
                                        </ProtectedRoute>
                                    }
                                />
                                <Route
                                    path="/wallet"
                                    element={
                                        <ProtectedRoute>
                                            <Navigate to="/portfolio" replace />
                                        </ProtectedRoute>
                                    }
                                />
                                <Route
                                    path="/simulation"
                                    element={
                                        <ProtectedRoute>
                                            <Simulation />
                                        </ProtectedRoute>
                                    }
                                />
                                <Route path="/admin" element={<Navigate to="/admin/users" replace />} />
                                <Route
                                    path="/notifications"
                                    element={
                                        <ProtectedRoute>
                                            <Notifications />
                                        </ProtectedRoute>
                                    }
                                />
                                <Route
                                    path="/settings"
                                    element={
                                        <ProtectedRoute>
                                            <UserSettings />
                                        </ProtectedRoute>
                                    }
                                />
                                <Route
                                    path="/admin/users"
                                    element={
                                        <RoleProtectedRoute allowedRoles={['ADMIN']}>
                                            <AdminUsersAndAccounts />
                                        </RoleProtectedRoute>
                                    }
                                />
                                <Route
                                    path="/admin/accounts"
                                    element={<Navigate to="/admin/users" replace />}
                                />
                                <Route
                                    path="/admin/settings"
                                    element={<Navigate to="/admin/users" replace />}
                                />
                                <Route
                                    path="/admin/audit"
                                    element={
                                        <RoleProtectedRoute allowedRoles={['ADMIN']}>
                                            <AdminAudit />
                                        </RoleProtectedRoute>
                                    }
                                />
                                <Route path="/admin/market-ops" element={<Navigate to="/admin/users" replace />} />
                            </Route>
                            <Route path="*" element={<Navigate to="/" replace />} />
                        </Routes>
                        </BrowserRouter>
                        </QueryProvider>
                    </AuthProvider>
                </ErrorBoundary>
            </ThemeProvider>
        </LanguageProvider>
    );
}

export default App;
