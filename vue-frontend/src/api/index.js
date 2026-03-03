import axios from 'axios'
// import { useAuthStore } from '@/stores/auth' // Removed to avoid circular dependency
// import router from '@/router' // Removed to avoid circular dependency

const api = axios.create({
    baseURL: 'http://localhost:8080/api',
    timeout: 10000,
    headers: {
        'Content-Type': 'application/json'
    }
})

// Request interceptor - add token
api.interceptors.request.use(
    config => {
        const token = localStorage.getItem('token')
        if (token) {
            config.headers.Authorization = `Bearer ${token}`
        }
        return config
    },
    error => Promise.reject(error)
)

// Response interceptor - handle errors
api.interceptors.response.use(
    response => response,
    error => {
        if (error.response?.status === 401) {
            // Manually clear storage instead of using store action to avoid circular dependency
            localStorage.removeItem('token')
            localStorage.removeItem('user')

            // Break circular dependency by using window.location or dynamic import
            // Since we are logging out, a full reload is safer/cleaner anyway
            if (window.location.pathname.startsWith('/admin')) {
                window.location.href = '/admin/login'
            } else {
                window.location.href = '/login'
            }
        }
        return Promise.reject(error)
    }
)

export default api

// API methods
export const stationApi = {
    getAll: () => api.get('/stations'),
    getById: (id) => api.get(`/stations/${id}`),
}

export const reservationApi = {
    getMyReservations: () => api.get('/reservations'),
    create: (data) => api.post('/reservations', data),
    getBySpotAndDate: (spotId, date) => api.get(`/reservations/spot/${spotId}/date/${date}`),
    checkIn: (id) => api.post(`/reservations/${id}/checkin`),
    checkOut: (id) => api.post(`/reservations/${id}/checkout`),
    cancel: (id) => api.post(`/reservations/${id}/cancel`),
}

export const orderApi = {
    getMyOrders: () => api.get('/orders'),
    pay: (id, method) => api.post(`/orders/${id}/pay`, { paymentMethod: method }),
}

export const adminApi = {
    getDashboard: () => api.get('/admin/dashboard'),
    getStations: () => api.get('/admin/stations'),
    createStation: (data) => api.post('/admin/stations', data),
    updateStation: (id, data) => api.put(`/admin/stations/${id}`, data),
    deleteStation: (id) => api.delete(`/admin/stations/${id}`),
    // Pile management
    createPile: (stationId, data) => api.post(`/admin/piles?stationId=${stationId}`, data),
    updatePile: (id, data) => api.put(`/admin/piles/${id}`, data),
    // Spot management
    createSpot: (pileId, data) => api.post(`/admin/spots?pileId=${pileId}`, data),
    updateSpot: (id, data) => api.put(`/admin/spots/${id}`, data),
    getUsers: () => api.get('/admin/users'),
    updateUserStatus: (id, status) => api.put(`/admin/users/${id}/status?status=${status}`),
    getOrders: () => api.get('/admin/orders'),
}

