package com.charging.service;

import com.charging.dto.ChargingPileDTO;
import com.charging.dto.ChargingStationDTO;
import com.charging.dto.ParkingSpotDTO;
import com.charging.entity.ChargingPile;
import com.charging.entity.ChargingStation;
import com.charging.entity.ParkingSpot;
import com.charging.repository.ChargingPileRepository;
import com.charging.repository.ChargingStationRepository;
import com.charging.repository.ParkingSpotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 充电站服务
 */
@Service
@RequiredArgsConstructor
public class ChargingStationService {

    private final ChargingStationRepository stationRepository;
    private final ChargingPileRepository pileRepository;
    private final ParkingSpotRepository spotRepository;

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
        return stationRepository.save(station);
    }

    @Transactional
    public ChargingStation update(Long id, ChargingStation updateData) {
        ChargingStation station = stationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("充电站不存在"));

        if (updateData.getName() != null) {
            station.setName(updateData.getName());
        }
        if (updateData.getAddress() != null) {
            station.setAddress(updateData.getAddress());
        }
        if (updateData.getCity() != null) {
            station.setCity(updateData.getCity());
        }
        if (updateData.getDistrict() != null) {
            station.setDistrict(updateData.getDistrict());
        }
        if (updateData.getContact() != null) {
            station.setContact(updateData.getContact());
        }
        if (updateData.getBusinessHours() != null) {
            station.setBusinessHours(updateData.getBusinessHours());
        }
        if (updateData.getStatus() != null) {
            station.setStatus(updateData.getStatus());
        }

        return stationRepository.save(station);
    }

    @Transactional
    public void deleteById(Long id) {
        stationRepository.deleteById(id);
    }

    public List<ChargingPile> getPilesByStationId(Long stationId) {
        return pileRepository.findByStationId(stationId);
    }

    private ChargingStationDTO convertToDTO(ChargingStation station) {
        List<ChargingPile> piles = pileRepository.findByStationId(station.getId());
        int availableCount = (int) piles.stream()
                .filter(p -> p.getStatus() == 1)
                .count();

        // Convert piles to DTOs with spots
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
