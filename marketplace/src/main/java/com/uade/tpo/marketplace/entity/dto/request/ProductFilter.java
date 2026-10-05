package com.uade.tpo.marketplace.entity.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class ProductFilter {
    private List<Long> productIds;
    private Long sellerId;
    private Boolean active;
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    private List<Long> sellerIds;
    private String title;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private String platform;
    private String region;
    private LocalDate releaseDateFrom;
    private LocalDate releaseDateTo;
    private String developer;
    private String publisher;
    private Integer minMetacriticScore;
    private Integer maxMetacriticScore;
    private Boolean featured;
    private List<Long> categoryIds;


    private Double minAvgRating;
    private Long minRatingCount;
    private Integer minStock;
    private Long minAmountSold;
    private BigDecimal minDiscountPercent;


    public List<Long> getProductIds() {
        return productIds;
    }
    public void setProductIds(List<Long> productIds) {
        this.productIds = productIds;
    }
    public Long getSellerId() {
        return sellerId;
    }
    public void setSellerId(Long sellerId) {
        this.sellerId = sellerId;
    }
    public List<Long> getSellerIds() {
        return sellerIds;
    }
    public void setSellerIds(List<Long> sellerIds) {
        this.sellerIds = sellerIds;
    }
    public String getTitle() {
        return title;
    }
    public void setTitle(String title) {
        this.title = title;
    }
    public BigDecimal getMinPrice() {
        return minPrice;
    }
    public void setMinPrice(BigDecimal minPrice) {
        this.minPrice = minPrice;
    }
    public BigDecimal getMaxPrice() {
        return maxPrice;
    }
    public void setMaxPrice(BigDecimal maxPrice) {
        this.maxPrice = maxPrice;
    }
    public String getPlatform() {
        return platform;
    }
    public void setPlatform(String platform) {
        this.platform = platform;
    }
    public String getRegion() {
        return region;
    }
    public void setRegion(String region) {
        this.region = region;
    }
    public LocalDate getReleaseDateFrom() {
        return releaseDateFrom;
    }
    public void setReleaseDateFrom(LocalDate releaseDateFrom) {
        this.releaseDateFrom = releaseDateFrom;
    }
    public LocalDate getReleaseDateTo() {
        return releaseDateTo;
    }
    public void setReleaseDateTo(LocalDate releaseDateTo) {
        this.releaseDateTo = releaseDateTo;
    }
    public String getDeveloper() {
        return developer;
    }
    public void setDeveloper(String developer) {
        this.developer = developer;
    }
    public String getPublisher() {
        return publisher;
    }
    public void setPublisher(String publisher) {
        this.publisher = publisher;
    }
    public Integer getMinMetacriticScore() {
        return minMetacriticScore;
    }
    public void setMinMetacriticScore(Integer minMetacriticScore) {
        this.minMetacriticScore = minMetacriticScore;
    }
    public Integer getMaxMetacriticScore() {
        return maxMetacriticScore;
    }
    public void setMaxMetacriticScore(Integer maxMetacriticScore) {
        this.maxMetacriticScore = maxMetacriticScore;
    }
    public Boolean getFeatured() {
        return featured;
    }
    public void setFeatured(Boolean featured) {
        this.featured = featured;
    }
    public List<Long> getCategoryIds() {
        return categoryIds;
    }
    public void setCategoryIds(List<Long> categoryIds) {
        this.categoryIds = categoryIds;
    }
    public Double getMinAvgRating() {
        return minAvgRating;
    }
    public void setMinAvgRating(Double minAvgRating) {
        this.minAvgRating = minAvgRating;
    }
    public Long getMinRatingCount() {
        return minRatingCount;
    }
    public void setMinRatingCount(Long minRatingCount) {
        this.minRatingCount = minRatingCount;
    }
    public Integer getMinStock() {
        return minStock;
    }
    public void setMinStock(Integer minStock) {
        this.minStock = minStock;
    }
    public Long getMinAmountSold() {
        return minAmountSold;
    }
    public void setMinAmountSold(Long minAmountSold) {
        this.minAmountSold = minAmountSold;
    }
    public BigDecimal getMinDiscountPercent() {
        return minDiscountPercent;
    }
    public void setMinDiscountPercent(BigDecimal minDiscountPercent) {
        this.minDiscountPercent = minDiscountPercent;
    }


}