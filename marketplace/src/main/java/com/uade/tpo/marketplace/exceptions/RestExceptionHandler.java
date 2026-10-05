package com.uade.tpo.marketplace.exceptions;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class RestExceptionHandler {

    private final Logger log = LoggerFactory.getLogger(RestExceptionHandler.class);

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiError> handleBadRequest(BadRequestException ex, HttpServletRequest req) {
        return build(ex, HttpStatus.BAD_REQUEST, "La solicitud realizada es incorrecta o inválida.", req);
    }

    @ExceptionHandler(CategoryDuplicateException.class)
    public ResponseEntity<ApiError> handleCategoryDuplicate(CategoryDuplicateException ex, HttpServletRequest req) {
        return build(ex, HttpStatus.CONFLICT, "La categoría que intenta agregar ya existe.", req);
    }

    @ExceptionHandler(CategoryNotFoundException.class)
    public ResponseEntity<ApiError> handleCategoryNotFound(CategoryNotFoundException ex, HttpServletRequest req) {
        return build(ex, HttpStatus.NOT_FOUND, "No se encontró la categoría solicitada.", req);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException ex, HttpServletRequest req) {
        return build(ex, HttpStatus.CONFLICT, "Se produjo un conflicto de negocio.", req);
    }

    @ExceptionHandler(CouponAlreadyUsedException.class)
    public ResponseEntity<ApiError> handleCouponAlreadyUsed(CouponAlreadyUsedException ex, HttpServletRequest req) {
        return build(ex, HttpStatus.CONFLICT, "El cupón ya fue utilizado.", req);
    }

    @ExceptionHandler(CouponInvalidTypeException.class)
    public ResponseEntity<ApiError> handleCouponInvalidType(CouponInvalidTypeException ex, HttpServletRequest req) {
        return build(ex, HttpStatus.BAD_REQUEST, "El código proporcionado no corresponde a un cupón válido para esta operación.", req);
    }

    @ExceptionHandler(CouponNotApplicableException.class)
    public ResponseEntity<ApiError> handleCouponNotApplicable(CouponNotApplicableException ex, HttpServletRequest req) {
        return build(ex, HttpStatus.BAD_REQUEST, "El cupón no es aplicable a este ítem o no cumple las condiciones.", req);
    }

    @ExceptionHandler(CouponTargetBuyerMissingException.class)
    public ResponseEntity<ApiError> handleCouponTargetBuyerMissing(CouponTargetBuyerMissingException ex, HttpServletRequest req) {
        return build(ex, HttpStatus.BAD_REQUEST, "El cupón no está dirigido a un comprador específico.", req);
    }

    @ExceptionHandler(DigitalKeyAssignmentException.class)
    public ResponseEntity<ApiError> handleDigitalKeyAssignment(DigitalKeyAssignmentException ex, HttpServletRequest req) {
        return build(ex, HttpStatus.CONFLICT, "Error al asignar claves digitales al ítem de la orden.", req);
    }

    @ExceptionHandler(DigitalKeyDuplicateException.class)
    public ResponseEntity<ApiError> handleDigitalKeyDuplicate(DigitalKeyDuplicateException ex, HttpServletRequest req) {
        return build(ex, HttpStatus.CONFLICT, "La clave digital ya se encuentra registrada.", req);
    }

    @ExceptionHandler(DigitalKeyNotFoundException.class)
    public ResponseEntity<ApiError> handleDigitalKeyNotFound(DigitalKeyNotFoundException ex, HttpServletRequest req) {
        return build(ex, HttpStatus.NOT_FOUND, "La clave digital solicitada no fue encontrada.", req);
    }

    @ExceptionHandler(DiscountConflictException.class)
    public ResponseEntity<ApiError> handleDiscountConflict(DiscountConflictException ex, HttpServletRequest req) {
        return build(ex, HttpStatus.CONFLICT, "No se puede aplicar el descuento: conflicto con la orden.", req);
    }

    @ExceptionHandler(DiscountNotFoundException.class)
    public ResponseEntity<ApiError> handleDiscountNotFound(DiscountNotFoundException ex, HttpServletRequest req) {
        return build(ex, HttpStatus.NOT_FOUND, "El descuento solicitado no fue encontrado.", req);
    }

    @ExceptionHandler(DuplicateCouponException.class)
    public ResponseEntity<ApiError> handleDuplicateCoupon(DuplicateCouponException ex, HttpServletRequest req) {
        return build(ex, HttpStatus.CONFLICT, "El código de cupón ya existe.", req);
    }

    @ExceptionHandler(DuplicateOrderItemException.class)
    public ResponseEntity<ApiError> handleDuplicateOrderItem(DuplicateOrderItemException ex, HttpServletRequest req) {
        return build(ex, HttpStatus.CONFLICT, "La orden contiene el mismo producto más de una vez.", req);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiError> handleDuplicateResource(DuplicateResourceException ex, HttpServletRequest req) {
        return build(ex, HttpStatus.CONFLICT, "El recurso ya existe en el sistema.", req);
    }

    @ExceptionHandler(DuplicateReviewException.class)
    public ResponseEntity<ApiError> handleDuplicateReview(DuplicateReviewException ex, HttpServletRequest req) {
        return build(ex, HttpStatus.CONFLICT, "Ya existe una reseña para este ítem de compra.", req);
    }

    @ExceptionHandler(ExpiredDiscountException.class)
    public ResponseEntity<ApiError> handleExpiredDiscount(ExpiredDiscountException ex, HttpServletRequest req) {
        return build(ex, HttpStatus.BAD_REQUEST, "El descuento ha expirado.", req);
    }

    @ExceptionHandler(FileStorageException.class)
    public ResponseEntity<ApiError> handleFileStorage(FileStorageException ex, HttpServletRequest req) {
        return build(ex, HttpStatus.INTERNAL_SERVER_ERROR, "Error al almacenar o procesar archivos.", req);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiError> handleForbidden(ForbiddenException ex, HttpServletRequest req) {
        return build(ex, HttpStatus.FORBIDDEN, "No tiene permisos para realizar esta acción.", req);
    }

    @ExceptionHandler(ImageNotFoundException.class)
    public ResponseEntity<ApiError> handleImageNotFound(ImageNotFoundException ex, HttpServletRequest req) {
        return build(ex, HttpStatus.NOT_FOUND, "La imagen solicitada no fue encontrada.", req);
    }

    @ExceptionHandler(ImageProcessingException.class)
    public ResponseEntity<ApiError> handleImageProcessing(ImageProcessingException ex, HttpServletRequest req) {
        return build(ex, HttpStatus.INTERNAL_SERVER_ERROR, "Error al procesar la imagen.", req);
    }

        @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ApiError> handleInsufficientStock(InsufficientStockException ex, HttpServletRequest req) {
        return build(ex, HttpStatus.CONFLICT, "Stock insuficiente para completar la compra.", req);
    }

    @ExceptionHandler(InvalidDigitalKeyFormat.class)
    public ResponseEntity<ApiError> handleInvalidDigitalKeyFormat(InvalidDigitalKeyFormat ex, HttpServletRequest req) {
        return build(ex, HttpStatus.BAD_REQUEST, "El formato de la clave digital es inválido.", req);
    }

    @ExceptionHandler(InvalidDiscountException.class)
    public ResponseEntity<ApiError> handleInvalidDiscount(InvalidDiscountException ex, HttpServletRequest req) {
        return build(ex, HttpStatus.BAD_REQUEST, "El descuento es inválido o no aplicable.", req);
    }

    @ExceptionHandler(InvalidFileException.class)
    public ResponseEntity<ApiError> handleInvalidFile(InvalidFileException ex, HttpServletRequest req) {
        return build(ex, HttpStatus.BAD_REQUEST, "Archivo inválido o formato no soportado.", req);
    }

    @ExceptionHandler(OrderDuplicateException.class)
    public ResponseEntity<ApiError> handleOrderDuplicate(OrderDuplicateException ex, HttpServletRequest req) {
        return build(ex, HttpStatus.CONFLICT, "La orden de compra ya se encuentra registrada.", req);
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ApiError> handleOrderNotFound(OrderNotFoundException ex, HttpServletRequest req) {
        return build(ex, HttpStatus.NOT_FOUND, "La orden solicitada no fue encontrada.", req);
    }

    @ExceptionHandler(OrderProcessingException.class)
    public ResponseEntity<ApiError> handleOrderProcessing(OrderProcessingException ex, HttpServletRequest req) {
        return build(ex, HttpStatus.CONFLICT, "Error al procesar la orden.", req);
    }

    @ExceptionHandler(OutOfStockException.class)
    public ResponseEntity<ApiError> handleOutOfStock(OutOfStockException ex, HttpServletRequest req) {
        return build(ex, HttpStatus.CONFLICT, "El producto no tiene stock disponible.", req);
    }

    @ExceptionHandler(ProductImageNotFoundException.class)
    public ResponseEntity<ApiError> handleProductImageNotFound(ProductImageNotFoundException ex, HttpServletRequest req) {
        return build(ex, HttpStatus.NOT_FOUND, "Imagen de producto no encontrada.", req);
    }

    @ExceptionHandler(ProductNotActiveException.class)
    public ResponseEntity<ApiError> handleProductNotActive(ProductNotActiveException ex, HttpServletRequest req) {
        return build(ex, HttpStatus.BAD_REQUEST, "El producto no está activo.", req);
    }

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ApiError> handleProductNotFound(ProductNotFoundException ex, HttpServletRequest req) {
        return build(ex, HttpStatus.NOT_FOUND, "No se encontró el producto solicitado.", req);
    }

    @ExceptionHandler(ProductOwnershipException.class)
    public ResponseEntity<ApiError> handleProductOwnership(ProductOwnershipException ex, HttpServletRequest req) {
        return build(ex, HttpStatus.FORBIDDEN, "Operación no permitida sobre el producto (permiso/propiedad).", req);
    }

    @ExceptionHandler(ProductSelfPurchaseException.class)
    public ResponseEntity<ApiError> handleProductSelfPurchase(ProductSelfPurchaseException ex, HttpServletRequest req) {
        return build(ex, HttpStatus.CONFLICT, "No se permite comprar un producto propio.", req);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest req) {
        return build(ex, HttpStatus.NOT_FOUND, "El recurso solicitado no fue encontrado en el sistema.", req);
    }

    @ExceptionHandler(ReviewNotFoundException.class)
    public ResponseEntity<ApiError> handleReviewNotFound(ReviewNotFoundException ex, HttpServletRequest req) {
        return build(ex, HttpStatus.NOT_FOUND, "La reseña solicitada no fue encontrada.", req);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiError> handleUnauthorizedException(UnauthorizedException ex, HttpServletRequest req) {
        return build(ex, req.getUserPrincipal() == null ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN,
                "No tienes permisos para realizar esta acción.", req);
    }

    @ExceptionHandler(UserDuplicateException.class)
    public ResponseEntity<ApiError> handleUserDuplicate(UserDuplicateException ex, HttpServletRequest req) {
        return build(ex, HttpStatus.CONFLICT, "El usuario que intenta crear ya se encuentra registrado.", req);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiError> handleUserNotFound(UserNotFoundException ex, HttpServletRequest req) {
        return build(ex, HttpStatus.NOT_FOUND, "El usuario solicitado no fue encontrado.", req);
    }


    @ExceptionHandler({IllegalArgumentException.class, org.springframework.web.bind.MethodArgumentNotValidException.class,
        org.springframework.http.converter.HttpMessageNotReadableException.class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
        org.springframework.web.bind.MissingServletRequestParameterException.class})
    public ResponseEntity<ApiError> handleValidation(Exception ex, HttpServletRequest req) {
        return build(new BadRequestException("Datos inválidos. Revisa los campos enviados."), HttpStatus.BAD_REQUEST, "Datos inválidos.", req);
    }

    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(Exception ex, HttpServletRequest req) {
        return build(new UnauthorizedException("Credenciales inválidas."), HttpStatus.UNAUTHORIZED, "Credenciales inválidas.", req);
    }

    // Excepción genérica para manejar cualquier otra excepción no prevista
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleAll(Exception ex, HttpServletRequest req) {
        return build(ex, HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado en el servidor.", req);
    }


    private ResponseEntity<ApiError> build(Throwable ex, HttpStatus status, String defaultSpanishMessage, HttpServletRequest req) {
        String message = !status.is5xxServerError() && ex.getMessage() != null && !ex.getMessage().isBlank()
                ? ex.getMessage()
                : defaultSpanishMessage;

        ApiError body = new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                List.of(),
                req == null ? "" : req.getRequestURI()
        );

        if (status.is5xxServerError()) {
            log.error("Error {} en {}: {}", status.value(), body.path(), message, ex);
        } else {
            log.info("Manejado {} para {} -> {}", status.value(), body.path(), message);
        }

        return ResponseEntity.status(status).body(body);
    }


}