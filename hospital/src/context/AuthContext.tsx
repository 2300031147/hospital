import { createContext, useState, useContext, useEffect } from 'react';

const AuthContext = createContext<any>(null);

const ALLOWED_ROLE = 'hospital_admin';

export const AuthProvider = ({ children }) => {
    const [user, setUser] = useState(null);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        const token = localStorage.getItem('hosp_token');
        const role = localStorage.getItem('hosp_role');
        const hospital_id = localStorage.getItem('hosp_hospital_id');
        const username = localStorage.getItem('hosp_username');
        const full_name = localStorage.getItem('hosp_full_name');

        // Only hydrate if the stored role matches this portal's allowed role
        if (token && role === ALLOWED_ROLE) {
            setUser({ token, role, hospital_id: hospital_id ? parseInt(hospital_id) : null, username, full_name });
        } else if (token && role && role !== ALLOWED_ROLE) {
            // Wrong role stored — clear it to prevent cross-portal leakage
            localStorage.removeItem('hosp_token');
            localStorage.removeItem('hosp_role');
            localStorage.removeItem('hosp_hospital_id');
            localStorage.removeItem('hosp_username');
            localStorage.removeItem('hosp_full_name');
        }
        setLoading(false);
    }, []);

    const login = (data) => {
        const { access_token, role, hospital_id, username, full_name, user_id } = data;

        // Reject at context level if role doesn't match
        if (role !== ALLOWED_ROLE) {
            throw new Error('Unauthorized role for this portal');
        }

        localStorage.setItem('hosp_token', access_token);
        localStorage.setItem('hosp_role', role);
        if (hospital_id != null) localStorage.setItem('hosp_hospital_id', hospital_id);
        if (username) localStorage.setItem('hosp_username', username);
        if (full_name) localStorage.setItem('hosp_full_name', full_name);

        setUser({ token: access_token, role, hospital_id, username: username || `user_${user_id}`, full_name });
    };

    const logout = () => {
        localStorage.removeItem('hosp_token');
        localStorage.removeItem('hosp_role');
        localStorage.removeItem('hosp_hospital_id');
        localStorage.removeItem('hosp_username');
        localStorage.removeItem('hosp_full_name');
        setUser(null);
    };

    return (
        <AuthContext.Provider value={{ user, login, logout, loading }}>
            {children}
        </AuthContext.Provider>
    );
};

export const useAuth = () => useContext(AuthContext);
