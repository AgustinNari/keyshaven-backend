package com.uade.tpo.marketplace.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import com.uade.tpo.marketplace.entity.basic.Category;

import com.uade.tpo.marketplace.entity.basic.Order;
import com.uade.tpo.marketplace.entity.basic.OrderItem;
import com.uade.tpo.marketplace.entity.basic.Product;
import com.uade.tpo.marketplace.entity.basic.ProductImage;
import com.uade.tpo.marketplace.entity.basic.Review;
import com.uade.tpo.marketplace.entity.basic.User;
import com.uade.tpo.marketplace.entity.dto.create.ReviewCreateDto;
import com.uade.tpo.marketplace.entity.dto.response.LatestReviewResponseDto;
import com.uade.tpo.marketplace.entity.dto.response.ProductImageResponseDto;
import com.uade.tpo.marketplace.entity.dto.response.ReviewDeletionResponseDto;
import com.uade.tpo.marketplace.entity.dto.response.ReviewResponseDto;
import com.uade.tpo.marketplace.entity.dto.update.ReviewUpdateDto;
import com.uade.tpo.marketplace.entity.enums.OrderStatus;
import com.uade.tpo.marketplace.entity.enums.Role;
import com.uade.tpo.marketplace.exceptions.BadRequestException;
import com.uade.tpo.marketplace.exceptions.DuplicateReviewException;
import com.uade.tpo.marketplace.exceptions.OrderNotFoundException;
import com.uade.tpo.marketplace.exceptions.ProductNotFoundException;
import com.uade.tpo.marketplace.exceptions.ResourceNotFoundException;
import com.uade.tpo.marketplace.exceptions.ReviewNotFoundException;
import com.uade.tpo.marketplace.exceptions.UnauthorizedException;
import com.uade.tpo.marketplace.exceptions.UserNotFoundException;
import com.uade.tpo.marketplace.extra.mappers.ProductImageMapper;
import com.uade.tpo.marketplace.extra.mappers.ReviewMapper;
import com.uade.tpo.marketplace.repository.interfaces.IOrderItemRepository;
import com.uade.tpo.marketplace.repository.interfaces.IOrderRepository;
import com.uade.tpo.marketplace.repository.interfaces.IProductImageRepository;
import com.uade.tpo.marketplace.repository.interfaces.IProductRepository;
import com.uade.tpo.marketplace.repository.interfaces.IReviewRepository;
import com.uade.tpo.marketplace.repository.interfaces.IUserRepository;
import com.uade.tpo.marketplace.service.interfaces.IReviewService;

@Service
public class ReviewService implements IReviewService {

    @Autowired
    private IReviewRepository reviewRepository;
    @Autowired
    private IProductRepository productRepository;
    @Autowired
    private IUserRepository userRepository;
    @Autowired
    private IOrderItemRepository orderItemRepository;
    @Autowired
    private IOrderRepository orderRepository;
    @Autowired
    private IProductImageRepository productImageRepository;

    @Autowired
    private ReviewMapper reviewMapper;
    @Autowired
    private ProductImageMapper productImageMapper;







