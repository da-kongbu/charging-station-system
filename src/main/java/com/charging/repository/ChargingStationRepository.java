package com.charging.repository;

import com.charging.entity.ChargingStation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChargingStationRepository extends JpaRepository<ChargingStation, Long> {

    Optional<ChargingStation> findByNameAndAddress(String name, String address);
    
    List<ChargingStation> findByStatus(Integer status);
    
    List<ChargingStation> findByCity(String city);
    
    List<ChargingStation> findByDistrict(String district);
    
    List<ChargingStation> findByCityAndDistrict(String city, String district);
    
    @Query("SELECT s FROM ChargingStation s WHERE s.name LIKE %:keyword% OR s.address LIKE %:keyword%")
    List<ChargingStation> searchByKeyword(@Param("keyword") String keyword);
    
    @Query("SELECT s FROM ChargingStation s WHERE s.status = 1")
    List<ChargingStation> findAllAvailable();
}
