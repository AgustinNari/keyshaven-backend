package com.uade.tpo.marketplace.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uade.tpo.marketplace.entity.basic.Category;
import com.uade.tpo.marketplace.entity.basic.Discount;
import com.uade.tpo.marketplace.entity.basic.Product;
import com.uade.tpo.marketplace.entity.basic.User;
import com.uade.tpo.marketplace.entity.dto.create.DiscountCreateDto;
import com.uade.tpo.marketplace.entity.dto.create.OrderItemCreateDto;
import com.uade.tpo.marketplace.entity.dto.response.CouponValidationResponseDto;
import com.uade.tpo.marketplace.entity.dto.response.DiscountResponseDto;
import com.uade.tpo.marketplace.entity.dto.update.DiscountUpdateDto;
import com.uade.tpo.marketplace.entity.enums.DiscountScope;
import com.uade.tpo.marketplace.entity.enums.DiscountType;
import com.uade.tpo.marketplace.entity.enums.Role;
import com.uade.tpo.marketplace.exceptions.BadRequestException;
import com.uade.tpo.marketplace.exceptions.CategoryNotFoundException;
import com.uade.tpo.marketplace.exceptions.CouponAlreadyUsedException;
import com.uade.tpo.marketplace.exceptions.CouponNotApplicableException;
import com.uade.tpo.marketplace.exceptions.CouponTargetBuyerMissingException;
import com.uade.tpo.marketplace.exceptions.DiscountNotFoundException;
import com.uade.tpo.marketplace.exceptions.DuplicateCouponException;
import com.uade.tpo.marketplace.exceptions.DuplicateResourceException;
import com.uade.tpo.marketplace.exceptions.OrderNotFoundException;
import com.uade.tpo.marketplace.exceptions.ProductNotFoundException;
import com.uade.tpo.marketplace.exceptions.ResourceNotFoundException;
import com.uade.tpo.marketplace.exceptions.UnauthorizedException;
import com.uade.tpo.marketplace.exceptions.UserNotFoundException;
import com.uade.tpo.marketplace.extra.mappers.DiscountMapper;
import com.uade.tpo.marketplace.repository.interfaces.ICategoryRepository;
import com.uade.tpo.marketplace.repository.interfaces.IDiscountRepository;
import com.uade.tpo.marketplace.repository.interfaces.IProductRepository;
import com.uade.tpo.marketplace.repository.interfaces.IUserRepository;
import com.uade.tpo.marketplace.service.interfaces.IDiscountService;



@Service
public class DiscountService implements IDiscountService {
    
    @Autowired
    private IDiscountRepository discountRepository;
    @Autowired
    private IProductRepository productRepository;
    @Autowired
    private ICategoryRepository categoryRepository;
    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private DiscountMapper discountMapper;