    @Override
    @Transactional(rollbackFor = Throwable.class)
    public ReviewResponseDto createReview(ReviewCreateDto dto, Long buyerId)
            throws ResourceNotFoundException, BadRequestException, DuplicateReviewException {

        if (dto == null) throw new BadRequestException("Datos de la reseña no proporcionados.");
        if (dto.productId() == null) throw new BadRequestException("Debe indicarse el productId para la reseña.");
        if (dto.rating() == null || dto.rating() < 1 || dto.rating() > 10) {
            throw new BadRequestException("La calificación (rating) debe estar entre 1 y 10.");
        }

        Product product = productRepository.findById(dto.productId())
                .orElseThrow(() -> new ProductNotFoundException("Producto no encontrado (id=" + dto.productId() + ")."));

        User buyer = userRepository.findById(buyerId)
                .orElseThrow(() -> new UserNotFoundException("Usuario comprador no encontrado (id=" + buyerId + ")."));

        if (dto.orderItemId() == null) {
            throw new BadRequestException("Debe indicarse el orderItemId para la reseña.");
        }

        OrderItem oi = orderItemRepository.findById(dto.orderItemId())
                .orElseThrow(() -> new OrderNotFoundException("Item de la orden no encontrado (id=" + dto.orderItemId() + ")."));

        if (oi.getProduct() == null || !Objects.equals(oi.getProduct().getId(), dto.productId())) {
            throw new BadRequestException("El item de la orden no corresponde al producto indicado.");
        }

        Order order = orderRepository.findById(oi.getOrder() != null ? oi.getOrder().getId() : null)
                .orElseThrow(() -> new OrderNotFoundException("Order no encontrado (id=" + (oi.getOrder() != null ? oi.getOrder().getId() : null) + ")."));

        if (order.getBuyer() == null || !Objects.equals(order.getBuyer().getId(), buyerId)) {
            throw new BadRequestException("La orden no pertenece al comprador indicado.");
        }

        if (order.getStatus() != OrderStatus.COMPLETED) {
            throw new BadRequestException("La orden no se encuentra completada.");
        }

        if (reviewRepository.existsByOrderItemId(dto.orderItemId())) {
            throw new DuplicateReviewException("Ya existe una reseña asociada a ese ítem.");
        }

        if (product.getId() != null && buyerId != null &&
                reviewRepository.existsByProductIdAndBuyerId(product.getId(), buyerId)) {
            throw new DuplicateReviewException("El comprador ya realizó una reseña para este producto, por lo que no puede realizar otra.");
        }

        Review review = reviewMapper.toEntity(dto);
        review.setBuyer(buyer);
        review.setProduct(product);
        review.setSeller(product.getSeller());
        review.setVisible(true);
        review.setOrderItem(oi);

        Review saved = reviewRepository.save(review);

        oi.setReview(saved);
        orderItemRepository.save(oi);

        return reviewMapper.toResponse(saved);
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public ReviewResponseDto updateReview(Long reviewId, ReviewUpdateDto dto, Long requestingUserId) throws ResourceNotFoundException, UnauthorizedException {
        if (reviewId == null) throw new BadRequestException("Id de reseña no proporcionado.");
        Review existing = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ReviewNotFoundException("Reseña no encontrada (id=" + reviewId + ")."));

        User requester = userRepository.findById(requestingUserId)
                .orElseThrow(() -> new UserNotFoundException("Usuario solicitante no encontrado (id=" + requestingUserId + ")."));

        boolean isOwner = existing.getBuyer() != null && Objects.equals(existing.getBuyer().getId(), requestingUserId);

        if (!isOwner) {
            throw new UnauthorizedException("No tiene permiso para modificar esta reseña.");
        }

        if (dto == null || (dto.rating() != null && (dto.rating() < 1 || dto.rating() > 10))) {
            throw new BadRequestException("La calificación debe estar entre 1 y 10.");
        }
        reviewMapper.updateFromDto(dto, existing);

        Review saved = reviewRepository.save(existing);
        return reviewMapper.toResponse(saved);
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public ReviewDeletionResponseDto deleteReview(Long reviewId, Long requestingUserId) throws ResourceNotFoundException, UnauthorizedException {
        if (reviewId == null) throw new BadRequestException("Id de reseña no proporcionado.");
        Review existing = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ReviewNotFoundException("Reseña no encontrada (id=" + reviewId + ")."));

        User requester = userRepository.findById(requestingUserId)
                .orElseThrow(() -> new UserNotFoundException("Usuario solicitante no encontrado (id=" + requestingUserId + ")."));

        boolean isOwner = existing.getBuyer() != null && Objects.equals(existing.getBuyer().getId(), requestingUserId);
        if (!isOwner) {
            throw new UnauthorizedException("No tiene permiso para eliminar esta reseña.");
        }

        if (existing.getOrderItem() != null) {
            OrderItem oi = orderItemRepository.findById(existing.getOrderItem().getId()).orElse(null);
            if (oi != null) {
                oi.setReview(null);
                orderItemRepository.save(oi);
            }
        }
        
        reviewRepository.delete(existing);


        return new ReviewDeletionResponseDto(true, reviewId, "Reseña eliminada exitosamente.");
    }


