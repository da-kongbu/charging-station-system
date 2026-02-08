<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const form = ref({ username: '', password: '' })
const loading = ref(false)
const error = ref('')

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
    <div class="login-card">
      <div class="login-header">
        <span class="icon">🔐</span>
        <h1>管理后台</h1>
        <p>请使用管理员账号登录</p>
      </div>

      <form @submit.prevent="handleLogin">
        <div class="form-group">
          <label>用户名</label>
          <input v-model="form.username" type="text" class="form-control" placeholder="请输入用户名" />
        </div>
        <div class="form-group">
          <label>密码</label>
          <input v-model="form.password" type="password" class="form-control" placeholder="请输入密码" />
        </div>
        <p v-if="error" class="error-text">{{ error }}</p>
        <button type="submit" class="btn btn-block" :disabled="loading">
          {{ loading ? '登录中...' : '登录管理后台' }}
        </button>
      </form>

      <div class="login-footer">
        <RouterLink to="/">← 返回用户端</RouterLink>
      </div>
    </div>
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

.login-card {
  background: white;
  padding: 40px;
  border-radius: 16px;
  width: 100%;
  max-width: 400px;
  box-shadow: 0 20px 60px rgba(0,0,0,0.3);
}

.login-header {
  text-align: center;
  margin-bottom: 30px;
}

.login-header .icon {
  font-size: 3rem;
}

.login-header h1 {
  font-size: 1.5rem;
  margin: 10px 0 5px;
}

.login-header p {
  color: var(--text-light);
}

.btn-block {
  width: 100%;
  padding: 14px;
  background: #2d3436;
  color: white;
  margin-top: 10px;
}

.btn-block:hover {
  background: #1e2526;
}

.error-text {
  color: var(--danger);
  font-size: 0.9rem;
  margin-bottom: 10px;
}

.login-footer {
  text-align: center;
  margin-top: 25px;
}

.login-footer a {
  color: var(--text-light);
  font-size: 0.9rem;
}
</style>
