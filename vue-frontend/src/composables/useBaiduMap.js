import { ref, nextTick } from 'vue'

// --- 全局单例状态管理 ---
// 避免多个组件同时请求导致重复插入 script 标签
const scriptStatus = {
    loading: false,
    loaded: false,
    callbacks: [] // 存储等待加载完成的 promise resolve
}

const AK = import.meta.env.VITE_BAIDU_AK

/**
 * 核心：动态加载百度地图脚本
 * @returns {Promise<Object>} BMapGL 对象
 */
function loadBMapScript() {
    return new Promise((resolve, reject) => {
        // 1. 如果已加载，直接返回
        if (scriptStatus.loaded && window.BMapGL) {
            resolve(window.BMapGL)
            return
        }

        // 2. 如果正在加载，将 resolve 放入队列等待
        if (scriptStatus.loading) {
            scriptStatus.callbacks.push(resolve)
            return
        }

        // 3. 开始加载
        scriptStatus.loading = true
        scriptStatus.callbacks.push(resolve)

        // 定义全局回调函数 (百度脚本加载完会自动调用这个名字)
        window.onBMapCallback = () => {
            console.log('[BaiduMap] SDK Loaded Successfully')
            scriptStatus.loaded = true
            scriptStatus.loading = false
            // 执行所有等待的回调
            scriptStatus.callbacks.forEach(cb => cb(window.BMapGL))
            scriptStatus.callbacks = []
        }

        const script = document.createElement('script')
        script.type = 'text/javascript'
        // 注意：加上 &callback=onBMapCallback 参数
        script.src = `//api.map.baidu.com/api?type=webgl&v=1.0&ak=${AK}&callback=onBMapCallback`
        script.onerror = (e) => {
            scriptStatus.loading = false
            console.error('[BaiduMap] Script Load Error:', e)
            reject(new Error('百度地图脚本加载失败，请检查网络或AK配置'))
        }
        document.head.appendChild(script)
    })
}

export function useBaiduMap(containerId) {
    const mapInstance = ref(null)

    // 等待百度地图脚本加载 (兼容旧代码调用)
    async function waitForMap() {
        return await loadBMapScript()
    }

    // 初始化地图
    async function initMap(centerPoint, zoomLevel = 13) {
        if (!containerId) return null

        try {
            await nextTick() // 等待 DOM
            const BMapGL = await loadBMapScript()

            const container = document.getElementById(containerId)
            if (container) {
                const map = new BMapGL.Map(containerId)
                const point = new BMapGL.Point(centerPoint.lng, centerPoint.lat)
                map.centerAndZoom(point, zoomLevel)
                map.enableScrollWheelZoom(true)

                // 添加常用控件
                map.addControl(new BMapGL.ScaleControl())
                map.addControl(new BMapGL.ZoomControl())

                mapInstance.value = map
                return map
            }
        } catch (error) {
            console.error('[useBaiduMap] Init failed:', error)
            throw error
        }
    }

    // 添加标记
    function addMarker(point, options = {}) {
        if (!mapInstance.value || typeof window.BMapGL === 'undefined') return null

        const BMapGL = window.BMapGL
        const pt = new BMapGL.Point(point.lng, point.lat)
        const marker = new BMapGL.Marker(pt)

        if (options.label) {
            const label = new BMapGL.Label(options.label.text, {
                position: pt,
                offset: new BMapGL.Size(10, -10)
            })
            if (options.label.style) {
                label.setStyle(options.label.style)
            }
            mapInstance.value.addOverlay(label)
        }

        if (options.infoWindow) {
            const infoWindow = new BMapGL.InfoWindow(options.infoWindow.content, {
                width: options.infoWindow.width || 220,
                height: options.infoWindow.height || 100,
                title: options.infoWindow.title
            })
            marker.addEventListener("click", function () {
                this.openInfoWindow(infoWindow)
            })
        }

        mapInstance.value.addOverlay(marker)
        return marker
    }

    // 自动调整视野
    function setViewport(points) {
        if (!mapInstance.value || points.length === 0) return

        const BMapGL = window.BMapGL
        const bPoints = points.map(p => new BMapGL.Point(p.lng, p.lat))
        const viewport = mapInstance.value.getViewport(bPoints)
        mapInstance.value.centerAndZoom(viewport.center, viewport.zoom)
    }

    return {
        mapInstance,
        waitForMap,
        loadBMapScript,
        initMap,
        addMarker,
        setViewport
    }
}