    @Override
    public Page<ReviewResponseDto> getReviewsByProduct(Long productId, Pageable pageable) throws ResourceNotFoundException {
        if (productId == null) throw new BadRequestException("productId no proporcionado.");
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Producto no encontrado (id=" + productId + ")."));

        Page<Review> page = reviewRepository.findByProductIdAndVisible(productId, pageable);
        List<ReviewResponseDto> dtos = page.getContent().stream()
                .map(reviewMapper::toResponse)
                .collect(Collectors.toList());

        return new PageImpl<>(dtos, pageable, page.getTotalElements());
    }


    @Override
    public Page<ReviewResponseDto> getReviewsByUser(Long userId, Pageable pageable) throws ResourceNotFoundException {
        if (userId == null) throw new BadRequestException("userId no proporcionado.");
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Usuario no encontrado (id=" + userId + ")."));

        Page<Review> page = reviewRepository.findByBuyerId(userId, pageable);
        List<ReviewResponseDto> dtos = page.getContent().stream().map(reviewMapper::toResponse).collect(Collectors.toList());
        return new PageImpl<>(dtos, pageable, page.getTotalElements());
    }

    @Override
    public Page<ReviewResponseDto> getAllReviews(Pageable pageable) {
        Page<Review> page = reviewRepository.findAll(pageable);
        List<ReviewResponseDto> dtos = page.getContent().stream().map(reviewMapper::toResponse).collect(Collectors.toList());
        return new PageImpl<>(dtos, pageable, page.getTotalElements());
    }


    @Override
    public Pair<Double, Long> getAverageRatingAndCountByProduct(Long productId) throws ResourceNotFoundException {
        if (productId == null) throw new BadRequestException("productId no proporcionado.");
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Producto no encontrado (id=" + productId + ")."));

        BigDecimal avgBd = reviewRepository.getAverageRatingByProductId(productId);
        Long count = reviewRepository.getCountByProductId(productId);

        double avg = 0.0;
        if (avgBd != null) {
            avg = avgBd.doubleValue();
        }

        if (count == null) count = 0L;

        return Pair.of(avg, count);
    }


    @Override
    @Transactional(rollbackFor = Throwable.class)
    public ReviewResponseDto toggleReviewVisibility(Long reviewId, boolean visible, Long requestingUserId)
            throws ResourceNotFoundException, UnauthorizedException {

        if (reviewId == null) throw new BadRequestException("Id de reseña no proporcionado.");
        Review existing = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ReviewNotFoundException("Reseña no encontrada (id=" + reviewId + ")."));

        if (requestingUserId == null) throw new BadRequestException("Id de usuario solicitante no proporcionado.");
        User requester = userRepository.findById(requestingUserId)
                .orElseThrow(() -> new UserNotFoundException("Usuario solicitante no encontrado (id=" + requestingUserId + ")."));

        if (requester.getRole() != Role.ADMIN) {
            throw new UnauthorizedException("No tienes permiso para cambiar la visibilidad de la reseña.");
        }

        if (Objects.equals(existing.isVisible(), visible)) {
            return reviewMapper.toResponse(existing);
        }

        existing.setVisible(visible);
        Review saved = reviewRepository.save(existing);

