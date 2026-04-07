<script setup>
import { ref } from 'vue'
import { RouterLink } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const drawer = ref(false)
</script>

<template>
  <v-app-bar color="white" elevation="1" density="comfortable" scroll-behavior="elevate">
    <v-container class="d-flex align-center pa-0" style="max-width: 1200px; margin: 0 auto; width: 100%;">
      <!-- Logo -->
      <RouterLink to="/" class="d-flex align-center text-decoration-none" style="gap: 8px;">
        <v-icon color="primary" size="28">mdi-lightning-bolt</v-icon>
        <span class="text-h6 font-weight-bold text-primary">绿能充电</span>
      </RouterLink>

      <v-spacer />

      <!-- Desktop nav -->
      <div class="d-none d-md-flex align-center" style="gap: 24px;">
        <RouterLink to="/" class="text-body-2 font-weight-medium text-grey-darken-1 text-decoration-none nav-link">充电站</RouterLink>
        <template v-if="authStore.isAuthenticated">
          <RouterLink to="/reservations" class="text-body-2 font-weight-medium text-grey-darken-1 text-decoration-none nav-link">我的预约</RouterLink>
          <RouterLink to="/orders" class="text-body-2 font-weight-medium text-grey-darken-1 text-decoration-none nav-link">我的订单</RouterLink>
        </template>
      </div>

      <v-spacer />

      <!-- Auth buttons -->
      <div class="d-none d-md-flex align-center" style="gap: 10px;">
        <template v-if="authStore.isAuthenticated">
          <v-menu>
            <template v-slot:activator="{ props }">
              <v-btn v-bind="props" variant="text" prepend-icon="mdi-account-circle">
                {{ authStore.user.username }}
              </v-btn>
            </template>
            <v-list density="compact" min-width="140">
              <v-list-item @click="authStore.logout()">
                <template v-slot:prepend>
                  <v-icon>mdi-logout</v-icon>
                </template>
                <v-list-item-title>退出登录</v-list-item-title>
              </v-list-item>
            </v-list>
          </v-menu>
        </template>
        <template v-else>
          <v-btn to="/login" variant="outlined" color="primary" size="small">登录</v-btn>
          <v-btn to="/register" variant="flat" color="primary" size="small">注册</v-btn>
        </template>
      </div>

      <!-- Mobile hamburger -->
      <v-app-bar-nav-icon class="d-md-none" @click="drawer = !drawer" />
    </v-container>
  </v-app-bar>

  <!-- Mobile drawer -->
  <v-navigation-drawer v-model="drawer" temporary location="right">
    <v-list density="comfortable" nav>
      <v-list-item to="/" prepend-icon="mdi-ev-station" title="充电站" />
      <template v-if="authStore.isAuthenticated">
        <v-list-item to="/reservations" prepend-icon="mdi-calendar-clock" title="我的预约" />
        <v-list-item to="/orders" prepend-icon="mdi-receipt-text" title="我的订单" />
        <v-divider class="my-2" />
        <v-list-item @click="authStore.logout()" prepend-icon="mdi-logout" title="退出登录" />
      </template>
      <template v-else>
        <v-divider class="my-2" />
        <v-list-item to="/login" prepend-icon="mdi-login" title="登录" />
        <v-list-item to="/register" prepend-icon="mdi-account-plus" title="注册" />
      </template>
    </v-list>
  </v-navigation-drawer>
</template>

<style scoped>
.nav-link:hover,
.nav-link.router-link-active {
  color: rgb(var(--v-theme-primary)) !important;
}
</style>
