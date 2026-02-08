import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

// User Views
import Home from '@/views/user/Home.vue'
import Login from '@/views/user/Login.vue'
import Register from '@/views/user/Register.vue'
import StationDetail from '@/views/user/StationDetail.vue'
import Reservations from '@/views/user/Reservations.vue'
import Orders from '@/views/user/Orders.vue'

// Admin Views
import AdminLogin from '@/views/admin/Login.vue'
import Dashboard from '@/views/admin/Dashboard.vue'
import Stations from '@/views/admin/Stations.vue'
import Users from '@/views/admin/Users.vue'
import AdminOrders from '@/views/admin/Orders.vue'

const routes = [
    // User Routes
    { path: '/', name: 'Home', component: Home },
    { path: '/login', name: 'Login', component: Login },
    { path: '/register', name: 'Register', component: Register },
    { path: '/station/:id', name: 'StationDetail', component: StationDetail },
    { path: '/reservations', name: 'Reservations', component: Reservations, meta: { requiresAuth: true } },
    { path: '/orders', name: 'Orders', component: Orders, meta: { requiresAuth: true } },

    // Admin Routes
    { path: '/admin/login', name: 'AdminLogin', component: AdminLogin },
    { path: '/admin', redirect: '/admin/dashboard' },
    { path: '/admin/dashboard', name: 'Dashboard', component: Dashboard, meta: { requiresAuth: true, requiresAdmin: true } },
    { path: '/admin/stations', name: 'AdminStations', component: Stations, meta: { requiresAuth: true, requiresAdmin: true } },
    { path: '/admin/users', name: 'AdminUsers', component: Users, meta: { requiresAuth: true, requiresAdmin: true } },
    { path: '/admin/orders', name: 'AdminOrders', component: AdminOrders, meta: { requiresAuth: true, requiresAdmin: true } },
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

// Navigation Guards
router.beforeEach((to, from, next) => {
    const authStore = useAuthStore()

    if (to.meta.requiresAuth && !authStore.isAuthenticated) {
        if (to.path.startsWith('/admin')) {
            next('/admin/login')
        } else {
            next('/login')
        }
        return
    }

    if (to.meta.requiresAdmin && !authStore.isAdmin) {
        next('/admin/login')
        return
    }

    next()
})

export default router
