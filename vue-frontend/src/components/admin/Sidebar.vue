<script setup>
import { RouterLink, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const route = useRoute()

const menuItems = [
  { path: '/admin/dashboard', icon: '📊', label: '仪表盘' },
  { path: '/admin/stations', icon: '🏢', label: '充电站管理' },
  { path: '/admin/users', icon: '👥', label: '用户管理' },
  { path: '/admin/orders', icon: '📋', label: '订单管理' },
]

function logout() {
  authStore.logout()
}
</script>

<template>
  <aside class="sidebar">
    <div class="sidebar-logo">
      <span class="icon">⚡</span>
      <h2>绿能充电管理</h2>
    </div>

    <nav class="sidebar-menu">
      <RouterLink 
        v-for="item in menuItems" 
        :key="item.path" 
        :to="item.path"
        :class="{ active: route.path === item.path }"
      >
        <span class="icon">{{ item.icon }}</span>
        <span>{{ item.label }}</span>
      </RouterLink>
      <a href="#" @click.prevent="logout">
        <span class="icon">🚪</span>
        <span>退出登录</span>
      </a>
    </nav>
  </aside>
</template>

<style scoped>
.sidebar {
  width: 240px;
  background: #2d3436;
  color: white;
  position: fixed;
  height: 100vh;
  overflow-y: auto;
}

.sidebar-logo {
  text-align: center;
  padding: 25px 20px;
  border-bottom: 1px solid rgba(255,255,255,0.1);
}

.sidebar-logo .icon {
  font-size: 2.5rem;
}

.sidebar-logo h2 {
  font-size: 1.1rem;
  margin-top: 10px;
  font-weight: 600;
}

.sidebar-menu {
  padding: 15px 0;
}

.sidebar-menu a {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 25px;
  color: rgba(255,255,255,0.7);
  text-decoration: none;
  transition: all 0.3s;
  border-left: 3px solid transparent;
}

.sidebar-menu a:hover,
.sidebar-menu a.active {
  background: rgba(255,255,255,0.1);
  color: white;
  border-left-color: var(--primary);
}

.sidebar-menu .icon {
  font-size: 1.2rem;
  width: 24px;
  text-align: center;
}
</style>
