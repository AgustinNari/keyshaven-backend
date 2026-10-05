package com.uade.tpo.marketplace.controllers;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.uade.tpo.marketplace.controllers.auth.CurrentUserProvider;
import com.uade.tpo.marketplace.entity.dto.create.DiscountCreateDto;
import com.uade.tpo.marketplace.entity.dto.request.CouponValidationBulkRequestDto;
import com.uade.tpo.marketplace.entity.dto.request.CouponValidationRequestDto;
import com.uade.tpo.marketplace.entity.dto.response.CouponValidationResponseDto;
import com.uade.tpo.marketplace.entity.dto.response.DiscountResponseDto;
import com.uade.tpo.marketplace.entity.dto.update.DiscountUpdateDto;
import com.uade.tpo.marketplace.exceptions.DuplicateResourceException;
import com.uade.tpo.marketplace.exceptions.ResourceNotFoundException;
import com.uade.tpo.marketplace.exceptions.UnauthorizedException;
import com.uade.tpo.marketplace.service.interfaces.IDiscountService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/discounts")
public class DiscountsController {

    @Autowired private IDiscountService discountService;
    @Autowired private com.uade.tpo.marketplace.extra.mappers.DiscountMapper discountMapper;
    @Autowired private CurrentUserProvider currentUserProvider;

    @PostMapping
    public ResponseEntity<DiscountResponseDto> createDiscount(@Valid @RequestBody DiscountCreateDto dto,
                                                      Authentication authentication)
            throws DuplicateResourceException, ResourceNotFoundException {
        Long createdByUserId = currentUserProvider.getCurrentUserId(authentication);
        var out = discountService.createDiscount(dto, createdByUserId);
        return ResponseEntity.created(URI.create("/discounts/" + out.id())).body(out);
    }

    @PutMapping("/{id}")
    public DiscountResponseDto updateDiscount(@PathVariable Long id,
                                      @Valid @RequestBody DiscountUpdateDto dto,
                                      Authentication authentication)
            throws ResourceNotFoundException, UnauthorizedException, DuplicateResourceException {
        Long requestingUserId = currentUserProvider.getCurrentUserId(authentication);
        return discountService.updateDiscount(id, dto, requestingUserId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DiscountResponseDto> getDiscountById(@PathVariable Long id) {
        return discountService.getDiscountById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/code/{code}")
    public ResponseEntity<DiscountResponseDto> getActiveDiscountByCode(@PathVariable String code) {
        return discountService.getActiveDiscountByCode(code)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/active")
    public Page<DiscountResponseDto> getActiveDiscounts(Pageable pageable,
                                            @RequestParam(required = false) Boolean onlyActive) {
        return discountService.getActiveDiscounts(pageable, Optional.ofNullable(onlyActive));
    }

    @GetMapping
    public Page<DiscountResponseDto> getAllDiscounts(Pageable pageable) {
        return discountService.getAllDiscounts(pageable);
    }

    @GetMapping("/product/{productId}")
    public Optional<DiscountResponseDto> getHighestValueDiscountForProduct(@PathVariable Long productId, @RequestParam(defaultValue = "1") Integer quantity){
        if (quantity < 1) throw new com.uade.tpo.marketplace.exceptions.BadRequestException("Cantidad inválida");
        return discountService.getHighestValueDiscountForOrderItem(new com.uade.tpo.marketplace.entity.dto.create.OrderItemCreateDto(productId, null, quantity)).map(discountMapper::toResponse);
    }

    @GetMapping("/buyer/active-coupons")
    public Page<DiscountResponseDto> getAllActiveCouponsByTargetBuyerId(Authentication authentication, Pageable pageable) {
        Long buyerId = currentUserProvider.getCurrentUserId(authentication);
        return discountService.getAllActiveCouponsByTargetBuyerId(buyerId, pageable);
    }


    @PostMapping("/validate")
    public ResponseEntity<CouponValidationResponseDto> validateCouponForOrderItemPreview(
            @Valid @RequestBody CouponValidationRequestDto request,
            Authentication authentication) {

        Long authUserId = currentUserProvider.getCurrentUserId(authentication);
        Long buyerId = request.buyerId() == null ? authUserId : request.buyerId();

        if (buyerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (request.buyerId() != null && !Objects.equals(request.buyerId(), authUserId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        CouponValidationResponseDto response =
                discountService.validateCouponForOrderItemPreview(request.code(), buyerId, request.item());

        return ResponseEntity.ok(response);
    }


    @PostMapping("/validate/bulk")
    public ResponseEntity<CouponValidationResponseDto> validateCouponForOrderItemsPreview(
            @Valid @RequestBody CouponValidationBulkRequestDto request,
            Authentication authentication) {

        Long authUserId = currentUserProvider.getCurrentUserId(authentication);
        Long buyerId = request.buyerId() == null ? authUserId : request.buyerId();

        if (buyerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (request.buyerId() != null && !Objects.equals(request.buyerId(), authUserId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        CouponValidationResponseDto response =
                discountService.validateCouponForOrderItemsPreview(request.code(), buyerId, request.items());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/seller/me")
    public Page<DiscountResponseDto> getDiscountsForSellerManagement(Pageable pageable, Authentication authentication)
            throws ResourceNotFoundException, UnauthorizedException {

        Long requestingUserId = currentUserProvider.getCurrentUserId(authentication);

        return discountService.getDiscountsForSellerManagement(requestingUserId, pageable);
    }

    @GetMapping("/admin/categories")
    public Page<DiscountResponseDto> getDiscountsForAdminManagement(
            Pageable pageable,
            Authentication authentication)
            throws ResourceNotFoundException, UnauthorizedException {

        Long requestingUserId = currentUserProvider.getCurrentUserId(authentication);
        return discountService.getDiscountsForAdminManagement(requestingUserId, pageable);
    }



}
