<script setup>
import { ref, onMounted, computed, watch } from 'vue'
import Header from '@/components/common/Header.vue'
import StationCard from '@/components/business/StationCard.vue'
import { stationApi } from '@/api'
import { useLocation } from '@/composables/useLocation'
import { useBaiduMap } from '@/composables/useBaiduMap'

const stations = ref([])
const loading = ref(true)
const searchKeyword = ref('')
const viewMode = ref('list')

// Composables
const { userLocation, getUserLocation, calculateDistance } = useLocation()
const { mapInstance, initMap, addMarker, setViewport } = useBaiduMap('map-container')

onMounted(async () => {
  await loadStations()
})

async function loadStations() {
  loading.value = true
  
  try {
    // 1. 获取位置
    await getUserLocation()
    
    // 2. 获取站点数据
    const res = await stationApi.getAll()
    let rawStations = res.data.data || []
    
    // 3. 计算距离并排序
    if (userLocation.value) {
      stations.value = rawStations.map(s => ({
        ...s,
        distance: (s.longitude && s.latitude) 
          ? calculateDistance(
              userLocation.value.longitude, userLocation.value.latitude, 
              s.longitude, s.latitude
            )
          : null
      })).sort((a, b) => {
        if (a.distance === null && b.distance === null) return 0
        if (a.distance === null) return 1
        if (b.distance === null) return -1
        return parseFloat(a.distance) - parseFloat(b.distance)
      })
    } else {
      stations.value = rawStations
    }

  } catch (error) {
    console.error('Failed to load data:', error)
  } finally {
    loading.value = false
  }
}

// 分页逻辑
const currentPage = ref(1)
const pageSize = 9

const filteredStations = computed(() => {
  if (!searchKeyword.value) return stations.value
  const keyword = searchKeyword.value.toLowerCase()
  return stations.value.filter(s => 
    s.name.toLowerCase().includes(keyword) ||
    (s.address && s.address.toLowerCase().includes(keyword)) ||
    (s.city && s.city.toLowerCase().includes(keyword))
  )
})

const paginatedStations = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  const end = start + pageSize
  return filteredStations.value.slice(start, end)
})

const totalPages = computed(() => Math.ceil(filteredStations.value.length / pageSize))

function changePage(page) {
  if (page >= 1 && page <= totalPages.value) {
    currentPage.value = page
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }
}

// 地图模式切换
function toggleView() {
  viewMode.value = viewMode.value === 'list' ? 'map' : 'list'
}

// 监听视图切换，初始化地图
watch(viewMode, async (newVal) => {
  if (newVal === 'map') {
    // 给一点时间让 DOM 渲染 map-container
    setTimeout(async () => {
        await initMapWithMarkers()
    }, 100)
  }
})

async function initMapWithMarkers() {
    let center = { lng: 116.404, lat: 39.915 } // 默认北京
    if (userLocation.value) {
        center = { lng: userLocation.value.longitude, lat: userLocation.value.latitude }
    } else if (stations.value.length > 0) {
        center = { lng: stations.value[0].longitude, lat: stations.value[0].latitude }
    }

    await initMap(center)

    // 添加用户位置
    if (userLocation.value) {
        addMarker({
            lng: userLocation.value.longitude, 
            lat: userLocation.value.latitude
        }, {
            label: {
                text: '我的位置',
                style: {
                    color: '#007BFF',
                    fontSize: '12px',
                    border: '1px solid #007BFF',
                    padding: '2px'
                }
            }
        })
    }

    // 添加站点
    const points = []
    stations.value.forEach(station => {
        if (!station.longitude || !station.latitude) return
        
        const pt = { lng: station.longitude, lat: station.latitude }
        points.push(pt)
        
        // InfoWindow 内容
        const content = `
            <div style="padding:8px;min-width:200px">
                <h4 style="margin:0 0 8px 0;color:#333">${station.name}</h4>
                <p style="margin:0;font-size:12px;color:#666">${station.address || ''}</p>
                <p style="margin:5px 0;font-size:12px;color:#00b894">📍 距离: ${station.distance || '?'} km</p>
                <p style="margin:5px 0;font-size:12px">⚡ 充电桩: ${station.piles?.length || 0} 个</p>
                <a href="#/station/${station.id}" style="color:#00b894;text-decoration:none;font-weight:bold">查看详情 →</a>
            </div>
        `
        
        addMarker(pt, {
            infoWindow: {
                title: station.name,
                content: content
            }
        })
    })

    // 调整视野
    if (points.length > 0) {
       setViewport(points) 
    }
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
        <div class="section-header">
          <h2 class="section-title">附近充电站</h2>
          <div class="view-toggle">
            <button class="btn btn-outline" :class="{ active: viewMode === 'list' }" @click="toggleView">列表</button>
            <button class="btn btn-outline" :class="{ active: viewMode === 'map' }" @click="toggleView">地图</button>
          </div>
        </div>
        
        <div v-if="loading" class="text-center mt-2">
          <div class="spinner"></div>
        </div>

        <div v-if="viewMode === 'map'" id="map-container" class="map-container"></div>

        <div v-else class="grid grid-3">
          <StationCard 
            v-for="station in paginatedStations" 
            :key="station.id"
            :station="station"
          />
        </div>

        <div v-if="!loading && viewMode === 'list' && totalPages > 1" class="pagination-controls">
          <button 
            class="btn btn-outline" 
            :disabled="currentPage === 1"
            @click="changePage(currentPage - 1)"
          >
            上一页
          </button>
          <span class="page-info">{{ currentPage }} / {{ totalPages }}</span>
          <button 
            class="btn btn-outline" 
            :disabled="currentPage === totalPages"
            @click="changePage(currentPage + 1)"
          >
            下一页
          </button>
        </div>

        <p v-if="!loading && filteredStations.length === 0" class="text-center mt-2">
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

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 30px;
}

.section-title {
  margin-bottom: 0;
}

.view-toggle .btn {
  border-radius: 20px;
  padding: 5px 15px;
  margin-left: 10px;
}

.view-toggle .btn.active {
  background: var(--primary);
  color: white;
}

.map-container {
  width: 100%;
  height: 500px;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 5px 15px rgba(0,0,0,0.1);
  margin-top: 20px;
}



.pagination-controls {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 20px;
  margin-top: 30px;
}

.page-info {
  font-weight: bold;
  color: var(--text);
}
</style>
