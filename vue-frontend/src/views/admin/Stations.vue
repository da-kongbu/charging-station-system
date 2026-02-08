<script setup>
import { ref, onMounted } from 'vue'
import Sidebar from '@/components/admin/Sidebar.vue'
import { adminApi } from '@/api'

const stations = ref([])
const loading = ref(true)
const showStationModal = ref(false)
const showPileModal = ref(false)
const showSpotModal = ref(false)
const editingId = ref(null)
const selectedStation = ref(null)
const selectedPile = ref(null)

const stationForm = ref({
  name: '',
  address: '',
  city: '北京',
  district: '',
  contact: '',
  businessHours: '24小时营业',
})

const pileForm = ref({
  pileCode: '',
  pileType: 'DC',
  power: 120,
  voltage: 750,
  current: 160,
  brand: '',
  connectorType: '国标DC',
})

const spotForm = ref({
  spotCode: '',
  spotType: 'STANDARD',
  pricePerHour: 5,
  serviceFee: 0.8,
})

onMounted(async () => {
  await loadStations()
})

async function loadStations() {
  loading.value = true
  try {
    const response = await adminApi.getStations()
    stations.value = response.data.data || []
  } catch (error) {
    console.error('Failed to load stations:', error)
  } finally {
    loading.value = false
  }
}

// Station CRUD
function openAddStationModal() {
  editingId.value = null
  stationForm.value = { name: '', address: '', city: '北京', district: '', contact: '', businessHours: '24小时营业' }
  showStationModal.value = true
}

function openEditStationModal(station) {
  editingId.value = station.id
  stationForm.value = { ...station }
  showStationModal.value = true
}

async function handleStationSubmit() {
  try {
    if (editingId.value) {
      await adminApi.updateStation(editingId.value, stationForm.value)
      alert('更新成功')
    } else {
      await adminApi.createStation(stationForm.value)
      alert('创建成功')
    }
    showStationModal.value = false
    await loadStations()
  } catch (error) {
    alert(error.response?.data?.message || '操作失败')
  }
}

async function handleDeleteStation(id) {
  if (!confirm('确定删除此充电站？')) return
  try {
    await adminApi.deleteStation(id)
    alert('删除成功')
    await loadStations()
  } catch (error) {
    alert(error.response?.data?.message || '删除失败')
  }
}

// Pile CRUD
function openAddPileModal(station) {
  selectedStation.value = station
  pileForm.value = { pileCode: '', pileType: 'DC', power: 120, voltage: 750, current: 160, brand: '', connectorType: '国标DC' }
  showPileModal.value = true
}

async function handlePileSubmit() {
  try {
    await adminApi.createPile(selectedStation.value.id, {
      ...pileForm.value,
      status: 1
    })
    alert('充电桩创建成功')
    showPileModal.value = false
    await loadStations()
  } catch (error) {
    alert(error.response?.data?.message || '创建失败')
  }
}

// Spot CRUD
function openAddSpotModal(pile) {
  selectedPile.value = pile
  spotForm.value = { spotCode: '', spotType: 'STANDARD', pricePerHour: 5, serviceFee: 0.8 }
  showSpotModal.value = true
}

async function handleSpotSubmit() {
  try {
    await adminApi.createSpot(selectedPile.value.id, {
      ...spotForm.value,
      status: 0
    })
    alert('车位创建成功')
    showSpotModal.value = false
    await loadStations()
  } catch (error) {
    alert(error.response?.data?.message || '创建失败')
  }
}
</script>