    @Override
    @Transactional(rollbackFor = Throwable.class)
    public DiscountResponseDto createDiscount(DiscountCreateDto dto, Long createdByUserId)
            throws DuplicateResourceException, ResourceNotFoundException {


        if (dto == null) throw new BadRequestException("Datos de descuento no proporcionados.");
        validateBounds(dto.type(), dto.value(), dto.minQuantity(), dto.maxQuantity(), dto.startsAt(), dto.endsAt());


        if (dto.type() == null) {
            throw new BadRequestException("El tipo de descuento (PERCENT o FIXED) debe ser especificado.");
        }

        if (dto.scope() == null) {
            throw new BadRequestException("El ámbito del descuento (scope) debe ser especificado.");
        }

        if (dto.code() != null){
            if (discountRepository.existsByCode(dto.code())) {
                throw new DuplicateCouponException("Ya existe un cupón con el código: " + dto.code());
            }
        }

  
        if (dto.targetProductId() != null) {
            Product product = productRepository.findById(dto.targetProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Producto objetivo no encontrado (id=" + dto.targetProductId() + ")."));
            if (!product.getSeller().getId().equals(createdByUserId)) {
                throw new UnauthorizedException("El usuario solicitante no puede crear descuentos dirigidos a productos que no le pertenecen.");
            }
        }
        if (dto.targetCategoryId() != null) {
            categoryRepository.findById(dto.targetCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Categoría objetivo no encontrada (id=" + dto.targetCategoryId() + ")."));
            Optional<User> user = userRepository.findById(createdByUserId);
            if (user.isEmpty() || !user.get().getRole().equals(Role.ADMIN)) {
                throw new UnauthorizedException("El usuario solicitante no puede crear descuentos dirigidos a categorías.");
            }

        }
        if (dto.targetSellerId() != null) {
            userRepository.findById(dto.targetSellerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Vendedor objetivo no encontrado (id=" + dto.targetSellerId() + ")."));
            if (!dto.targetSellerId().equals(createdByUserId)) {
                throw new UnauthorizedException("El usuario solicitante no puede crear descuentos dirigidos a otros vendedores.");
            }
        }
        if (dto.targetBuyerId() != null) {
            userRepository.findById(dto.targetBuyerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Comprador objetivo no encontrado (id=" + dto.targetBuyerId() + ")."));
            if (dto.targetBuyerId().equals(createdByUserId)) {
                throw new UnauthorizedException("El usuario solicitante no puede crear descuentos dirigidos a si mismo.");
            }
        }

        if (dto.targetProductId() == null && dto.targetCategoryId() == null && dto.targetSellerId() == null) {
            throw new BadRequestException("Debe especificarse al menos un objetivo para el descuento: targetProductId, targetCategoryId o targetSellerId.");
        }

        DiscountCreateDto dtoToUse = dto;

        if (dto.minQuantity() == null) {
            dtoToUse = new DiscountCreateDto(
                    dto.code(),
                    dto.type(),
                    dto.value(),
                    dto.scope(),
                    dto.targetProductId(),
                    dto.targetCategoryId(),
                    dto.targetSellerId(),
                    1,
                    1000,
                    dto.startsAt(),
                    dto.endsAt(),
                    new BigDecimal(1),
                    new BigDecimal(1000000000),
                    dto.endsAt(),
                    dto.targetBuyerId()
            );
            
        }

            if (dto.type() == DiscountType.FIXED) {
                if (dto.targetBuyerId() == null) {
                    List<User> buyers = userRepository.findAll()
                            .stream()
                            .filter(u -> !u.getId().equals(createdByUserId))
                            .collect(Collectors.toList());

                    if (buyers.isEmpty()) {
                        throw new UserNotFoundException("No se encontraron compradores válidos para asignar al cupón.");
                    }

                    User randomBuyer = buyers.get(new java.util.Random().nextInt(buyers.size()));
                    Long chosenBuyerId = randomBuyer.getId();

                    if (dto.minQuantity() == null) {
                        dtoToUse = new DiscountCreateDto(
                                dto.code(),
                                dto.type(),
                                dto.value(),
                                dto.scope(),
                                dto.targetProductId(),
                                dto.targetCategoryId(),
                                dto.targetSellerId(),
                                1,
                                1000,
                                dto.startsAt(),
                                dto.endsAt(),
                                new BigDecimal(1),
                                new BigDecimal(1000000000),
                                dto.endsAt(),
                                chosenBuyerId
                        );
                    }
                    else {
                        dtoToUse = new DiscountCreateDto(
                                dto.code(),
                                dto.type(),
                                dto.value(),
                                dto.scope(),
                                dto.targetProductId(),
                                dto.targetCategoryId(),
                                dto.targetSellerId(),
                                dto.minQuantity(),
                                dto.maxQuantity(),
                                dto.startsAt(),
                                dto.endsAt(),
                                dto.minPrice(),
                                dto.maxPrice(),
                                dto.expiresAt(),
                                chosenBuyerId
                        );
                    }
                }

                if (dtoToUse.code() == null || dtoToUse.code().isBlank()) {
                    throw new BadRequestException("Los cupones (tipo FIXED) deben tener un código (code) definido.");
                }
            }

            Discount entity = discountMapper.toEntity(dtoToUse);
            Discount saved = discountRepository.save(entity);
            return discountMapper.toResponse(saved);
        }


    @Override
    @Transactional(rollbackFor = Throwable.class)
    public DiscountResponseDto updateDiscount(Long id, DiscountUpdateDto dto, Long requestingUserId)
            throws ResourceNotFoundException, UnauthorizedException, DuplicateResourceException {

        Discount existing = discountRepository.findById(id)
                .orElseThrow(() -> new DiscountNotFoundException("Descuento no encontrado (id=" + id + ")."));

        if (requestingUserId == null) throw new BadRequestException("Id de usuario no proporcionado.");

        User requester = userRepository.findById(requestingUserId)
                .orElseThrow(() -> new UserNotFoundException("Usuario solicitante no encontrado."));
        boolean isAdmin = requester.getRole() == Role.ADMIN;
        boolean ownsSeller = existing.getTargetSeller() != null && Objects.equals(existing.getTargetSeller().getId(), requestingUserId);
        boolean ownsProduct = existing.getTargetProduct() != null && existing.getTargetProduct().getSeller() != null &&
                Objects.equals(existing.getTargetProduct().getSeller().getId(), requestingUserId);
        if (!isAdmin && !ownsSeller && !ownsProduct) {
            throw new UnauthorizedException("No tienes permiso para modificar este descuento.");
        }
        

        if (dto.code() != null && !dto.code().equalsIgnoreCase(existing.getCode())) {
            if (discountRepository.existsByCode(dto.code())) {
                throw new DuplicateCouponException("Ya existe un cupón con el código: " + dto.code());
            }
        }

    
        if (dto.targetProductId() != null) {
            Product product = productRepository.findById(dto.targetProductId())
                    .orElseThrow(() -> new ProductNotFoundException("Producto objetivo no encontrado (id=" + dto.targetProductId() + ")."));
            if (!product.getSeller().getId().equals(requestingUserId)) {
                throw new UnauthorizedException("El usuario solicitante no puede crear descuentos dirigidos a productos que no le pertenecen.");
            }
        }
        if (dto.targetCategoryId() != null) {
            categoryRepository.findById(dto.targetCategoryId())
                    .orElseThrow(() -> new CategoryNotFoundException("Categoría objetivo no encontrada (id=" + dto.targetCategoryId() + ")."));
            Optional<User> user = userRepository.findById(requestingUserId);
            if (user.isEmpty() || !user.get().getRole().equals(Role.ADMIN)) {
                throw new UnauthorizedException("El usuario solicitante no puede crear descuentos dirigidos a categorías.");
            }

        }
        if (dto.targetSellerId() != null) {
            userRepository.findById(dto.targetSellerId())
                    .orElseThrow(() -> new UserNotFoundException("Vendedor objetivo no encontrado (id=" + dto.targetSellerId() + ")."));
            if (!dto.targetSellerId().equals(requestingUserId)) {
                throw new UnauthorizedException("El usuario solicitante no puede crear descuentos dirigidos a otros vendedores.");
            }
        }
        if (dto.targetBuyerId() != null) {
            userRepository.findById(dto.targetBuyerId())
                    .orElseThrow(() -> new UserNotFoundException("Comprador objetivo no encontrado (id=" + dto.targetBuyerId() + ")."));
            if (dto.targetBuyerId().equals(requestingUserId)) {
                throw new UnauthorizedException("El usuario solicitante no puede crear descuentos dirigidos a si mismo.");
            }
        }


        

        DiscountUpdateDto dtoToUse = dto;
        

            if (dto.type() == DiscountType.FIXED) {
                if (dto.targetBuyerId() == null) {
                    List<User> buyers = userRepository.findAll()
                            .stream()
                            .filter(u -> !u.getId().equals(requestingUserId))
                            .collect(Collectors.toList());

                    if (buyers.isEmpty()) {
                        throw new UserNotFoundException("No se encontraron compradores válidos para asignar al cupón.");
                    }

                    User randomBuyer = buyers.get(new java.util.Random().nextInt(buyers.size()));
                    Long chosenBuyerId = randomBuyer.getId();

                    dtoToUse = new DiscountUpdateDto(
                            dto.code(),
                            dto.type(),
                            dto.value(),
                            dto.scope(),
                            dto.targetProductId(),
                            dto.targetCategoryId(),
                            dto.targetSellerId(),
                            dto.minQuantity(),
                            dto.maxQuantity(),
                            dto.startsAt(),
                            dto.endsAt(),
                            dto.minPrice(),
                            dto.maxPrice(),
                            dto.active(),
                            dto.expiresAt(),
                            chosenBuyerId
                    );
                }

                if (dtoToUse.code() == null || dtoToUse.code().isBlank()) {
                    throw new BadRequestException("Los cupones (tipo FIXED) deben tener un código (code) definido.");
                }
            }

        validateBounds(dtoToUse.type() == null ? existing.getType() : dtoToUse.type(),
            dtoToUse.value() == null ? existing.getValue() : dtoToUse.value(),
            dtoToUse.minQuantity() == null ? existing.getMinQuantity() : dtoToUse.minQuantity(),
            dtoToUse.maxQuantity() == null ? existing.getMaxQuantity() : dtoToUse.maxQuantity(),
            dtoToUse.startsAt() == null ? existing.getStartsAt() : dtoToUse.startsAt(),
            dtoToUse.endsAt() == null ? existing.getEndsAt() : dtoToUse.endsAt());
        discountMapper.updateFromDto(dtoToUse, existing);
        Discount saved = discountRepository.save(existing);

        if (dto.targetProductId() != null) {
            discountRepository.eraseSellerId(saved.getId());
        }
        if (dto.targetSellerId() != null) {
            discountRepository.eraseProductId(saved.getId());
        }

        return discountMapper.toResponse(saved);
    }







    @Override
    public Optional<DiscountResponseDto> getDiscountById(Long id) {
        return discountRepository.findById(id).map(discountMapper::toResponse);
    }

    @Override
    public Optional<DiscountResponseDto> getActiveDiscountByCode(String code) {
        if (code == null) return Optional.empty();
  
        Optional<Discount> d = discountRepository.findByCodeAndActive(code);
        return d.filter(this::isActiveNow).map(discountMapper::toResponse);
    }






    @Override
    public BigDecimal calculateDiscountAmount(Discount discount, OrderItemCreateDto item) {
        if (discount == null) return BigDecimal.ZERO.setScale(2);
        if (!discount.isActive() || !isActiveNow(discount)) return BigDecimal.ZERO.setScale(2);
        if (item == null) return BigDecimal.ZERO.setScale(2);


        Product product = productRepository.findById(item.productId()).orElse(null);
        BigDecimal unitPrice = product != null && product.getPrice() != null ? product.getPrice() : BigDecimal.ZERO;
        int qty = item.quantity() == null ? 0 : item.quantity();
        BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(qty));


        if ((discount.getMaxQuantity() != null && qty > discount.getMaxQuantity()) || (discount.getMinQuantity() != null && qty < discount.getMinQuantity())) {
            return BigDecimal.ZERO.setScale(2);
        }


        boolean applies = false;
        DiscountScope scope = discount.getScope();
        if (scope == null) {
            throw new BadRequestException("El ámbito (scope) del descuento no está definido, por lo que no se puede calcular el descuento.");
        }

        switch (scope) {
            case PRODUCT -> {
                applies = discount.getTargetProduct() != null && product != null
                        && Objects.equals(discount.getTargetProduct().getId(), product.getId());
            }
            case CATEGORY -> {
                if (discount.getTargetCategory() != null && product != null && product.getCategories() != null) {
                    applies = product.getCategories().stream()
                            .anyMatch(c -> Objects.equals(c.getId(), discount.getTargetCategory().getId()));
                }
            }
            case SELLER -> {
                applies = discount.getTargetSeller() != null && product != null && product.getSeller() != null
                        && Objects.equals(product.getSeller().getId(), discount.getTargetSeller().getId());
            }
            default -> applies = false;
        }

        if (!applies) return BigDecimal.ZERO.setScale(2);

        BigDecimal amount = BigDecimal.ZERO;
        DiscountType type = discount.getType();

        if (type == DiscountType.PERCENT) {
            BigDecimal pct = discount.getValue() == null ? BigDecimal.ZERO : discount.getValue();
            amount = lineTotal.multiply(pct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        } else if (type == DiscountType.FIXED) {
  
            BigDecimal fixed = discount.getValue() == null ? BigDecimal.ZERO : discount.getValue();
            amount = fixed;
        } else {
            amount = BigDecimal.ZERO;
        }

  
        if (amount.compareTo(lineTotal) > 0) amount = lineTotal;

        return amount.setScale(2, RoundingMode.HALF_UP);
    }













    @Override
    public Page<DiscountResponseDto> getActiveDiscounts(Pageable pageable, Optional<Boolean> onlyActive) {
        if (onlyActive.isPresent() && onlyActive.get()) {
            Page<Discount> page = discountRepository.findCurrentlyActive(pageable);
            List<DiscountResponseDto> dtos = page.getContent().stream().map(discountMapper::toResponse).collect(Collectors.toList());
            return new PageImpl<>(dtos, pageable, page.getTotalElements());
        } else {
            Page<Discount> page = discountRepository.findAll(pageable);
            List<DiscountResponseDto> dtos = page.getContent().stream().map(discountMapper::toResponse).collect(Collectors.toList());
            return new PageImpl<>(dtos, pageable, page.getTotalElements());
        }
    }

    @Override
    public Page<DiscountResponseDto> getActiveDiscountsForProduct(Long productId, Integer productQuantity, Pageable pageable) {
        if (productId == null) throw new BadRequestException("Producto no especificado.");
        Page<Discount> page = discountRepository.findActiveProductDiscounts(productId, pageable);
        List<Discount> filtered = page.getContent().stream()
                .filter(d -> d.getMinQuantity() == null || productQuantity == null || productQuantity >= d.getMinQuantity())
                .collect(Collectors.toList());
        List<DiscountResponseDto> dtos = filtered.stream().map(discountMapper::toResponse).collect(Collectors.toList());
        return new PageImpl<>(dtos, pageable, page.getTotalElements());
    }

    @Override
    public Page<DiscountResponseDto> getAllDiscounts(Pageable pageable) {
        Page<Discount> page = discountRepository.findAll(pageable);
        List<DiscountResponseDto> dtos = page.getContent().stream().map(discountMapper::toResponse).collect(Collectors.toList());
        return new PageImpl<>(dtos, pageable, page.getTotalElements());
    }

    @Override
    public Optional<Discount> validateCouponCodeForOrderItem(String code, Long buyerId, OrderItemCreateDto item, BigDecimal subtotal)
            throws ResourceNotFoundException, BadRequestException {

        if (code == null || code.isBlank()) throw new BadRequestException("Código de descuento no proporcionado.");
        if (item == null) throw new BadRequestException("OrderItem no proporcionado.");

        Discount discount = discountRepository.findByCodeAndActive(code)
                .orElseThrow(() -> new DiscountNotFoundException("Cupón no encontrado o no activo."));

        if (!isActiveNow(discount)) throw new CouponAlreadyUsedException("El cupón no está vigente.");

        if (discount.getType() != DiscountType.FIXED) {
            throw new BadRequestException("El código proporcionado no corresponde a un cupón válido (debe ser FIXED).");
        }


        if (discount.getTargetBuyer() != null && discount.getTargetBuyer().getId() != null) {
            if (buyerId == null || !Objects.equals(discount.getTargetBuyer().getId(), buyerId)) {
                throw new CouponNotApplicableException("El cupón no es aplicable a este comprador.");
            }
        }
        else if (discount.getTargetBuyer() == null || discount.getTargetBuyer().getId() == null) {
            throw new CouponTargetBuyerMissingException("El cupón no está dirigido a ningún comprador específico, por lo tanto no es aplicable.");
     
        }


        Product product = productRepository.findById(item.productId()).orElse(null);
        if (product == null) {
            throw new OrderNotFoundException("Producto del ítem no encontrado.");
        }

        boolean applicable = false;

        if (discount.getTargetProduct() != null && discount.getTargetProduct().getId() != null) {
            applicable = Objects.equals(discount.getTargetProduct().getId(), product.getId());
        }
        if (!applicable && discount.getTargetCategory() != null && discount.getTargetCategory().getId() != null) {
            if (product.getCategories() != null) {
                applicable = product.getCategories().stream()
                        .anyMatch(c -> Objects.equals(c.getId(), discount.getTargetCategory().getId()));
            }
        }
        if (!applicable && discount.getTargetSeller() != null && discount.getTargetSeller().getId() != null) {
            if (product.getSeller() != null) {
                applicable = Objects.equals(product.getSeller().getId(), discount.getTargetSeller().getId());
            }
        }

        if (!applicable) {
            throw new CouponNotApplicableException("El cupón no es aplicable a este ítem de orden.");
        }

  
        int qty = item.quantity() == null ? 0 : item.quantity();
        BigDecimal unitPrice = product.getPrice() != null ? product.getPrice() : BigDecimal.ZERO;
        BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(qty));

        if ((discount.getMaxQuantity() != null && qty > discount.getMaxQuantity()) || (discount.getMinQuantity() != null && qty < discount.getMinQuantity())) {
            throw new BadRequestException("El cupón requiere una cantidad mínima de " + discount.getMinQuantity() + " unidades.");
        }
        if (discount.getMinPrice() != null && lineTotal.compareTo(discount.getMinPrice()) < 0) {
            throw new BadRequestException("El cupón requiere un subtotal mínimo para aplicarse.");
        }
        if (discount.getMaxPrice() != null && lineTotal.compareTo(discount.getMaxPrice()) > 0) {
            throw new CouponNotApplicableException("El cupón no es aplicable para montos mayores a " + discount.getMaxPrice());
        }

        BigDecimal amount = calculateDiscountAmount(discount, item);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new CouponNotApplicableException("El cupón no genera un descuento aplicable a este ítem.");
        }

        return Optional.of(discount);
    }


    @Override
    public Optional<Discount> getHighestValueDiscountForOrderItem(OrderItemCreateDto item) {

        if (item == null || item.productId() == null) return Optional.empty();

            Product product = productRepository.findById(item.productId()).orElse(null);
            if (product == null) return Optional.empty();

   
            List<Discount> candidates = new ArrayList<>(discountRepository.getHighestValueDiscountsForProduct(product.getId()));
            if (product.getCategories() != null) {
                for (var category : product.getCategories()) candidates.addAll(discountRepository.getHighestValueDiscountsForCategory(category.getId(), product.getPrice()));
            }
            if (product.getSeller() != null) candidates.addAll(discountRepository.getHighestValueDiscountsForSeller(product.getSeller().getId(), product.getPrice()));
            return candidates.stream().filter(d -> calculateDiscountAmount(d, item).signum() > 0)
                .max(Comparator.comparing(d -> calculateDiscountAmount(d, item)));
    }


    @Override
    public Optional<DiscountResponseDto> getHighestValueDiscountForProduct(Long productId) {

        if (productId == null) return Optional.empty();
        return getHighestValueDiscountForOrderItem(new OrderItemCreateDto(productId, null, 1)).map(discountMapper::toResponse);
    }


    @Override
    public Page<DiscountResponseDto> getAllActiveCouponsByTargetBuyerId(Long targetBuyerId, Pageable pageable) {
        if (targetBuyerId == null) throw new BadRequestException("Id de comprador no proporcionado.");
        Page<Discount> page = discountRepository.getAllActiveCouponsByTargetBuyerId(targetBuyerId, pageable);
        List<DiscountResponseDto> dtos = page.getContent().stream().map(discountMapper::toResponse).collect(Collectors.toList());
        return new PageImpl<>(dtos, pageable, page.getTotalElements());
    }


    @Override
    @Transactional(rollbackFor = Throwable.class)
    public void markCouponAsUsed(Long discountId, Long targetBuyerId) throws ResourceNotFoundException {
        if (discountId == null) throw new ResourceNotFoundException("Id de cupón no proporcionado.");
        if (targetBuyerId == null) throw new ResourceNotFoundException("Id de comprador no proporcionado.");

        Discount discount = discountRepository.findById(discountId)
            .orElseThrow(() -> new DiscountNotFoundException("Cupón no encontrado (id=" + discountId + ")."));

        if (!discount.isActive()) {
            throw new CouponAlreadyUsedException("El cupón no está activo, por lo tanto no se puede marcar como utilizado.");
        }


        if (discount.getTargetBuyer() == null || !Objects.equals(discount.getTargetBuyer().getId(), targetBuyerId)) {
            throw new UnauthorizedException("El cupón no es dirigido a este comprador, por lo tanto no se puede marcar como utilizado.");
        }

        int updated = discountRepository.markCouponAsUsed(discountId, targetBuyerId);
        if (updated == 0) throw new CouponAlreadyUsedException("El cupón ya fue utilizado.");
    }




    private void validateBounds(DiscountType type, BigDecimal value, Integer minQty, Integer maxQty, Instant starts, Instant ends) {
        if (value == null || value.signum() < 0 || (type == DiscountType.PERCENT && value.compareTo(BigDecimal.valueOf(100)) > 0))
            throw new BadRequestException("Valor de descuento inválido.");
        if ((minQty != null && minQty < 1) || (maxQty != null && maxQty < 1) || (minQty != null && maxQty != null && minQty > maxQty))
            throw new BadRequestException("Rango de cantidades inválido.");
        if (starts != null && ends != null && starts.isAfter(ends)) throw new BadRequestException("Rango de fechas inválido.");
    }

    private boolean isActiveNow(Discount d) {
        if (d == null) return false;
        if (!d.isActive()) return false;
        Instant now = Instant.now();
        if (d.getStartsAt() != null && now.isBefore(d.getStartsAt())) return false;
        if (d.getEndsAt() != null && now.isAfter(d.getEndsAt())) return false;
        if (d.getExpiresAt() != null && now.isAfter(d.getExpiresAt())) return false;
        return true;
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public Optional<DiscountResponseDto> generateNewRandomCoupon(Long targetBuyerId) {
        if (targetBuyerId == null) return Optional.empty();
        String code = generateCouponCode();
        Optional<Discount> d = discountRepository.findByCode(code);
        
        while (d.isPresent()) {
            code = generateCouponCode();
            d = discountRepository.findByCode(code);
        }
        DiscountType type = DiscountType.FIXED;

        
        BigDecimal value = new BigDecimal(Math.random() * 100).setScale(2, RoundingMode.HALF_UP);

        DiscountScope scope = DiscountScope.PRODUCT;
        if (Math.random() < 0.5) scope = DiscountScope.CATEGORY;
        if (Math.random() < 0.5) scope = DiscountScope.SELLER;

        Long targetProductId = null;
        Long targetCategoryId = null;
        Long targetSellerId = null;

        if(scope == DiscountScope.SELLER) {
            targetSellerId = getRandomSellerId();
        }
        else if(scope == DiscountScope.CATEGORY) {
            targetCategoryId = getRandomCategoryId();
        }
        else {
            targetProductId = getRandomProductId();
        }

        BigDecimal productPrice = BigDecimal.ZERO;
        if (scope == DiscountScope.PRODUCT) {
            Product product = productRepository.findById(targetProductId).orElse(null);
            if (product != null && product.getPrice() != null) {
                productPrice = product.getPrice();
            }
        }

        Integer minQuantity = 1;
        Integer maxQuantity = minQuantity + (int) Math.round(Math.random() * 100);


        long daysToAdd = 5 + (long) (Math.random() * 26);
        long secondsToAdd = daysToAdd * 24 * 60 * 60;

        Instant startsAt = Instant.now().minusSeconds((long) Math.round(Math.random() * 100));
        Instant endsAt = startsAt.plusSeconds(secondsToAdd);

        
        BigDecimal minPrice = new BigDecimal(Math.random() * 100).setScale(2, RoundingMode.HALF_UP);
        BigDecimal maxPrice = minPrice.add(new BigDecimal(Math.random() * 100).setScale(2, RoundingMode.HALF_UP));

        if (scope == DiscountScope.PRODUCT) {
            minPrice = productPrice;
            maxPrice = productPrice.multiply(new BigDecimal(maxQuantity)).setScale(2, RoundingMode.HALF_UP);
            maxPrice = maxPrice.add(new BigDecimal(Math.random() * 100).setScale(2, RoundingMode.HALF_UP));
        }


        Discount discount = new Discount();
        discount.setCode(code);
        discount.setType(type);
        discount.setValue(value);
        discount.setScope(scope);
        discount.setTargetBuyer(userRepository.findById(targetBuyerId).orElse(null));
        discount.setTargetSeller(scope == DiscountScope.SELLER ? userRepository.findById(targetSellerId).orElse(null) : null);
        discount.setTargetCategory(scope == DiscountScope.CATEGORY ? categoryRepository.findById(targetCategoryId).orElse(null) : null);
        discount.setTargetProduct(scope == DiscountScope.PRODUCT ? productRepository.findById(targetProductId).orElse(null) : null);
        discount.setMinQuantity(minQuantity);
        discount.setMaxQuantity(maxQuantity);
        discount.setStartsAt(startsAt);
        discount.setEndsAt(endsAt);
        discount.setExpiresAt(endsAt);
        discount.setMinPrice(minPrice);
        discount.setMaxPrice(maxPrice);
        discount.setActive(true);
        discount.setExpiresAt(endsAt);

        discountRepository.save(discount);
        return Optional.of(discountMapper.toResponse(discount));
    }

    private Long getRandomProductId() {
        List<Product> products = productRepository.findAll();
        if (!products.isEmpty()) {
            return products.get((int) Math.round(Math.random() * (products.size() - 1))).getId();
        }
        return null; 
    }

    private Long getRandomCategoryId() {
        List<Category> categories = categoryRepository.findAll();
        if (!categories.isEmpty()) {
            return categories.get((int) Math.round(Math.random() * (categories.size() - 1))).getId();
        }
        return null;
    }


    private Long getRandomSellerId() {
        List<User> users = userRepository.findAll();
        if (!users.isEmpty()) {
            List<User> sellers = users.stream()
                    .filter(user -> user.getRole().equals(Role.SELLER))
                    .collect(Collectors.toList());
            
            if (!sellers.isEmpty()) {
                return sellers.get((int) Math.round(Math.random() * (sellers.size() - 1))).getId();
            }
            else{
                throw new UserNotFoundException("No se encontraron vendedores.");
                
            }
        }
        return null;
        
    }
private String generateCouponCode() {
    String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    Random random = new Random();

    StringBuilder couponCode = new StringBuilder();
    for (int i = 0; i < 4; i++) {
        if (i > 0) couponCode.append("-"); 
        for (int j = 0; j < 4; j++) {
            int randomIndex = random.nextInt(characters.length());
            couponCode.append(characters.charAt(randomIndex));
        }
    }
    return couponCode.toString();
}

















@Override
public CouponValidationResponseDto validateCouponForOrderItemPreview(
        String code, Long buyerId, OrderItemCreateDto item) {


    if (code == null || code.isBlank()) {
        return new CouponValidationResponseDto(false, "Código de descuento no proporcionado.", BigDecimal.ZERO, null, null, null);
    }
    if (item == null) {
        return new CouponValidationResponseDto(false, "OrderItem no proporcionado.", BigDecimal.ZERO, null, null, null);
    }

    try {
        Optional<Discount> optDiscount = discountRepository.findByCodeAndActive(code);
        if (optDiscount.isEmpty()) {
            return new CouponValidationResponseDto(false, "Cupón no encontrado o no activo.", BigDecimal.ZERO, null, null, null);
        }

        Discount discount = optDiscount.get();


        if (!isActiveNow(discount)) {
            return new CouponValidationResponseDto(false, "El cupón no está vigente.", BigDecimal.ZERO, null, discount.getId(), null);
        }


        if (discount.getType() != DiscountType.FIXED) {
            return new CouponValidationResponseDto(false, "El código proporcionado no corresponde a un cupón válido (debe ser FIXED).",
                    BigDecimal.ZERO, null, discount.getId(), null);
        }


        if (discount.getTargetBuyer() != null && discount.getTargetBuyer().getId() != null) {
            if (buyerId == null || !Objects.equals(discount.getTargetBuyer().getId(), buyerId)) {
                return new CouponValidationResponseDto(false, "El cupón no es aplicable a este comprador.", BigDecimal.ZERO, null, discount.getId(), null);
            }
        } else {

            return new CouponValidationResponseDto(false, "El cupón no está dirigido a ningún comprador específico.", BigDecimal.ZERO, null, discount.getId(), null);
        }


        Product product = productRepository.findById(item.productId()).orElse(null);
        if (product == null) {
            return new CouponValidationResponseDto(false, "Producto del ítem no encontrado.", BigDecimal.ZERO, null, discount.getId(), null);
        }


        boolean applicable = false;

        if (discount.getTargetProduct() != null && discount.getTargetProduct().getId() != null) {
            applicable = Objects.equals(discount.getTargetProduct().getId(), product.getId());
        }

        if (!applicable && discount.getTargetCategory() != null && discount.getTargetCategory().getId() != null) {
            if (product.getCategories() != null) {
                applicable = product.getCategories().stream()
                        .anyMatch(c -> Objects.equals(c.getId(), discount.getTargetCategory().getId()));
            }
        }

        if (!applicable && discount.getTargetSeller() != null && discount.getTargetSeller().getId() != null) {
            if (product.getSeller() != null) {
                applicable = Objects.equals(product.getSeller().getId(), discount.getTargetSeller().getId());
            }
        }

        if (!applicable) {
            return new CouponValidationResponseDto(false, "El cupón no es aplicable a este ítem de orden.", BigDecimal.ZERO, null, discount.getId(), product.getId());
        }


        int qty = item.quantity() == null ? 0 : item.quantity();
        BigDecimal unitPrice = product.getPrice() != null ? product.getPrice() : BigDecimal.ZERO;
        BigDecimal lineTotal;
        
        lineTotal = unitPrice.multiply(BigDecimal.valueOf(qty));
        

        if ((discount.getMaxQuantity() != null && qty > discount.getMaxQuantity()) || (discount.getMinQuantity() != null && qty < discount.getMinQuantity())) {
            return new CouponValidationResponseDto(false, "El cupón requiere una cantidad mínima de " + discount.getMinQuantity() + " unidades.", BigDecimal.ZERO, lineTotal, discount.getId(), product.getId());
        }

        if (discount.getMinPrice() != null && lineTotal.compareTo(discount.getMinPrice()) < 0) {
            return new CouponValidationResponseDto(false, "El cupón requiere un subtotal mínimo para aplicarse.", BigDecimal.ZERO, lineTotal, discount.getId(), product.getId());
        }

        if (discount.getMaxPrice() != null && lineTotal.compareTo(discount.getMaxPrice()) > 0) {
            return new CouponValidationResponseDto(false, "El cupón no es aplicable para montos mayores a " + discount.getMaxPrice(), BigDecimal.ZERO, lineTotal, discount.getId(), product.getId());
        }


        BigDecimal amount = calculateDiscountAmount(discount, item);
        if (amount == null) amount = BigDecimal.ZERO;

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return new CouponValidationResponseDto(false, "El cupón no genera un descuento aplicable a este ítem.", BigDecimal.ZERO, lineTotal, discount.getId(), product.getId());
        }


        BigDecimal newTotal = lineTotal.subtract(amount);
        if (newTotal.compareTo(BigDecimal.ZERO) < 0) newTotal = BigDecimal.ZERO;

        return new CouponValidationResponseDto(true, "Cupón válido.", amount, newTotal, discount.getId(), product.getId());

    } catch (Exception ex) {
        return new CouponValidationResponseDto(false, "Error interno al validar el cupón.", BigDecimal.ZERO, null, null, null);
    }
}






@Override
public CouponValidationResponseDto validateCouponForOrderItemsPreview(
        String code, Long buyerId, List<OrderItemCreateDto> items) {

    if (code == null || code.isBlank()) {
        return new CouponValidationResponseDto(false, "Código de descuento no proporcionado.", BigDecimal.ZERO, null, null, null);
    }
    if (items == null || items.isEmpty()) {
        return new CouponValidationResponseDto(false, "Lista de ítems no proporcionada.", BigDecimal.ZERO, null, null, null);
    }

    try {
        Optional<Discount> optDiscount = discountRepository.findByCodeAndActive(code);
        if (optDiscount.isEmpty()) {
            return new CouponValidationResponseDto(false, "Cupón no encontrado o no activo.", BigDecimal.ZERO, null, null, null);
        }
        Discount discount = optDiscount.get();


        if (!isActiveNow(discount)) {
            return new CouponValidationResponseDto(false, "El cupón no está vigente.", BigDecimal.ZERO, null, discount.getId(), null);
        }


        if (discount.getType() != DiscountType.FIXED) {
            return new CouponValidationResponseDto(false, "El código proporcionado no corresponde a un cupón válido (debe ser FIXED).",
                    BigDecimal.ZERO, null, discount.getId(), null);
        }


        if (discount.getTargetBuyer() != null && discount.getTargetBuyer().getId() != null) {
            if (buyerId == null || !Objects.equals(discount.getTargetBuyer().getId(), buyerId)) {
                return new CouponValidationResponseDto(false, "El cupón no es aplicable a este comprador.", BigDecimal.ZERO, null, discount.getId(), null);
            }
        } else {
            return new CouponValidationResponseDto(false, "El cupón no está dirigido a ningún comprador específico.", BigDecimal.ZERO, null, discount.getId(), null);
        }


        BigDecimal bestAmount = BigDecimal.ZERO;
        BigDecimal bestNewTotal = null;
        Long bestProductId = null;
        BigDecimal bestLineTotal = BigDecimal.ZERO;

        for (OrderItemCreateDto item : items) {
            if (item == null) continue;
            Product product = productRepository.findById(item.productId()).orElse(null);
            if (product == null) {

                continue;
            }


            boolean applicable = false;
            if (discount.getTargetProduct() != null && discount.getTargetProduct().getId() != null) {
                applicable = Objects.equals(discount.getTargetProduct().getId(), product.getId());
            }
            if (!applicable && discount.getTargetCategory() != null && discount.getTargetCategory().getId() != null) {
                if (product.getCategories() != null) {
                    applicable = product.getCategories().stream()
                            .anyMatch(c -> Objects.equals(c.getId(), discount.getTargetCategory().getId()));
                }
            }
            if (!applicable && discount.getTargetSeller() != null && discount.getTargetSeller().getId() != null) {
                if (product.getSeller() != null) {
                    applicable = Objects.equals(product.getSeller().getId(), discount.getTargetSeller().getId());
                }
            }
            if (!applicable) continue;

            int qty = item.quantity() == null ? 0 : item.quantity();
            if (qty <= 0) continue;
            BigDecimal unitPrice = product.getPrice() != null ? product.getPrice() : BigDecimal.ZERO;
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(qty));


            if (discount.getMinQuantity() != null && qty < discount.getMinQuantity()) continue;
            if (discount.getMinPrice() != null && lineTotal.compareTo(discount.getMinPrice()) < 0) continue;
            if (discount.getMaxPrice() != null && lineTotal.compareTo(discount.getMaxPrice()) > 0) continue;


            BigDecimal amount = calculateDiscountAmount(discount, item);
            if (amount == null) amount = BigDecimal.ZERO;
            if (amount.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal newTotal = lineTotal.subtract(amount);
            if (newTotal.compareTo(BigDecimal.ZERO) < 0) newTotal = BigDecimal.ZERO;


            boolean isBetter = false;
            int cmp = amount.compareTo(bestAmount);
            if (cmp > 0) {
                isBetter = true;
            } else if (cmp == 0 && amount.compareTo(BigDecimal.ZERO) > 0) {

                int ltCmp = lineTotal.compareTo(bestLineTotal);
                if (ltCmp > 0) {
                    isBetter = true;
                } else if (ltCmp == 0) {

                    if (bestProductId == null || (product.getId() != null && product.getId() < bestProductId)) {
                        isBetter = true;
                    }
                }
            }

            if (isBetter) {
                bestAmount = amount;
                bestNewTotal = newTotal;
                bestProductId = product.getId();
                bestLineTotal = lineTotal;
            }
        }

        if (bestAmount.compareTo(BigDecimal.ZERO) > 0) {
            return new CouponValidationResponseDto(true, "Cupón válido para uno de los ítems.", bestAmount, bestNewTotal, discount.getId(), bestProductId);
        } else {
            return new CouponValidationResponseDto(false, "Ninguno de los ítems es aplicable al cupón.", BigDecimal.ZERO, null, discount.getId(), null);
        }

    } catch (Exception ex) {

        return new CouponValidationResponseDto(false, "Error interno al validar el cupón.", BigDecimal.ZERO, null, null, null);
    }
}

    @Override
    @Transactional(readOnly = true)
    public Page<DiscountResponseDto> getDiscountsForSellerManagement(Long requestingUserId, Pageable pageable)
            throws ResourceNotFoundException, UnauthorizedException {

        if (requestingUserId == null) throw new UnauthorizedException("Id de usuario solicitante no proporcionado.");

        User seller = userRepository.findById(requestingUserId)
                .orElseThrow(() -> new UserNotFoundException("Vendedor no encontrado (id=" + requestingUserId + ")."));

        if (seller.getRole() != Role.SELLER) {
            throw new UnauthorizedException("El usuario solicitante no es un vendedor válido.");
        }

        Pageable effective = pageable == null ? PageRequest.of(0, 20) : pageable;

        Page<Discount> page = discountRepository.findDiscountsForSeller(requestingUserId, effective);
        List<DiscountResponseDto> dtos = page.getContent().stream().map(discountMapper::toResponse).collect(Collectors.toList());
        return new PageImpl<>(dtos, effective, page.getTotalElements());
    }


    @Override
    @Transactional(readOnly = true)
    public Page<DiscountResponseDto> getDiscountsForAdminManagement(Long requestingUserId, Pageable pageable)
            throws ResourceNotFoundException, UnauthorizedException {

        if (requestingUserId == null) throw new UnauthorizedException("Id de usuario solicitante no proporcionado.");


        User requester = userRepository.findById(requestingUserId)
                .orElseThrow(() -> new UserNotFoundException("Usuario solicitante no encontrado (id=" + requestingUserId + ")."));

        if (requester.getRole() != Role.ADMIN) {
            throw new UnauthorizedException("No autorizado: solo administradores pueden ver descuentos de categorías.");
        }

        Pageable effective = pageable == null ? PageRequest.of(0, 20) : pageable;

        Page<Discount> page = discountRepository.getDiscountsForCategories(effective);
        List<DiscountResponseDto> dtos = page.getContent().stream()
                .map(discountMapper::toResponse)
                .collect(Collectors.toList());

        return new PageImpl<>(dtos, effective, page.getTotalElements());
    }





}