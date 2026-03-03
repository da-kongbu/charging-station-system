package com.charging.service;

import com.charging.dto.ChargingPileDTO;
import com.charging.dto.ChargingStationDTO;
import com.charging.dto.ParkingSpotDTO;
import com.charging.entity.ChargingPile;
import com.charging.entity.ChargingStation;
import com.charging.entity.ParkingSpot;
import com.charging.entity.Reservation;
import com.charging.entity.User;
import com.charging.repository.ChargingPileRepository;
import com.charging.repository.ChargingStationRepository;
import com.charging.repository.ParkingSpotRepository;
import com.charging.repository.ReservationRepository;
import com.charging.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * 充电站服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChargingStationService {

    private final ChargingStationRepository stationRepository;
    private final ChargingPileRepository pileRepository;
    private final ParkingSpotRepository spotRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;

    private final Random random = new Random();

    public List<ChargingStationDTO> findAll() {
        return stationRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<ChargingStationDTO> findAllAvailable() {
        return stationRepository.findAllAvailable().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public Optional<ChargingStationDTO> findById(Long id) {
        return stationRepository.findById(id)
                .map(this::convertToDTO);
    }

    public List<ChargingStationDTO> findByCity(String city) {
        return stationRepository.findByCity(city).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<ChargingStationDTO> searchByKeyword(String keyword) {
        return stationRepository.searchByKeyword(keyword).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public ChargingStation create(ChargingStation station) {
        // 建立双向关联，确保级联保存成功
        if (station.getPiles() != null) {
            for (ChargingPile pile : station.getPiles()) {
                pile.setStation(station);
                if (pile.getParkingSpots() != null) {
                    for (ParkingSpot spot : pile.getParkingSpots()) {
                        spot.setPile(pile);
                    }
                }
            }
        }
        ChargingStation saved = stationRepository.save(station);

        // === 自动为每个车位生成模拟预约数据（时间条） ===
        generateReservationsForStation(saved);

        return saved;
    }

    @Transactional
    public ChargingStation update(Long id, ChargingStation updateData) {
        ChargingStation station = stationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("充电站不存在"));

        if (updateData.getName() != null)
            station.setName(updateData.getName());
        if (updateData.getAddress() != null)
            station.setAddress(updateData.getAddress());
        if (updateData.getCity() != null)
            station.setCity(updateData.getCity());
        if (updateData.getDistrict() != null)
            station.setDistrict(updateData.getDistrict());
        if (updateData.getContact() != null)
            station.setContact(updateData.getContact());
        if (updateData.getBusinessHours() != null)
            station.setBusinessHours(updateData.getBusinessHours());
        if (updateData.getStatus() != null)
            station.setStatus(updateData.getStatus());

        return stationRepository.save(station);
    }

    @Transactional
    public void deleteById(Long id) {
        stationRepository.deleteById(id);
    }

    public List<ChargingPile> getPilesByStationId(Long stationId) {
        return pileRepository.findByStationId(stationId);
    }

    // =============================================
    // 模拟预约数据生成逻辑
    // =============================================

    /**
     * 为新创建的站点的所有车位生成模拟预约时间条
     */
    private void generateReservationsForStation(ChargingStation station) {
        User mockUser = userRepository.findAll().stream()
                .filter(u -> u.getRole() == 0)
                .findFirst()
                .orElse(userRepository.findAll().stream().findFirst().orElse(null));

        if (mockUser == null) {
            log.warn("没有可用用户，跳过预约数据生成");
            return;
        }

        List<ChargingPile> piles = pileRepository.findByStationId(station.getId());
        for (ChargingPile pile : piles) {
            List<ParkingSpot> spots = spotRepository.findByPileId(pile.getId());
            for (ParkingSpot spot : spots) {
                simulateDailyReservations(spot, mockUser);
            }
        }
        log.info("已为站点 [{}] 生成模拟预约数据", station.getName());
    }

    /**
     * 为单个车位生成当天的随机预约时间段
     * 40% 预约中(黄) | 40% 占用中(红) | 20% 空闲(绿)
     */
    private void simulateDailyReservations(ParkingSpot spot, User user) {
        LocalDateTime now = LocalDateTime.now();
        List<Reservation> dailyReservations = new ArrayList<>();

        double chance = random.nextDouble();

        if (chance < 0.4) {
            // 预约中：生成一条未来的记录
            LocalDateTime start = now.plusMinutes(10 + random.nextInt(21));
            LocalDateTime end = start.plusMinutes(60);
            saveReservation(user, spot, start, end, 1, dailyReservations);
            spot.setStatus(2); // 预约中
        } else if (chance < 0.8) {
            // 占用中：生成一条跨越当前时间的记录
            LocalDateTime start = now.minusMinutes(30 + random.nextInt(60));
            LocalDateTime end = now.plusMinutes(15 + random.nextInt(46));
            saveReservation(user, spot, start, end, 2, dailyReservations);
            spot.setStatus(3); // 使用中
        } else {
            spot.setStatus(1); // 空闲
        }
        spotRepository.save(spot);

        // 填充额外的历史/未来干扰数据
        LocalDate today = LocalDate.now();
        for (int i = 0; i < 2; i++) {
            int randomHour = 8 + random.nextInt(14);
            LocalDateTime start = LocalDateTime.of(today, LocalTime.of(randomHour, 0))
                    .plusMinutes(random.nextInt(60));
            LocalDateTime end = start.plusMinutes(60 + random.nextInt(60));

            boolean conflict = false;
            for (Reservation existing : dailyReservations) {
                if (start.isBefore(existing.getEndTime()) && end.isAfter(existing.getStartTime())) {
                    conflict = true;
                    break;
                }
            }
            if (spot.getStatus() == 1 && start.isBefore(now) && end.isAfter(now)) {
                conflict = true;
            }
            if (!conflict) {
                int status = 1;
                if (end.isBefore(now))
                    status = 3;
                saveReservation(user, spot, start, end, status, dailyReservations);
            }
        }
    }

    private void saveReservation(User user, ParkingSpot spot, LocalDateTime start, LocalDateTime end,
            int status, List<Reservation> list) {
        Reservation res = Reservation.builder()
                .user(user)
                .spot(spot)
                .startTime(start)
                .endTime(end)
                .status(status)
                .carPlate("京A" + random.nextInt(99999))
                .createdAt(LocalDateTime.now().minusHours(1))
                .build();
        if (status == 2 || status == 3) {
            res.setActualArrivalTime(start.plusMinutes(2));
        }
        reservationRepository.save(res);
        list.add(res);
    }

    // =============================================
    // DTO 转换逻辑
    // =============================================

    private ChargingStationDTO convertToDTO(ChargingStation station) {
        List<ChargingPile> piles = pileRepository.findByStationId(station.getId());
        int availableCount = (int) piles.stream()
                .filter(p -> p.getStatus() == 1)
                .count();

        List<ChargingPileDTO> pileDTOs = piles.stream()
                .map(this::convertPileToDTO)
                .collect(Collectors.toList());

        return ChargingStationDTO.builder()
                .id(station.getId())
                .name(station.getName())
                .address(station.getAddress())
                .city(station.getCity())
                .district(station.getDistrict())
                .longitude(station.getLongitude())
                .latitude(station.getLatitude())
                .contact(station.getContact())
                .businessHours(station.getBusinessHours())
                .description(station.getDescription())
                .status(station.getStatus())
                .pileCount(piles.size())
                .availablePileCount(availableCount)
                .piles(pileDTOs)
                .build();
    }

    private ChargingPileDTO convertPileToDTO(ChargingPile pile) {
        List<ParkingSpot> spots = spotRepository.findByPileId(pile.getId());

        List<ParkingSpotDTO> spotDTOs = spots.stream()
                .map(this::convertSpotToDTO)
                .collect(Collectors.toList());

        return ChargingPileDTO.builder()
                .id(pile.getId())
                .pileCode(pile.getPileCode())
                .pileNo(pile.getPileCode())
                .pileType(pile.getPileType())
                .power(pile.getPower())
                .voltage(pile.getVoltage())
                .current(pile.getCurrent())
                .brand(pile.getBrand())
                .connectorType(pile.getConnectorType())
                .status(pile.getStatus())
                .parkingSpots(spotDTOs)
                .build();
    }

    private ParkingSpotDTO convertSpotToDTO(ParkingSpot spot) {
        return ParkingSpotDTO.builder()
                .id(spot.getId())
                .spotCode(spot.getSpotCode())
                .spotNo(spot.getSpotCode())
                .spotType(spot.getSpotType())
                .pricePerHour(spot.getPricePerHour())
                .serviceFee(spot.getServiceFee())
                .status(spot.getStatus())
                .build();
    }
}
