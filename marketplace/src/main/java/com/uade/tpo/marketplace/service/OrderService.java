package com.uade.tpo.marketplace.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uade.tpo.marketplace.entity.basic.DigitalKey;
import com.uade.tpo.marketplace.entity.basic.Discount;
import com.uade.tpo.marketplace.entity.basic.Order;
import com.uade.tpo.marketplace.entity.basic.OrderItem;
import com.uade.tpo.marketplace.entity.basic.Product;
import com.uade.tpo.marketplace.entity.basic.User;
import com.uade.tpo.marketplace.entity.dto.create.OrderCreateDto;
import com.uade.tpo.marketplace.entity.dto.create.OrderItemCreateDto;
import com.uade.tpo.marketplace.entity.dto.response.AdminStatsExtrasResponseDto;
import com.uade.tpo.marketplace.entity.dto.response.OrderItemResponseDto;
import com.uade.tpo.marketplace.entity.dto.response.OrderKeyResponseDto;
import com.uade.tpo.marketplace.entity.dto.response.OrderResponseDto;
import com.uade.tpo.marketplace.entity.enums.KeyStatus;
import com.uade.tpo.marketplace.entity.enums.OrderStatus;
import com.uade.tpo.marketplace.exceptions.BadRequestException;
import com.uade.tpo.marketplace.exceptions.CouponAlreadyUsedException;
import com.uade.tpo.marketplace.exceptions.CouponNotApplicableException;
import com.uade.tpo.marketplace.exceptions.DigitalKeyAssignmentException;
import com.uade.tpo.marketplace.exceptions.DuplicateOrderItemException;
import com.uade.tpo.marketplace.exceptions.InsufficientStockException;
import com.uade.tpo.marketplace.exceptions.OrderNotFoundException;
import com.uade.tpo.marketplace.exceptions.OrderProcessingException;
import com.uade.tpo.marketplace.exceptions.ProductNotFoundException;
import com.uade.tpo.marketplace.exceptions.ProductSelfPurchaseException;
import com.uade.tpo.marketplace.exceptions.ResourceNotFoundException;
import com.uade.tpo.marketplace.exceptions.RestExceptionHandler;
import com.uade.tpo.marketplace.exceptions.UnauthorizedException;
import com.uade.tpo.marketplace.exceptions.UserNotFoundException;
import com.uade.tpo.marketplace.extra.mappers.OrderItemMapper;
import com.uade.tpo.marketplace.extra.mappers.OrderMapper;
import com.uade.tpo.marketplace.repository.interfaces.IDigitalKeyRepository;
import com.uade.tpo.marketplace.repository.interfaces.IOrderItemRepository;
import com.uade.tpo.marketplace.repository.interfaces.IOrderRepository;
import com.uade.tpo.marketplace.repository.interfaces.IProductRepository;
import com.uade.tpo.marketplace.repository.interfaces.IReviewRepository;
import com.uade.tpo.marketplace.repository.interfaces.IUserRepository;
import com.uade.tpo.marketplace.service.interfaces.IDiscountService;
import com.uade.tpo.marketplace.service.interfaces.IOrderService;
import com.uade.tpo.marketplace.service.interfaces.IUserService;

import jakarta.persistence.Tuple;

@Service
public class OrderService implements IOrderService {

    @Autowired
    private IOrderRepository orderRepository;
    @Autowired
    private IProductRepository productRepository;
    @Autowired
    private IUserRepository userRepository;
    @Autowired
    private IDigitalKeyRepository digitalKeyRepository;
    @Autowired
    private IDiscountService discountService;
    @Autowired
    private IUserService userService;
    @Autowired
    private IOrderItemRepository orderItemRepository;
    @Autowired
    private IReviewRepository reviewRepository;

    @Autowired
    private OrderItemMapper orderItemMapper;
    @Autowired
    private OrderMapper orderMapper;


