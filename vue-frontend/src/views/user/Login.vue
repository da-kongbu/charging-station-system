<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const form = ref({
  username: '',
  password: ''
})
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
    <div class="auth-card">
      <div class="auth-header">
        <span class="auth-icon">⚡</span>
        <h1>用户登录</h1>
        <p>欢迎回来，请登录您的账号</p>
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

        <button type="submit" class="btn btn-primary btn-block" :disabled="loading">
          {{ loading ? '登录中...' : '登录' }}
        </button>
      </form>

      <div class="auth-footer">
        <p>还没有账号？<RouterLink to="/register">立即注册</RouterLink></p>
        <RouterLink to="/" class="back-link">← 返回首页</RouterLink>
      </div>
    </div>
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

.auth-card {
  background: white;
  padding: 40px;
  border-radius: 16px;
  width: 100%;
  max-width: 420px;
  box-shadow: 0 20px 60px rgba(0,0,0,0.2);
}

.auth-header {
  text-align: center;
  margin-bottom: 30px;
}

.auth-icon {
  font-size: 3rem;
}

.auth-header h1 {
  font-size: 1.6rem;
  margin: 10px 0 5px;
  color: var(--text);
}

.auth-header p {
  color: var(--text-light);
}

.btn-block {
  width: 100%;
  margin-top: 10px;
  padding: 14px;
}

.error-text {
  color: var(--danger);
  font-size: 0.9rem;
  margin-bottom: 10px;
}

.auth-footer {
  text-align: center;
  margin-top: 25px;
}

.auth-footer p {
  color: var(--text-light);
  margin-bottom: 10px;
}

.auth-footer a {
  color: var(--primary);
  font-weight: 500;
}

.back-link {
  font-size: 0.9rem;
}
</style>
