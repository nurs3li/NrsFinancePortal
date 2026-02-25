import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './auth/AuthContext';
import { ThemeProvider } from './theme/ThemeContext';
import { ProtectedRoute } from './auth/ProtectedRoute';
import { RoleProtectedRoute } from './auth/RoleProtectedRoute';
import { Layout } from './components/Layout';
import { Login } from './pages/Login';
import { Dashboard } from './pages/Dashboard';
import { News } from './pages/News';
import { Market } from './pages/Market';
import { Portfolio } from './pages/Portfolio';
import { Trade } from './pages/Trade';
import { Profile } from './pages/Profile';
import { Transactions } from './pages/Transactions';
import { FmTasks } from './pages/FmTasks';
import { FmRisk } from './pages/FmRisk';
import { FmTaskDetail } from './pages/FmTaskDetail';
import { AdminDashboard } from './pages/AdminDashboard';
import { AdminTasks } from './pages/AdminTasks';
import { AdminUsers } from './pages/AdminUsers';
import { Notifications } from './pages/Notifications';
import { AdminSuspicious } from './pages/AdminSuspicious';
import { AdminAccounts } from './pages/AdminAccounts';
import { AdminSettings } from './pages/AdminSettings';
import { AdminAudit } from './pages/AdminAudit';


function App() {
    return (
        <ThemeProvider>
            <AuthProvider>
                <BrowserRouter>
                    <Routes>
                        <Route path="/" element={<Navigate to="/dashboard" replace />} />
                        <Route path="/login" element={<Login />} />
                        <Route element={<Layout />}>
                            <Route path="/dashboard" element={<ProtectedRoute><Dashboard /></ProtectedRoute>} />
                            <Route path="/news" element={<ProtectedRoute><News /></ProtectedRoute>} />
                            <Route path="/market" element={<ProtectedRoute><Market /></ProtectedRoute>} />
                            <Route path="/portfolio" element={<ProtectedRoute><Portfolio /></ProtectedRoute>} />
                            <Route path="/trade" element={<ProtectedRoute><Trade /></ProtectedRoute>} />
                            <Route path="/profile" element={<ProtectedRoute><Profile /></ProtectedRoute>} />
                            <Route path="/transactions" element={<ProtectedRoute><Transactions /></ProtectedRoute>} />
                            <Route path="/fm/tasks" element={<RoleProtectedRoute allowedRoles={['FINANCE_MANAGER', 'ADMIN']}><FmTasks /></RoleProtectedRoute>} />
                            <Route path="/fm/tasks/:id" element={<RoleProtectedRoute allowedRoles={['FINANCE_MANAGER', 'ADMIN']}><FmTaskDetail /></RoleProtectedRoute>} />
                            <Route path="/fm/risk" element={<RoleProtectedRoute allowedRoles={['FINANCE_MANAGER', 'ADMIN']}><FmRisk /></RoleProtectedRoute>} />
                            <Route path="/admin" element={<RoleProtectedRoute allowedRoles={['ADMIN']}><AdminDashboard /></RoleProtectedRoute>} />
                            <Route path="/admin/tasks" element={<RoleProtectedRoute allowedRoles={['ADMIN']}><AdminTasks /></RoleProtectedRoute>} />
                            <Route path="/admin/users" element={<RoleProtectedRoute allowedRoles={['ADMIN']}><AdminUsers /></RoleProtectedRoute>} />
                            <Route path="/notifications" element={<ProtectedRoute><Notifications /></ProtectedRoute>} />
                            <Route path="/operasyon/suspicious" element={<RoleProtectedRoute allowedRoles={['FINANCE_MANAGER', 'ADMIN']}><AdminSuspicious /></RoleProtectedRoute>} />
                            <Route path="/admin/accounts" element={<RoleProtectedRoute allowedRoles={['ADMIN']}><AdminAccounts /></RoleProtectedRoute>} />
                            <Route path="/admin/settings" element={<RoleProtectedRoute allowedRoles={['ADMIN']}><AdminSettings /></RoleProtectedRoute>} />
                            <Route path="/admin/audit" element={<RoleProtectedRoute allowedRoles={['ADMIN']}><AdminAudit /></RoleProtectedRoute>} />
                        </Route>
                        <Route path="*" element={<Navigate to="/dashboard" replace />} />
                    </Routes>
                </BrowserRouter>
            </AuthProvider>
        </ThemeProvider>
    );
}

export default App;