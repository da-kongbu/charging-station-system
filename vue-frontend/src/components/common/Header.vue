<script setup>
import { RouterLink } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
</script>

<template>
  <header class="header">
    <div class="container header-content">
      <RouterLink to="/" class="logo">
        <span class="logo-icon">⚡</span>
        <span class="logo-text">绿能充电</span>
      </RouterLink>

      <nav class="nav-links">
        <RouterLink to="/">充电站</RouterLink>
        <template v-if="authStore.isAuthenticated">
          <RouterLink to="/reservations">我的预约</RouterLink>
          <RouterLink to="/orders">我的订单</RouterLink>
        </template>
      </nav>

      <div class="auth-buttons">
        <template v-if="authStore.isAuthenticated">
          <span class="credit-score" v-if="authStore.user.creditScore !== undefined">
            ⭐ {{ authStore.user.creditScore }}
          </span>
          <span class="username">{{ authStore.user.username }}</span>
          <button @click="authStore.logout()" class="btn btn-outline btn-sm">退出</button>
        </template>
        <template v-else>
          <RouterLink to="/login" class="btn btn-outline btn-sm">登录</RouterLink>
          <RouterLink to="/register" class="btn btn-primary btn-sm">注册</RouterLink>
        </template>
      </div>
    </div>
  </header>
</template>

<style scoped>
.header {
  background: white;
  box-shadow: 0 2px 10px rgba(0,0,0,0.05);
  position: sticky;
  top: 0;
  z-index: 100;
}

.header-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 70px;
}

.logo {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 1.4rem;
  font-weight: 700;
  color: var(--primary);
}

.logo-icon {
  font-size: 1.8rem;
}

.nav-links {
  display: flex;
  gap: 30px;
}

.nav-links a {
  font-weight: 500;
  color: var(--text-light);
  transition: color 0.3s;
}

.nav-links a:hover,
.nav-links a.router-link-active {
  color: var(--primary);
}

.auth-buttons {
  display: flex;
  align-items: center;
  gap: 12px;
}

.username {
  color: var(--text);
  font-weight: 500;
}

.credit-score {
  font-size: 0.9em;
  color: #fbbf24;
  margin-right: 5px;
  background: #fffbeb;
  padding: 2px 8px;
  border-radius: 12px;
  border: 1px solid #fcd34d;
}

@media (max-width: 768px) {
  .nav-links {
    display: none;
  }
}
</style>
