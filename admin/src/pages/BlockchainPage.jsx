import { useState, useEffect } from 'react';
import { getBlockchain, verifyBlockchain } from '../services/api';

export default function BlockchainPage() {
    const [chain, setChain] = useState([]);
    const [loading, setLoading] = useState(true);
    const [verifyResult, setVerifyResult] = useState(null);
    const [verifying, setVerifying] = useState(false);
    const [expandedIdx, setExpandedIdx] = useState(null);

    useEffect(() => {
        (async () => {
            try { setChain(await getBlockchain(50)); } catch (e) { console.error(e); }
            finally { setLoading(false); }
        })();
    }, []);

    const handleVerify = async () => {
        setVerifying(true);
        try {
            const res = await verifyBlockchain();
            setVerifyResult(res);
        } catch (e) { setVerifyResult({ valid: false, error: e.message }); }
        finally { setVerifying(false); }
    };

    if (loading) return <div className="page fade-in"><p style={{ color: 'var(--color-text-muted)' }}>Loading blockchain...</p></div>;

    return (
        <div className="page fade-in">
            <div className="page-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <div>
                    <h1 className="page-title">Blockchain Audit Trail</h1>
                    <p className="page-subtitle">Immutable record of all routing decisions and system events</p>
                </div>
                <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                    <button className="btn btn-secondary" onClick={handleVerify} disabled={verifying}>
                        {verifying ? 'Verifying...' : 'Verify Chain Integrity'}
                    </button>
                    {verifyResult && (
                        <span className={`badge ${verifyResult.valid ? 'badge-success' : 'badge-critical'}`}>
                            {verifyResult.valid ? '✓ VALID' : '✗ INVALID'}
                        </span>
                    )}
                </div>
            </div>

            <div className="card">
                <div className="card-header">
                    <span className="card-title">Chain ({chain.length} blocks)</span>
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                    {chain.map((block, i) => {
                        let blockData;
                        try { blockData = typeof block.data === 'string' ? JSON.parse(block.data) : block.data; } catch { blockData = block.data; }
                        const isExpanded = expandedIdx === block.idx;

                        return (
                            <div key={block.idx} style={{
                                padding: '12px 16px', borderRadius: 'var(--radius-sm)',
                                background: i === 0 ? 'var(--color-accent-dim)' : 'var(--color-bg-tertiary)',
                                border: `1px solid ${i === 0 ? 'rgba(0,212,170,0.2)' : 'var(--color-border)'}`,
                                cursor: 'pointer',
                            }} onClick={() => setExpandedIdx(isExpanded ? null : block.idx)}>
                                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                                        <span style={{
                                            width: 28, height: 28, borderRadius: 6,
                                            background: 'var(--color-bg-elevated)', border: '1px solid var(--color-border)',
                                            display: 'flex', alignItems: 'center', justifyContent: 'center',
                                            fontSize: 11, fontWeight: 700, color: 'var(--color-text-secondary)', fontFamily: 'var(--font-mono)',
                                        }}>
                                            #{block.idx}
                                        </span>
                                        <div>
                                            <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--color-text-primary)' }}>
                                                {blockData?.event || 'Block Entry'}
                                            </div>
                                            <div style={{ fontSize: 10, color: 'var(--color-text-muted)', fontFamily: 'var(--font-mono)' }}>
                                                {block.timestamp}
                                            </div>
                                        </div>
                                    </div>
                                    <div style={{ textAlign: 'right' }}>
                                        <div style={{ fontSize: 9, color: 'var(--color-text-muted)', fontFamily: 'var(--font-mono)' }}>
                                            {block.hash?.substring(0, 16)}...
                                        </div>
                                        <div style={{ fontSize: 10, color: 'var(--color-text-disabled)' }}>
                                            {isExpanded ? '▲ collapse' : '▼ expand'}
                                        </div>
                                    </div>
                                </div>

                                {isExpanded && (
                                    <div style={{ marginTop: 12, padding: 12, background: 'var(--color-bg-secondary)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--color-border)' }}>
                                        <div style={{ fontSize: 10, color: 'var(--color-text-muted)', marginBottom: 4 }}>PREV HASH</div>
                                        <div style={{ fontSize: 10, fontFamily: 'var(--font-mono)', color: 'var(--color-text-secondary)', wordBreak: 'break-all', marginBottom: 8 }}>
                                            {block.prev_hash}
                                        </div>
                                        <div style={{ fontSize: 10, color: 'var(--color-text-muted)', marginBottom: 4 }}>HASH</div>
                                        <div style={{ fontSize: 10, fontFamily: 'var(--font-mono)', color: 'var(--color-accent)', wordBreak: 'break-all', marginBottom: 8 }}>
                                            {block.hash}
                                        </div>
                                        <div style={{ fontSize: 10, color: 'var(--color-text-muted)', marginBottom: 4 }}>DATA</div>
                                        <pre style={{
                                            fontSize: 11, fontFamily: 'var(--font-mono)', color: 'var(--color-text-secondary)',
                                            background: 'var(--color-bg-primary)', padding: 10, borderRadius: 'var(--radius-sm)',
                                            overflow: 'auto', maxHeight: 200, whiteSpace: 'pre-wrap', wordBreak: 'break-word',
                                        }}>
                                            {JSON.stringify(blockData, null, 2)}
                                        </pre>
                                    </div>
                                )}
                            </div>
                        );
                    })}
                </div>
            </div>
        </div>
    );
}
