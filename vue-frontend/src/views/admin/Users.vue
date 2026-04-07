<script setup>
import { ref, onMounted, computed } from 'vue'
import Sidebar from '@/components/admin/Sidebar.vue'
import { adminApi } from '@/api'

const users = ref([])
const loading = ref(true)
const searchKeyword = ref('')
const snackbar = ref({ show: false, message: '', color: 'success' })
const confirmDialog = ref({ show: false, userId: null, action: '' })

onMounted(async () => { await loadUsers() })

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

const filteredUsers = computed(() => {
  if (!searchKeyword.value) return users.value
  const keyword = searchKeyword.value.toLowerCase()
  return users.value.filter(u =>
    u.username.toLowerCase().includes(keyword) ||
    (u.phone && u.phone.includes(keyword))
  )
})

async function handleToggleStatus() {
  const user = confirmDialog.value
  const newStatus = user.action === 'disable' ? 0 : 1
  try {
    await adminApi.updateUserStatus(user.userId, newStatus)
    snackbar.value = { show: true, message: '操作成功', color: 'success' }
    await loadUsers()
  } catch (error) {
    snackbar.value = { show: true, message: error.response?.data?.message || '操作失败', color: 'error' }
  }
}

function formatDate(str) {
  if (!str) return '-'
  return new Date(str).toLocaleString('zh-CN')
}
</script>

<template>
  <div class="d-flex" style="min-height: 100vh;">
    <Sidebar />

    <v-main>
      <v-container class="pa-6">
        <div class="d-flex align-center justify-space-between mb-6">
          <h1 class="text-h5 font-weight-bold">用户管理</h1>
          <v-text-field
            v-model="searchKeyword"
            variant="outlined"
            density="compact"
            prepend-inner-icon="mdi-magnify"
            placeholder="搜索用户名/手机号"
            hide-details
            style="max-width: 280px;"
          />
        </div>

        <div v-if="loading" class="text-center py-8">
          <v-progress-circular indeterminate color="primary" size="48" />
        </div>

        <v-card v-else rounded="lg">
          <v-table hover>
            <thead>
              <tr>
                <th>ID</th>
                <th>用户名</th>
                <th>真实姓名</th>
                <th>手机号</th>
                <th>车牌</th>
                <th>角色</th>
                <th>状态</th>
                <th>注册时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="filteredUsers.length === 0">
                <td colspan="9" class="text-center text-grey py-6">暂无数据</td>
              </tr>
              <tr v-for="u in filteredUsers" :key="u.id">
                <td>{{ u.id }}</td>
                <td class="font-weight-medium">{{ u.username }}</td>
                <td>{{ u.realName || '-' }}</td>
                <td>{{ u.phone || '-' }}</td>
                <td>{{ u.carPlate || '-' }}</td>
                <td>
                  <v-chip :color="u.role === 1 ? 'warning' : 'primary'" size="small" variant="tonal">
                    {{ u.role === 1 ? '管理员' : '用户' }}
                  </v-chip>
                </td>
                <td>
                  <v-chip :color="u.status === 1 ? 'success' : 'error'" size="small" variant="tonal">
                    {{ u.status === 1 ? '正常' : '禁用' }}
                  </v-chip>
                </td>
                <td class="text-body-2 text-grey">{{ formatDate(u.createdAt) }}</td>
                <td>
                  <v-btn
                    v-if="u.role !== 1"
                    :color="u.status === 1 ? 'error' : 'success'"
                    size="small"
                    variant="outlined"
                    @click="confirmDialog = { show: true, userId: u.id, action: u.status === 1 ? 'disable' : 'enable' }"
                  >
                    {{ u.status === 1 ? '禁用' : '启用' }}
                  </v-btn>
                </td>
              </tr>
            </tbody>
          </v-table>
        </v-card>
      </v-container>
    </v-main>

    <!-- Confirm Dialog -->
    <v-dialog v-model="confirmDialog.show" max-width="360">
      <v-card rounded="lg">
        <v-card-title>{{ confirmDialog.action === 'disable' ? '禁用用户' : '启用用户' }}</v-card-title>
        <v-card-text>确定{{ confirmDialog.action === 'disable' ? '禁用' : '启用' }}该用户？</v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="confirmDialog.show = false">取消</v-btn>
          <v-btn :color="confirmDialog.action === 'disable' ? 'error' : 'success'" variant="flat" @click="handleToggleStatus(); confirmDialog.show = false">确认</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-snackbar v-model="snackbar.show" :color="snackbar.color" :timeout="3000" location="top">
      {{ snackbar.message }}
    </v-snackbar>
  </div>
</template>
