package com.charging.repository;

import com.charging.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    
    Optional<Order> findByOrderNo(String orderNo);
    
    List<Order> findByUserId(Long userId);
    
    List<Order> findByUserIdAndStatus(Long userId, Integer status);
    
    List<Order> findByUserIdAndPaymentStatus(Long userId, Integer paymentStatus);
    
    Optional<Order> findByReservationId(Long reservationId);
    
    @Query("SELECT o FROM Order o WHERE o.user.id = :userId ORDER BY o.createdAt DESC")
    List<Order> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);
    
    @Query("SELECT o FROM Order o WHERE o.status = 1 AND o.createdAt < :timeout")
    List<Order> findUnpaidTimeoutOrders(@Param("timeout") LocalDateTime timeout);
    
    @Query("SELECT COUNT(o) FROM Order o WHERE o.status = 3 AND o.createdAt BETWEEN :start AND :end")
    Long countCompletedOrdersBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    
    @Query("SELECT SUM(o.totalAmount) FROM Order o WHERE o.status = 3 AND o.createdAt BETWEEN :start AND :end")
    java.math.BigDecimal sumRevenueBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
