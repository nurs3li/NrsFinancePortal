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
import { MarketHeatmap } from './pages/MarketHeatmap';
import { Portfolio } from './pages/Portfolio';
import { Trade } from './pages/Trade';
import { Transactions } from './pages/Transactions';
import { Wallet } from './pages/Wallet';
import { Simulation } from './pages/Simulation';
import { FmTasks } from './pages/FmTasks';
import { FmRisk } from './pages/FmRisk';
import { FmTaskDetail } from './pages/FmTaskDetail';
import { FmFundRequests } from './pages/FmFundRequests';
import { AdminDashboard } from './pages/AdminDashboard';
import { AdminTasks } from './pages/AdminTasks';
import { AdminUsersAndAccounts } from './pages/AdminUsersAndAccounts';
import { Notifications } from './pages/Notifications';
import { AdminSuspicious } from './pages/AdminSuspicious';
import { AdminAudit } from './pages/AdminAudit';

// NOT: Onceden agir sayfalari React.lazy ile sarmaliyorduk. Ancak React 18 Suspense'in
// "pending sirasinda eski UI'yi tut" davranisi nedeniyle VIOP sekmesindeki yogun render
// commit'leri pending state'i takip ediyor ve Outlet asla yeni sayfaya gecmiyordu — URL
// degisse bile DOM eski Market sayfasinda kaliyordu (defaultPrevented=false ile teshis).
// Eager import'a donduk: bundle birazcik buyuyor ama navigation deterministik calisiyor.

function App() {
    return (
        <ThemeProvider>
            <LanguageProvider>
                <ErrorBoundary>
                    <AuthProvider>
                        <QueryProvider>
                        <BrowserRouter>
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
                                    path="/trade"
                                    element={
                                        <ProtectedRoute>
                                            <Trade />
                                        </ProtectedRoute>
                                    }
                                />
                                <Route
                                    path="/transactions"
                                    element={
                                        <ProtectedRoute>
                                            <Transactions />
                                        </ProtectedRoute>
                                    }
                                />
                                <Route
                                    path="/wallet"
                                    element={
                                        <ProtectedRoute>
                                            <Wallet />
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
                                <Route
                                    path="/fm/tasks"
                                    element={
                                        <RoleProtectedRoute
                                            allowedRoles={['FINANCE_MANAGER']}
                                        >
                                            <FmTasks />
                                        </RoleProtectedRoute>
                                    }
                                />
                                <Route
                                    path="/fm/tasks/:id"
                                    element={
                                        <RoleProtectedRoute
                                            allowedRoles={['FINANCE_MANAGER']}
                                        >
                                            <FmTaskDetail />
                                        </RoleProtectedRoute>
                                    }
                                />
                                <Route
                                    path="/fm/risk"
                                    element={
                                        <RoleProtectedRoute
                                            allowedRoles={['FINANCE_MANAGER']}
                                        >
                                            <FmRisk />
                                        </RoleProtectedRoute>
                                    }
                                />
                                <Route
                                    path="/fm/fund-requests"
                                    element={
                                        <RoleProtectedRoute
                                            allowedRoles={['FINANCE_MANAGER']}
                                        >
                                            <FmFundRequests />
                                        </RoleProtectedRoute>
                                    }
                                />
                                <Route
                                    path="/admin"
                                    element={
                                        <RoleProtectedRoute allowedRoles={['ADMIN']}>
                                            <AdminDashboard />
                                        </RoleProtectedRoute>
                                    }
                                />
                                <Route
                                    path="/notifications"
                                    element={
                                        <ProtectedRoute>
                                            <Notifications />
                                        </ProtectedRoute>
                                    }
                                />
                                <Route
                                    path="/operasyon/suspicious"
                                    element={
                                        <RoleProtectedRoute
                                            allowedRoles={['FINANCE_MANAGER']}
                                        >
                                            <AdminSuspicious />
                                        </RoleProtectedRoute>
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
                                    element={<Navigate to="/admin" replace />}
                                />
                                <Route
                                    path="/admin/audit"
                                    element={
                                        <RoleProtectedRoute allowedRoles={['ADMIN']}>
                                            <AdminAudit />
                                        </RoleProtectedRoute>
                                    }
                                />
                                <Route
                                    path="/admin/metrics"
                                    element={<Navigate to="/admin" replace />}
                                />
                                <Route
                                    path="/admin/tasks"
                                    element={
                                        <RoleProtectedRoute allowedRoles={['ADMIN']}>
                                            <AdminTasks />
                                        </RoleProtectedRoute>
                                    }
                                />
                            </Route>
                            <Route path="*" element={<Navigate to="/" replace />} />
                        </Routes>
                        </BrowserRouter>
                        </QueryProvider>
                    </AuthProvider>
                </ErrorBoundary>
            </LanguageProvider>
        </ThemeProvider>
    );
}

export default App;