    private final Logger log = LoggerFactory.getLogger(RestExceptionHandler.class);

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public OrderResponseDto createOrder(OrderCreateDto dto, Long buyerId)
            throws ResourceNotFoundException, InsufficientStockException, BadRequestException {

        if (dto == null || dto.items() == null || dto.items().isEmpty()) {
            throw new BadRequestException("El pedido debe contener al menos un ítem.");
        }

        if (dto.items().stream().anyMatch(item -> item == null || item.productId() == null || item.quantity() == null || item.quantity() <= 0)) {
            throw new BadRequestException("Los ítems deben indicar un producto y una cantidad positiva.");
        }
        Map<Long, Long> countsByProduct = dto.items().stream()
            .filter(Objects::nonNull)
            .map(item -> item.productId())
            .filter(Objects::nonNull)
            .collect(Collectors.groupingBy(pid -> pid, Collectors.counting()));

        List<Long> duplicatedProductIds = countsByProduct.entrySet().stream()
            .filter(e -> e.getValue() > 1)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());

        if (!duplicatedProductIds.isEmpty()) {
            throw new DuplicateOrderItemException("No se permiten múltiples ítems del mismo producto en una única orden. Productos duplicados: " + duplicatedProductIds);
        }

        if (buyerId == null) {
            throw new BadRequestException("El pedido debe tener un comprador.");
        }

    
        User buyer = userRepository.findByIdForUpdate(buyerId)
                .orElseThrow(() -> new UserNotFoundException("Comprador no encontrado (id=" + buyerId + ")."));


        Set<Long> productIds = dto.items().stream()
                .map(OrderItemCreateDto::productId)
                .collect(Collectors.toSet());

        Map<Long, Product> productMap = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

    
        Map<Long, Integer> requiredPerProduct = new HashMap<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        for (OrderItemCreateDto itemDto : dto.items()) {
            if (itemDto.quantity() == null || itemDto.quantity() <= 0) {
                throw new BadRequestException("Cantidad inválida para productId=" + itemDto.productId());
            }

            Product p = productMap.get(itemDto.productId());
            if (p == null || !p.isActive()) {
                throw new ProductNotFoundException("Producto no encontrado o inactivo (id=" + itemDto.productId() + ").");
            }

            if (buyerId != null && p.getSeller() != null && p.getSeller().getId() != null
                    && buyerId.equals(p.getSeller().getId())) {
                throw new ProductSelfPurchaseException("No estás autorizado para comprar tu propio producto.");
            }

            int minQty = p.getMinPurchaseQuantity() == null ? 1 : p.getMinPurchaseQuantity();
            int maxQty = p.getMaxPurchaseQuantity() == null ? Integer.MAX_VALUE : p.getMaxPurchaseQuantity();
            if (itemDto.quantity() < minQty || itemDto.quantity() > maxQty) {
                throw new BadRequestException(String.format("La cantidad para el producto %d debe estar entre %d y %d.",
                        p.getId(), minQty, maxQty));
            }

            int available = digitalKeyRepository.countAvailableByProductId(p.getId());
            if (available < itemDto.quantity()) {
                throw new InsufficientStockException("Stock insuficiente para el producto: " + p.getTitle());
            }

            requiredPerProduct.merge(p.getId(), itemDto.quantity(), Integer::sum);

            BigDecimal unitPrice = p.getPrice() == null ? BigDecimal.ZERO : p.getPrice();
            subtotal = subtotal.add(unitPrice.multiply(BigDecimal.valueOf(itemDto.quantity())));
        }


    Order order = new Order();
    order.setBuyer(buyer);
    order.setStatus(OrderStatus.PENDING);
    order.setNotes(dto.notes());
    order.setSubtotal(subtotal);


