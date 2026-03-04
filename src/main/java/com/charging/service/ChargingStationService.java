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
 * 充电站点管理服务
 * 
 * 作用：提供关于寻找场地、维护场地、新建场站的一系列核心操作。
 * 特别包含了一个能够智能根据空闲生成“假预约时间窗”的生成器，用于撑起前端丰富的数据展示盘。
 */
@Slf4j // 开启自动日志对象注入
@Service
@RequiredArgsConstructor
public class ChargingStationService {

    // 注入一系列的持久层(Dao)仓储接口
    private final ChargingStationRepository stationRepository;
    private final ChargingPileRepository pileRepository;
    private final ParkingSpotRepository spotRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;

    private final Random random = new Random();

    /**
     * 获取所有充电站信息（后台管理端使用）
     */
    public List<ChargingStationDTO> findAll() {
        return stationRepository.findAll().stream()
                .map(this::convertToDTO) // 实体对象不能直接丢前端，全部转一遍 DTO
                .collect(Collectors.toList());
    }

    /**
     * 仅获取在正常营业的充电站（小程序用户端地图寻找时使用）
     */
    public List<ChargingStationDTO> findAllAvailable() {
        return stationRepository.findAllAvailable().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public Optional<ChargingStationDTO> findById(Long id) {
        return stationRepository.findById(id).map(this::convertToDTO);
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

    /**
     * 新增一个大型充电场站及附属所有设备 (核心表单录入)
     * 利用了基于 Spring AOP 的事务机制控制，只要任何一边数据库炸了，全部回滚
     */
    @Transactional
    public ChargingStation create(ChargingStation station) {
        // [关键] 内存中建立双向关联对象树，确保 JPA 能够级联顺利保存 (CascadeType.ALL)
        if (station.getPiles() != null) {
            for (ChargingPile pile : station.getPiles()) {
                pile.setStation(station); // 给桩指认所在的站
                if (pile.getParkingSpots() != null) {
                    for (ParkingSpot spot : pile.getParkingSpots()) {
                        spot.setPile(pile); // 给坑指认归属的桩
                    }
                }
            }
        }
        // 只要 save 老大，附注的小兵由于 cascade 都会跟着被存进数据库不同表里
        ChargingStation saved = stationRepository.save(station);

        // === 特色补充逻辑：由于是 demo 新创建没啥数据，自动为每个新车位捏造一点随机预约数据(甘特图用) ===
        generateReservationsForStation(saved);

        return saved;
    }

    @Transactional
    public ChargingStation update(Long id, ChargingStation updateData) {
        // 查出原始站
        ChargingStation station = stationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("充电站不存在"));

        // 分别替换局部字段
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
    // 模拟预约历史数据生成的脚本逻辑区
    // 主要是为了解决展示"时间条甘特图"没有数据不好看的问题
    // =============================================

    /**
     * 辅助脚本：为新创建的站点的所有车位填充伪造的时间进度条
     */
    private void generateReservationsForStation(ChargingStation station) {
        // 找个背锅侠，用系统隐藏用户或者管理员来认领这些伪造的占位订单
        User mockUser = userRepository.findByUsername("_system")
                .orElse(userRepository.findAll().stream()
                        .filter(u -> u.getRole() == -1)
                        .findFirst().orElse(null));

        if (mockUser == null) {
            log.warn("没有可用虚拟系统伪用户，跳过预约数据生成");
            return;
        }

        // 把站底下的柱子和坑全拿出来，跑循环，给每个坑都随机染上点儿预定颜色
        List<ChargingPile> piles = pileRepository.findByStationId(station.getId());
        for (ChargingPile pile : piles) {
            List<ParkingSpot> spots = spotRepository.findByPileId(pile.getId());
            for (ParkingSpot spot : spots) {
                simulateDailyReservations(spot, mockUser);
            }
        }
        log.info("已成功为新站点 [{}] 生成一堆用于测试的随机排期预约数据", station.getName());
    }

    /**
     * 针对单独一个车位制造历史排期轨迹
     */
    private void simulateDailyReservations(ParkingSpot spot, User user) {
        LocalDateTime now = LocalDateTime.now();
        List<Reservation> dailyReservations = new ArrayList<>();

        double chance = random.nextDouble();

        // 模拟当前时点这根桩的状态大沙盘
        if (chance < 0.4) {
            // 人还没到，显示预约中(黄)：安排一条未来 10-31 分钟后起算的时段记录
            LocalDateTime start = now.plusMinutes(10 + random.nextInt(21));
            LocalDateTime end = start.plusMinutes(60);
            saveReservation(user, spot, start, end, 1, dailyReservations);
            spot.setStatus(2); // 2: 改变车位状态为预约中锁止
        } else if (chance < 0.8) {
            // 车已经在冲了，显示占用中(红)：安排一条开始时间在过去，结束时间在未来的段
            LocalDateTime start = now.minusMinutes(30 + random.nextInt(60));
            LocalDateTime end = now.plusMinutes(15 + random.nextInt(46));
            saveReservation(user, spot, start, end, 2, dailyReservations);
            spot.setStatus(3); // 3: 改变车位状态为通电工作占用中
        } else {
            // 没人鸟它
            spot.setStatus(1); // 1: 绿色空闲
        }
        spotRepository.save(spot); // 同步把车位的底色保存了

        // 接着往这根柱子上再叠加几个今天其余时间点的历史或未来干扰项定标针
        LocalDate today = LocalDate.now();
        for (int i = 0; i < 2; i++) {
            int randomHour = 8 + random.nextInt(14); // 早8点到晚22点
            LocalDateTime start = LocalDateTime.of(today, LocalTime.of(randomHour, 0))
                    .plusMinutes(random.nextInt(60));
            LocalDateTime end = start.plusMinutes(60 + random.nextInt(60));

            // 防撞击检测！如果在排这根柱子的时候和前面定好的时间有相交重叠的摩擦区间，就不要了直接丢弃
            boolean conflict = false;
            for (Reservation existing : dailyReservations) {
                if (start.isBefore(existing.getEndTime()) && end.isAfter(existing.getStartTime())) {
                    conflict = true;
                    break;
                }
            }
            // 不要覆盖住现在当前的空闲状态
            if (spot.getStatus() == 1 && start.isBefore(now) && end.isAfter(now)) {
                conflict = true;
            }
            // 实在算计到没撞上，才存入库
            if (!conflict) {
                int status;
                if (end.isBefore(now)) {
                    status = 3; // 那是过去时，设为已完成
                } else if (start.isAfter(now)) {
                    status = 1; // 还没到那会，设为排队预约中
                } else {
                    status = 2; // 刚好压中了
                }
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
        // 稍微严谨点，如果是占用或结束，得有实际打卡到达时间
        if (status == 2 || status == 3) {
            res.setActualArrivalTime(start.plusMinutes(2));
        }
        reservationRepository.save(res);
        list.add(res);
    }

    // =============================================
    // DTO 数据遮罩转换层区
    // 主要是把各种关联循环引用全部打平装箱成干净的结构
    // =============================================

    private ChargingStationDTO convertToDTO(ChargingStation station) {
        List<ChargingPile> piles = pileRepository.findByStationId(station.getId());

        // 数出站里状态为 1 (空闲可用) 的充电柱个数，用于展示在列表告诉车主此站闲着没
        int availableCount = (int) piles.stream()
                .filter(p -> p.getStatus() == 1)
                .count();

        // 递归降维套娃打包柱子
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
                .availablePileCount(availableCount) // <= 非常关键的数据，展示大字用
                .piles(pileDTOs)
                .build();
    }

    private ChargingPileDTO convertPileToDTO(ChargingPile pile) {
        List<ParkingSpot> spots = spotRepository.findByPileId(pile.getId());

        // 递归降维套娃打包车位
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
        // 最底层原子数据组装
        return ParkingSpotDTO.builder()
                .id(spot.getId())
                .spotCode(spot.getSpotCode())
                .spotNo(spot.getSpotCode()) // No等同于Code做回退兼容
                .spotType(spot.getSpotType())
                .pricePerHour(spot.getPricePerHour())
                .serviceFee(spot.getServiceFee())
                .status(spot.getStatus())
                .build();
    }
}
