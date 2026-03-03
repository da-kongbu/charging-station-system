const https = require('https');

// 百度地图API配置
const AK = 'SOO3FVZZpkeh1kzHNSXNOtOB4HMCWXFA';

// 要搜索的城市和区域
const searchAreas = [
    { city: '北京', location: '116.404,39.915', radius: 10000 },
    { city: '北京', location: '116.310,39.990', radius: 10000 },  // 海淀
    { city: '北京', location: '116.460,39.920', radius: 10000 },  // 朝阳
    { city: '上海', location: '121.474,31.230', radius: 10000 },
    { city: '深圳', location: '114.060,22.543', radius: 10000 },
];

// 存储所有充电站
const allStations = [];
const seenNames = new Set();

// 调用百度地图地点检索API
function searchChargingStations(location, radius) {
    return new Promise((resolve, reject) => {
        const query = encodeURIComponent('充电站');
        const url = `https://api.map.baidu.com/place/v2/search?query=${query}&location=${location}&radius=${radius}&output=json&ak=${AK}`;

        https.get(url, (res) => {
            let data = '';
            res.on('data', chunk => data += chunk);
            res.on('end', () => {
                try {
                    const result = JSON.parse(data);
                    if (result.status === 0 && result.results) {
                        resolve(result.results);
                    } else {
                        console.log('API返回:', result.message || result.status);
                        resolve([]);
                    }
                } catch (e) {
                    reject(e);
                }
            });
        }).on('error', reject);
    });
}

// 生成SQL插入语句
function generateSQL(stations) {
    let sql = `-- 百度地图充电站数据导入脚本
-- 生成时间: ${new Date().toISOString()}
-- 共 ${stations.length} 个充电站

-- 清空现有数据（可选，谨慎执行）
-- DELETE FROM parking_spots;
-- DELETE FROM charging_piles;
-- DELETE FROM charging_stations;

`;

    stations.forEach((station, index) => {
        const id = index + 1;
        const name = station.name.replace(/'/g, "''");
        const address = (station.address || '').replace(/'/g, "''");
        const city = (station.city || '').replace(/'/g, "''");
        const district = (station.area || '').replace(/'/g, "''");
        const lng = station.location?.lng || 0;
        const lat = station.location?.lat || 0;

        sql += `
-- ${id}. ${station.name}
INSERT INTO charging_stations (id, name, address, city, district, longitude, latitude, contact, business_hours, created_at, updated_at)
VALUES (${id}, '${name}', '${address}', '${city}', '${district}', ${lng}, ${lat}, '', '24小时营业', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 添加充电桩
INSERT INTO charging_piles (id, station_id, pile_code, pile_type, power, voltage, current, brand, connector_type, status, created_at, updated_at)
VALUES (${id * 2 - 1}, ${id}, '${id}-DC-001', 'DC', 120, 750, 160, '特来电', '国标DC', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO charging_piles (id, station_id, pile_code, pile_type, power, voltage, current, brand, connector_type, status, created_at, updated_at)
VALUES (${id * 2}, ${id}, '${id}-AC-001', 'AC', 7, 220, 32, '星星充电', '国标AC', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 添加车位
INSERT INTO parking_spots (id, pile_id, spot_code, spot_type, price_per_hour, service_fee, status, created_at, updated_at)
VALUES (${id * 4 - 3}, ${id * 2 - 1}, 'A01', 'STANDARD', 2.0, 0.8, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO parking_spots (id, pile_id, spot_code, spot_type, price_per_hour, service_fee, status, created_at, updated_at)
VALUES (${id * 4 - 2}, ${id * 2 - 1}, 'A02', 'STANDARD', 2.0, 0.8, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO parking_spots (id, pile_id, spot_code, spot_type, price_per_hour, service_fee, status, created_at, updated_at)
VALUES (${id * 4 - 1}, ${id * 2}, 'B01', 'STANDARD', 1.5, 0.6, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO parking_spots (id, pile_id, spot_code, spot_type, price_per_hour, service_fee, status, created_at, updated_at)
VALUES (${id * 4}, ${id * 2}, 'B02', 'STANDARD', 1.5, 0.6, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

`;
    });

    return sql;
}

// 生成Java DataInitializer代码
function generateJavaCode(stations) {
    let code = `// 自动生成的充电站初始化数据
// 生成时间: ${new Date().toISOString()}
// 共 ${stations.length} 个充电站

private void initChargingStations() {
`;

    stations.forEach((station, index) => {
        const name = station.name.replace(/"/g, '\\"');
        const address = (station.address || '').replace(/"/g, '\\"');
        const city = (station.city || '').replace(/"/g, '\\"');
        const district = (station.area || '').replace(/"/g, '\\"');
        const lng = station.location?.lng || 0;
        const lat = station.location?.lat || 0;

        code += `
    // ${index + 1}. ${station.name}
    createStation("${name}", "${address}", "${city}", "${district}", ${lng}, ${lat});
`;
    });

    code += `}

private void createStation(String name, String address, String city, String district, double lng, double lat) {
    ChargingStation station = new ChargingStation();
    station.setName(name);
    station.setAddress(address);
    station.setCity(city);
    station.setDistrict(district);
    station.setLongitude(lng);
    station.setLatitude(lat);
    station.setBusinessHours("24小时营业");
    chargingStationRepository.save(station);
    
    // 创建充电桩和车位...
}
`;

    return code;
}

async function main() {
    console.log('🔍 开始从百度地图搜索充电站...\n');

    for (const area of searchAreas) {
        console.log(`📍 搜索 ${area.city} 区域 (${area.location})...`);
        try {
            const results = await searchChargingStations(area.location, area.radius);
            console.log(`   找到 ${results.length} 个结果`);

            for (const r of results) {
                // 去重
                if (!seenNames.has(r.name)) {
                    seenNames.add(r.name);
                    allStations.push({
                        ...r,
                        city: area.city
                    });
                }
            }
        } catch (e) {
            console.error(`   搜索失败: ${e.message}`);
        }
    }

    console.log(`\n✅ 共找到 ${allStations.length} 个不重复的充电站\n`);

    // 输出JSON数据
    const fs = require('fs');

    // 保存JSON
    const jsonPath = './charging_stations_data.json';
    fs.writeFileSync(jsonPath, JSON.stringify(allStations, null, 2), 'utf8');
    console.log(`📄 JSON数据已保存到: ${jsonPath}`);

    // 保存SQL
    const sqlPath = './charging_stations_import.sql';
    fs.writeFileSync(sqlPath, generateSQL(allStations), 'utf8');
    console.log(`📄 SQL脚本已保存到: ${sqlPath}`);

    // 打印部分数据预览
    console.log('\n📋 数据预览 (前5条):');
    allStations.slice(0, 5).forEach((s, i) => {
        console.log(`${i + 1}. ${s.name}`);
        console.log(`   地址: ${s.address}`);
        console.log(`   坐标: ${s.location?.lng}, ${s.location?.lat}`);
    });
}

main().catch(console.error);
