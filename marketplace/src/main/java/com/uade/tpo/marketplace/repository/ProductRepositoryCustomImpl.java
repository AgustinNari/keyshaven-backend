package com.uade.tpo.marketplace.repository;

import java.math.BigDecimal;
import java.sql.Blob;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.uade.tpo.marketplace.entity.basic.Category;
import com.uade.tpo.marketplace.entity.basic.DigitalKey;
import com.uade.tpo.marketplace.entity.basic.Discount;
import com.uade.tpo.marketplace.entity.basic.OrderItem;
import com.uade.tpo.marketplace.entity.basic.Product;
import com.uade.tpo.marketplace.entity.basic.ProductImage;
import com.uade.tpo.marketplace.entity.basic.Review;
import com.uade.tpo.marketplace.entity.dto.request.ProductFilter;
import com.uade.tpo.marketplace.entity.dto.response.CategoryResponseDto;
import com.uade.tpo.marketplace.entity.dto.response.ProductListDTO;
import com.uade.tpo.marketplace.entity.enums.DiscountType;
import com.uade.tpo.marketplace.entity.enums.KeyStatus;
import com.uade.tpo.marketplace.exceptions.ImageProcessingException;
import com.uade.tpo.marketplace.exceptions.InvalidFileException;
import com.uade.tpo.marketplace.repository.interfaces.IDiscountRepository;
import com.uade.tpo.marketplace.repository.interfaces.IProductRepository;
import com.uade.tpo.marketplace.repository.interfaces.IProductRepositoryCustom;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

@Repository
public class ProductRepositoryCustomImpl implements IProductRepositoryCustom {

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private IDiscountRepository discountRepository;

