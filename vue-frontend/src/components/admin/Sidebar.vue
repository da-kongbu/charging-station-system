<script setup>
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()

const menuItems = [
  { path: '/admin/dashboard', icon: 'mdi-view-dashboard', label: '仪表盘' },
  { path: '/admin/stations', icon: 'mdi-ev-station', label: '充电站管理' },
  { path: '/admin/users', icon: 'mdi-account-group', label: '用户管理' },
  { path: '/admin/orders', icon: 'mdi-clipboard-list', label: '订单管理' },
]

function logout() {
  authStore.logout()
  router.push('/admin/login')
}
</script>

<template>
  <v-navigation-drawer
    permanent
    :width="240"
    color="grey-darken-3"
    theme="dark"
  >
    <!-- Logo -->
    <div class="pa-5 text-center border-b border-white border-opacity-10">
      <v-icon size="40" color="primary">mdi-lightning-bolt</v-icon>
      <div class="text-subtitle-1 font-weight-bold mt-2">绿能充电管理</div>
    </div>

    <!-- Menu -->
    <v-list density="comfortable" nav class="mt-2">
      <v-list-item
        v-for="item in menuItems"
        :key="item.path"
        :to="item.path"
        :prepend-icon="item.icon"
        :title="item.label"
        :active="route.path === item.path"
        color="primary"
        rounded="lg"
        class="mx-2 mb-1"
      />
      <v-divider class="my-3 mx-4" />
      <v-list-item
        @click="logout"
        prepend-icon="mdi-logout"
        title="退出登录"
        rounded="lg"
        class="mx-2"
      />
    </v-list>
  </v-navigation-drawer>
</template>
