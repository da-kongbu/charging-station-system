<script setup>
import { ref, onMounted } from 'vue'
import Sidebar from '@/components/admin/Sidebar.vue'
import { adminApi } from '@/api'

const users = ref([])
const loading = ref(true)
const searchKeyword = ref('')

onMounted(async () => {
  await loadUsers()
})

async function loadUsers() {
  loading.value = true
  try {
    const response = await adminApi.getUsers()
    users.value = response.data.data || []
  } catch (error) {
    console.error('Failed to load users:', error)
  } finally {
    loading.value = false
  }
}

async function toggleStatus(user) {
  const newStatus = user.status === 1 ? 0 : 1
  const action = newStatus === 0 ? '禁用' : '启用'
  if (!confirm(`确定${action}该用户？`)) return
  
  try {
    await adminApi.updateUserStatus(user.id, newStatus)
    alert('操作成功')
    await loadUsers()
  } catch (error) {
    alert(error.response?.data?.message || '操作失败')
  }
}

const filteredUsers = () => {
  if (!searchKeyword.value) return users.value
  const keyword = searchKeyword.value.toLowerCase()
  return users.value.filter(u => 
    u.username.toLowerCase().includes(keyword) ||
    (u.phone && u.phone.includes(keyword))
  )
}
</script>

<template>
  <div class="admin-layout">
    <Sidebar />
    
    <main class="admin-main">
      <div class="top-bar">
        <h1>用户管理</h1>
        <div class="search-bar">
          <input v-model="searchKeyword" type="text" class="form-control" placeholder="搜索用户名/手机号" />
        </div>
      </div>

      <div v-if="loading" class="text-center mt-2">
        <div class="spinner"></div>
      </div>

      <table v-else class="data-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>用户名</th>
            <th>真实姓名</th>
            <th>手机号</th>
            <th>车牌</th>
            <th>角色</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="filteredUsers().length === 0">
            <td colspan="8" class="text-center">暂无数据</td>
          </tr>
          <tr v-for="u in filteredUsers()" :key="u.id">
            <td>{{ u.id }}</td>
            <td>{{ u.username }}</td>
            <td>{{ u.realName || '-' }}</td>
            <td>{{ u.phone || '-' }}</td>
            <td>{{ u.carPlate || '-' }}</td>
            <td>
              <span class="badge" :class="u.role === 1 ? 'badge-warning' : 'badge-success'">
                {{ u.role === 1 ? '管理员' : '用户' }}
              </span>
            </td>
            <td>
              <span class="badge" :class="u.status === 1 ? 'badge-success' : 'badge-danger'">
                {{ u.status === 1 ? '正常' : '禁用' }}
              </span>
            </td>
            <td>
              <button 
                v-if="u.status === 1" 
                class="btn btn-danger btn-sm" 
                @click="toggleStatus(u)"
              >禁用</button>
              <button 
                v-else 
                class="btn btn-primary btn-sm" 
                @click="toggleStatus(u)"
              >启用</button>
            </td>
          </tr>
        </tbody>
      </table>
    </main>
  </div>
</template>

<style scoped>
.admin-layout {
  display: flex;
  min-height: 100vh;
}

.admin-main {
  margin-left: 240px;
  flex: 1;
  padding: 30px;
  background: var(--bg);
}

.top-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 25px;
}

.top-bar h1 {
  font-size: 1.8rem;
}

.search-bar .form-control {
  width: 250px;
}
</style>
