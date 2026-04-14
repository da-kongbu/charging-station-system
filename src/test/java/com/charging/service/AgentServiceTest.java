package com.charging.service;

import com.charging.dto.ChargingPileDTO;
import com.charging.dto.ChargingStationDTO;
import com.charging.dto.ParkingSpotDTO;
import com.charging.dto.ReservationRequest;
import com.charging.dto.agent.AgentChatRequest;
import com.charging.dto.agent.AgentChatResponse;
import com.charging.dto.agent.AgentHistoryMessage;
import com.charging.entity.ChargingPile;
import com.charging.entity.ChargingStation;
import com.charging.entity.ParkingSpot;
import com.charging.entity.Reservation;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class AgentServiceTest {

    @Test
    void confirmReservationShouldKeepPendingTokenWhenCreateFails() throws Exception {
        StationDiscoveryService stationDiscoveryService = new StationDiscoveryService(new StubChargingStationService());
        AgentService agentService = new AgentService(
                new StubChargingStationService(), new FailingReservationService(), null, stationDiscoveryService);

        @SuppressWarnings("unchecked")
        ThreadLocal<Long> currentUserId = (ThreadLocal<Long>) ReflectionTestUtils.getField(agentService, "currentUserId");
        currentUserId.set(7L);

        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, Object> pendingReservations =
                (ConcurrentHashMap<String, Object>) ReflectionTestUtils.getField(agentService, "pendingReservations");

        Class<?> pendingType = Class.forName("com.charging.service.AgentService$PendingReservation");
        var constructor = pendingType.getDeclaredConstructor(
                Long.class, Long.class, LocalDateTime.class, LocalDateTime.class, LocalDateTime.class);
        constructor.setAccessible(true);
        Object pending = constructor.newInstance(
                7L, 11L, LocalDateTime.now().plusHours(1), LocalDateTime.now().plusHours(2), LocalDateTime.now());
        pendingReservations.put("token-123", pending);

        String result = ReflectionTestUtils.invokeMethod(agentService, "confirmReservation", "token-123");

        assertThat(result).isEqualTo("该时间段车位已被预约");
        assertThat(pendingReservations).containsKey("token-123");
    }

    @Test
    void queryAvailableSpotsShouldFilterByChargingType() {
        ParkingSpotService parkingSpotService = new StubParkingSpotService(buildSpot("SPOT-DC-001", "DC"));
        StationDiscoveryService stationDiscoveryService = new StationDiscoveryService(new StubChargingStationService());
        AgentService agentService = new AgentService(
                new StubChargingStationService(), null, parkingSpotService, stationDiscoveryService);

        String result = ReflectionTestUtils.invokeMethod(
                agentService,
                "queryAvailableSpots",
                1L,
                "2026-04-11T15:00:00",
                "2026-04-11T17:00:00",
                "快充");

        assertThat(result).contains("SPOT-DC-001");

        String slowChargeResult = ReflectionTestUtils.invokeMethod(
                agentService,
                "queryAvailableSpots",
                1L,
                "2026-04-11T15:00:00",
                "2026-04-11T17:00:00",
                "慢充");

        assertThat(slowChargeResult).isEqualTo("该充电站在指定时间段内没有可用的交流慢充车位");
    }

    @Test
    void buildSystemPromptShouldContainCurrentTimeHint() {
        StationDiscoveryService stationDiscoveryService = new StationDiscoveryService(new StubChargingStationService());
        AgentService agentService = new AgentService(
                new StubChargingStationService(), null, null, stationDiscoveryService);

        String systemPrompt = ReflectionTestUtils.invokeMethod(agentService, "buildSystemPrompt");

        assertThat(systemPrompt).contains("当前系统时间：");
        assertThat(systemPrompt).contains("当前时区：");
        assertThat(systemPrompt).contains("多个条件时要一起处理");
    }

    @Test
    void agentChatShouldShortCircuitSimpleStationQuery() {
        AgentService agentService = buildAgentService(null, null);

        AgentChatResponse response = agentService.agentChat(
                AgentChatRequest.builder()
                        .question("帮我查询一下附近的充电站")
                        .lat(31.782)
                        .lng(119.965)
                        .history(List.of())
                        .build(),
                null,
                "req-simple");

        assertThat(response.getType()).isEqualTo("stations");
        assertThat(response.getContent())
                .contains("我帮你查到以下充电站")
                .contains("星星充电站")
                .contains("理想超充站");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stations = (List<Map<String, Object>>) response.getData();
        assertThat(stations).hasSize(2);
        assertThat(stations.get(0).get("名称")).isEqualTo("星星充电站");
    }

    @Test
    void agentChatShouldUseKeywordForSimpleStationQuery() {
        AgentService agentService = buildAgentService(null, null);

        AgentChatResponse response = agentService.agentChat(
                AgentChatRequest.builder()
                        .question("查一下理想附近的充电站")
                        .lat(31.782)
                        .lng(119.965)
                        .history(List.of())
                        .build(),
                null,
                "req-keyword");

        assertThat(response.getType()).isEqualTo("stations");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stations = (List<Map<String, Object>>) response.getData();
        assertThat(stations).hasSize(1);
        assertThat(stations.get(0).get("名称")).isEqualTo("理想超充站");
    }

    @Test
    void agentChatShouldShortCircuitNearbyFastChargeQuery() {
        AgentService agentService = buildAgentService(null, null);

        AgentChatResponse response = agentService.agentChat(
                AgentChatRequest.builder()
                        .question("那附近可用的快充充电桩有哪些呢")
                        .lat(31.782)
                        .lng(119.965)
                        .history(List.of())
                        .build(),
                null,
                "req-fast");

        assertThat(response.getType()).isEqualTo("stations");
        assertThat(response.getContent()).contains("支持直流快充");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stations = (List<Map<String, Object>>) response.getData();
        assertThat(stations).hasSize(1);
        assertThat(stations.get(0).get("名称")).isEqualTo("星星充电站");
    }

    @Test
    void agentChatShouldTreatNearbyAvailableStationQueryAsGenericDiscovery() {
        AgentService agentService = buildAgentService(null, null);

        AgentChatResponse response = agentService.agentChat(
                AgentChatRequest.builder()
                        .question("附近空闲的充电站")
                        .lat(31.782)
                        .lng(119.965)
                        .history(List.of())
                        .build(),
                null,
                "req-available");

        assertThat(response.getType()).isEqualTo("stations");
        assertThat(response.getContent()).contains("我帮你查到以下充电站");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stations = (List<Map<String, Object>>) response.getData();
        assertThat(stations).isNotEmpty();
    }

    @Test
    void agentChatShouldCreatePendingReservationFromHistoryWhenConditionsAreExplicit() {
        ParkingSpot spot = buildSpot("SPOT-DC-001", "DC");
        AgentService agentService = buildAgentService(
                new SuccessfulReservationService(),
                new StubParkingSpotService(spot));

        AgentChatResponse response = agentService.agentChat(
                AgentChatRequest.builder()
                        .question("帮我预约第一个充电站，时间是现在，时长两小时，类型要快充")
                        .lat(31.782)
                        .lng(119.965)
                        .history(List.of(
                                AgentHistoryMessage.builder()
                                        .role("assistant")
                                        .content("""
                                                我帮你查到以下充电站：
                                                [站点数据]
                                                1. id=1 星星充电站 可用桩1/7
                                                2. id=2 理想超充站 可用桩2/6
                                                """)
                                        .build()))
                        .build(),
                7L,
                "req-reservation");

        assertThat(response.getType()).isEqualTo("reservation_pending");
        assertThat(response.getContent()).contains("请确认预约信息");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getData();
        assertThat(data).containsKeys("confirm_token", "station_name", "spot_code", "start_time", "end_time");
    }

    @Test
    void agentChatShouldCreatePendingReservationFromImplicitFollowUpContext() {
        ParkingSpot spot = buildSpot("SPOT-DC-001", "DC");
        AgentService agentService = buildAgentService(
                new SuccessfulReservationService(),
                new StubParkingSpotService(spot));

        AgentChatResponse response = agentService.agentChat(
                AgentChatRequest.builder()
                        .question("第一个，现在开始2小时，充电类型快充")
                        .lat(31.782)
                        .lng(119.965)
                        .history(List.of(
                                AgentHistoryMessage.builder()
                                        .role("assistant")
                                        .content("""
                                                我帮你查到以下充电站：
                                                [站点数据]
                                                1. id=1 星星充电站 可用桩1/7
                                                2. id=2 理想超充站 可用桩2/6
                                                """)
                                        .build()))
                        .build(),
                7L,
                "req-reservation-implicit");

        assertThat(response.getType()).isEqualTo("reservation_pending");
        assertThat(response.getContent()).contains("请确认预约信息");
    }

    @Test
    void agentChatShouldPromptForMissingReservationDetailsWhenLlmIsUnavailable() {
        AgentService agentService = buildAgentService(null, null);

        AgentChatResponse response = agentService.agentChat(
                AgentChatRequest.builder()
                        .question("帮我预约第一个")
                        .lat(31.782)
                        .lng(119.965)
                        .history(stationHistory())
                        .build(),
                7L,
                "req-reservation-followup");

        assertThat(response.getType()).isEqualTo("text");
        assertThat(response.getContent())
                .contains("我已经锁定第一个充电站")
                .contains("开始时间")
                .contains("持续多久")
                .contains("快充/慢充");
    }

    @Test
    void agentChatShouldCreatePendingReservationAfterLockedFollowUpPrompt() {
        ParkingSpot spot = buildSpot("SPOT-DC-001", "DC");
        AgentService agentService = buildAgentService(
                new SuccessfulReservationService(),
                new StubParkingSpotService(spot));

        AgentChatResponse response = agentService.agentChat(
                AgentChatRequest.builder()
                        .question("时间现在，持续两小时，要快充")
                        .lat(31.782)
                        .lng(119.965)
                        .history(List.of(
                                stationHistory().get(0),
                                AgentHistoryMessage.builder()
                                        .role("assistant")
                                        .content("我已经锁定第一个充电站了，请告诉我开始时间、持续多久，以及是否需要快充/慢充。")
                                        .build()))
                        .build(),
                7L,
                "req-reservation-after-followup");

        assertThat(response.getType()).isEqualTo("reservation_pending");
        assertThat(response.getContent()).contains("请确认预约信息");
    }

    @Test
    void reservationFollowUpDegradedResponseShouldPromptForMissingDetails() {
        AgentService agentService = buildAgentService(null, null);

        AgentChatResponse response = ReflectionTestUtils.invokeMethod(
                agentService,
                "buildReservationFollowUpDegradedResponse",
                "帮我预约第一个",
                stationHistory(),
                "empty_llm_content");

        assertThat(response).isNotNull();
        assertThat(response.getType()).isEqualTo("text");
        assertThat(response.getContent())
                .contains("我已经锁定第一个充电站")
                .contains("开始时间")
                .contains("持续多久")
                .contains("快充/慢充");
    }

    @Test
    void shouldNotRetryWhenLlmRequestTimesOut() {
        AgentService agentService = buildAgentService(null, null);

        Boolean shouldRetry = ReflectionTestUtils.invokeMethod(
                agentService,
                "shouldRetryLlmRequest",
                1,
                2,
                Enum.valueOf(resolveLlmFailureTypeClass(), "TIMEOUT"));

        assertThat(shouldRetry).isFalse();
    }

    @Test
    void shouldRetryForConnectionFailureBeforeLastAttempt() {
        AgentService agentService = buildAgentService(null, null);

        Boolean shouldRetry = ReflectionTestUtils.invokeMethod(
                agentService,
                "shouldRetryLlmRequest",
                1,
                2,
                Enum.valueOf(resolveLlmFailureTypeClass(), "CONNECTION"));

        assertThat(shouldRetry).isTrue();
    }

    @Test
    void agentChatShouldKeepDeterministicReservationFlowWhenTimeIsProvided() {
        ParkingSpot spot = buildSpot("SPOT-DC-001", "DC");
        AgentService agentService = buildAgentService(
                new SuccessfulReservationService(),
                new StubParkingSpotService(spot));

        AgentChatResponse response = agentService.agentChat(
                AgentChatRequest.builder()
                        .question("帮我预约第一个，现在开始2小时")
                        .lat(31.782)
                        .lng(119.965)
                        .history(stationHistory())
                        .build(),
                7L,
                "req-reservation-time-provided");

        assertThat(response.getType()).isEqualTo("reservation_pending");
        assertThat(response.getContent()).contains("请确认预约信息");
    }

    @Test
    void agentChatShouldUpdatePendingReservationWhenUserChangesChargingType() throws Exception {
        ParkingSpot acSpot = buildSpot(201L, "SPOT-AC-001", "AC");
        ParkingSpot dcSpot = buildSpot(202L, "SPOT-DC-001", "DC");
        AgentService agentService = buildAgentService(
                new SuccessfulReservationService(),
                new StubParkingSpotService(acSpot, dcSpot));

        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, Object> pendingReservations =
                (ConcurrentHashMap<String, Object>) ReflectionTestUtils.getField(agentService, "pendingReservations");

        Class<?> pendingType = Class.forName("com.charging.service.AgentService$PendingReservation");
        var constructor = pendingType.getDeclaredConstructor(
                Long.class, Long.class, LocalDateTime.class, LocalDateTime.class, LocalDateTime.class);
        constructor.setAccessible(true);
        LocalDateTime startTime = LocalDateTime.of(2026, 4, 13, 12, 59);
        LocalDateTime endTime = startTime.plusHours(3);
        pendingReservations.put("old-token", constructor.newInstance(
                7L, acSpot.getId(), startTime, endTime, LocalDateTime.now()));

        AgentChatResponse response = agentService.agentChat(
                AgentChatRequest.builder()
                        .question("我需要快充")
                        .lat(31.782)
                        .lng(119.965)
                        .history(List.of(
                                stationHistory().get(0),
                                AgentHistoryMessage.builder()
                                        .role("assistant")
                                        .content("[待确认预约] confirm_token=old-token station_id=1 spot_id=201 星星充电站 SPOT-AC-001 2026-04-13T12:59:00~2026-04-13T15:59:00 charging_type=交流慢充")
                                        .build()))
                        .build(),
                7L,
                "req-reservation-change-type");

        assertThat(response.getType()).isEqualTo("reservation_pending");
        assertThat(response.getContent()).contains("直流快充");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getData();
        assertThat(data.get("charging_type")).isEqualTo("直流快充");
        assertThat(data.get("spot_id")).isEqualTo(202L);
        assertThat(data.get("confirm_token")).isNotEqualTo("old-token");
        assertThat(pendingReservations).doesNotContainKey("old-token");
        assertThat(pendingReservations).containsKey((String) data.get("confirm_token"));
    }

    @Test
    void agentChatShouldReusePendingChargingTypeWhenUserOnlyChangesTime() {
        ParkingSpot dcSpot = buildSpot(202L, "SPOT-DC-001", "DC");
        AgentService agentService = buildAgentService(
                new SuccessfulReservationService(),
                new StubParkingSpotService(dcSpot));

        AgentChatResponse response = agentService.agentChat(
                AgentChatRequest.builder()
                        .question("改成现在开始两小时")
                        .lat(31.782)
                        .lng(119.965)
                        .history(List.of(
                                stationHistory().get(0),
                                AgentHistoryMessage.builder()
                                        .role("assistant")
                                        .content("[待确认预约] confirm_token=old-token station_id=1 spot_id=202 星星充电站 SPOT-DC-001 2026-04-13T12:59:00~2026-04-13T15:59:00 charging_type=直流快充")
                                        .build()))
                        .build(),
                7L,
                "req-reservation-change-time");

        assertThat(response.getType()).isEqualTo("reservation_pending");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getData();
        assertThat(data.get("charging_type")).isEqualTo("直流快充");
    }

    @Test
    void agentChatShouldKeepModelUnavailableMessageWithoutStationContext() {
        AgentService agentService = buildAgentService(null, null);

        AgentChatResponse response = agentService.agentChat(
                AgentChatRequest.builder()
                        .question("帮我预约第一个")
                        .lat(31.782)
                        .lng(119.965)
                        .history(List.of())
                        .build(),
                7L,
                "req-reservation-no-context");

        assertThat(response.getType()).isEqualTo("text");
        assertThat(response.getContent()).isEqualTo("抱歉，模型服务配置缺失，请联系管理员检查配置。");
    }

    @Test
    void agentChatShouldReturnStationDetailFromOrdinalFollowUp() {
        AgentService agentService = buildAgentService(null, null);

        AgentChatResponse response = agentService.agentChat(
                AgentChatRequest.builder()
                        .question("第一个充电站")
                        .lat(31.782)
                        .lng(119.965)
                        .history(stationHistory())
                        .build(),
                null,
                "req-station-detail");

        assertThat(response.getType()).isEqualTo("station_detail");
        assertThat(response.getContent()).contains("继续帮你预约");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getData();
        assertThat(data.get("名称")).isEqualTo("星星充电站");
    }

    @Test
    void agentChatShouldKeepComplexReservationRequestsOnAgentPath() {
        AgentService agentService = buildAgentService(null, null);

        AgentChatResponse response = agentService.agentChat(
                AgentChatRequest.builder()
                        .question("帮我预约第一个充电站，如果没有快充就换一个")
                        .lat(31.782)
                        .lng(119.965)
                        .history(List.of())
                        .build(),
                null,
                "req-complex");

        assertThat(response.getType()).isEqualTo("text");
        assertThat(response.getContent()).isEqualTo("抱歉，模型服务配置缺失，请联系管理员检查配置。");
    }

    private List<AgentHistoryMessage> stationHistory() {
        return List.of(
                AgentHistoryMessage.builder()
                        .role("assistant")
                        .content("""
                                我帮你查到以下充电站：
                                [站点数据]
                                1. id=1 星星充电站 可用桩1/7
                                2. id=2 理想超充站 可用桩2/6
                                """)
                        .build());
    }

    @SuppressWarnings("unchecked")
    private Class<Enum> resolveLlmFailureTypeClass() {
        try {
            return (Class<Enum>) Class.forName("com.charging.service.AgentService$LlmFailureType");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private AgentService buildAgentService(ReservationService reservationService, ParkingSpotService parkingSpotService) {
        StubChargingStationService chargingStationService = new StubChargingStationService();
        StationDiscoveryService stationDiscoveryService = new StationDiscoveryService(chargingStationService);
        return new AgentService(
                chargingStationService,
                reservationService,
                parkingSpotService,
                stationDiscoveryService);
    }

    private ParkingSpot buildSpot(String spotCode, String pileType) {
        return buildSpot(100L, spotCode, pileType);
    }

    private ParkingSpot buildSpot(Long spotId, String spotCode, String pileType) {
        ChargingStation station = ChargingStation.builder()
                .id(1L)
                .name("星星充电站")
                .build();

        ChargingPile pile = ChargingPile.builder()
                .id(10L)
                .station(station)
                .pileCode("PILE-001")
                .pileType(pileType)
                .power(BigDecimal.valueOf(120))
                .status(1)
                .build();

        return ParkingSpot.builder()
                .id(spotId)
                .pile(pile)
                .spotCode(spotCode)
                .spotType("STANDARD")
                .status(1)
                .pricePerHour(BigDecimal.valueOf(8))
                .serviceFee(BigDecimal.ONE)
                .build();
    }

    private static class FailingReservationService extends ReservationService {
        FailingReservationService() {
            super(null, null, null, null);
        }

        @Override
        public Reservation create(Long userId, ReservationRequest request) {
            throw new RuntimeException("该时间段车位已被预约");
        }
    }

    private static class SuccessfulReservationService extends ReservationService {
        SuccessfulReservationService() {
            super(null, null, null, null);
        }

        @Override
        public Optional<String> validateReservationRequest(Long spotId, LocalDateTime startTime, LocalDateTime endTime) {
            return Optional.empty();
        }
    }

    private static class StubChargingStationService extends ChargingStationService {
        private final List<ChargingStationDTO> stations = List.of(
                ChargingStationDTO.builder()
                        .id(1L)
                        .name("星星充电站")
                        .address("服务区东侧")
                        .city("常州")
                        .latitude(BigDecimal.valueOf(31.782))
                        .longitude(BigDecimal.valueOf(119.965))
                        .pileCount(7)
                        .availablePileCount(1)
                        .businessHours("24小时营业")
                        .piles(List.of(
                                ChargingPileDTO.builder()
                                        .id(11L)
                                        .pileCode("PILE-DC-001")
                                        .pileType("DC")
                                        .status(1)
                                        .parkingSpots(List.of(
                                                ParkingSpotDTO.builder().id(101L).spotCode("SPOT-DC-001").status(1).build()))
                                        .build()))
                        .build(),
                ChargingStationDTO.builder()
                        .id(2L)
                        .name("理想超充站")
                        .address("中吴大道 1926 号")
                        .city("常州")
                        .latitude(BigDecimal.valueOf(31.790))
                        .longitude(BigDecimal.valueOf(119.970))
                        .pileCount(6)
                        .availablePileCount(2)
                        .businessHours("24小时营业")
                        .piles(List.of(
                                ChargingPileDTO.builder()
                                        .id(12L)
                                        .pileCode("PILE-AC-001")
                                        .pileType("AC")
                                        .status(1)
                                        .parkingSpots(List.of(
                                                ParkingSpotDTO.builder().id(102L).spotCode("SPOT-AC-001").status(1).build()))
                                        .build()))
                        .build());

        StubChargingStationService() {
            super(null, null, null, null, null);
        }

        @Override
        public List<ChargingStationDTO> findAllAvailable() {
            return stations;
        }

        @Override
        public List<ChargingStationDTO> searchByKeyword(String keyword) {
            return stations.stream()
                    .filter(station -> station.getName().contains(keyword)
                            || station.getAddress().contains(keyword)
                            || (station.getCity() != null && station.getCity().contains(keyword)))
                    .toList();
        }

        @Override
        public Optional<ChargingStationDTO> findById(Long id) {
            return stations.stream()
                    .filter(station -> station.getId().equals(id))
                    .findFirst();
        }
    }

    private static class StubParkingSpotService extends ParkingSpotService {
        private final List<ParkingSpot> spots;

        StubParkingSpotService(ParkingSpot... spots) {
            super(null);
            this.spots = List.of(spots);
        }

        @Override
        public Optional<ParkingSpot> findById(Long id) {
            return spots.stream().filter(spot -> spot.getId().equals(id)).findFirst();
        }

        @Override
        public List<ParkingSpot> findAvailableSpotsForTime(LocalDateTime startTime, LocalDateTime endTime) {
            return spots;
        }
    }
}
