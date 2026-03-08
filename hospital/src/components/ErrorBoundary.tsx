import React from 'react';

export default class ErrorBoundary extends React.Component<any, any> {
    state = { hasError: false, error: null };

    static getDerivedStateFromError(error) {
        return { hasError: true, error };
    }

    componentDidCatch(error, info) {
        console.error('Boundary caught:', error, info);
        // In production: send to Sentry
    }

    render() {
        if (this.state.hasError) {
            return (
                <div style={{
                    padding: 40, textAlign: 'center',
                    color: 'var(--color-critical)'
                }}>
                    <h2>System Error</h2>
                    <p>An unexpected error occurred.</p>
                    <button onClick={() => window.location.reload()}>
                        Reload
                    </button>
                </div>
            );
        }
        return this.props.children;
    }
}