    List<OrderItem> orderItemsList = new ArrayList<>();
    for (OrderItemCreateDto itemDto : dto.items()) {
        Product prod = productMap.get(itemDto.productId());
        BigDecimal unitPrice = prod.getPrice() == null ? BigDecimal.ZERO : prod.getPrice();

        OrderItem oi = orderItemMapper.toEntityFromCreate(prod.getId(), itemDto.quantity(), unitPrice);
        oi.setProduct(prod);
        oi.setOrder(order);

        if (oi.getLineTotal() == null) {
            oi.setLineTotal(unitPrice.multiply(BigDecimal.valueOf(oi.getQuantity())));
        }

        orderItemsList.add(oi);
    }


    Map<Long, OrderItem> orderItemMap = orderItemsList.stream()
            .filter(oi -> oi.getProduct() != null && oi.getProduct().getId() != null)
            .collect(Collectors.toMap(oi -> oi.getProduct().getId(), Function.identity()));

    BigDecimal totalDiscounts = BigDecimal.ZERO;
    Set<String> couponsUsedInThisOrder = new HashSet<>();

    for (OrderItemCreateDto itemDto : dto.items()) {
        OrderItem oi = orderItemMap.get(itemDto.productId());
        if (oi == null) {
            throw new BadRequestException("Item inconsistente: no se encontró el orderItem para productId=" + itemDto.productId());
        }
        Optional<Discount> bestAutoOpt = discountService.getHighestValueDiscountForOrderItem(itemDto);
        if (bestAutoOpt.isPresent()) {
            Discount best = bestAutoOpt.get();
            BigDecimal amount = discountService.calculateDiscountAmount(best, itemDto);
            oi.setDiscount(best);
            oi.setDiscountAmount(amount == null ? BigDecimal.ZERO : amount);
            totalDiscounts = totalDiscounts.add(oi.getDiscountAmount());
            BigDecimal base = oi.getUnitPrice() == null ? BigDecimal.ZERO : oi.getUnitPrice().multiply(BigDecimal.valueOf(oi.getQuantity()));
            BigDecimal discountAmt = oi.getDiscountAmount() == null ? BigDecimal.ZERO : oi.getDiscountAmount();
            oi.setLineTotal(base.subtract(discountAmt).max(BigDecimal.ZERO));
            continue;
        }

        String itemCoupon = itemDto.couponCode();
        if (itemCoupon != null && !itemCoupon.isBlank()) {
            itemCoupon = itemCoupon.trim();
            if (couponsUsedInThisOrder.contains(itemCoupon)) {
                throw new CouponAlreadyUsedException("El cupón " + itemCoupon + " ya fue usado en otro ítem de esta orden.");
            }

            try {
                Optional<Discount> couponOpt = discountService.validateCouponCodeForOrderItem(itemCoupon, buyerId, itemDto, subtotal);
                if (couponOpt.isPresent()) {
                    Discount coupon = couponOpt.get();
                    BigDecimal amount = discountService.calculateDiscountAmount(coupon, itemDto);
                    if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
                        oi.setDiscount(coupon);
                        oi.setDiscountAmount(amount);
                        totalDiscounts = totalDiscounts.add(amount);

                        discountService.markCouponAsUsed(coupon.getId(), buyerId);
                        couponsUsedInThisOrder.add(itemCoupon);
                    } else {
                        oi.setDiscountAmount(BigDecimal.ZERO);
                    }
                } else {
                    oi.setDiscountAmount(BigDecimal.ZERO);
                }
            } catch (ResourceNotFoundException | BadRequestException ex) {
                throw new CouponNotApplicableException("Cupón inválido para productId=" + itemDto.productId() + ": " + ex.getMessage());
            }
        } else {
            oi.setDiscountAmount(BigDecimal.ZERO);
        }

