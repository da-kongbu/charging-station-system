<script>
export default { name: 'Home' }
</script>

<script setup>
import { ref, onMounted, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import StationCard from '@/components/business/StationCard.vue'
import api, { stationApi } from '@/api'
import { useLocation } from '@/composables/useLocation'
import { useBaiduMap } from '@/composables/useBaiduMap'
import { autoImportNearbyStations } from '@/services/autoImport'
import { useSmartSearchStore } from '@/stores/smartSearch'

const router = useRouter()
const smartStore = useSmartSearchStore()

const stations = ref([])
const loading = ref(true)
const searchKeyword = ref('')
const viewMode = ref('list')

// 搜索状态（本地 UI 状态，数据存 store）
const smartLoading = ref(false)

const importStatus = ref('idle') // idle | importing | done | skipped
const importProgress = ref('')
const importedCount = ref(0)

const { userLocation, getUserLocation, calculateDistance } = useLocation()
const { mapInstance, initMap, addMarker, setViewport } = useBaiduMap('map-container')

onMounted(async () => {
  await loadStations()
})

async function loadStations() {
  loading.value = true

  try {
    await getUserLocation()

    // 先查后端有没有充电站数据
    let res = await stationApi.getAll()
    let rawStations = res.data.data || []

    // 数据库为空且已登录 → 触发自动导入
    const userStr = localStorage.getItem('user')
    const user = userStr ? JSON.parse(userStr) : {}

    if (rawStations.length === 0 && user.id) {
      importStatus.value = 'importing'
      importProgress.value = '正在准备搜索附近充电站...'

      try {
        const count = await autoImportNearbyStations(
          api,
          (msg) => { importProgress.value = msg }
        )
        importedCount.value = count
        importStatus.value = 'done'
      } catch (err) {
        console.error('[Home] 导入失败:', err)
        importStatus.value = 'skipped'
      }

      // 重新加载站点列表
      res = await stationApi.getAll()
      rawStations = res.data.data || []
    } else {
      importStatus.value = 'skipped'
    }

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

const currentPage = ref(1)
const pageSize = 9

const filteredStations = computed(() => {
  // AI 模式激活时不再显示普通列表
  if (smartStore.active) return []
  return stations.value
})

const paginatedStations = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return filteredStations.value.slice(start, start + pageSize)
})

const totalPages = computed(() => Math.ceil(filteredStations.value.length / pageSize))

// AI 搜索：调用 AI Agent 获取推荐站点
async function aiSearch() {
  const query = searchKeyword.value.trim()
  if (!query || smartLoading.value) return

  smartLoading.value = true

  try {
    const body = { question: query }
    if (userLocation.value) {
      body.lat = userLocation.value.latitude
      body.lng = userLocation.value.longitude
    }

    const response = await api.post('/ai/agent', body)
    const json = response.data

    if (json.code === 200 && json.data) {
      const data = json.data
      const summaryText = data.content || ''

      if (data.type === 'stations' && data.data?.length) {
        const mappedStations = data.data.map(s => ({
          id: s.id || s['ID'],
          name: s['名称'] || s.name,
          address: s['地址'] || s.address,
          city: s['城市'] || s.city,
          distance: s['距离(km)'] || s.distanceKm,
          availablePiles: s['当前可用桩数'] ?? s.availablePileCount ?? null,
          totalPiles: s['总桩数'] ?? s.pileCount ?? null,
          piles: [],
          status: 1,
          _smartRecommended: true
        }))
        smartStore.setResults(mappedStations, summaryText)
      } else {
        smartStore.setResults([], summaryText || '未找到相关推荐')
      }
    } else {
      smartStore.setResults([], json.message || '未找到相关推荐')
    }
  } catch (error) {
    console.error('AI 搜索失败:', error)
    smartStore.setResults([], '搜索失败，请稍后再试')
  } finally {
    smartLoading.value = false
  }
}

function clearSearchResults() {
  smartStore.clear()
  searchKeyword.value = ''
}

function formatSummary(text) {
  if (!text) return ''
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/\n\n/g, '<div style="height:8px"></div>')
    .replace(/\n/g, '<br>')
}

function goToStation(id) {
  router.push(`/station/${id}`)
}

function toggleView() {
  viewMode.value = viewMode.value === 'list' ? 'map' : 'list'
}

watch(viewMode, async (newVal) => {
  if (newVal === 'map') {
    setTimeout(async () => {
      await initMapWithMarkers()
    }, 100)
  }
})

