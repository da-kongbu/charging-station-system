import { ref } from 'vue'

// 全局单例状态，所有组件共享同一次定位结果
const globalLocation = ref(null)
const globalStatus = ref('idle') // idle, loading, success, error
let locationPromise = null // 缓存进行中的请求，防止重复调用

export function useLocation() {

    /**
     * 获取用户位置 (优先使用百度地图SDK，降级使用HTML5)
     * 全局只发一次请求，后续调用直接复用结果
     */
    function getUserLocation() {
        // 已经成功获取过 → 直接返回缓存
        if (globalStatus.value === 'success' && globalLocation.value) {
            return Promise.resolve(globalLocation.value)
        }
        // 正在请求中 → 复用同一个 Promise，不重复发请求
        if (locationPromise) {
            return locationPromise
        }

        globalStatus.value = 'loading'
        locationPromise = new Promise((resolve) => {
            // 优先使用百度地图定位SDK (如果已加载)
            if (typeof BMapGL !== 'undefined' && BMapGL.Geolocation) {
                const geolocation = new BMapGL.Geolocation()
                geolocation.getCurrentPosition(function (r) {
                    if (this.getStatus() === BMAP_STATUS_SUCCESS) {
                        globalLocation.value = {
                            longitude: r.point.lng,
                            latitude: r.point.lat
                        }
                        globalStatus.value = 'success'
                        locationPromise = null
                        resolve(globalLocation.value)
                    } else {
                        console.warn('[useLocation] 百度地图定位失败:', this.getStatus())
                        locationPromise = null
                        tryHtml5Location(resolve)
                    }
                })
            } else {
                locationPromise = null
                tryHtml5Location(resolve)
            }
        })
        return locationPromise
    }

    /**
     * HTML5 定位降级方案
     */
    function tryHtml5Location(resolve) {
        if (navigator.geolocation) {
            navigator.geolocation.getCurrentPosition(
                (position) => {
                    globalLocation.value = {
                        longitude: position.coords.longitude,
                        latitude: position.coords.latitude
                    }
                    globalStatus.value = 'success'
                    resolve(globalLocation.value)
                },
                (error) => {
                    console.error('[useLocation] HTML5定位失败:', error)
                    globalStatus.value = 'error'
                    resolve(null)
                },
                { timeout: 10000 }
            )
        } else {
            console.warn('[useLocation] 浏览器不支持定位')
            globalStatus.value = 'error'
            resolve(null)
        }
    }

    // 辅助函数：将角度转为弧度
    function toRad(d) {
        return d * Math.PI / 180.0;
    }

    /**
     * 计算两点间的距离（单位：km，保留1位小数）
     * 使用 Haversine 公式，纯数学计算，不依赖百度地图实例
     * 参数兼容：支持 (lon1, lat1, lon2, lat2) 或 (point1, point2)
     */
    function calculateDistance(arg1, arg2, arg3, arg4) {
        let lng1, lat1, lng2, lat2;

        // 参数适配
        if (typeof arg1 === 'object' && typeof arg2 === 'object') {
            // (point1, point2) 形式
            lng1 = arg1.lng || arg1.longitude;
            lat1 = arg1.lat || arg1.latitude;
            lng2 = arg2.lng || arg2.longitude;
            lat2 = arg2.lat || arg2.latitude;
        } else {
            // (lon1, lat1, lon2, lat2) 形式，兼容旧代码
            lng1 = arg1;
            lat1 = arg2;
            lng2 = arg3;
            lat2 = arg4;
        }

        if (!lng1 || !lat1 || !lng2 || !lat2) return 0;

        const EARTH_RADIUS = 6378.137; // 地球半径（千米）

        const radLat1 = toRad(lat1);
        const radLat2 = toRad(lat2);
        const a = radLat1 - radLat2;
        const b = toRad(lng1) - toRad(lng2);

        const s = 2 * Math.asin(Math.sqrt(
            Math.pow(Math.sin(a / 2), 2) +
            Math.cos(radLat1) * Math.cos(radLat2) * Math.pow(Math.sin(b / 2), 2)
        ));

        const distance = s * EARTH_RADIUS;
        return distance.toFixed(1); // 返回字符串，保留1位小数，保持与原API一致
    }

    return {
        userLocation: globalLocation,
        locationStatus: globalStatus,
        getUserLocation,
        calculateDistance
    }
}
