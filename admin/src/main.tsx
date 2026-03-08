import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { Toaster } from 'react-hot-toast'
import './index.css'
import App from './App.jsx'
import * as Sentry from "@sentry/react";

Sentry.init({
    dsn: import.meta.env.VITE_SENTRY_DSN || "https://public@sentry.example.com/1",
    integrations: [
        Sentry.browserTracingIntegration(),
        Sentry.replayIntegration(),
    ],
    tracesSampleRate: 1.0,
    replaysSessionSampleRate: 0.1,
    replaysOnErrorSampleRate: 1.0,
});

createRoot(document.getElementById('root')).render(
    <StrictMode>
        <App />
        <Toaster position="top-right" />
    </StrictMode>,
)
