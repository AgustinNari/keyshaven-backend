package com.uade.tpo.marketplace.repository.interfaces;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.uade.tpo.marketplace.entity.basic.Discount;
import com.uade.tpo.marketplace.entity.enums.DiscountScope;
import com.uade.tpo.marketplace.entity.enums.DiscountType;

import jakarta.transaction.Transactional;

@Repository
public interface IDiscountRepository extends JpaRepository<Discount, Long> {

       @Query("SELECT d FROM Discount d WHERE d.startsAt <= :at AND (d.endsAt IS NULL OR d.endsAt >= :at) AND d.active = true")
       Page<Discount> findActiveAt(@Param("at") Instant at, Pageable pageable);
       
       @Query("SELECT d FROM Discount d WHERE d.value >= :minValue")
       Page<Discount> findByMinValue(@Param("minValue") BigDecimal minValue, Pageable pageable);
       
       @Query("SELECT d FROM Discount d WHERE (d.startsAt IS NULL OR d.startsAt <= CURRENT_TIMESTAMP) AND (d.endsAt IS NULL OR d.endsAt >= CURRENT_TIMESTAMP) AND d.active = true")
       Page<Discount> findCurrentlyActive(Pageable pageable);
       
       Page<Discount> findByActiveTrue(Pageable pageable);

       Optional<Discount> findByCode(String code);

       @Query("SELECT d FROM Discount d WHERE d.code = :code AND d.active = true " +
              "AND (d.startsAt IS NULL OR d.startsAt <= CURRENT_TIMESTAMP) AND (d.endsAt IS NULL OR d.endsAt >= CURRENT_TIMESTAMP)")
       Optional<Discount> findByCodeAndActive(String code);

       boolean existsByCode(String code);
       

       Page<Discount> findByType(DiscountType type, Pageable pageable);

       @Query("SELECT d FROM Discount d WHERE d.type = :type AND d.active = :active " +
              "AND (d.startsAt IS NULL OR d.startsAt <= CURRENT_TIMESTAMP) AND (d.endsAt IS NULL OR d.endsAt >= CURRENT_TIMESTAMP)")
       Page<Discount> findByTypeAndActive(DiscountType type, boolean active, Pageable pageable);

       Page<Discount> findByScope(DiscountScope scope, Pageable pageable);


       @Query("SELECT d FROM Discount d WHERE d.active = true AND d.scope = :scope AND d.targetProduct.id = :productId " +
              "AND (d.startsAt IS NULL OR d.startsAt <= CURRENT_TIMESTAMP) AND (d.endsAt IS NULL OR d.endsAt >= CURRENT_TIMESTAMP)")
       Page<Discount> findActiveDiscountsByScopeAndProduct(@Param("scope") DiscountScope scope, @Param("productId") Long productId, Pageable pageable);

       @Query("SELECT d FROM Discount d WHERE d.active = true AND d.scope = :scope AND d.targetCategory.id = :categoryId " +
              "AND (d.startsAt IS NULL OR d.startsAt <= CURRENT_TIMESTAMP) AND (d.endsAt IS NULL OR d.endsAt >= CURRENT_TIMESTAMP)")
       Page<Discount> findActiveDiscountsByScopeAndCategory(@Param("scope") DiscountScope scope, @Param("categoryId") Long categoryId, Pageable pageable);

       @Query("SELECT d FROM Discount d WHERE d.active = true AND d.scope = :scope AND d.targetSeller.id = :sellerId " +
              "AND (d.startsAt IS NULL OR d.startsAt <= CURRENT_TIMESTAMP) AND (d.endsAt IS NULL OR d.endsAt >= CURRENT_TIMESTAMP)")
       Page<Discount> findActiveDiscountsByScopeAndSeller(@Param("scope") DiscountScope scope, @Param("sellerId") Long sellerId, Pageable pageable);
       


       
       @Query("SELECT d FROM Discount d WHERE d.active = true AND d.scope = 'PRODUCT' AND d.targetProduct.id = :productId " +
              "AND (d.startsAt IS NULL OR d.startsAt <= CURRENT_TIMESTAMP) AND (d.endsAt IS NULL OR d.endsAt >= CURRENT_TIMESTAMP)")
       Page<Discount> findActiveProductDiscounts(@Param("productId") Long productId, Pageable pageable);
       
       @Query("SELECT d FROM Discount d WHERE d.active = true AND d.scope = 'CATEGORY' AND d.targetCategory.id = :categoryId " +
              "AND (d.startsAt IS NULL OR d.startsAt <= CURRENT_TIMESTAMP) AND (d.endsAt IS NULL OR d.endsAt >= CURRENT_TIMESTAMP)")
       Page<Discount> findActiveCategoryDiscounts(@Param("categoryId") Long categoryId, Pageable pageable);

       @Query("SELECT d FROM Discount d WHERE d.active = true AND d.scope = 'SELLER' AND d.targetSeller.id = :sellerId " +
              "AND (d.startsAt IS NULL OR d.startsAt <= CURRENT_TIMESTAMP) AND (d.endsAt IS NULL OR d.endsAt >= CURRENT_TIMESTAMP)")
       Page<Discount> findActiveSellerDiscounts(@Param("sellerId") Long sellerId, Pageable pageable);

       
       @Query("SELECT d FROM Discount d WHERE d.targetBuyer.id = :targetBuyerId AND d.active = true " +
              "AND (d.startsAt IS NULL OR d.startsAt <= CURRENT_TIMESTAMP) AND (d.endsAt IS NULL OR d.endsAt >= CURRENT_TIMESTAMP)")
       Page<Discount> findActiveByTargetBuyerId(Long targetBuyerId, Pageable pageable);

