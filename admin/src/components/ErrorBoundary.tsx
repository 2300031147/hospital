import React from 'react';

class ErrorBoundary extends React.Component<any, any> {
    constructor(props) {
        super(props);
        this.state = { hasError: false, error: null };
    }

    static getDerivedStateFromError(error) {
        // Update state so the next render will show the fallback UI.
        return { hasError: true, error };
    }

    componentDidCatch(error, errorInfo) {
        // Log the error to console (and later to Sentry)
        console.error("ErrorBoundary caught an error:", error, errorInfo);
    }

    render() {
        if (this.state.hasError) {
            // Render a fallback UI
            return (
                <div style={{
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    justifyContent: 'center',
                    height: '100vh',
                    backgroundColor: 'var(--color-bg)',
                    color: 'var(--color-text)',
                    fontFamily: 'system-ui, sans-serif'
                }}>
                    <div style={{
                        backgroundColor: 'var(--color-surface)',
                        padding: '2rem 3rem',
                        borderRadius: '12px',
                        boxShadow: '0 8px 24px rgba(0,0,0,0.1)',
                        textAlign: 'center',
                        maxWidth: '500px'
                    }}>
                        <div style={{
                            width: '64px',
                            height: '64px',
                            borderRadius: '32px',
                            backgroundColor: 'rgba(239, 68, 68, 0.1)',
                            color: 'var(--color-critical, #ef4444)',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            fontSize: '32px',
                            margin: '0 auto 1.5rem auto'
                        }}>
                            ⚠️
                        </div>
                        <h2 style={{ margin: '0 0 1rem 0', fontWeight: '600' }}>Dashboard Encountered an Error</h2>
                        <p style={{ color: 'var(--color-text-muted)', marginBottom: '2rem', lineHeight: '1.5' }}>
                            A component crashed, but the rest of the application is isolated. Please reload to resume dispatch operations.
                        </p>
                        <button
                            onClick={() => window.location.reload()}
                            style={{
                                backgroundColor: 'var(--color-primary)',
                                color: 'white',
                                border: 'none',
                                padding: '0.75rem 1.5rem',
                                marginTop: '1.5rem',
                                borderRadius: '6px',
                                fontWeight: '500',
                                cursor: 'pointer',
                                transition: 'background-color 0.2s'
                            }}
                            onMouseOut={(e) => (e.target as HTMLElement).style.opacity = '1'}
                        >
                            Reload Dashboard
                        </button>
                    </div>
                </div>
            );
        }

        return this.props.children;
    }
}

export default ErrorBoundary;