async function initMapWithMarkers() {
  let center = { lng: 116.404, lat: 39.915 }
  if (userLocation.value) {
    center = { lng: userLocation.value.longitude, lat: userLocation.value.latitude }
  } else if (stations.value.length > 0) {
    center = { lng: stations.value[0].longitude, lat: stations.value[0].latitude }
  }

  await initMap(center)

  if (userLocation.value) {
    addMarker({
      lng: userLocation.value.longitude,
      lat: userLocation.value.latitude
    }, {
      label: {
        text: '我的位置',
        style: { color: '#007BFF', fontSize: '12px', border: '1px solid #007BFF', padding: '2px' }
      }
    })
  }

  const points = []
  stations.value.forEach(station => {
    if (!station.longitude || !station.latitude) return
    const pt = { lng: station.longitude, lat: station.latitude }
    points.push(pt)

    const content = `
      <div style="padding:8px;min-width:200px">
        <h4 style="margin:0 0 8px 0;color:#333">${station.name}</h4>
        <p style="margin:0;font-size:12px;color:#666">${station.address || ''}</p>
        <p style="margin:5px 0;font-size:12px;color:#00b894">距离: ${station.distance || '?'} km</p>
        <p style="margin:5px 0;font-size:12px">充电桩: ${station.piles?.length || 0} 个</p>
        <a href="/station/${station.id}" style="color:#00b894;text-decoration:none;font-weight:bold">查看详情</a>
      </div>
    `

    addMarker(pt, {
      infoWindow: { title: station.name, content }
    })
  })

  if (points.length > 0) {
    setViewport(points)
  }
}
</script>

