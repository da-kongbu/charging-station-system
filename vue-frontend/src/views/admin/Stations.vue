<script setup>
import { ref, onMounted, nextTick } from 'vue'
import Sidebar from '@/components/admin/Sidebar.vue'
import { adminApi } from '@/api'
import { useBaiduMap } from '@/composables/useBaiduMap'

const stations = ref([])
const loading = ref(true)
const showStationModal = ref(false)
const showPileModal = ref(false)
const showSpotModal = ref(false)
const editingId = ref(null)
const selectedStation = ref(null)
const selectedPile = ref(null)
const snackbar = ref({ show: false, message: '', color: 'success' })
const confirmDialog = ref({ show: false, title: '', message: '', action: null })

const { initMap, mapInstance, loadBMapScript } = useBaiduMap('station-map-picker')
const mapMarker = ref(null)

const stationForm = ref({
  name: '', address: '', city: '北京', district: '', contact: '',
  businessHours: '24小时营业', longitude: null, latitude: null,
})

const pileForm = ref({
  pileCode: '', pileType: 'DC', power: 120, voltage: 750, current: 160,
  brand: '', connectorType: '国标DC',
})

const spotForm = ref({
  spotCode: '', spotType: 'STANDARD', pricePerHour: 5, serviceFee: 0.8,
})

onMounted(async () => { await loadStations() })

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

async function initMapPicker() {
  try {
    const BMapGL = await loadBMapScript()
    let centerLng = stationForm.value.longitude || 116.404
    let centerLat = stationForm.value.latitude || 39.915
    const map = await initMap({ lng: centerLng, lat: centerLat }, 13)
    if (!map) return

    map.enableScrollWheelZoom(true)
    map.addControl(new BMapGL.ScaleControl())
    map.addControl(new BMapGL.ZoomControl())

    if (stationForm.value.longitude && stationForm.value.latitude) {
      addMapMarker(stationForm.value.longitude, stationForm.value.latitude)
    }

    map.addEventListener('click', function(e) {
      const pt = e.latlng || e.point
      if (!pt) return
      stationForm.value.longitude = pt.lng
      stationForm.value.latitude = pt.lat
      addMapMarker(pt.lng, pt.lat)

      const geoc = new BMapGL.Geocoder()
      geoc.getLocation(pt, function(result) {
        if (result) {
          stationForm.value.address = result.address
          if (result.addressComponents) {
            stationForm.value.city = result.addressComponents.city || stationForm.value.city
            stationForm.value.district = result.addressComponents.district || ''
          }
        }
      })
    })
  } catch (error) {
    console.error('Map init error:', error)
  }
}

function addMapMarker(lng, lat) {
  if (!mapInstance.value || typeof window.BMapGL === 'undefined') return
  const BMapGL = window.BMapGL
  if (mapMarker.value) mapInstance.value.removeOverlay(mapMarker.value)
  const pt = new BMapGL.Point(lng, lat)
  mapMarker.value = new BMapGL.Marker(pt)
  mapInstance.value.addOverlay(mapMarker.value)
  mapInstance.value.panTo(pt)
}

async function searchAddress() {
  if (!mapInstance.value || !stationForm.value.address) return
  const BMapGL = window.BMapGL
  const local = new BMapGL.LocalSearch(mapInstance.value, {
    onSearchComplete: function(results) {
      if (results && results.getNumPois() > 0) {
        const poi = results.getPoi(0)
        stationForm.value.longitude = poi.point.lng
        stationForm.value.latitude = poi.point.lat
        addMapMarker(poi.point.lng, poi.point.lat)
        mapInstance.value.centerAndZoom(poi.point, 15)
      }
    }
  })
  local.search(stationForm.value.address)
}

function openAddStationModal() {
  editingId.value = null
  stationForm.value = { name: '', address: '', city: '北京', district: '', contact: '', businessHours: '24小时营业', longitude: null, latitude: null }
  showStationModal.value = true
  nextTick(() => setTimeout(initMapPicker, 200))
}

function openEditStationModal(station) {
  editingId.value = station.id
  stationForm.value = { ...station }
  showStationModal.value = true
  nextTick(() => setTimeout(initMapPicker, 200))
}