    @Autowired
    private IProductRepository productRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<ProductListDTO> findProductsWithAggregates(ProductFilter filter, Pageable pageable, boolean activeOnly) {
        CriteriaBuilder cb = em.getCriteriaBuilder();

        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<Product> root = cq.from(Product.class);

        Expression<Long> idExpr = root.get("id");
        Expression<String> titleExpr = root.get("title");
        Expression<BigDecimal> priceExpr = root.get("price");
        Expression<Boolean> activeExpr = root.get("active");
        Expression<String> platformExpr = root.get("platform");
        Expression<String> regionExpr = root.get("region");
        Expression<LocalDate> releaseDateExpr = root.get("releaseDate");
        Expression<String> developerExpr = root.get("developer");
        Expression<String> publisherExpr = root.get("publisher");
        Expression<Integer> metaExpr = root.get("metacriticScore");
        Expression<Boolean> featuredExpr = root.get("featured");
        Expression<Long> sellerIdExpr = root.get("seller").get("id");
        Expression<String> sellerNameExpr = root.get("seller").get("displayName");
        Expression<Instant> createdAtExpr = root.get("createdAt");

        Subquery<Double> avgRatingSq = cq.subquery(Double.class);
        {
            Root<Review> rSub = avgRatingSq.from(Review.class);
            avgRatingSq.select(cb.avg(rSub.get("rating").as(Double.class)));
            avgRatingSq.where(cb.equal(rSub.get("product"), root), cb.isTrue(rSub.get("visible")));
        }

        Subquery<Long> ratingCountSq = cq.subquery(Long.class);
        {
            Root<Review> rcSub = ratingCountSq.from(Review.class);
            ratingCountSq.select(cb.count(rcSub.get("id")));
            ratingCountSq.where(cb.equal(rcSub.get("product"), root), cb.isTrue(rcSub.get("visible")));
        }

        Subquery<Long> stockSq = cq.subquery(Long.class);
        {
            Root<DigitalKey> dkSub = stockSq.from(DigitalKey.class);
            stockSq.select(cb.count(dkSub));
            stockSq.where(cb.equal(dkSub.get("product"), root), cb.equal(dkSub.get("status"), KeyStatus.AVAILABLE));
        }

        Subquery<Long> soldSq = cq.subquery(Long.class);
        {
            Root<DigitalKey> dkSub2 = soldSq.from(DigitalKey.class);
            soldSq.select(cb.count(dkSub2));
            soldSq.where(cb.equal(dkSub2.get("product"), root), cb.equal(dkSub2.get("status"), KeyStatus.SOLD));
        }

        Subquery<Long> amountSoldSq = cq.subquery(Long.class);
        {
            Root<OrderItem> oiSub = amountSoldSq.from(OrderItem.class);
            amountSoldSq.select(cb.coalesce(cb.sum(oiSub.get("quantity").as(Long.class)), 0L));
            amountSoldSq.where(cb.equal(oiSub.get("product"), root));
        }

        cq.multiselect(
                idExpr.alias("id"),
                sellerIdExpr.alias("sellerId"),
                sellerNameExpr.alias("sellerDisplayName"),
                titleExpr.alias("title"),
                priceExpr.alias("price"),
                activeExpr.alias("active"),
                platformExpr.alias("platform"),
                regionExpr.alias("region"),
                releaseDateExpr.alias("releaseDate"),
                developerExpr.alias("developer"),
                publisherExpr.alias("publisher"),
                metaExpr.alias("metacriticScore"),
                featuredExpr.alias("featured"),
                avgRatingSq.alias("avgRating"),
                ratingCountSq.alias("ratingCount"),
                stockSq.alias("stock"),
                soldSq.alias("sold"),
                amountSoldSq.alias("amountSold"),
                createdAtExpr.alias("createdAt")
        );

        List<Predicate> predicates = new ArrayList<>();
        if (filter != null && filter.getActive() != null) predicates.add(cb.equal(root.get("active"), filter.getActive()));
        if (activeOnly) {
            predicates.add(cb.isTrue(root.get("active")));

            Subquery<Long> stockExist = cq.subquery(Long.class);
            {
                Root<DigitalKey> dkSub = stockExist.from(DigitalKey.class);
                stockExist.select(dkSub.get("id"));
                stockExist.where(
                    cb.equal(dkSub.get("product"), root),
                    cb.equal(dkSub.get("status"), KeyStatus.AVAILABLE)
                );
            }
            predicates.add(cb.exists(stockExist));
        }

        if (filter != null) {
            if (filter.getProductIds() != null && !filter.getProductIds().isEmpty()) {
                predicates.add(root.get("id").in(filter.getProductIds()));
            }
            if (filter.getSellerId() != null) predicates.add(cb.equal(root.get("seller").get("id"), filter.getSellerId()));
            if (filter.getSellerIds() != null && !filter.getSellerIds().isEmpty()) {
                predicates.add(root.get("seller").get("id").in(filter.getSellerIds()));
            }
            if (filter.getTitle() != null && !filter.getTitle().isBlank())
                predicates.add(cb.like(cb.lower(root.get("title")), "%" + filter.getTitle().toLowerCase() + "%"));
            if (filter.getMinPrice() != null) predicates.add(cb.ge(root.get("price"), filter.getMinPrice()));
            if (filter.getMaxPrice() != null) predicates.add(cb.le(root.get("price"), filter.getMaxPrice()));
            if (filter.getPlatform() != null) predicates.add(cb.equal(cb.lower(root.get("platform")), filter.getPlatform().toLowerCase()));
            if (filter.getRegion() != null) predicates.add(cb.equal(cb.lower(root.get("region")), filter.getRegion().toLowerCase()));
            if (filter.getDeveloper() != null) predicates.add(cb.like(cb.lower(root.get("developer")), "%" + filter.getDeveloper().toLowerCase() + "%"));
            if (filter.getPublisher() != null) predicates.add(cb.like(cb.lower(root.get("publisher")), "%" + filter.getPublisher().toLowerCase() + "%"));
            if (filter.getReleaseDateFrom() != null) predicates.add(cb.greaterThanOrEqualTo(root.get("releaseDate"), filter.getReleaseDateFrom()));
            if (filter.getReleaseDateTo() != null) predicates.add(cb.lessThanOrEqualTo(root.get("releaseDate"), filter.getReleaseDateTo()));
            if (filter.getMinMetacriticScore() != null) predicates.add(cb.ge(root.get("metacriticScore"), filter.getMinMetacriticScore()));
            if (filter.getFeatured() != null) predicates.add(cb.equal(root.get("featured"), filter.getFeatured()));

            if (filter.getCategoryIds() != null && !filter.getCategoryIds().isEmpty()) {
                Subquery<Long> catExist = cq.subquery(Long.class);
                Root<Product> p2 = catExist.from(Product.class);
                Join<Product, Category> joinCat = p2.join("categories", JoinType.INNER);
                catExist.select(p2.get("id"));
                catExist.where(cb.equal(p2.get("id"), root.get("id")), joinCat.get("id").in(filter.getCategoryIds()));
                predicates.add(cb.exists(catExist));
            }

            if (filter.getMinAvgRating() != null) {
                Subquery<Long> avgExist = cq.subquery(Long.class);
                Root<Review> rAvg = avgExist.from(Review.class);
                Expression<Long> rProdId = rAvg.get("product").get("id");
                avgExist.select(rProdId);
                avgExist.where(cb.equal(rAvg.get("product"), root), cb.isTrue(rAvg.get("visible")));
                avgExist.groupBy(rProdId);
                avgExist.having(cb.greaterThanOrEqualTo(cb.avg(rAvg.get("rating").as(Double.class)), filter.getMinAvgRating()));
                predicates.add(cb.exists(avgExist));
            }

            if (filter.getMinRatingCount() != null) {
                Subquery<Long> rcExist = cq.subquery(Long.class);
                Root<Review> rCount = rcExist.from(Review.class);
                Expression<Long> rcProdId = rCount.get("product").get("id");
                rcExist.select(rcProdId);
                rcExist.where(cb.equal(rCount.get("product"), root), cb.isTrue(rCount.get("visible")));
                rcExist.groupBy(rcProdId);
                rcExist.having(cb.greaterThanOrEqualTo(cb.count(rCount.get("id")), filter.getMinRatingCount()));
                predicates.add(cb.exists(rcExist));
            }

            if (filter.getMinStock() != null) {
                Subquery<Long> stockExist = cq.subquery(Long.class);
                Root<DigitalKey> dk = stockExist.from(DigitalKey.class);
                Expression<Long> dkProdId = dk.get("product").get("id");
                stockExist.select(dkProdId);
                stockExist.where(cb.equal(dk.get("product"), root), cb.equal(dk.get("status"), KeyStatus.AVAILABLE));
                stockExist.groupBy(dkProdId);
                stockExist.having(cb.greaterThanOrEqualTo(cb.count(dk.get("id")), filter.getMinStock().longValue()));
                predicates.add(cb.exists(stockExist));
            }

            if (filter.getMinAmountSold() != null) {
                Subquery<Long> amtExist = cq.subquery(Long.class);
                Root<OrderItem> oi = amtExist.from(OrderItem.class);
                Expression<Long> oiProdId = oi.get("product").get("id");
                amtExist.select(oiProdId);
                amtExist.where(cb.equal(oi.get("product"), root));
                amtExist.groupBy(oiProdId);
                amtExist.having(cb.greaterThanOrEqualTo(cb.coalesce(cb.sum(oi.get("quantity").as(Long.class)), 0L), filter.getMinAmountSold()));
                predicates.add(cb.exists(amtExist));
            }

            if (filter.getMinDiscountPercent() != null) {
                Object rawObj = filter.getMinDiscountPercent();
                BigDecimal raw = null;
                if (rawObj instanceof BigDecimal) {
                    raw = (BigDecimal) rawObj;
                } else if (rawObj instanceof Number) {
                    raw = BigDecimal.valueOf(((Number) rawObj).doubleValue());
                } else {
                    try {
                        raw = new BigDecimal(String.valueOf(rawObj));
                    } catch (Exception ex) {
                        raw = null;
                    }
                }

                if (raw != null) {
                    BigDecimal minFrac = raw.compareTo(BigDecimal.ONE) > 0 ? raw.movePointLeft(2) : raw;
                    BigDecimal minAsWhole = minFrac.movePointRight(2);

                    Subquery<Long> discExist = cq.subquery(Long.class);
                    Root<Discount> d = discExist.from(Discount.class);
                    discExist.select(d.get("id"));

                    Expression<BigDecimal> dValue = d.get("value");
                    Predicate condType = cb.equal(d.get("type"), DiscountType.PERCENT);
                    Predicate condActive = cb.isTrue(d.get("active"));

                    Instant now = Instant.now();
                    Predicate condStarts = cb.lessThanOrEqualTo(d.get("startsAt"), now);
                    Predicate condEnds = cb.or(cb.isNull(d.get("endsAt")), cb.greaterThanOrEqualTo(d.get("endsAt"), now));

                    Predicate condValue = cb.greaterThanOrEqualTo(dValue, minAsWhole);

                    Predicate condProduct = cb.equal(d.get("targetProduct"), root);
                    Predicate condSeller = cb.equal(d.get("targetSeller"), root.get("seller"));

                    Subquery<Long> catMatch = discExist.subquery(Long.class);
                    Root<Product> p3 = catMatch.from(Product.class);
                    Join<Product, Category> cjoin = p3.join("categories", JoinType.INNER);
                    catMatch.select(cjoin.get("id"));
                    catMatch.where(cb.equal(p3.get("id"), root.get("id")), cb.equal(cjoin, d.get("targetCategory")));
                    Predicate condCategory = cb.exists(catMatch);

                    Predicate anyTarget = cb.or(condProduct, condSeller, condCategory);
                    discExist.where(cb.and(anyTarget, condType, condActive, condStarts, condEnds, condValue));

                    predicates.add(cb.exists(discExist));
                }
            }
        }

        cq.where(predicates.toArray(new Predicate[0]));

        Map<String, Expression<?>> sortExprMap = new HashMap<>();
        sortExprMap.put("avgRating", avgRatingSq);
        sortExprMap.put("ratingCount", ratingCountSq);
        sortExprMap.put("stock", stockSq);
        sortExprMap.put("sold", soldSq);
        sortExprMap.put("amountSold", amountSoldSq);
        sortExprMap.put("createdAt", createdAtExpr);
        sortExprMap.put("price", priceExpr);
        sortExprMap.put("title", titleExpr);

        List<Order> orders = new ArrayList<>();
        if (pageable.getSort().isSorted()) {
            for (Sort.Order s : pageable.getSort()) {
                String prop = s.getProperty();
                boolean asc = s.isAscending();

                Expression<?> expr = sortExprMap.get(prop);
                if (expr != null) {
                    orders.add(asc ? cb.asc((Expression) expr) : cb.desc((Expression) expr));
                } else {

                    try {
                        orders.add(asc ? cb.asc(root.get(prop)) : cb.desc(root.get(prop)));
                    } catch (IllegalArgumentException ex) {

                        orders.add(cb.desc(createdAtExpr));
                    }
                }
            }
        } else {
            orders.add(cb.desc(createdAtExpr));
        }
        cq.orderBy(orders);

        List<Tuple> tuples = em.createQuery(cq)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList();

        List<ProductListDTO> dtos = new ArrayList<>();
        List<Long> productIds = new ArrayList<>();
        Map<Long, Long> productToSeller = new HashMap<>();
        for (Tuple t : tuples) {
            ProductListDTO dto = new ProductListDTO();
            Long id = (Long) t.get("id");
            productIds.add(id);

            dto.setId(id);
            Long sellerId = (Long) t.get("sellerId");
            dto.setSellerId(sellerId);
            productToSeller.put(id, sellerId);

            dto.setSellerDisplayName((String) t.get("sellerDisplayName"));
            dto.setTitle((String) t.get("title"));
            dto.setPrice((BigDecimal) t.get("price"));
            boolean active = Boolean.TRUE.equals(t.get("active"));
            dto.setActive(active);
            dto.setPlatform((String) t.get("platform"));
            dto.setRegion((String) t.get("region"));
            dto.setReleaseDate((LocalDate) t.get("releaseDate"));
            dto.setDeveloper((String) t.get("developer"));
            dto.setPublisher((String) t.get("publisher"));
            dto.setMetacriticScore((Integer) t.get("metacriticScore"));
            dto.setFeatured(Boolean.TRUE.equals(t.get("featured")));

            dto.setAvgRating(t.get("avgRating") != null ? ((Number) t.get("avgRating")).doubleValue() : null);
            dto.setRatingCount(t.get("ratingCount") != null ? ((Number) t.get("ratingCount")).longValue() : 0L);
            dto.setStock(t.get("stock") != null ? ((Number) t.get("stock")).intValue() : 0);
            dto.setSold(t.get("sold") != null ? ((Number) t.get("sold")).intValue() : 0);
            dto.setAmountSold(t.get("amountSold") != null ? ((Number) t.get("amountSold")).longValue() : 0L);
            dto.setCreatedAt(t.get("createdAt") != null ? (Instant) t.get("createdAt") : null);

            dtos.add(dto);
        }

        Map<Long, List<CategoryResponseDto>> productCategoriesMap = loadCategoryDtosForProducts(productIds);
        Map<Long, ProductImage> primaryImageMap = loadPrimaryImageForProducts(productIds);
        Map<Long, DiscountBest> bestDiscounts = loadBestDiscountsForProducts(productIds, productCategoriesMap, productToSeller);

        for (ProductListDTO dto : dtos) {
            Long pid = dto.getId();

            List<CategoryResponseDto> catDtos = productCategoriesMap.get(pid);
            dto.setCategories(catDtos != null ? catDtos : Collections.emptyList());

            ProductImage primary = primaryImageMap.get(pid);
            if (primary != null) {
                dto.setPrimaryImageContentType(primary.getContentType());
                try {
                    String base64 = blobToBase64(primary.getImage());
                    if (base64 != null && primary.getContentType() != null) {
                        dto.setPrimaryImageDataUrl("data:" + primary.getContentType() + ";base64," + base64);
                    } else {
                        dto.setPrimaryImageDataUrl(null);
                    }
                } catch (RuntimeException ex) {
                    dto.setPrimaryImageDataUrl(null);
                }
            }

            DiscountBest best = bestDiscounts.get(pid);
            if (best != null && best.discountId != null) {
                dto.setBestDiscountId(best.discountId);
                dto.setBestDiscountPercentage(best.discountValue);
            } else {
                dto.setBestDiscountId(null);
                dto.setBestDiscountPercentage(null);
            }
        }

        long total = computeCount(filter, activeOnly);
        return new PageImpl<>(dtos, pageable, total);
    }

