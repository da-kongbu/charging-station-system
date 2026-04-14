package com.charging.service;

import com.alibaba.fastjson2.JSON;
import com.charging.dto.ChargingStationDTO;
import com.charging.dto.agent.AgentChatResponse;
import com.charging.dto.agent.StationCardData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StationDiscoveryService {

    private final ChargingStationService stationService;

    public AgentChatResponse buildStationCardResponse(Double lat, Double lng, String keyword, int maxResults) {
        return buildStationCardResponse(lat, lng, keyword, null, maxResults);
    }

    public AgentChatResponse buildStationCardResponse(Double lat, Double lng, String keyword, String chargingType,
            int maxResults) {
        StationDiscoveryResult result = queryNearbyStations(lat, lng, keyword, chargingType, maxResults);
        return AgentChatResponse.of("stations", buildSummary(result), toStationCardMaps(result.stations()));
    }

    public String buildStationJson(Double lat, Double lng, String keyword, int maxResults) {
        return buildStationJson(lat, lng, keyword, null, maxResults);
    }

    public String buildStationJson(Double lat, Double lng, String keyword, String chargingType, int maxResults) {
        StationDiscoveryResult result = queryNearbyStations(lat, lng, keyword, chargingType, maxResults);
        if (result.stations().isEmpty()) {
            return buildEmptyMessage(result.keyword(), result.chargingType());
        }
        return JSON.toJSONString(toStationCardMaps(result.stations()));
    }

    public StationDiscoveryResult queryNearbyStations(Double lat, Double lng, String keyword, int maxResults) {
        return queryNearbyStations(lat, lng, keyword, null, maxResults);
    }

    public StationDiscoveryResult queryNearbyStations(Double lat, Double lng, String keyword, String chargingType,
            int maxResults) {
        List<ChargingStationDTO> stations = loadStations(keyword);
        String normalizedChargingType = normalizeChargingType(chargingType);
        if (normalizedChargingType != null) {
            stations = stations.stream()
                    .filter(station -> supportsChargingType(station, normalizedChargingType))
                    .toList();
        }
        int totalMatches = stations.size();

        List<StationCardData> stationCards = new ArrayList<>();
        for (ChargingStationDTO station : stations) {
            Double distanceKm = null;
            if (lat != null && lng != null
                    && station.getLatitude() != null && station.getLongitude() != null) {
                double dist = haversineDistance(
                        lat, lng,
                        station.getLatitude().doubleValue(), station.getLongitude().doubleValue());
                distanceKm = Math.round(dist * 10.0) / 10.0;
            }

            stationCards.add(StationCardData.builder()
                    .id(station.getId())
                    .name(station.getName())
                    .address(station.getAddress())
                    .city(station.getCity())
                    .pileCount(station.getPileCount())
                    .availablePileCount(station.getAvailablePileCount())
                    .businessHours(station.getBusinessHours() != null ? station.getBusinessHours() : "全天")
                    .distanceKm(distanceKm)
                    .build());
        }

        if (lat != null && lng != null) {
            stationCards.sort((a, b) -> {
                Double da = a.getDistanceKm();
                Double db = b.getDistanceKm();
                if (da == null && db == null) return 0;
                if (da == null) return 1;
                if (db == null) return -1;
                return Double.compare(da, db);
            });
        }

        if (maxResults > 0 && stationCards.size() > maxResults) {
            stationCards = new ArrayList<>(stationCards.subList(0, maxResults));
        }

        return new StationDiscoveryResult(stationCards, totalMatches, normalizeKeyword(keyword), normalizedChargingType);
    }

    private List<ChargingStationDTO> loadStations(String keyword) {
        String normalizedKeyword = normalizeKeyword(keyword);
        if (normalizedKeyword == null) {
            // 不过滤可用桩数，返回全部站点让距离排序决定结果
            // 可用状态由卡片数据中的 availablePileCount 体现
            return stationService.findAllAvailable();
        }
        return stationService.searchByKeyword(normalizedKeyword);
    }

    private String buildSummary(StationDiscoveryResult result) {
        if (result.stations().isEmpty()) {
            return buildEmptyMessage(result.keyword(), result.chargingType());
        }

        int displayCount = result.stations().size();
        StringBuilder sb = new StringBuilder();

        // 1. 总结句
        if (result.chargingType() != null) {
            sb.append("我帮你查到以下支持").append(displayChargingType(result.chargingType()))
                    .append("的充电站，按距离由近到远排列：");
        } else {
            sb.append("我帮你查到以下充电站，按距离由近到远排列：");
        }

        // 2. 每项一行摘要（保留站点名，便于前端与后续对话做指代消解）
        for (int i = 0; i < displayCount; i++) {
            StationCardData station = result.stations().get(i);
            sb.append("\n").append(i + 1).append(". ")
                    .append(defaultText(station.getName(), "未命名充电站"));
            if (station.getDistanceKm() != null) {
                sb.append("，约 ").append(station.getDistanceKm()).append(" km");
            } else {
                sb.append("，").append(defaultText(shortenAddress(station.getAddress()), "未知位置"));
            }
            if (station.getAvailablePileCount() != null && station.getAvailablePileCount() > 0) {
                sb.append("，有空闲桩（").append(station.getAvailablePileCount())
                        .append("/").append(station.getPileCount()).append("）");
                if (i == 0) {
                    sb.append("，建议优先查看");
                } else if (i == displayCount - 1 && displayCount > 1) {
                    sb.append("，可作为备选");
                }
            } else if (station.getAvailablePileCount() != null) {
                sb.append("，当前无空闲桩");
            }
        }

        // 3. 引导语
        if (result.totalMatches() > displayCount) {
            sb.append("\n\n还有 ").append(result.totalMatches() - displayCount)
                    .append(" 个站点可选。点击下方卡片可查看详情与预约信息。");
        } else {
            sb.append("\n\n点击下方卡片可查看详情与预约信息。");
        }

        return sb.toString();
    }

    /**
     * 截取地址前半段作为位置简称（取第一个逗号/路/号之前的部分）
     */
    private String shortenAddress(String address) {
        if (address == null || address.isBlank()) return null;
        // 去掉省市区前缀
        String shortened = address.replaceAll("^(.*?[省市]){0,2}.*?[市区县]", "");
        if (shortened.length() > 15) {
            shortened = shortened.substring(0, 15);
        }
        return shortened.isBlank() ? address.substring(0, Math.min(address.length(), 15)) : shortened;
    }

    private String buildEmptyMessage(String keyword, String chargingType) {
        if (keyword != null && chargingType != null) {
            return "没有找到包含 '" + keyword + "' 且支持" + displayChargingType(chargingType) + "的充电站";
        }
        if (keyword != null) {
            return "没有找到包含 '" + keyword + "' 的充电站";
        }
        if (chargingType != null) {
            return "当前附近没有可用的" + displayChargingType(chargingType) + "充电站，建议稍后再试。";
        }
        return "当前附近没有空闲的充电站，建议稍后再试。";
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String normalized = keyword.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String defaultText(String text, String fallback) {
        return text != null && !text.isBlank() ? text : fallback;
    }

    private List<Map<String, Object>> toStationCardMaps(List<StationCardData> stations) {
        List<Map<String, Object>> data = new ArrayList<>();
        for (StationCardData station : stations) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("id", station.getId());
            info.put("名称", station.getName());
            info.put("地址", station.getAddress());
            info.put("城市", station.getCity());
            info.put("总桩数", station.getPileCount());
            info.put("当前可用桩数", station.getAvailablePileCount());
            info.put("营业时间", station.getBusinessHours());
            if (station.getDistanceKm() != null) {
                info.put("距离(km)", station.getDistanceKm());
            }
            data.add(info);
        }
        return data;
    }

    private boolean supportsChargingType(ChargingStationDTO station, String chargingType) {
        if (station.getPiles() == null || station.getPiles().isEmpty()) {
            return false;
        }
        return station.getPiles().stream()
                .filter(pile -> normalizeChargingType(pile.getPileType()) != null)
                .filter(pile -> chargingType.equals(normalizeChargingType(pile.getPileType())))
                .filter(pile -> pile.getStatus() != null && pile.getStatus() == 1)
                .anyMatch(pile -> pile.getParkingSpots() == null || pile.getParkingSpots().isEmpty()
                        || pile.getParkingSpots().stream()
                                .anyMatch(spot -> spot.getStatus() != null && spot.getStatus() == 1));
    }

    private String normalizeChargingType(String chargingType) {
        if (chargingType == null || chargingType.isBlank()) {
            return null;
        }
        String normalized = chargingType.trim().toUpperCase(Locale.ROOT);
        if (normalized.contains("快充") || normalized.contains("直流") || "DC".equals(normalized)) {
            return "DC";
        }
        if (normalized.contains("慢充") || normalized.contains("交流") || "AC".equals(normalized)) {
            return "AC";
        }
        return normalized;
    }

    private String displayChargingType(String chargingType) {
        return switch (normalizeChargingType(chargingType)) {
            case "DC" -> "直流快充";
            case "AC" -> "交流慢充";
            default -> chargingType;
        };
    }

    private double haversineDistance(double lat1, double lng1, double lat2, double lng2) {
        final double earthRadius = 6378.137;
        double radLat1 = Math.toRadians(lat1);
        double radLat2 = Math.toRadians(lat2);
        double a = radLat1 - radLat2;
        double b = Math.toRadians(lng1) - Math.toRadians(lng2);
        double s = 2 * Math.asin(Math.sqrt(
                Math.pow(Math.sin(a / 2), 2) +
                        Math.cos(radLat1) * Math.cos(radLat2) * Math.pow(Math.sin(b / 2), 2)));
        return s * earthRadius;
    }

    public record StationDiscoveryResult(List<StationCardData> stations, int totalMatches, String keyword,
                                         String chargingType) {}
}
