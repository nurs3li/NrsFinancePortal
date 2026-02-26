import React from 'react';

type Props = { children: React.ReactNode };

type State = { hasError: boolean; error: Error | null };

export class ErrorBoundary extends React.Component<Props, State> {
    constructor(props: Props) {
        super(props);
        this.state = { hasError: false, error: null };
    }

    static getDerivedStateFromError(error: Error): State {
        return { hasError: true, error };
    }

    componentDidCatch(error: Error, errorInfo: React.ErrorInfo) {
        console.error('ErrorBoundary caught:', error, errorInfo);
    }

    render() {
        if (this.state.hasError && this.state.error) {
            return (
                <div
                    style={{
                        minHeight: '100vh',
                        display: 'flex',
                        flexDirection: 'column',
                        alignItems: 'center',
                        justifyContent: 'center',
                        padding: 24,
                        background: '#1a1a2e',
                        color: '#eee',
                        fontFamily: 'system-ui, sans-serif',
                    }}
                >
                    <h1 style={{ fontSize: '1.5rem', marginBottom: 8 }}>Bir hata oluştu</h1>
                    <p style={{ color: '#aaa', fontSize: '0.875rem', marginBottom: 16, maxWidth: 480, textAlign: 'center' }}>
                        Beklenmeyen bir sorun oluştu. Sayfayı yenileyerek tekrar deneyebilirsiniz.
                    </p>
                    <button
                        type="button"
                        onClick={() => window.location.reload()}
                        style={{
                            padding: '10px 20px',
                            fontSize: '0.9375rem',
                            background: '#4a90d9',
                            color: '#fff',
                            border: 'none',
                            borderRadius: 8,
                            cursor: 'pointer',
                        }}
                    >
                        Sayfayı yenile
                    </button>
                </div>
            );
        }
        return this.props.children;
    }
}