<script setup>
import { ref, onMounted } from 'vue'
import Header from '@/components/common/Header.vue'
import { stationApi } from '@/api'

const stations = ref([])
const loading = ref(true)
const searchKeyword = ref('')

onMounted(async () => {
  await loadStations()
})

async function loadStations() {
  loading.value = true
  try {
    const response = await stationApi.getAll()
    stations.value = response.data.data || []
  } catch (error) {
    console.error('Failed to load stations:', error)
  } finally {
    loading.value = false
  }
}

const filteredStations = () => {
  if (!searchKeyword.value) return stations.value
  const keyword = searchKeyword.value.toLowerCase()
  return stations.value.filter(s => 
    s.name.toLowerCase().includes(keyword) ||
    (s.address && s.address.toLowerCase().includes(keyword)) ||
    (s.city && s.city.toLowerCase().includes(keyword))
  )
}
</script>

<template>
  <div class="page">
    <Header />
    
    <main class="main">
      <section class="hero">
        <div class="container">
          <h1>找到您身边的充电站</h1>
          <p>便捷预约，轻松充电</p>
          <div class="search-box">
            <input 
              v-model="searchKeyword" 
              type="text" 
              placeholder="搜索充电站名称或地址..."
              class="search-input"
            />
            <button class="btn btn-primary">搜索</button>
          </div>
        </div>
      </section>

      <section class="stations-section container">
        <h2 class="section-title">附近充电站</h2>
        
        <div v-if="loading" class="text-center mt-2">
          <div class="spinner"></div>
        </div>

        <div v-else class="grid grid-3">
          <RouterLink 
            v-for="station in filteredStations()" 
            :key="station.id"
            :to="`/station/${station.id}`"
            class="station-card card"
          >
            <div class="station-image">
              <span class="station-icon">🔌</span>
            </div>
            <div class="station-info">
              <h3>{{ station.name }}</h3>
              <p class="station-address">📍 {{ station.city }} {{ station.district }} {{ station.address }}</p>
              <div class="station-meta">
                <span class="badge badge-success">营业中</span>
                <span class="pile-count">{{ station.piles?.length || 0 }} 个充电桩</span>
              </div>
            </div>
          </RouterLink>
        </div>

        <p v-if="!loading && filteredStations().length === 0" class="text-center mt-2">
          暂无充电站数据
        </p>
      </section>
    </main>
  </div>
</template>

<style scoped>
.page {
  min-height: 100vh;
}

.hero {
  background: linear-gradient(135deg, #00b894 0%, #0984e3 100%);
  color: white;
  padding: 80px 0;
  text-align: center;
}

.hero h1 {
  font-size: 2.5rem;
  margin-bottom: 10px;
}

.hero p {
  font-size: 1.2rem;
  opacity: 0.9;
  margin-bottom: 30px;
}

.search-box {
  display: flex;
  max-width: 500px;
  margin: 0 auto;
  background: white;
  border-radius: 50px;
  padding: 6px;
  box-shadow: 0 10px 30px rgba(0,0,0,0.2);
}

.search-input {
  flex: 1;
  border: none;
  padding: 12px 20px;
  font-size: 1rem;
  outline: none;
  border-radius: 50px;
}

.search-box .btn {
  border-radius: 50px;
  padding: 12px 30px;
}

.stations-section {
  padding: 50px 0;
}

.section-title {
  font-size: 1.8rem;
  margin-bottom: 30px;
  color: var(--text);
}

.station-card {
  cursor: pointer;
  transition: all 0.3s ease;
  overflow: hidden;
}

.station-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 10px 25px rgba(0,0,0,0.1);
}

.station-image {
  height: 120px;
  background: linear-gradient(135deg, #dfe6e9 0%, #b2bec3 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  margin: -20px -20px 15px -20px;
}

.station-icon {
  font-size: 3rem;
}

.station-info h3 {
  font-size: 1.1rem;
  margin-bottom: 8px;
  color: var(--text);
}

.station-address {
  font-size: 0.9rem;
  color: var(--text-light);
  margin-bottom: 12px;
}

.station-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.pile-count {
  font-size: 0.85rem;
  color: var(--text-light);
}
</style>