    private Map<Long, List<CategoryResponseDto>> loadCategoryDtosForProducts(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) return Collections.emptyMap();

        List<Object[]> rows = em.createQuery(
                "SELECT p.id, c FROM Product p JOIN p.categories c WHERE p.id IN :ids", Object[].class)
                .setParameter("ids", productIds)
                .getResultList();

        Map<Long, List<CategoryResponseDto>> tmp = new HashMap<>();
        for (Object[] r : rows) {
            Long pid = (Long) r[0];
            Category c = (Category) r[1];

            CategoryResponseDto dto = new CategoryResponseDto(
                    c.getId(),
                    c.getDescription(),
                    null,
                    c.isFeatured()
            );

            tmp.computeIfAbsent(pid, k -> new ArrayList<>()).add(dto);
        }
        return tmp;
    }

    private Map<Long, ProductImage> loadPrimaryImageForProducts(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) return Collections.emptyMap();
        List<ProductImage> imgs = em.createQuery(
                "SELECT pi FROM ProductImage pi WHERE pi.product.id IN :ids AND pi.isPrimary = true", ProductImage.class)
                .setParameter("ids", productIds)
                .getResultList();

        return imgs.stream().collect(Collectors.toMap(pi -> pi.getProduct().getId(), Function.identity(), (a, b) -> a));
    }

    private Map<Long, DiscountBest> loadBestDiscountsForProducts(List<Long> productIds,
                                                                 Map<Long, List<CategoryResponseDto>> productCategoriesMap,
                                                                 Map<Long, Long> productToSeller) {
        Map<Long, DiscountBest> out = new HashMap<>();
        if (productIds == null || productIds.isEmpty()) return out;

        for (Long pid : productIds) {
            BigDecimal bestVal = null;
            Long bestId = null;
            BigDecimal productPrice = productRepository.findById(pid).get().getPrice();

            try {
                List<Discount> prod = discountRepository.getHighestValueDiscountsForProduct(pid);
                if (prod != null && !prod.isEmpty()) {
                    Discount d = prod.get(0);
                    if (d != null && d.getValue() != null) {
                        bestVal = d.getValue();
                        bestId = d.getId();
                    }
                }
            } catch (Exception ignored) {}

            List<CategoryResponseDto> catDtos = productCategoriesMap.get(pid);
            if (catDtos != null && !catDtos.isEmpty()) {
                for (CategoryResponseDto crd : catDtos) {
                    try {
                        Long cid = crd.id();
                        List<Discount> catList = discountRepository.getHighestValueDiscountsForCategory(cid, productPrice);
                        if (catList != null && !catList.isEmpty()) {
                            Discount c = catList.get(0);
                            if (c != null && c.getValue() != null) {
                                if (bestVal == null || c.getValue().compareTo(bestVal) > 0) {
                                    bestVal = c.getValue();
                                    bestId = c.getId();
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }

            Long sellerId = productToSeller.get(pid);
            if (sellerId != null) {
                try {
                    List<Discount> sellerList = discountRepository.getHighestValueDiscountsForSeller(sellerId, productPrice);
                    if (sellerList != null && !sellerList.isEmpty()) {
                        Discount s = sellerList.get(0);
                        if (s != null && s.getValue() != null) {
                            if (bestVal == null || s.getValue().compareTo(bestVal) > 0) {
                                bestVal = s.getValue();
                                bestId = s.getId();
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

            out.put(pid, new DiscountBest(bestId, bestVal));
        }
        return out;
    }

    private String blobToBase64(Blob blob) {
        try {
            long length = blob.length();
            if (length == 0) return null;
            if (length > Integer.MAX_VALUE) {
                throw new InvalidFileException("Blob demasiado grande para convertir a byte[]");
            }
            byte[] bytes = blob.getBytes(1, (int) length);
            return Base64.getEncoder().encodeToString(bytes);
        } catch (SQLException e) {
            throw new ImageProcessingException("Error al leer los bytes del Blob", e);
        }
    }

    private long computeCount(ProductFilter filter, boolean activeOnly) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<Product> root = cq.from(Product.class);
        cq.select(cb.countDistinct(root.get("id")));

        List<Predicate> predicates = new ArrayList<>();

        if (filter != null && filter.getActive() != null) predicates.add(cb.equal(root.get("active"), filter.getActive()));
        if (activeOnly) {
            predicates.add(cb.isTrue(root.get("active")));

            Subquery<Long> stockExist = cq.subquery(Long.class);
            {
                Root<DigitalKey> dkSub = stockExist.from(DigitalKey.class);
                stockExist.select(dkSub.get("id"));
                stockExist.where(
                    cb.equal(dkSub.get("product"), root),
                    cb.equal(dkSub.get("status"), KeyStatus.AVAILABLE)
                );
            }
            predicates.add(cb.exists(stockExist));
        }

        if (filter != null) {
            if (filter.getProductIds() != null && !filter.getProductIds().isEmpty()) {
                predicates.add(root.get("id").in(filter.getProductIds()));
            }
            if (filter.getSellerId() != null) predicates.add(cb.equal(root.get("seller").get("id"), filter.getSellerId()));
            if (filter.getSellerIds() != null && !filter.getSellerIds().isEmpty()) {
                predicates.add(root.get("seller").get("id").in(filter.getSellerIds()));
            }
            if (filter.getTitle() != null && !filter.getTitle().isBlank())
                predicates.add(cb.like(cb.lower(root.get("title")), "%" + filter.getTitle().toLowerCase() + "%"));
            if (filter.getMinPrice() != null) predicates.add(cb.ge(root.get("price"), filter.getMinPrice()));
            if (filter.getMaxPrice() != null) predicates.add(cb.le(root.get("price"), filter.getMaxPrice()));
            if (filter.getPlatform() != null) predicates.add(cb.equal(cb.lower(root.get("platform")), filter.getPlatform().toLowerCase()));
            if (filter.getRegion() != null) predicates.add(cb.equal(cb.lower(root.get("region")), filter.getRegion().toLowerCase()));
            if (filter.getDeveloper() != null) predicates.add(cb.like(cb.lower(root.get("developer")), "%" + filter.getDeveloper().toLowerCase() + "%"));
            if (filter.getPublisher() != null) predicates.add(cb.like(cb.lower(root.get("publisher")), "%" + filter.getPublisher().toLowerCase() + "%"));
            if (filter.getReleaseDateFrom() != null) predicates.add(cb.greaterThanOrEqualTo(root.get("releaseDate"), filter.getReleaseDateFrom()));
            if (filter.getReleaseDateTo() != null) predicates.add(cb.lessThanOrEqualTo(root.get("releaseDate"), filter.getReleaseDateTo()));
            if (filter.getMinMetacriticScore() != null) predicates.add(cb.ge(root.get("metacriticScore"), filter.getMinMetacriticScore()));
            if (filter.getFeatured() != null) predicates.add(cb.equal(root.get("featured"), filter.getFeatured()));

            if (filter.getCategoryIds() != null && !filter.getCategoryIds().isEmpty()) {
                Subquery<Long> catExist = cq.subquery(Long.class);
                Root<Product> p2 = catExist.from(Product.class);
                Join<Product, Category> joinCat = p2.join("categories", JoinType.INNER);
                catExist.select(p2.get("id"));
                catExist.where(cb.equal(p2.get("id"), root.get("id")), joinCat.get("id").in(filter.getCategoryIds()));
                predicates.add(cb.exists(catExist));
            }

            if (filter.getMinAvgRating() != null) {
                Subquery<Long> avgExist = cq.subquery(Long.class);
                Root<Review> rAvg = avgExist.from(Review.class);
                Expression<Long> rProdId = rAvg.get("product").get("id");
                avgExist.select(rProdId);
                avgExist.where(cb.equal(rAvg.get("product"), root), cb.isTrue(rAvg.get("visible")));
                avgExist.groupBy(rProdId);
                avgExist.having(cb.greaterThanOrEqualTo(cb.avg(rAvg.get("rating").as(Double.class)), filter.getMinAvgRating()));
                predicates.add(cb.exists(avgExist));
            }

            if (filter.getMinRatingCount() != null) {
                Subquery<Long> rcExist = cq.subquery(Long.class);
                Root<Review> rCount = rcExist.from(Review.class);
                Expression<Long> rcProdId = rCount.get("product").get("id");
                rcExist.select(rcProdId);
                rcExist.where(cb.equal(rCount.get("product"), root), cb.isTrue(rCount.get("visible")));
                rcExist.groupBy(rcProdId);
                rcExist.having(cb.greaterThanOrEqualTo(cb.count(rCount.get("id")), filter.getMinRatingCount()));
                predicates.add(cb.exists(rcExist));
            }

            if (filter.getMinStock() != null) {
                Subquery<Long> stockExist = cq.subquery(Long.class);
                Root<DigitalKey> dk = stockExist.from(DigitalKey.class);
                Expression<Long> dkProdId = dk.get("product").get("id");
                stockExist.select(dkProdId);
                stockExist.where(cb.equal(dk.get("product"), root), cb.equal(dk.get("status"), KeyStatus.AVAILABLE));
                stockExist.groupBy(dkProdId);
                stockExist.having(cb.greaterThanOrEqualTo(cb.count(dk.get("id")), filter.getMinStock().longValue()));
                predicates.add(cb.exists(stockExist));
            }

            if (filter.getMinAmountSold() != null) {
                Subquery<Long> amtExist = cq.subquery(Long.class);
                Root<OrderItem> oi = amtExist.from(OrderItem.class);
                Expression<Long> oiProdId = oi.get("product").get("id");
                amtExist.select(oiProdId);
                amtExist.where(cb.equal(oi.get("product"), root));
                amtExist.groupBy(oiProdId);
                amtExist.having(cb.greaterThanOrEqualTo(cb.coalesce(cb.sum(oi.get("quantity").as(Long.class)), 0L), filter.getMinAmountSold()));
                predicates.add(cb.exists(amtExist));
            }

            if (filter.getMinDiscountPercent() != null) {
                Object rawObj = filter.getMinDiscountPercent();
                BigDecimal raw = null;
                if (rawObj instanceof BigDecimal) {
                    raw = (BigDecimal) rawObj;
                } else if (rawObj instanceof Number) {
                    raw = BigDecimal.valueOf(((Number) rawObj).doubleValue());
                } else {
                    try {
                        raw = new BigDecimal(String.valueOf(rawObj));
                    } catch (Exception ex) {
                        raw = null;
                    }
                }

                if (raw != null) {
                    BigDecimal minFrac = raw.compareTo(BigDecimal.ONE) > 0 ? raw.movePointLeft(2) : raw;
                    BigDecimal minAsWhole = minFrac.movePointRight(2);

                    Subquery<Long> discExist = cq.subquery(Long.class);
                    Root<Discount> d = discExist.from(Discount.class);
                    discExist.select(d.get("id"));

                    Expression<BigDecimal> dValue = d.get("value");
                    Predicate condType = cb.equal(d.get("type"), DiscountType.PERCENT);
                    Predicate condActive = cb.isTrue(d.get("active"));

                    Instant now = Instant.now();
                    Predicate condStarts = cb.lessThanOrEqualTo(d.get("startsAt"), now);
                    Predicate condEnds = cb.or(cb.isNull(d.get("endsAt")), cb.greaterThanOrEqualTo(d.get("endsAt"), now));

                    Predicate condValue = cb.greaterThanOrEqualTo(dValue, minAsWhole);

                    Predicate condProduct = cb.equal(d.get("targetProduct"), root);
                    Predicate condSeller = cb.equal(d.get("targetSeller"), root.get("seller"));

                    Subquery<Long> catMatch = discExist.subquery(Long.class);
                    Root<Product> p3 = catMatch.from(Product.class);
                    Join<Product, Category> cjoin = p3.join("categories", JoinType.INNER);
                    catMatch.select(cjoin.get("id"));
                    catMatch.where(cb.equal(p3.get("id"), root.get("id")), cb.equal(cjoin, d.get("targetCategory")));
                    Predicate condCategory = cb.exists(catMatch);

                    Predicate anyTarget = cb.or(condProduct, condSeller, condCategory);
                    discExist.where(cb.and(anyTarget, condType, condActive, condStarts, condEnds, condValue));

                    predicates.add(cb.exists(discExist));
                }
            }
        }

        cq.where(predicates.toArray(new Predicate[0]));
        Long result = em.createQuery(cq).getSingleResult();
        return result != null ? result : 0L;
    }

    private static class DiscountBest {
        Long discountId;
        BigDecimal discountValue;
        DiscountBest(Long id, BigDecimal val) { this.discountId = id; this.discountValue = val; }
    }
}
