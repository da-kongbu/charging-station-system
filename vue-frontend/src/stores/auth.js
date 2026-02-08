import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import api from '@/api'

export const useAuthStore = defineStore('auth', () => {
    const token = ref(localStorage.getItem('token') || '')
    const user = ref(JSON.parse(localStorage.getItem('user') || '{}'))

    const isAuthenticated = computed(() => !!token.value)
    const isAdmin = computed(() => user.value?.role === 1)

    async function login(username, password) {
        const response = await api.post('/auth/login', { username, password })
        const data = response.data.data

        token.value = data.token
        user.value = {
            id: data.userId,
            username: data.username,
            role: data.role
        }

        localStorage.setItem('token', data.token)
        localStorage.setItem('user', JSON.stringify(user.value))

        return data
    }

    async function register(userData) {
        const response = await api.post('/auth/register', userData)
        return response.data
    }

    function logout() {
        token.value = ''
        user.value = {}
        localStorage.removeItem('token')
        localStorage.removeItem('user')
    }

    return {
        token,
        user,
        isAuthenticated,
        isAdmin,
        login,
        register,
        logout
    }
})