        BigDecimal base = oi.getUnitPrice() == null ? BigDecimal.ZERO : oi.getUnitPrice().multiply(BigDecimal.valueOf(oi.getQuantity()));
        BigDecimal discountAmt = oi.getDiscountAmount() == null ? BigDecimal.ZERO : oi.getDiscountAmount();
        oi.setLineTotal(base.subtract(discountAmt).max(BigDecimal.ZERO));
    }

        order.setItems(new HashSet<>(orderItemsList));

    
        order.setDiscountAmount(totalDiscounts.setScale(2, RoundingMode.HALF_UP));
        order.setTotalAmount(order.getSubtotal().subtract(order.getDiscountAmount()).max(BigDecimal.ZERO));
    Order savedOrder = orderRepository.saveAndFlush(order);

    Map<Long, List<OrderItem>> persistedItemsByProduct = orderItemsList.stream()
            .filter(oi -> oi != null && oi.getProduct() != null && oi.getProduct().getId() != null)
            .peek(oi -> {
                log.info("orderItem manejado después del save: oi.id={} productId={} qty={}", oi.getId(), oi.getProduct().getId(), oi.getQuantity());
            })
            .collect(Collectors.groupingBy(oi -> oi.getProduct().getId()));

    List<Long> sortedProductIds = requiredPerProduct.keySet().stream().sorted().collect(Collectors.toList());
    Instant now = Instant.now();

    Map<Long, List<DigitalKey>> reservedCandidates = new HashMap<>();
    for (Long pid : sortedProductIds) {
        int qtyNeeded = requiredPerProduct.get(pid);
        Page<DigitalKey> availablePage = digitalKeyRepository.findAvailableForUpdateByProductId(
                productMap.get(pid), KeyStatus.AVAILABLE, PageRequest.of(0, qtyNeeded)
        );
        List<DigitalKey> available = availablePage.getContent();
        if (available.size() < qtyNeeded) {
            throw new InsufficientStockException("Stock insuficiente durante la confirmación para el producto id=" + pid);
        }
        reservedCandidates.put(pid, available);
    }

    for (Long pid : sortedProductIds) {
        int qtyNeeded = requiredPerProduct.get(pid);

        List<OrderItem> itemsForProduct = persistedItemsByProduct.getOrDefault(pid, Collections.emptyList());

        int totalRequested = itemsForProduct.stream().mapToInt(OrderItem::getQuantity).sum();
        if (totalRequested != qtyNeeded) {
            throw new BadRequestException("Inconsistencia en cantidades solicitadas para productId=" + pid + ": " + totalRequested + " != " + qtyNeeded);
        }

        Deque<Long> keysQueue = reservedCandidates.get(pid).stream()
                .map(DigitalKey::getId)
                .collect(Collectors.toCollection(ArrayDeque::new));

        for (OrderItem oi : itemsForProduct) {
            int qtyForOi = oi.getQuantity();
            List<Long> keysForOi = new ArrayList<>(qtyForOi);
            for (int i = 0; i < qtyForOi; i++) {
                if (keysQueue.isEmpty()) {
                    throw new DigitalKeyAssignmentException("No se pudieron asignar todas las claves necesarias para productId=" + pid);
                }
                keysForOi.add(keysQueue.poll());
            }

            int assigned = digitalKeyRepository.markAsSoldAndAssignToOrderItemBulk(
                    keysForOi,
                    KeyStatus.SOLD.name(),
                    KeyStatus.AVAILABLE.name(),
                    now,
                    oi.getId()
            );

            if (assigned < keysForOi.size()) {
                throw new DigitalKeyAssignmentException("No se pudieron asignar todas las claves necesarias para productId=" + pid);
            }

            List<DigitalKey> soldKeys = digitalKeyRepository.findAllById(keysForOi);
            soldKeys.forEach(k -> {
                k.setStatus(KeyStatus.SOLD);
                k.setSoldAt(now);
                k.setOrderItem(oi);
            });
            oi.setDigitalKeys(new HashSet<>(soldKeys));
        }
    }
        savedOrder.setStatus(OrderStatus.COMPLETED);
        savedOrder.setCompletedAt(now);
        int updateOrderStatus = orderRepository.updateOrderStatus(savedOrder.getId(), OrderStatus.COMPLETED, now);
        if (updateOrderStatus == 0) {
            throw new OrderProcessingException("No se pudo marcar la orden como completada.");
        }

        Optional<Order> refreshed = orderRepository.findOrderWithItemsAndKeys(savedOrder.getId());
        if (refreshed.isPresent()) savedOrder = refreshed.get();



        BigDecimal buyerBalance = buyer.getBuyerBalance();
        if (buyerBalance == null) buyerBalance = BigDecimal.ZERO;

        buyerBalance = buyerBalance.add(savedOrder.getTotalAmount());

        while (buyerBalance.compareTo(BigDecimal.valueOf(100)) >= 0) {
            buyerBalance = buyerBalance.subtract(BigDecimal.valueOf(100));
            discountService.generateNewRandomCoupon(buyer.getId());
        }

        userService.updateBuyerBalance(buyer.getId(), buyerBalance);



        boolean includeKeyCodes = true;
        return orderMapper.toResponse(savedOrder, includeKeyCodes);
    }














