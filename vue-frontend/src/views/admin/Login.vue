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
    const data = await authStore.login(form.value.username, form.value.password)

    if (data.role !== 1) {
      error.value = '您没有管理员权限'
      authStore.logout()
      return
    }

    router.push('/admin/dashboard')
  } catch (err) {
    error.value = err.response?.data?.message || '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <v-card class="login-card mx-auto" max-width="400" elevation="12" rounded="lg">
      <v-card-text class="pa-8">
        <div class="text-center mb-6">
          <v-icon size="48" color="grey-darken-3">mdi-shield-lock</v-icon>
          <h1 class="text-h5 mt-3 mb-1">管理后台</h1>
          <p class="text-body-2 text-grey">请使用管理员账号登录</p>
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

          <v-btn type="submit" color="grey-darken-3" size="large" block :loading="loading">
            登录管理后台
          </v-btn>
        </v-form>
      </v-card-text>

      <v-divider />

      <v-card-actions class="justify-center pa-4">
        <v-btn to="/" variant="text" size="small" prepend-icon="mdi-arrow-left">返回用户端</v-btn>
      </v-card-actions>
    </v-card>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #2d3436 0%, #636e72 100%);
  padding: 20px;
}
</style>