async function handleStationSubmit() {
  try {
    if (editingId.value) {
      await adminApi.updateStation(editingId.value, stationForm.value)
      snackbar.value = { show: true, message: '更新成功', color: 'success' }
    } else {
      await adminApi.createStation(stationForm.value)
      snackbar.value = { show: true, message: '创建成功', color: 'success' }
    }
    showStationModal.value = false
    await loadStations()
  } catch (error) {
    snackbar.value = { show: true, message: error.response?.data?.message || '操作失败', color: 'error' }
  }
}

async function handleDeleteStation(id) {
  confirmDialog.value = {
    show: true,
    title: '删除充电站',
    message: '确定删除此充电站？此操作不可恢复。',
    action: async () => {
      try {
        await adminApi.deleteStation(id)
        snackbar.value = { show: true, message: '删除成功', color: 'success' }
        await loadStations()
      } catch (error) {
        snackbar.value = { show: true, message: error.response?.data?.message || '删除失败', color: 'error' }
      }
    }
  }
}

function openAddPileModal(station) {
  selectedStation.value = station
  pileForm.value = { pileCode: '', pileType: 'DC', power: 120, voltage: 750, current: 160, brand: '', connectorType: '国标DC' }
  showPileModal.value = true
}

async function handlePileSubmit() {
  try {
    await adminApi.createPile(selectedStation.value.id, { ...pileForm.value, status: 1 })
    snackbar.value = { show: true, message: '充电桩创建成功', color: 'success' }
    showPileModal.value = false
    await loadStations()
  } catch (error) {
    snackbar.value = { show: true, message: error.response?.data?.message || '创建失败', color: 'error' }
  }
}

function openAddSpotModal(pile) {
  selectedPile.value = pile
  spotForm.value = { spotCode: '', spotType: 'STANDARD', pricePerHour: 5, serviceFee: 0.8 }
  showSpotModal.value = true
}

async function handleSpotSubmit() {
  try {
    await adminApi.createSpot(selectedPile.value.id, { ...spotForm.value, status: 0 })
    snackbar.value = { show: true, message: '车位创建成功', color: 'success' }
    showSpotModal.value = false
    await loadStations()
  } catch (error) {
    snackbar.value = { show: true, message: error.response?.data?.message || '创建失败', color: 'error' }
  }
}

async function handleConfirm() {
  confirmDialog.value.show = false
  if (confirmDialog.value.action) await confirmDialog.value.action()
}
</script>

