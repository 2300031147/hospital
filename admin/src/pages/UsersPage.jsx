import { useState, useEffect } from 'react';
import { getUsers, createUser, deleteUser, resetUserPassword } from '../services/api';

export default function UsersPage() {
    const [users, setUsers] = useState([]);
    const [loading, setLoading] = useState(true);
    const [showCreate, setShowCreate] = useState(false);
    const [form, setForm] = useState({ username: '', password: '', full_name: '', role: 'paramedic', ambulance_id: '', hospital_id: '' });
    const [resetId, setResetId] = useState(null);
    const [newPw, setNewPw] = useState('');

    const fetchUsers = async () => {
        try { setUsers(await getUsers()); } catch (e) { console.error(e); }
        finally { setLoading(false); }
    };

    useEffect(() => { fetchUsers(); }, []);

    const handleCreate = async () => {
        try {
            const data = { ...form };
            if (data.hospital_id === '') data.hospital_id = null; else data.hospital_id = Number(data.hospital_id);
            if (data.ambulance_id === '') data.ambulance_id = null;
            await createUser(data);
            setShowCreate(false);
            setForm({ username: '', password: '', full_name: '', role: 'paramedic', ambulance_id: '', hospital_id: '' });
            fetchUsers();
        } catch (e) { alert(e.message); }
    };

    const handleDelete = async (id, name) => {
        if (!window.confirm(`Delete user "${name}"?`)) return;
        try { await deleteUser(id); fetchUsers(); } catch (e) { alert(e.message); }
    };

    const handleReset = async (id) => {
        try { await resetUserPassword(id, newPw); setResetId(null); setNewPw(''); alert('Password updated'); } catch (e) { alert(e.message); }
    };

    const roleColors = {
        command_center: { bg: 'var(--color-critical-dim)', color: 'var(--color-critical)' },
        hospital_admin: { bg: 'var(--color-success-dim)', color: 'var(--color-success)' },
        paramedic: { bg: 'var(--color-info-dim)', color: 'var(--color-info)' },
    };

    if (loading) return <div className="page fade-in"><p style={{ color: 'var(--color-text-muted)' }}>Loading users...</p></div>;

    return (
        <div className="page fade-in">
            <div className="page-header" style={{ display: 'flex', justifyContent: 'space-between' }}>
                <div>
                    <h1 className="page-title">User Management</h1>
                    <p className="page-subtitle">{users.length} registered users</p>
                </div>
                <button className="btn btn-primary" onClick={() => setShowCreate(!showCreate)}>
                    {showCreate ? '✕ Cancel' : '+ Create User'}
                </button>
            </div>

            {showCreate && (
                <div className="card" style={{ marginBottom: 16 }}>
                    <div className="card-title" style={{ marginBottom: 12 }}>New User</div>
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
                        <div>
                            <label className="label">Username</label>
                            <input className="input" value={form.username} onChange={e => setForm(f => ({ ...f, username: e.target.value }))} placeholder="username" />
                        </div>
                        <div>
                            <label className="label">Password</label>
                            <input className="input" type="password" value={form.password} onChange={e => setForm(f => ({ ...f, password: e.target.value }))} placeholder="password" />
                        </div>
                        <div>
                            <label className="label">Full Name</label>
                            <input className="input" value={form.full_name} onChange={e => setForm(f => ({ ...f, full_name: e.target.value }))} placeholder="Full name" />
                        </div>
                        <div>
                            <label className="label">Role</label>
                            <select className="select" value={form.role} onChange={e => setForm(f => ({ ...f, role: e.target.value }))}>
                                <option value="paramedic">Paramedic</option>
                                <option value="hospital_admin">Hospital Admin</option>
                                <option value="command_center">Command Center</option>
                            </select>
                        </div>
                        <div>
                            <label className="label">Ambulance ID</label>
                            <input className="input" value={form.ambulance_id} onChange={e => setForm(f => ({ ...f, ambulance_id: e.target.value }))} placeholder="AMB-001" />
                        </div>
                        <div>
                            <label className="label">Hospital ID</label>
                            <input className="input" type="number" value={form.hospital_id} onChange={e => setForm(f => ({ ...f, hospital_id: e.target.value }))} placeholder="1" />
                        </div>
                    </div>
                    <button className="btn btn-primary" style={{ marginTop: 12 }} onClick={handleCreate}>Create User</button>
                </div>
            )}

            <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                {users.map(u => {
                    const rc = roleColors[u.role] || roleColors.paramedic;
                    return (
                        <div key={u.id} className="card" style={{ padding: '14px 18px' }}>
                            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                                    <div style={{
                                        width: 36, height: 36, borderRadius: 10,
                                        background: rc.bg, border: `1px solid ${rc.color}30`,
                                        display: 'flex', alignItems: 'center', justifyContent: 'center',
                                        fontSize: 14, fontWeight: 800, color: rc.color,
                                    }}>
                                        {u.full_name?.charAt(0) || '?'}
                                    </div>
                                    <div>
                                        <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--color-text-primary)' }}>
                                            {u.full_name}
                                            <span style={{ fontSize: 11, color: 'var(--color-text-muted)', fontWeight: 400, marginLeft: 6 }}>@{u.username}</span>
                                        </div>
                                        <div style={{ display: 'flex', gap: 8, marginTop: 2 }}>
                                            <span className="badge" style={{ background: rc.bg, color: rc.color }}>{u.role?.replace('_', ' ').toUpperCase()}</span>
                                            {u.ambulance_id && <span style={{ fontSize: 10, color: 'var(--color-text-muted)' }}>AMB: {u.ambulance_id}</span>}
                                            {u.hospital_id && <span style={{ fontSize: 10, color: 'var(--color-text-muted)' }}>Hospital #{u.hospital_id}</span>}
                                        </div>
                                    </div>
                                </div>
                                <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                                    {resetId === u.id ? (
                                        <>
                                            <input className="input" type="password" placeholder="New password" style={{ width: 140, padding: '5px 8px', fontSize: 12 }}
                                                value={newPw} onChange={e => setNewPw(e.target.value)} />
                                            <button className="btn btn-primary btn-sm" onClick={() => handleReset(u.id)}>Set</button>
                                            <button className="btn btn-secondary btn-sm" onClick={() => setResetId(null)}>✕</button>
                                        </>
                                    ) : (
                                        <>
                                            <button className="btn btn-secondary btn-sm" onClick={() => { setResetId(u.id); setNewPw(''); }}>Reset PW</button>
                                            <button className="btn btn-danger btn-sm" onClick={() => handleDelete(u.id, u.full_name)}>Delete</button>
                                        </>
                                    )}
                                </div>
                            </div>
                        </div>
                    );
                })}
            </div>
        </div>
    );
}
