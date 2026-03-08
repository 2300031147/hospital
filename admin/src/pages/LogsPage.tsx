import { useState, useEffect, useCallback } from 'react';
import { getLogs } from '../services/api';

export default function LogsPage() {
    const [logs, setLogs] = useState([]);
    const [loading, setLoading] = useState(true);
    const [filter, setFilter] = useState('all');
    const [search, setSearch] = useState('');
    const [limit, setLimit] = useState(100);

    const fetchLogs = useCallback(async () => {
        try { setLogs(await getLogs(limit)); } catch (e) { console.error(e); }
        finally { setLoading(false); }
    }, [limit]);

    useEffect(() => { fetchLogs(); }, [fetchLogs]);


    const typeColors = {
        dispatch: { bg: 'var(--color-info-dim)', color: 'var(--color-info)', icon: 'D' },
        reroute: { bg: 'var(--color-warning-dim)', color: 'var(--color-warning)', icon: 'R' },
        handoff: { bg: 'var(--color-accent-dim)', color: 'var(--color-accent)', icon: 'H' },
        acceptance: { bg: 'var(--color-success-dim)', color: 'var(--color-success)', icon: 'A' },
        overload: { bg: 'var(--color-critical-dim)', color: 'var(--color-critical)', icon: '!' },
        bed_release: { bg: 'var(--color-success-dim)', color: 'var(--color-success)', icon: 'B' },
        login: { bg: 'var(--color-info-dim)', color: 'var(--color-info)', icon: 'L' },
    };

    const getTypeInfo = (log) => {
        const msg = (log.message || log.event || '').toLowerCase();
        if (msg.includes('dispatch') || msg.includes('routed')) return typeColors.dispatch;
        if (msg.includes('reroute')) return typeColors.reroute;
        if (msg.includes('handoff')) return typeColors.handoff;
        if (msg.includes('accept')) return typeColors.acceptance;
        if (msg.includes('overload')) return typeColors.overload;
        if (msg.includes('bed') || msg.includes('release')) return typeColors.bed_release;
        if (msg.includes('login')) return typeColors.login;
        return { bg: 'var(--color-bg-tertiary)', color: 'var(--color-text-muted)', icon: '-' };
    };

    const filteredLogs = logs.filter(log => {
        const msg = (log.message || log.event || '').toLowerCase();
        if (filter !== 'all' && !msg.includes(filter)) return false;
        if (search && !msg.includes(search.toLowerCase())) return false;
        return true;
    });

    if (loading) return <div className="page fade-in"><p style={{ color: 'var(--color-text-muted)' }}>Loading system logs...</p></div>;

    return (
        <div className="page fade-in">
            <div className="page-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <div>
                    <h1 className="page-title">System Logs</h1>
                    <p className="page-subtitle">Real-time audit trail of all system events — auto-refreshes every 10s</p>
                </div>
                <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                    <span className="badge badge-info">{filteredLogs.length} / {logs.length}</span>
                    <button className="btn btn-secondary btn-sm" onClick={fetchLogs}>Refresh</button>
                </div>
            </div>

            {/* Filters */}
            <div className="card" style={{ marginBottom: 16, padding: 14 }}>
                <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', alignItems: 'center' }}>
                    <input className="input" placeholder="Search logs..." value={search}
                        onChange={e => setSearch(e.target.value)}
                        style={{ maxWidth: 280, padding: '8px 12px', fontSize: 13 }} />

                    {['all', 'dispatch', 'reroute', 'handoff', 'accept', 'overload', 'bed'].map(f => (
                        <button key={f} className={`btn btn-sm ${filter === f ? 'btn-primary' : 'btn-secondary'}`}
                            onClick={() => setFilter(f)}>
                            {f === 'all' ? 'All' : f.charAt(0).toUpperCase() + f.slice(1)}
                        </button>
                    ))}

                    <select className="select" value={limit} onChange={e => setLimit(Number(e.target.value))}
                        style={{ width: 100, padding: '6px 8px', fontSize: 12 }}>
                        <option value={50}>Last 50</option>
                        <option value={100}>Last 100</option>
                        <option value={200}>Last 200</option>
                    </select>
                </div>
            </div>

            {/* Log Entries */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                {filteredLogs.length === 0 && (
                    <div className="card" style={{ textAlign: 'center', padding: 40 }}>
                        <div style={{ fontSize: 16, marginBottom: 8, color: 'var(--color-text-muted)' }}>No results</div>
                        <p style={{ color: 'var(--color-text-muted)', fontSize: 13 }}>No logs match your filters</p>
                    </div>
                )}

                {filteredLogs.map((log, i) => {
                    const info = getTypeInfo(log);
                    return (
                        <div key={log.id || i} style={{
                            padding: '10px 16px', borderRadius: 'var(--radius-sm)',
                            background: 'var(--color-bg-secondary)', border: '1px solid var(--color-border)',
                            display: 'flex', alignItems: 'center', gap: 12,
                        }}>
                            <span style={{
                                width: 32, height: 32, borderRadius: 8,
                                background: info.bg, display: 'flex', alignItems: 'center', justifyContent: 'center',
                                fontSize: 14, flexShrink: 0,
                            }}>
                                {info.icon}
                            </span>
                            <div style={{ flex: 1, minWidth: 0 }}>
                                <div style={{ fontSize: 13, color: 'var(--color-text-primary)', fontWeight: 500, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                                    {log.message || log.event || 'System event'}
                                </div>
                                {log.details && (
                                    <div style={{ fontSize: 11, color: 'var(--color-text-muted)', marginTop: 2, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                                        {typeof log.details === 'string' ? log.details : JSON.stringify(log.details)}
                                    </div>
                                )}
                            </div>
                            <div style={{ fontSize: 10, color: 'var(--color-text-muted)', fontFamily: 'var(--font-mono)', whiteSpace: 'nowrap', flexShrink: 0 }}>
                                {log.timestamp || log.created_at || ''}
                            </div>
                        </div>
                    );
                })}
            </div>
        </div>
    );
}