<template>
  <div class="admin-layout">
    <Sidebar />
    
    <main class="admin-main">
      <div class="top-bar">
        <h1>充电站管理</h1>
        <button class="btn btn-primary" @click="openAddStationModal">+ 新增充电站</button>
      </div>

      <div v-if="loading" class="text-center mt-2">
        <div class="spinner"></div>
      </div>

      <!-- Stations List -->
      <div v-else class="stations-list">
        <div v-if="stations.length === 0" class="card text-center">暂无充电站数据</div>
        
        <div v-for="station in stations" :key="station.id" class="station-card card mb-2">
          <div class="station-header">
            <div>
              <h3>{{ station.name }}</h3>
              <p class="text-light">📍 {{ station.city }} {{ station.district }} {{ station.address }}</p>
            </div>
            <div class="actions">
              <button class="btn btn-primary btn-sm" @click="openAddPileModal(station)">+ 添加充电桩</button>
              <button class="btn btn-outline btn-sm" @click="openEditStationModal(station)">编辑</button>
              <button class="btn btn-danger btn-sm" @click="handleDeleteStation(station.id)">删除</button>
            </div>
          </div>

          <!-- Piles under this station -->
          <div v-if="station.piles?.length" class="piles-section">
            <div v-for="pile in station.piles" :key="pile.id" class="pile-item">
              <div class="pile-header">
                <span class="pile-code">🔌 {{ pile.pileCode }}</span>
                <span :class="pile.pileType === 'DC' ? 'badge badge-warning' : 'badge badge-success'">
                  {{ pile.pileType === 'DC' ? '快充' : '慢充' }}
                </span>
                <span class="pile-power">{{ pile.power }}kW</span>
                <button class="btn btn-outline btn-sm" @click="openAddSpotModal(pile)">+ 添加车位</button>
              </div>

              <!-- Spots under this pile -->
              <div v-if="pile.parkingSpots?.length" class="spots-grid">
                <div v-for="spot in pile.parkingSpots" :key="spot.id" class="spot-item">
                  <span class="spot-code">{{ spot.spotCode }}</span>
                  <span class="spot-price">¥{{ spot.pricePerHour }}/小时</span>
                  <span :class="spot.status === 0 ? 'badge badge-success' : 'badge badge-danger'">
                    {{ spot.status === 0 ? '空闲' : '占用' }}
                  </span>
                </div>
              </div>
              <p v-else class="no-spots">暂无车位</p>
            </div>
          </div>
          <p v-else class="no-piles">暂无充电桩 - 点击"添加充电桩"开始配置</p>
        </div>
      </div>
    </main>

    <!-- Station Modal -->
    <div v-if="showStationModal" class="modal-overlay" @click.self="showStationModal = false">
      <div class="modal-content card">
        <h2>{{ editingId ? '编辑充电站' : '新增充电站' }}</h2>
        <form @submit.prevent="handleStationSubmit">
          <div class="grid grid-2">
            <div class="form-group">
              <label>名称 *</label>
              <input v-model="stationForm.name" type="text" class="form-control" required />
            </div>
            <div class="form-group">
              <label>联系电话</label>
              <input v-model="stationForm.contact" type="text" class="form-control" />
            </div>
          </div>
          <div class="form-group">
            <label>地址 *</label>
            <input v-model="stationForm.address" type="text" class="form-control" required />
          </div>
          <div class="grid grid-2">
            <div class="form-group">
              <label>城市</label>
              <input v-model="stationForm.city" type="text" class="form-control" />
            </div>
            <div class="form-group">
              <label>区域</label>
              <input v-model="stationForm.district" type="text" class="form-control" />
            </div>
          </div>
          <div class="modal-actions">
            <button type="button" class="btn btn-outline" @click="showStationModal = false">取消</button>
            <button type="submit" class="btn btn-primary">保存</button>
          </div>
        </form>
      </div>
    </div>

    <!-- Pile Modal -->
    <div v-if="showPileModal" class="modal-overlay" @click.self="showPileModal = false">
      <div class="modal-content card">
        <h2>添加充电桩到 {{ selectedStation?.name }}</h2>
        <form @submit.prevent="handlePileSubmit">
          <div class="grid grid-2">
            <div class="form-group">
              <label>充电桩编号 *</label>
              <input v-model="pileForm.pileCode" type="text" class="form-control" placeholder="如: ZGC-DC-001" required />
            </div>
            <div class="form-group">
              <label>充电类型</label>
              <select v-model="pileForm.pileType" class="form-control">
                <option value="DC">直流快充</option>
                <option value="AC">交流慢充</option>
              </select>
            </div>
          </div>
          <div class="grid grid-2">
            <div class="form-group">
              <label>功率 (kW)</label>
              <input v-model.number="pileForm.power" type="number" class="form-control" />
            </div>
            <div class="form-group">
              <label>品牌</label>
              <input v-model="pileForm.brand" type="text" class="form-control" placeholder="如: 特来电" />
            </div>
          </div>
          <div class="modal-actions">
            <button type="button" class="btn btn-outline" @click="showPileModal = false">取消</button>
            <button type="submit" class="btn btn-primary">创建充电桩</button>
          </div>
        </form>
      </div>
    </div>

    <!-- Spot Modal -->
    <div v-if="showSpotModal" class="modal-overlay" @click.self="showSpotModal = false">
      <div class="modal-content card">
        <h2>添加车位到 {{ selectedPile?.pileCode }}</h2>
        <form @submit.prevent="handleSpotSubmit">
          <div class="grid grid-2">
            <div class="form-group">
              <label>车位编号 *</label>
              <input v-model="spotForm.spotCode" type="text" class="form-control" placeholder="如: A01" required />
            </div>
            <div class="form-group">
              <label>车位类型</label>
              <select v-model="spotForm.spotType" class="form-control">
                <option value="STANDARD">标准车位</option>
                <option value="LARGE">大型车位</option>
              </select>
            </div>
          </div>
          <div class="grid grid-2">
            <div class="form-group">
              <label>停车费 (元/小时)</label>
              <input v-model.number="spotForm.pricePerHour" type="number" step="0.1" class="form-control" />
            </div>
            <div class="form-group">
              <label>服务费 (元)</label>
              <input v-model.number="spotForm.serviceFee" type="number" step="0.1" class="form-control" />
            </div>
          </div>
          <div class="modal-actions">
            <button type="button" class="btn btn-outline" @click="showSpotModal = false">取消</button>
            <button type="submit" class="btn btn-primary">创建车位</button>
          </div>
        </form>
      </div>
    </div>
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

.station-card {
  margin-bottom: 20px;
}

.station-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 15px;
  padding-bottom: 15px;
  border-bottom: 1px solid #eee;
}

.station-header h3 {
  font-size: 1.2rem;
  margin-bottom: 5px;
}

.text-light {
  color: var(--text-light);
  font-size: 0.9rem;
}

.actions {
  display: flex;
  gap: 8px;
}

.piles-section {
  margin-top: 15px;
}

.pile-item {
  background: #f8f9fa;
  padding: 15px;
  border-radius: 8px;
  margin-bottom: 10px;
}

.pile-header {
  display: flex;
  align-items: center;
  gap: 15px;
  margin-bottom: 10px;
}

.pile-code {
  font-weight: 600;
}

.pile-power {
  color: var(--text-light);
}

.spots-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 10px;
}

.spot-item {
  background: white;
  padding: 10px 15px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  gap: 10px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.05);
}

.spot-code {
  font-weight: 600;
}

.spot-price {
  color: var(--text-light);
  font-size: 0.85rem;
}

.no-piles, .no-spots {
  color: var(--text-light);
  font-size: 0.9rem;
  padding: 10px 0;
}

/* Modal */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0,0,0,0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-content {
  width: 90%;
  max-width: 500px;
}

.modal-content h2 {
  margin-bottom: 20px;
}

.modal-actions {
  display: flex;
  gap: 10px;
  margin-top: 20px;
}

.modal-actions button {
  flex: 1;
}
</style>
