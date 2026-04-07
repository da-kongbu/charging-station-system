<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const form = ref({ username: '', password: '' })
const loading = ref(false)
const error = ref('')
const showPassword = ref(false)

async function handleLogin() {
  if (!form.value.username || !form.value.password) {
    error.value = '请填写用户名和密码'
    return
  }

  loading.value = true
  error.value = ''

  try {
    await authStore.login(form.value.username, form.value.password)
    router.push('/')
  } catch (err) {
    error.value = err.response?.data?.message || '登录失败，请检查用户名和密码'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <v-card class="auth-card mx-auto" max-width="420" elevation="12" rounded="lg">
      <v-card-text class="pa-8">
        <div class="text-center mb-6">
          <v-icon size="48" color="primary">mdi-lightning-bolt</v-icon>
          <h1 class="text-h5 mt-3 mb-1">用户登录</h1>
          <p class="text-body-2 text-grey">欢迎回来，请登录您的账号</p>
        </div>

        <v-alert v-if="error" type="error" variant="tonal" closable class="mb-4" @click:close="error = ''">
          {{ error }}
        </v-alert>

        <v-form @submit.prevent="handleLogin">
          <v-text-field
            v-model="form.username"
            label="用户名"
            variant="outlined"
            prepend-inner-icon="mdi-account"
            placeholder="请输入用户名"
            class="mb-2"
          />
          <v-text-field
            v-model="form.password"
            label="密码"
            variant="outlined"
            prepend-inner-icon="mdi-lock"
            :append-inner-icon="showPassword ? 'mdi-eye-off' : 'mdi-eye'"
            :type="showPassword ? 'text' : 'password'"
            placeholder="请输入密码"
            class="mb-4"
            @click:append-inner="showPassword = !showPassword"
          />

          <v-btn type="submit" color="primary" size="large" block :loading="loading">
            登录
          </v-btn>
        </v-form>
      </v-card-text>

      <v-divider />

      <v-card-actions class="justify-center pa-4">
        <span class="text-body-2 text-grey">还没有账号？</span>
        <v-btn to="/register" variant="text" color="primary" size="small">立即注册</v-btn>
        <v-spacer />
        <v-btn to="/" variant="text" size="small" prepend-icon="mdi-arrow-left">返回首页</v-btn>
      </v-card-actions>
    </v-card>
  </div>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #00b894 0%, #0984e3 100%);
  padding: 20px;
}
</style>