<template>
    <div>
    <!-- Hero -->
    <div class="hero">
      <v-container class="text-center">
        <h1 class="text-white text-h4 text-md-h3 font-weight-bold mb-2">找到您身边的充电站</h1>
        <p class="text-white text-body-1 mb-6" style="opacity: 0.9;">便捷预约，轻松充电</p>
        <v-responsive max-width="600" class="mx-auto">
          <div class="d-flex align-center">
            <v-text-field
              v-model="searchKeyword"
              variant="solo"
              rounded="pill"
              prepend-inner-icon="mdi-robot-outline"
              placeholder="试试说：附近有快充站吗？明天下午要充2小时"
              hide-details
              bg-color="white"
              @keydown.enter="aiSearch"
              class="flex-grow-1"
            />
            <v-btn
              icon="mdi-send"
              variant="flat"
              color="white"
              size="small"
              class="ml-2"
              :loading="smartLoading"
              @click="aiSearch"
            />
          </div>
        </v-responsive>
      </v-container>
    </div>

    <!-- Stations -->
    <v-container class="py-8">
      <div class="d-flex align-center justify-space-between mb-6">
        <h2 class="text-h5 font-weight-bold">附近充电站</h2>
        <v-btn-toggle v-model="viewMode" mandatory density="compact" variant="outlined" color="primary">
          <v-btn value="list" prepend-icon="mdi-view-list">列表</v-btn>
          <v-btn value="map" prepend-icon="mdi-map">地图</v-btn>
        </v-btn-toggle>
      </div>

      <!-- 首次导入加载界面 -->
      <div v-if="loading && importStatus === 'importing'" class="text-center py-12">
        <v-progress-circular indeterminate color="primary" size="64" />
        <h3 class="text-h6 mt-4 mb-2">正在为您搜索附近充电站</h3>
        <p class="text-body-2 text-grey">{{ importProgress }}</p>
        <p class="text-caption text-grey mt-2">首次加载需要一些时间，请稍候...</p>
      </div>

      <template v-else>
        <!-- 导入成功提示 -->
        <v-alert v-if="importStatus === 'done' && importedCount > 0" type="success" variant="tonal" closable class="mb-4">
          已为您找到并导入 {{ importedCount }} 个附近充电站
        </v-alert>

        <!-- 普通加载 -->
        <div v-if="loading" class="text-center py-8">
          <v-progress-circular indeterminate color="primary" size="48" />
        </div>

        <template v-else>
        <!-- AI 推荐态：从 store 读取 -->
        <div v-if="smartStore.hasResults" class="mb-6">
          <div class="d-flex align-center justify-space-between mb-4">
            <div class="d-flex align-center ga-2">
              <v-icon color="primary">mdi-robot-outline</v-icon>
              <h2 class="text-h6 font-weight-bold">AI 智能推荐</h2>
              <v-chip size="x-small" color="primary" variant="tonal">{{ smartStore.stations.length }} 个结果</v-chip>
            </div>
            <v-btn variant="text" size="small" prepend-icon="mdi-close" @click="clearSearchResults">清空推荐</v-btn>
          </div>
          <v-alert variant="tonal" color="primary" class="mb-4 smart-summary" rounded="lg">
            <div class="text-body-2" v-html="formatSummary(smartStore.summary)" />
          </v-alert>
          <v-row>
            <v-col v-for="station in smartStore.stations" :key="station.id" cols="12" sm="6" md="4" class="d-flex">
              <v-card hover rounded="lg" class="w-100 station-card d-flex flex-column" @click="goToStation(station.id)">
                <div class="station-image d-flex align-center justify-center">
                  <v-icon size="56" color="white">mdi-ev-station</v-icon>
                </div>
                <v-card-text class="flex-grow-1 d-flex flex-column pa-4">
                  <div class="d-flex align-start ga-2 mb-1">
                    <v-chip size="x-small" color="primary" variant="flat" class="flex-shrink-0 mt-1">AI推荐</v-chip>
                    <div class="text-subtitle-1 font-weight-bold station-title">{{ station.name }}</div>
                  </div>
                  <div class="text-body-2 text-grey-darken-1 mb-2 station-address">
                    <v-icon size="14" class="mr-1">mdi-map-marker</v-icon>
                    {{ station.address }}
                  </div>
                  <v-spacer />
                  <div class="station-footer d-flex align-center justify-space-between">
                    <span v-if="station.availablePiles != null" class="text-caption"
                      :class="station.availablePiles > 0 ? 'text-success' : 'text-grey'">
                      可用桩 {{ station.availablePiles }}/{{ station.totalPiles ?? '?' }}
                    </span>
                    <span v-else class="text-caption text-grey">桩位信息加载中</span>
                    <span v-if="station.distance" class="text-caption text-primary font-weight-bold">
                      {{ station.distance }} km
                    </span>
                  </div>
                </v-card-text>
              </v-card>
            </v-col>
          </v-row>
        </div>

        <!-- AI 搜索了但没有结果 -->
        <div v-else-if="smartStore.active && !smartStore.stations.length" class="mb-6">
          <div class="d-flex align-center justify-space-between mb-4">
            <div class="d-flex align-center ga-2">
              <v-icon color="primary">mdi-robot-outline</v-icon>
              <h2 class="text-h6 font-weight-bold">AI 智能推荐</h2>
            </div>
            <v-btn variant="text" size="small" prepend-icon="mdi-close" @click="clearSearchResults">清空推荐</v-btn>
          </div>
          <v-alert variant="tonal" color="primary" class="mb-4 smart-summary" rounded="lg">
            <div class="text-body-2" v-html="formatSummary(smartStore.summary)" />
          </v-alert>
          <div class="text-center py-6 text-grey">
            未找到匹配的充电站，换个说法试试？
          </div>
        </div>

        <!-- 普通态：站点列表 -->
        <template v-if="!smartStore.active">

          <div v-if="viewMode === 'map'" class="map-container">
            <div id="map-container" style="width:100%;height:500px;"></div>
          </div>

          <template v-else>
            <v-row>
              <v-col v-for="station in paginatedStations" :key="station.id" cols="12" sm="6" md="4" class="d-flex">
                <StationCard :station="station" class="w-100" />
              </v-col>
            </v-row>

            <div v-if="totalPages > 1" class="d-flex justify-center mt-6">
              <v-pagination v-model="currentPage" :length="totalPages" total-visible="5" color="primary" rounded="circle" />
            </div>
          </template>

          <div v-if="filteredStations.length === 0 && !loading" class="text-center py-12">
            <v-icon size="64" color="grey-lighten-1">mdi-ev-station</v-icon>
            <p class="text-grey mt-4">暂无充电站数据</p>
          </div>
        </template>
        </template>
      </template>
    </v-container>
  </div>
</template>

<style scoped>
.hero {
  background: linear-gradient(135deg, #00b894 0%, #0984e3 100%);
  padding: 80px 0;
}

.map-container {
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.station-image {
  background: linear-gradient(135deg, #00b894, #0984e3);
  height: 100px;
}

.station-title {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 1.3;
  min-height: 2.6em;
}

.station-address {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 1.4;
  min-height: 2.8em;
}

.station-footer {
  min-height: 20px;
}
</style>
