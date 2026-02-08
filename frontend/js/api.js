const API_BASE_URL = 'http://localhost:8080/api';

const API = {
    // 统一请求处理
    async request(endpoint, options = {}) {
        const token = localStorage.getItem('token');

        const defaultHeaders = {
            'Content-Type': 'application/json',
        };

        if (token) {
            defaultHeaders['Authorization'] = `Bearer ${token}`;
        }

        const config = {
            ...options,
            headers: {
                ...defaultHeaders,
                ...options.headers
            }
        };

        try {
            const response = await fetch(`${API_BASE_URL}${endpoint}`, config);

            // 处理 401 未授权
            if (response.status === 401) {
                Auth.logout();
                return;
            }

            const result = await response.json();

            if (!response.ok) {
                throw new Error(result.message || '请求失败');
            }

            return result;
        } catch (error) {
            console.error('API Error:', error);
            throw error;
        }
    },

    // Auth
    login: (data) => API.request('/auth/login', {
        method: 'POST',
        body: JSON.stringify(data)
    }),

    register: (data) => API.request('/auth/register', {
        method: 'POST',
        body: JSON.stringify(data)
    }),

    getProfile: () => API.request('/users/me'),

    // Stations
    getStations: (keyword = '') => API.request(keyword ? `/stations/search?keyword=${keyword}` : '/stations'),
    getStationDetails: (id) => API.request(`/stations/${id}`),
    getStationPiles: (id) => API.request(`/stations/${id}/piles`),

    // Reservations
    getMyReservations: () => API.request('/reservations'),
    createReservation: (data) => API.request('/reservations', {
        method: 'POST',
        body: JSON.stringify(data)
    }),
    cancelReservation: (id) => API.request(`/reservations/${id}/cancel`, { method: 'POST' }),

    // Orders
    getMyOrders: () => API.request('/orders'),
    payOrder: (id) => API.request(`/orders/${id}/pay`, { method: 'POST' })
};

const Auth = {
    isAuthenticated: () => !!localStorage.getItem('token'),

    getUser: () => JSON.parse(localStorage.getItem('user') || '{}'),

    login: (token, user) => {
        localStorage.setItem('token', token);
        localStorage.setItem('user', JSON.stringify(user));
    },

    logout: () => {
        localStorage.removeItem('token');
        localStorage.removeItem('user');
        // Determine path to login
        const isPages = window.location.pathname.includes('/pages/');
        window.location.href = isPages ? 'login.html' : 'pages/login.html';
    },

    checkAuth: () => {
        if (!Auth.isAuthenticated() && !window.location.href.includes('login.html') && !window.location.href.includes('register.html')) {
            const isPages = window.location.pathname.includes('/pages/');
            window.location.href = isPages ? 'login.html' : 'pages/login.html';
        }
    }
};