       @Query("SELECT d FROM Discount d WHERE d.scope = :scope AND d.targetBuyer.id = :targetBuyerId AND d.active = true " +
              "AND (d.startsAt IS NULL OR d.startsAt <= CURRENT_TIMESTAMP) AND (d.endsAt IS NULL OR d.endsAt >= CURRENT_TIMESTAMP)")
       Page<Discount> findActiveByScopeAndTargetBuyerId(DiscountScope scope, Long targetBuyerId, Pageable pageable);

       @Query("SELECT d FROM Discount d WHERE d.targetProduct.id = :productId AND d.active = true " +
              "AND d.type = 'PERCENT' " + "AND (d.minPrice IS NULL OR d.minPrice <= d.targetProduct.price) " + "AND (d.maxPrice IS NULL OR d.maxPrice >= d.targetProduct.price) " +
              "AND (d.startsAt IS NULL OR d.startsAt <= CURRENT_TIMESTAMP) AND (d.endsAt IS NULL OR d.endsAt >= CURRENT_TIMESTAMP) " +
              "ORDER BY d.value DESC")
       List<Discount> getHighestValueDiscountsForProduct(Long productId);

       @Query("SELECT d FROM Discount d WHERE d.targetCategory.id = :categoryId AND d.active = true " +
              "AND d.type = 'PERCENT' " + "AND (d.minPrice IS NULL OR d.minPrice <= :productPrice) " + "AND (d.maxPrice IS NULL OR d.maxPrice >= :productPrice) " +
              "AND (d.startsAt IS NULL OR d.startsAt <= CURRENT_TIMESTAMP) AND (d.endsAt IS NULL OR d.endsAt >= CURRENT_TIMESTAMP) " +
              "ORDER BY d.value DESC")
       List<Discount> getHighestValueDiscountsForCategory(Long categoryId, BigDecimal productPrice);

       @Query("SELECT d FROM Discount d WHERE d.targetSeller.id = :sellerId AND d.active = true " +
              "AND d.type = 'PERCENT' " + "AND (d.minPrice IS NULL OR d.minPrice <= :productPrice) " + "AND (d.maxPrice IS NULL OR d.maxPrice >= :productPrice) " +
              "AND (d.startsAt IS NULL OR d.startsAt <= CURRENT_TIMESTAMP) AND (d.endsAt IS NULL OR d.endsAt >= CURRENT_TIMESTAMP) " +
              "ORDER BY d.value DESC")
       List<Discount> getHighestValueDiscountsForSeller(Long sellerId, BigDecimal productPrice);


       @Query("SELECT d FROM Discount d WHERE d.type = 'FIXED' AND d.targetBuyer.id = :targetBuyerId AND d.active = true " +
              "AND (d.startsAt IS NULL OR d.startsAt <= CURRENT_TIMESTAMP) " +
              "AND (d.endsAt IS NULL OR d.endsAt >= CURRENT_TIMESTAMP)")
       Page<Discount> getAllActiveCouponsByTargetBuyerId(Long targetBuyerId, Pageable pageable);


       @Query("SELECT d FROM Discount d WHERE d.code = :code AND d.targetBuyer.id = :targetBuyerId AND d.active = true " +
              "AND (d.startsAt IS NULL OR d.startsAt <= CURRENT_TIMESTAMP) AND (d.endsAt IS NULL OR d.endsAt >= CURRENT_TIMESTAMP)")
       Optional<Discount> getActiveCouponByCodeAndTargetBuyerId(String code, Long targetBuyerId);

       @Modifying
       @Transactional
       @Query("UPDATE Discount d SET d.active = false WHERE d.id = :couponId AND d.active = true AND d.type = 'FIXED' AND d.targetBuyer.id = :targetBuyerId")
       int markCouponAsUsed(Long couponId, Long targetBuyerId);


       @Query("SELECT d FROM Discount d LEFT JOIN d.targetProduct p LEFT JOIN p.seller s LEFT JOIN d.targetSeller ts " +
              "WHERE (s.id = :sellerId OR ts.id = :sellerId)  ")
       Page<Discount> findDiscountsForSeller(@Param("sellerId") Long sellerId, Pageable pageable);

       @Query("SELECT d FROM Discount d " +
              "WHERE (d.targetSeller IS NOT NULL AND d.targetSeller.id = :sellerId) ")
       Page<Discount> findDiscountsForSeller2(@Param("sellerId") Long sellerId, Pageable pageable);

       @Modifying
       @Transactional
       @Query("UPDATE Discount d SET d.targetSeller = NULL WHERE d.id = :discountId")
       int eraseSellerId(Long discountId);

       @Modifying
       @Transactional
       @Query("UPDATE Discount d SET d.targetProduct = NULL WHERE d.id = :discountId")
       int eraseProductId(Long discountId);

       @Query("SELECT d FROM Discount d " +
              "WHERE d.targetCategory IS NOT NULL ")
       Page<Discount> getDiscountsForCategories(Pageable pageable);


}