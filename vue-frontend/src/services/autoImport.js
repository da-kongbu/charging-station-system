/**
 * 百度地图自动导入服务
 * 基于用户位置自动导入附近充电站数据
 */

import { useLocation } from '@/composables/useLocation'
import { useBaiduMap } from '@/composables/useBaiduMap'
import { enrichStationData } from '@/utils/dataSimulator'

// 搜索配置
const SEARCH_RADIUS = 50000 // 搜索半径 50km
const KEYWORDS = ['充电站', '特斯拉充电站', '国家电网充电站'] // 搜索关键词

/**
 * 检查并自动导入附近充电站
 * @param {Object} api - Axios实例
 * @returns {Promise<number>} 导入的站点数量
 */
export async function autoImportJiangsuStations(api) {
    console.log('[AutoImport] 开始检查周边充电站数据...')

    const { getUserLocation, calculateDistance } = useLocation()
    const { waitForMap } = useBaiduMap()

    try {
        // 确保地图加载完成
        await waitForMap()

        // 1. 获取用户位置
        const location = await getUserLocation()
        if (!location) {
            console.warn('[AutoImport] 无法获取用户位置，跳过自动导入')
            return 0
        }

        console.log(`[AutoImport] 获取位置成功: ${location.longitude}, ${location.latitude}`)

        // 2. 根据位置搜索并导入
        // 注意：useLocation 返回的是 { longitude, latitude }
        // 而 BMapGL 使用的是 { lng, lat }，需要转换一下，或者统一
        // 百度地图通常使用 lng/lat
        const bMapLocation = {
            lng: location.longitude,
            lat: location.latitude
        }

        return await importFromBaiduMap(api, bMapLocation, calculateDistance)

    } catch (error) {
        console.error('[AutoImport] 自动导入失败:', error)
        return 0
    }
}

/**
 * 从百度地图API导入充电站数据
 */
async function importFromBaiduMap(api, location, calculateDistanceFunc) {
    if (typeof BMapGL === 'undefined') {
        console.error('[AutoImport] 百度地图API未加载')
        return 0
    }

    let totalImported = 0
    const importedNames = new Set()
    const center = new BMapGL.Point(location.lng, location.lat)

    for (const keyword of KEYWORDS) {
        try {
            const count = await searchAndImportNearby(keyword, center, importedNames, api, calculateDistanceFunc)
            totalImported += count
            console.log(`[AutoImport] 关键词"${keyword}"导入: ${count} 个`)
            await sleep(1000)
        } catch (error) {
            console.error(`[AutoImport] 搜索"${keyword}"失败:`, error)
        }
    }

    if (totalImported > 0) {
        console.log(`[AutoImport] 周边充电站导入完成，共导入 ${totalImported} 个`)
    } else {
        console.log('[AutoImport] 未发现新的周边充电站')
    }

    return totalImported
}

/**
 * 搜索并导入周边的充电站
 */
function searchAndImportNearby(keyword, center, importedNames, api, calculateDistanceFunc) {
    return new Promise((resolve) => {
        const local = new BMapGL.LocalSearch(center, {
            pageCapacity: 50,
            onSearchComplete: async function (results) {
                if (!results || results.getCurrentNumPois() === 0) {
                    resolve(0)
                    return
                }

                const batchStations = []
                let newCount = 0
                const count = results.getCurrentNumPois()

                for (let i = 0; i < count; i++) {
                    const poi = results.getPoi(i)
                    if (!poi || importedNames.has(poi.title)) continue

                    importedNames.add(poi.title)

                    // 计算距离
                    const dist = calculateDistanceFunc(center.lng, center.lat, poi.point.lng, poi.point.lat)

                    // 使用模拟器增强数据 (生成桩和车位)
                    // 注意：enrichStationData 返回的是符合后端实体结构的完整对象
                    const enhancedStation = enrichStationData({
                        name: poi.title,
                        address: poi.address || poi.title,
                        city: extractCity(poi.address) || '未知城市',
                        district: extractDistrict(poi.address),
                        longitude: poi.point?.lng || 0,
                        latitude: poi.point?.lat || 0,
                        contact: poi.phoneNumber ? poi.phoneNumber.substring(0, 20) : '',
                        businessHours: '24小时营业',
                        description: `距离您约 ${dist}km`,
                        uid: poi.uid // 用于生成确定性的桩编号
                    })

                    batchStations.push(enhancedStation)
                    newCount++
                }

                if (batchStations.length > 0) {
                    await batchImportToBackend(batchStations, api)
                }

                resolve(newCount)
            }
        })

        local.searchNearby(keyword, center, SEARCH_RADIUS)
    })
}

async function batchImportToBackend(stations, api) {
    let successCount = 0
    for (const station of stations) {
        try {
            await api.post('/admin/stations', station)
            successCount++
        } catch (error) {
            // ignore
        }
    }
    return successCount
}

function extractCity(address) {
    if (!address) return ''
    const match = address.match(/([\u4e00-\u9fa5]+市)/)
    return match ? match[1] : ''
}

function extractDistrict(address) {
    if (!address) return ''
    const match = address.match(/([\u4e00-\u9fa5]+区|[\u4e00-\u9fa5]+县)/)
    return match ? match[1] : ''
}

function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms))
}

export default {
    autoImportJiangsuStations
}
