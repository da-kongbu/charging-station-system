/**
 * 充电站数据模拟器
 * 用于生成充电桩、车位及状态的模拟数据
 */

// 状态枚举 (后端使用 Integer)
const STATUS_ENUM = {
    OFFLINE: 0,   // 离线
    IDLE: 1,      // 空闲 (绿色)
    OCCUPIED: 2,  // 占用/充电中 (黄色)
    FAULTY: 3     // 故障 (红色)
};

// 辅助：生成随机整数 [min, max]
function getRandomInt(min, max) {
    return Math.floor(Math.random() * (max - min + 1)) + min;
}

// 辅助：获取加权随机状态
function getRandomStatus() {
    const rand = Math.random();
    if (rand < 0.6) return STATUS_ENUM.IDLE;      // 60% 概率空闲
    if (rand < 0.9) return STATUS_ENUM.OCCUPIED;  // 30% 概率占用
    if (rand < 0.95) return STATUS_ENUM.FAULTY;   // 5% 故障
    return STATUS_ENUM.OFFLINE;                   // 5% 离线
}

/**
 * 为单个充电站生成详情数据
 * @param {Object} station 原始站点数据
 * @returns {Object} 包含桩和车位信息的完整站点对象
 */
export function enrichStationData(station) {
    // 1. 随机生成该站点的充电桩数量 (2-8个)
    const pileCount = getRandomInt(2, 8);
    const piles = [];

    let totalSpots = 0;
    let availableSpots = 0;

    // Station ID 模拟 (用于生成 Pile Code)
    const stationPrefix = station.uid ? station.uid.substr(-4).toUpperCase() : getRandomInt(1000, 9999);

    for (let i = 0; i < pileCount; i++) {
        // 2. 生成桩编号
        const pileCode = `CP-${stationPrefix}-${String(i + 1).padStart(2, '0')}`;

        // 3. 随机生成该桩下的车位/枪头数量 (1-2个)
        const spotCount = getRandomInt(1, 2);
        const parkingSpots = [];
        const isDC = spotCount === 1; // 假设单枪是快充

        for (let j = 0; j < spotCount; j++) {
            const status = getRandomStatus();

            if (status === STATUS_ENUM.IDLE) {
                availableSpots++;
            }
            totalSpots++;

            // 生成车位数据 (对应 ParkingSpot 实体)
            parkingSpots.push({
                spotCode: `${pileCode}-${j + 1}`,
                spotType: isDC ? 'LARGE' : 'STANDARD',
                pricePerHour: isDC ? 2.5 : 1.5,
                serviceFee: isDC ? 1.0 : 0.8,
                status: status
            });
        }

        // 生成桩数据 (对应 ChargingPile 实体)
        piles.push({
            pileCode: pileCode,
            pileType: isDC ? 'DC' : 'AC',
            power: isDC ? 120 : 7,
            voltage: isDC ? 750 : 220,
            current: isDC ? 160 : 32,
            brand: '特来电', // 模拟品牌
            connectorType: isDC ? '国标DC' : '国标AC',
            status: 1, // 桩本身默认为正常(1)
            parkingSpots: parkingSpots
        });
    }

    // 返回符合后端 ChargingStation 实体结构的对象
    return {
        ...station,
        status: 1, // 营业中
        piles: piles // 嵌套的桩列表
    };
}
