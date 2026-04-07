<script setup>
import { ref, onMounted, computed, watch } from 'vue'
import StationCard from '@/components/business/StationCard.vue'
import { stationApi } from '@/api'
import { useLocation } from '@/composables/useLocation'
import { useBaiduMap } from '@/composables/useBaiduMap'

const stations = ref([])
const loading = ref(true)
const searchKeyword = ref('')
const viewMode = ref('list')

const { userLocation, getUserLocation, calculateDistance } = useLocation()
const { mapInstance, initMap, addMarker, setViewport } = useBaiduMap('map-container')

onMounted(async () => {
  await loadStations()
})

async function loadStations() {
  loading.value = true

  try {
    await getUserLocation()

    const res = await stationApi.getAll()
    let rawStations = res.data.data || []

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
  return filteredStations.value.slice(start, start + pageSize)
})

const totalPages = computed(() => Math.ceil(filteredStations.value.length / pageSize))

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
        <v-responsive max-width="500" class="mx-auto">
          <v-text-field
            v-model="searchKeyword"
            variant="solo"
            rounded="pill"
            prepend-inner-icon="mdi-magnify"
            placeholder="搜索充电站名称或地址..."
            hide-details
            bg-color="white"
          />
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

      <div v-if="loading" class="text-center py-8">
        <v-progress-circular indeterminate color="primary" size="48" />
      </div>

      <template v-else>
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

        <div v-if="filteredStations.length === 0" class="text-center py-12">
          <v-icon size="64" color="grey-lighten-1">mdi-ev-station</v-icon>
          <p class="text-grey mt-4">暂无充电站数据</p>
        </div>
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
</style>