<template>
  <div class="d-flex" style="min-height: 100vh;">
    <Sidebar />

    <v-main>
      <v-container class="pa-6">
        <div class="d-flex align-center justify-space-between mb-6">
          <h1 class="text-h5 font-weight-bold">充电站管理</h1>
          <v-btn color="primary" prepend-icon="mdi-plus" @click="openAddStationModal">新增充电站</v-btn>
        </div>

        <div v-if="loading" class="text-center py-8">
          <v-progress-circular indeterminate color="primary" size="48" />
        </div>

        <template v-else>
          <div v-if="stations.length === 0" class="text-center py-12">
            <v-icon size="64" color="grey-lighten-1">mdi-ev-station</v-icon>
            <p class="text-grey mt-4">暂无充电站数据</p>
          </div>

          <v-card v-for="station in stations" :key="station.id" class="mb-4" rounded="lg">
            <v-card-text class="pa-5">
              <div class="d-flex align-start justify-space-between mb-3">
                <div>
                  <div class="text-subtitle-1 font-weight-bold">{{ station.name }}</div>
                  <div class="text-body-2 text-grey mt-1">
                    <v-icon size="16" class="mr-1">mdi-map-marker</v-icon>
                    {{ station.city }} {{ station.district }} {{ station.address }}
                  </div>
                </div>
                <div class="d-flex ga-2">
                  <v-btn size="small" color="primary" variant="tonal" prepend-icon="mdi-plus" @click="openAddPileModal(station)">充电桩</v-btn>
                  <v-btn size="small" variant="outlined" prepend-icon="mdi-pencil" @click="openEditStationModal(station)">编辑</v-btn>
                  <v-btn size="small" color="error" variant="text" icon="mdi-delete" @click="handleDeleteStation(station.id)" />
                </div>
              </div>

              <v-divider class="mb-3" />

              <div v-if="station.piles?.length">
                <v-expansion-panels variant="accordion" multiple>
                  <v-expansion-panel v-for="pile in station.piles" :key="pile.id">
                    <v-expansion-panel-title>
                      <div class="d-flex align-center ga-3">
                        <v-icon :color="pile.pileType === 'DC' ? 'warning' : 'info'">
                          {{ pile.pileType === 'DC' ? 'mdi-flash' : 'mdi-current-ac' }}
                        </v-icon>
                        <span class="font-weight-medium">{{ pile.pileCode }}</span>
                        <v-chip :color="pile.pileType === 'DC' ? 'warning' : 'info'" size="x-small" variant="tonal">
                          {{ pile.pileType === 'DC' ? '快充' : '慢充' }}
                        </v-chip>
                        <span class="text-caption text-grey">{{ pile.power }}kW</span>
                      </div>
                    </v-expansion-panel-title>
                    <v-expansion-panel-text>
                      <div class="d-flex align-center justify-space-between mb-3">
                        <span class="text-body-2 text-grey">车位列表</span>
                        <v-btn size="x-small" variant="outlined" prepend-icon="mdi-plus" @click="openAddSpotModal(pile)">添加车位</v-btn>
                      </div>

                      <div v-if="pile.parkingSpots?.length" class="d-flex flex-wrap ga-2">
                        <v-sheet v-for="spot in pile.parkingSpots" :key="spot.id" rounded="lg" class="pa-3 d-flex align-center ga-2" border>
                          <span class="text-body-2 font-weight-medium">{{ spot.spotCode }}</span>
                          <span class="text-caption text-grey">¥{{ spot.pricePerHour }}/时</span>
                          <v-chip :color="spot.status === 0 ? 'success' : 'error'" size="x-small" variant="tonal">
                            {{ spot.status === 0 ? '空闲' : '占用' }}
                          </v-chip>
                        </v-sheet>
                      </div>
                      <div v-else class="text-body-2 text-grey py-2">暂无车位</div>
                    </v-expansion-panel-text>
                  </v-expansion-panel>
                </v-expansion-panels>
              </div>
              <div v-else class="text-body-2 text-grey py-2">暂无充电桩 — 点击"充电桩"按钮开始配置</div>
            </v-card-text>
          </v-card>
        </template>
      </v-container>
    </v-main>

    <!-- Station Dialog -->
    <v-dialog v-model="showStationModal" max-width="720" scrollable>
      <v-card rounded="lg">
        <v-card-title class="d-flex align-center justify-space-between">
          <span>{{ editingId ? '编辑充电站' : '新增充电站' }}</span>
          <v-btn icon="mdi-close" variant="text" size="small" @click="showStationModal = false" />
        </v-card-title>
        <v-card-text>
          <v-form @submit.prevent="handleStationSubmit">
            <v-row dense>
              <v-col cols="12" sm="6">
                <v-text-field v-model="stationForm.name" label="名称" variant="outlined" />
              </v-col>
              <v-col cols="12" sm="6">
                <v-text-field v-model="stationForm.contact" label="联系电话" variant="outlined" />
              </v-col>
            </v-row>

            <v-text-field v-model="stationForm.address" label="地址" variant="outlined" class="mb-2" hint="输入后点击搜索，或直接在地图上点击选择位置" persistent-hint>
              <template v-slot:append-inner>
                <v-btn size="small" variant="tonal" prepend-icon="mdi-magnify" @click="searchAddress">搜索</v-btn>
              </template>
            </v-text-field>

            <v-row dense>
              <v-col cols="12" sm="6">
                <v-text-field v-model="stationForm.city" label="城市" variant="outlined" />
              </v-col>
              <v-col cols="12" sm="6">
                <v-text-field v-model="stationForm.district" label="区域" variant="outlined" />
              </v-col>
            </v-row>

            <div class="text-body-2 text-grey mb-2">
              <v-icon size="16" class="mr-1">mdi-map-marker</v-icon>位置选择（点击地图标记位置）
            </div>
            <div id="station-map-picker" style="width:100%;height:300px;border-radius:8px;border:2px solid #e0e0e0;overflow:hidden;" />
            <v-sheet v-if="stationForm.longitude" color="green-lighten-5" rounded="lg" class="pa-2 mt-2 text-caption font-weight-medium text-primary">
              经度: {{ stationForm.longitude?.toFixed(6) }} | 纬度: {{ stationForm.latitude?.toFixed(6) }}
            </v-sheet>
          </v-form>
        </v-card-text>
        <v-divider />
        <v-card-actions class="pa-4">
          <v-spacer />
          <v-btn variant="text" @click="showStationModal = false">取消</v-btn>
          <v-btn color="primary" variant="flat" :disabled="!stationForm.longitude" @click="handleStationSubmit">保存</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- Pile Dialog -->
    <v-dialog v-model="showPileModal" max-width="500">
      <v-card rounded="lg">
        <v-card-title>添加充电桩到 {{ selectedStation?.name }}</v-card-title>
        <v-card-text>
          <v-form @submit.prevent="handlePileSubmit">
            <v-row dense>
              <v-col cols="12" sm="6">
                <v-text-field v-model="pileForm.pileCode" label="充电桩编号" variant="outlined" placeholder="如: ZGC-DC-001" />
              </v-col>
              <v-col cols="12" sm="6">
                <v-select v-model="pileForm.pileType" label="充电类型" variant="outlined" :items="[{ title: '直流快充', value: 'DC' }, { title: '交流慢充', value: 'AC' }]" />
              </v-col>
            </v-row>
            <v-row dense>
              <v-col cols="12" sm="6">
                <v-text-field v-model.number="pileForm.power" label="功率 (kW)" variant="outlined" type="number" />
              </v-col>
              <v-col cols="12" sm="6">
                <v-text-field v-model="pileForm.brand" label="品牌" variant="outlined" placeholder="如: 特来电" />
              </v-col>
            </v-row>
          </v-form>
        </v-card-text>
        <v-divider />
        <v-card-actions class="pa-4">
          <v-spacer />
          <v-btn variant="text" @click="showPileModal = false">取消</v-btn>
          <v-btn color="primary" variant="flat" @click="handlePileSubmit">创建充电桩</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- Spot Dialog -->
    <v-dialog v-model="showSpotModal" max-width="500">
      <v-card rounded="lg">
        <v-card-title>添加车位到 {{ selectedPile?.pileCode }}</v-card-title>
        <v-card-text>
          <v-form @submit.prevent="handleSpotSubmit">
            <v-row dense>
              <v-col cols="12" sm="6">
                <v-text-field v-model="spotForm.spotCode" label="车位编号" variant="outlined" placeholder="如: A01" />
              </v-col>
              <v-col cols="12" sm="6">
                <v-select v-model="spotForm.spotType" label="车位类型" variant="outlined" :items="[{ title: '标准车位', value: 'STANDARD' }, { title: '大型车位', value: 'LARGE' }]" />
              </v-col>
            </v-row>
            <v-row dense>
              <v-col cols="12" sm="6">
                <v-text-field v-model.number="spotForm.pricePerHour" label="停车费 (元/小时)" variant="outlined" type="number" step="0.1" />
              </v-col>
              <v-col cols="12" sm="6">
                <v-text-field v-model.number="spotForm.serviceFee" label="服务费 (元)" variant="outlined" type="number" step="0.1" />
              </v-col>
            </v-row>
          </v-form>
        </v-card-text>
        <v-divider />
        <v-card-actions class="pa-4">
          <v-spacer />
          <v-btn variant="text" @click="showSpotModal = false">取消</v-btn>
          <v-btn color="primary" variant="flat" @click="handleSpotSubmit">创建车位</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- Confirm Dialog -->
    <v-dialog v-model="confirmDialog.show" max-width="360">
      <v-card rounded="lg">
        <v-card-title>{{ confirmDialog.title }}</v-card-title>
        <v-card-text>{{ confirmDialog.message }}</v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="confirmDialog.show = false">取消</v-btn>
          <v-btn color="error" variant="flat" @click="handleConfirm">确认删除</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-snackbar v-model="snackbar.show" :color="snackbar.color" :timeout="3000" location="top">
      {{ snackbar.message }}
    </v-snackbar>
  </div>
</template>