        return reviewMapper.toResponse(saved);
    }



    @Override
    @Transactional(readOnly = true)
    public ReviewResponseDto getReviewByOrderItemIdForBuyer(Long orderItemId, Long requestingUserId)
            throws ResourceNotFoundException, UnauthorizedException {

        if (orderItemId == null) {
            throw new BadRequestException("orderItemId no proporcionado.");
        }
        if (requestingUserId == null) {
            throw new UnauthorizedException("Id de usuario solicitante no proporcionado.");
        }

        Optional<Review> opt = reviewRepository.findByOrderItemId(orderItemId);

        if (opt.isEmpty()) {
            throw new ReviewNotFoundException("No se encontró una reseña asociada al orderItem (id=" + orderItemId + ").");
        }

        Review review = opt.get();
        User buyer = review.getBuyer();
        Long buyerId = buyer != null ? buyer.getId() : null;

        if (buyerId == null || !Objects.equals(buyerId, requestingUserId)) {
            throw new UnauthorizedException("No autorizado: Solamente el comprador que creó la reseña puede verla.");
        }

        return reviewMapper.toResponse(review);
    }

     //CAMBIOS:

        @Override
    @Transactional(readOnly = true)
    public Page<LatestReviewResponseDto> getLatestReviews(Pageable pageable) {
        Page<Review> reviewsPage = reviewRepository.findLatestVisibleReviews(pageable);
        
        List<LatestReviewResponseDto> dtos = reviewsPage.getContent().stream()
                .map(this::enrichReviewWithDetails)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        
        return new PageImpl<>(dtos, pageable, reviewsPage.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public List<LatestReviewResponseDto> getLatestReviews(int count) {
        Pageable pageable = PageRequest.of(0, count);
        List<Review> reviews = reviewRepository.findTopNByVisibleTrueOrderByCreatedAtDesc(pageable);
        
        return reviews.stream()
                .map(this::enrichReviewWithDetails)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

private LatestReviewResponseDto enrichReviewWithDetails(Review review) {
    try {
        // Obtener información del producto
        String productTitle = null;
        String productImageDataUrl = null;
        List<String> productCategories = new ArrayList<>();
        
        if (review.getProduct() != null && review.getProduct().getId() != null) {
            Product product = productRepository.findById(review.getProduct().getId()).orElse(null);
            if (product != null) {
                productTitle = product.getTitle();
                
                // Obtener categorías del producto
                if (product.getCategories() != null) {
                    productCategories = product.getCategories().stream()
                            .filter(Objects::nonNull)
                            .map(Category::getDescription)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());
                }
                
                // Obtener imagen principal del producto
                Optional<ProductImage> primaryImageOpt = productImageRepository
                    .findFirstByProductIdAndIsPrimaryTrue(product.getId());
                
                if (primaryImageOpt.isPresent()) {
                    ProductImageResponseDto imageDto = productImageMapper.toResponse(primaryImageOpt.get());
                    productImageDataUrl = imageDto != null ? imageDto.dataUrl() : null;
                } else {
                    // Si no hay imagen principal, tomar la primera imagen
                    List<ProductImage> productImages = productImageRepository
                        .findByProductIdOrderByIdAsc(product.getId());
                    if (!productImages.isEmpty()) {
                        ProductImageResponseDto imageDto = productImageMapper.toResponse(productImages.get(0));
                        productImageDataUrl = imageDto != null ? imageDto.dataUrl() : null;
                    }
                }
            }
        }
        
        // Obtener información del usuario
        String buyerDisplayName = "Usuario";
        if (review.getBuyer() != null && review.getBuyer().getId() != null) {
            User buyer = userRepository.findById(review.getBuyer().getId()).orElse(null);
            if (buyer != null && buyer.getDisplayName() != null) {
                buyerDisplayName = buyer.getDisplayName();
            } else {
                buyerDisplayName = "Usuario #" + review.getBuyer().getId();
            }
        }
        
        return reviewMapper.toLatestReviewResponse(review, productTitle, productImageDataUrl, 
                                                  buyerDisplayName, productCategories);
        
    } catch (Exception e) {
        System.err.println("Error enriching review with id: " + review.getId() + ", error: " + e.getMessage());
        return null;
    }
}
}





