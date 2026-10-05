package com.uade.tpo.marketplace.repository.interfaces;

import java.math.BigDecimal;
import java.sql.Blob;
import java.time.Instant;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.uade.tpo.marketplace.entity.basic.User;
import com.uade.tpo.marketplace.entity.enums.Role;

import jakarta.transaction.Transactional;

@Repository
public interface IUserRepository extends JpaRepository<User, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);



    Page<User> findByRole(Role role, Pageable pageable);
    

    @Query("SELECT u FROM User u WHERE LOWER(u.displayName) = LOWER(:displayName)")
    Optional<User> findByDisplayNameIgnoreCase(@Param("displayName") String displayName);
    
    Optional<User> findByEmail(String email);
    
    @Query("SELECT u FROM User u WHERE LOWER(u.email) = LOWER(:email)")
    Optional<User> findByEmailIgnoreCase(@Param("email") String email);
    
    Page<User> findByActiveTrue(Pageable pageable);
    
    @Query("SELECT COUNT(u) > 0 FROM User u WHERE LOWER(u.displayName) = LOWER(:displayName)")
    boolean existsByDisplayNameIgnoreCase(@Param("displayName") String displayName);
    
    @Query("SELECT COUNT(u) > 0 FROM User u WHERE LOWER(u.email) = LOWER(:email)")
    boolean existsByEmailIgnoreCase(@Param("email") String email);
    
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.orders WHERE u.id = :userId")
    Optional<User> findByIdWithOrders(@Param("userId") Long userId);
    
    @Query("SELECT u FROM User u WHERE u.role = 'SELLER' AND SIZE(u.products) > 0")
    Page<User> findSellersWithProducts(Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.role = :role AND SIZE(u.products) > 0")
    Page<User> findUsersByRoleAndHasProducts(@Param("role") Role role, Pageable pageable);
    

    Page<User> findByCountry(String country, Pageable pageable);

    @Transactional
    @Modifying
    @Query("UPDATE User u SET u.buyerBalance = :newBalance WHERE u.id = :userId")
    int updateBuyerBalance(Long userId, BigDecimal newBalance);

    @Transactional
    @Modifying
    @Query("UPDATE User u SET u.lastLogin = :loginTime WHERE u.id = :userId")
    int registerNewLogin(Long userId, Instant loginTime);

    @Transactional
    @Modifying
    @Query("UPDATE User u SET u.avatar = :avatar, u.avatarContentType = :avatarContentType WHERE u.id = :userId")
    int uploadAvatarById(Long userId, Blob avatar, String avatarContentType);

    @Transactional
    @Modifying
    @Query("UPDATE User u SET u.avatar = NULL, u.avatarContentType = NULL WHERE u.id = :userId")
    int deleteAvatarById(Long userId);

    @Transactional
    @Modifying
    @Query("UPDATE User u SET u.active = :active WHERE u.id = :userId")
    int toggleUserActivity(@Param("userId") Long userId, @Param("active") boolean active);

}