@Override
public Page<OrderItemResponseDto> getOrderItemsByOrderId(Long orderId, Long requestingUserId, Pageable pageable)
        throws ResourceNotFoundException, UnauthorizedException {

    Order order = orderRepository.findOrderWithItemsAndProducts(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("Orden no encontrada (id=" + orderId + ")."));

    Long buyerId = order.getBuyer() != null ? order.getBuyer().getId() : null;
    boolean isBuyer = Objects.equals(requestingUserId, buyerId);
    boolean isSellerOfAnyItem = order.getItems() != null &&
        order.getItems().stream().anyMatch(oi ->
            oi.getProduct() != null && oi.getProduct().getSeller() != null &&
            Objects.equals(oi.getProduct().getSeller().getId(), requestingUserId)
        );

    if (!isBuyer && !isSellerOfAnyItem) {
        throw new UnauthorizedException("No autorizado para ver los ítems de esta orden.");
    }

    Set<OrderItem> itemsSet = Optional.ofNullable(order.getItems()).orElse(Collections.emptySet());
    if (!isBuyer) {
        itemsSet = itemsSet.stream().filter(oi -> oi.getProduct() != null && oi.getProduct().getSeller() != null &&
            Objects.equals(oi.getProduct().getSeller().getId(), requestingUserId)).collect(Collectors.toSet());
    }

    List<OrderItem> items = itemsSet.stream()
            .sorted(Comparator.comparing(
    
                    (OrderItem oi) -> oi.getId(),
                    Comparator.nullsLast(Long::compareTo)
            ).thenComparing(oi -> Optional.ofNullable(oi.getCreatedAt()).orElse(Instant.EPOCH)))
            .collect(Collectors.toList());

    int total = items.size();

    if (pageable == null || pageable.isUnpaged()) {
        List<OrderItemResponseDto> dtos = items.stream()
            .map(oi -> orderItemMapper.toResponse(oi, true))
            .collect(Collectors.toList());
        return new PageImpl<>(dtos, Pageable.unpaged(), total);
    }

    int pageSize = pageable.getPageSize();
    int pageNumber = pageable.getPageNumber();
    if (pageSize <= 0) {
        pageSize = total == 0 ? 1 : total;
    }
    int start = pageNumber * pageSize;
    if (start >= total) {
        return new PageImpl<>(Collections.emptyList(), pageable, total);
    }
    int end = Math.min(start + pageSize, total);

    List<OrderItem> pageItems = items.subList(start, end);
    List<OrderItemResponseDto> pageDtos = pageItems.stream()
            .map(oi -> orderItemMapper.toResponse(oi, true))
            .collect(Collectors.toList());

    return new PageImpl<>(pageDtos, pageable, total);
}


    @Override
    public Optional<OrderResponseDto> getOrderById(Long id, Long requestingUserId) throws ResourceNotFoundException, UnauthorizedException {
        Order order = orderRepository.findOrderWithItemsAndKeys(id)
                .orElseThrow(() -> new ResourceNotFoundException("Orden no encontrada (id=" + id + ")."));


        if (requestingUserId == null || order.getBuyer() == null || !Objects.equals(requestingUserId, order.getBuyer().getId())) {
            throw new UnauthorizedException("No autorizado para ver esta orden.");
        }
        return Optional.of(orderMapper.toResponse(order, true));
    }

    @Override
    public Page<OrderResponseDto> getOrdersByBuyer(Long buyerId, Pageable pageable) {
        if (buyerId == null) {
            throw new BadRequestException("buyerId no puede ser null");
        }

        if (!userRepository.existsById(buyerId)) {
            throw new UserNotFoundException("Comprador no encontrado (id=" + buyerId + ").");
        }

        Pageable effectivePageable = pageable;
        final int DEFAULT_PAGE = 0;
        final int DEFAULT_PAGE_SIZE = 20;

        if (effectivePageable == null) {
            effectivePageable = PageRequest.of(DEFAULT_PAGE, DEFAULT_PAGE_SIZE);
        } else {
            int pageNumber = effectivePageable.getPageNumber();
            int pageSize = effectivePageable.getPageSize();

            if (pageNumber < 0 || pageSize <= 0) {
                Sort sort = effectivePageable.getSort();
                if (pageNumber < 0) pageNumber = DEFAULT_PAGE;
                if (pageSize <= 0) pageSize = DEFAULT_PAGE_SIZE;
                effectivePageable = PageRequest.of(pageNumber, pageSize, sort);
            }
        }

        Page<Order> page = orderRepository.findByBuyerId(buyerId, effectivePageable);

        List<OrderResponseDto> dtos = Optional.ofNullable(page.getContent())
            .orElseGet(Collections::emptyList)
            .stream()
            .map(o -> orderMapper.toResponse(o, true))
            .collect(Collectors.toList());

        return new PageImpl<>(dtos, effectivePageable, page.getTotalElements());
    }

    @Override
    public Page<OrderResponseDto> getOrdersBySeller(Long sellerId, Pageable pageable) {
        if (sellerId == null) {
            throw new BadRequestException("sellerId no puede ser null");
        }

        if (!userRepository.existsById(sellerId)) {
            throw new UserNotFoundException("Vendedor no encontrado (id=" + sellerId + ").");
        }

        Pageable effectivePageable = pageable;
        final int DEFAULT_PAGE = 0;
        final int DEFAULT_PAGE_SIZE = 20;

        if (effectivePageable == null) {
            effectivePageable = PageRequest.of(DEFAULT_PAGE, DEFAULT_PAGE_SIZE);
        } else {
            int pageNumber = effectivePageable.getPageNumber();
            int pageSize = effectivePageable.getPageSize();

            if (pageNumber < 0 || pageSize <= 0) {
                Sort sort = effectivePageable.getSort();
                if (pageNumber < 0) pageNumber = DEFAULT_PAGE;
                if (pageSize <= 0) pageSize = DEFAULT_PAGE_SIZE;
                effectivePageable = PageRequest.of(pageNumber, pageSize, sort);
            }
        }

        effectivePageable = PageRequest.of(effectivePageable.getPageNumber(), effectivePageable.getPageSize(),
            Sort.by(Sort.Order.desc("id")));

        Page<Order> page = orderRepository.findBySellerId(sellerId, effectivePageable);

        List<OrderResponseDto> dtos = Optional.ofNullable(page.getContent())
            .orElseGet(Collections::emptyList)
            .stream()
            .map(order -> {
                Set<OrderItem> filteredItems = order.getItems().stream()
                    .filter(item -> item.getProduct().getSeller().getId().equals(sellerId))
                    .collect(Collectors.toSet());

                order.setItems(filteredItems);

                return orderMapper.toResponse(order, false);
            })
            .collect(Collectors.toList());

        return new PageImpl<>(dtos, effectivePageable, page.getTotalElements());
    }

    @Override
    public Page<OrderResponseDto> getAllOrders(Pageable pageable) {
        Page<Order> page = orderRepository.findAll(pageable);
        List<OrderResponseDto> dtos = page.getContent().stream()
                .map(o -> orderMapper.toResponse(o, true))
                .collect(Collectors.toList());
        return new PageImpl<>(dtos, pageable, page.getTotalElements());
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public OrderResponseDto completeOrder(Long orderId, Long performedByUserId)
            throws ResourceNotFoundException, UnauthorizedException, BadRequestException {

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Orden no encontrada (id=" + orderId + ")."));

        if (order.getStatus() == OrderStatus.COMPLETED) {
            throw new OrderProcessingException("La orden ya está completada.");
        }

        Instant now = Instant.now();
        int updated = orderRepository.updateOrderStatus(orderId, OrderStatus.COMPLETED, now);
        if (updated == 0) {
            throw new OrderProcessingException("No se pudo marcar la orden como completada.");
        }

        Order completed = orderRepository.findOrderWithItemsAndKeys(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Orden no encontrada luego de actualizar (id=" + orderId + ")."));

        boolean includeKeyCodes = true;
        return orderMapper.toResponse(completed, includeKeyCodes);
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public OrderResponseDto updateOrderStatus(Long orderId, String status, Long performedByUserId)
            throws ResourceNotFoundException, UnauthorizedException, BadRequestException {

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Orden no encontrada (id=" + orderId + ")."));

        OrderStatus newStatus;
        try {
            newStatus = OrderStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Estado inválido: " + status);
        }

        Instant completedAt = newStatus == OrderStatus.COMPLETED ? Instant.now() : null;
        int updated = orderRepository.updateOrderStatus(orderId, newStatus, completedAt);
        if (updated == 0) {
            throw new OrderProcessingException("No se pudo actualizar el estado de la orden.");
        }

        Order updatedOrder = orderRepository.findOrderWithItemsAndKeys(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Orden no encontrada luego de actualizar (id=" + orderId + ")."));

        boolean includeKeyCodes = true;
        return orderMapper.toResponse(updatedOrder, includeKeyCodes);
    }




    @Override
    public List<OrderKeyResponseDto> getKeysByOrderId(Long orderId, Long requestingUserId)
            throws ResourceNotFoundException, UnauthorizedException {

        if (orderId == null) throw new BadRequestException("orderId es nulo");
        if (requestingUserId == null) throw new UnauthorizedException("requestingUserId es nulo");

        Order order = orderRepository.findOrderWithItemsAndProductsAndKeys(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Orden no encontrada (id=" + orderId + ")."));

        Long buyerId = order.getBuyer() != null ? order.getBuyer().getId() : null;
        boolean isBuyer = Objects.equals(requestingUserId, buyerId);


        boolean isSellerOfAnyItem = order.getItems() != null && order.getItems().stream().anyMatch(oi ->
                oi.getProduct() != null && oi.getProduct().getSeller() != null &&
                        Objects.equals(oi.getProduct().getSeller().getId(), requestingUserId)
        );

        if (!isBuyer && !isSellerOfAnyItem) {
            throw new UnauthorizedException("No está autorizado para ver las claves de esta orden.");
        }

        List<OrderKeyResponseDto> out = new ArrayList<>();


        Set<OrderItem> items = Optional.ofNullable(order.getItems()).orElse(Collections.emptySet());
        for (OrderItem oi : items) {
            Set<DigitalKey> keys = Optional.ofNullable(oi.getDigitalKeys()).orElse(Collections.emptySet());
            for (DigitalKey dk : keys) {
                boolean canSeeKeyCode = false;

                if (isBuyer) {

                    canSeeKeyCode = true;
                } else {

                    if (oi.getProduct() != null && oi.getProduct().getSeller() != null &&
                            Objects.equals(oi.getProduct().getSeller().getId(), requestingUserId)) {
                        canSeeKeyCode = true;
                    }
                }

                OrderKeyResponseDto dto = mapKeyToDto(dk, canSeeKeyCode, order.getId());

                out.add(dto);
            }
        }

        return out;
    }

    @Override
    public List<OrderKeyResponseDto> getKeysByOrderItemId(Long orderItemId, Long requestingUserId)
            throws ResourceNotFoundException, UnauthorizedException {

        if (orderItemId == null) throw new BadRequestException("orderItemId es nulo");
        if (requestingUserId == null) throw new UnauthorizedException("requestingUserId es nulo");

        OrderItem oi = orderItemRepository.findByIdWithProductAndKeys(orderItemId)
                .orElseThrow(() -> new OrderNotFoundException("OrderItem no encontrado (id=" + orderItemId + ")."));

        Order order = oi.getOrder();
        if (order == null) {
            throw new OrderNotFoundException("El OrderItem no pertenece a ninguna orden válida (id=" + orderItemId + ").");
        }

        Long buyerId = order.getBuyer() != null ? order.getBuyer().getId() : null;
        boolean isBuyer = Objects.equals(requestingUserId, buyerId);
        boolean isSellerOfItem = oi.getProduct() != null && oi.getProduct().getSeller() != null &&
                Objects.equals(oi.getProduct().getSeller().getId(), requestingUserId);

        if (!isBuyer && !isSellerOfItem) {
            throw new UnauthorizedException("No está autorizado para ver las claves de este ítem.");
        }

        List<OrderKeyResponseDto> out = new ArrayList<>();
        Set<DigitalKey> keys = Optional.ofNullable(oi.getDigitalKeys()).orElse(Collections.emptySet());
        for (DigitalKey dk : keys) {
            boolean canSeeKeyCode = isBuyer || isSellerOfItem;
            out.add(mapKeyToDto(dk, canSeeKeyCode, order.getId()));
        }

        return out;
    }


    private OrderKeyResponseDto mapKeyToDto(DigitalKey dk, boolean includeKeyCode, Long orderId) {
        return new OrderKeyResponseDto(
                dk.getId(),
                includeKeyCode ? dk.getKeyCode() : null,
                dk.getStatus() != null ? dk.getStatus().name() : null,
                dk.getSoldAt(),
                dk.getOrderItem() != null ? dk.getOrderItem().getId() : null,
                orderId,
                dk.getProduct() != null ? dk.getProduct().getId() : null,
                dk.getProduct() != null ? dk.getProduct().getTitle() : null
        );
    }



    @Override
    public Optional<AdminStatsExtrasResponseDto> getAdminStatsExtras(Long requestingUserId) {
        
        User requestingUser = userRepository.findById(requestingUserId)
            .orElseThrow(() -> new UserNotFoundException("Usuario no encontrado (id=" + requestingUserId + ")."));

        if (!requestingUser.getRole().name().equals("ADMIN")) {
            throw new UnauthorizedException("No autorizado para ver estadísticas administrativas.");
        }

        Tuple totalOrdersAndRevenue = orderRepository.getAdminTotalOrdersAndRevenue();
        Integer totalOrders = totalOrdersAndRevenue != null && totalOrdersAndRevenue.get(0) != null
            ? ((Number) totalOrdersAndRevenue.get(0)).intValue() : 0;
        BigDecimal totalRevenue = totalOrdersAndRevenue != null && totalOrdersAndRevenue.get(1) != null
            ? (BigDecimal) totalOrdersAndRevenue.get(1) : null;

        Integer ordersToday = orderRepository.countOrdersPlacedToday();
        Integer totalReviews = reviewRepository.countTotalReviews();


        AdminStatsExtrasResponseDto dto = new AdminStatsExtrasResponseDto(
            totalOrders,
            ordersToday,
            totalReviews,
            totalRevenue != null ? totalRevenue.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO
        );

        return Optional.of(dto);
    }
}
