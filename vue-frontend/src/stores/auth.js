import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import api from '@/api'
import { autoImportJiangsuStations } from '@/services/autoImport'

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
            role: data.role,
            creditScore: data.creditScore
        }

        localStorage.setItem('token', data.token)
        localStorage.setItem('user', JSON.stringify(user.value))

        // 如果是管理员登录，自动检查并导入江苏省充电站
        if (data.role === 1) {
            // 延迟执行，不阻塞登录流程
            setTimeout(async () => {
                try {
                    console.log('[Auth] 管理员登录，检查充电站数据...')
                    const count = await autoImportJiangsuStations(api)
                    if (count > 0) {
                        console.log(`[Auth] 自动导入完成，共导入 ${count} 个充电站`)
                        // 强制刷新页面以显示新数据
                        window.location.reload()
                    }
                } catch (error) {
                    console.error('[Auth] 自动导入失败:', error)
                }
            }, 1000)
        }

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
