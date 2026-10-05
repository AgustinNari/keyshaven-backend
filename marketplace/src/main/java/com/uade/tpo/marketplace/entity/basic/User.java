package com.uade.tpo.marketplace.entity.basic;


import java.math.BigDecimal;
import java.sql.Blob;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hibernate.annotations.CreationTimestamp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.uade.tpo.marketplace.entity.enums.Role;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "users",
        indexes = {
            @Index(columnList = "first_name"),
            @Index(columnList = "last_name"),
            @Index(columnList = "email")
        })
public class User implements UserDetails {

    public User(String displayName, String firstName, String lastName, String email, String password, Role role, String phone, String sellerDescription, String country) {
        this.displayName = displayName;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.password = password;
        this.role = role;
        this.phone = phone;
        this.sellerDescription = sellerDescription;
        this.country = country;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "display_name", nullable=false, length = 100, unique = true)
    private String displayName;

    @Column(name = "avatar")
    private Blob avatar;

    @Column(name = "avatar_content_type", length = 100)
    private String avatarContentType;

    @Column(name = "seller_description")
    private String sellerDescription;

    @Column(name = "first_name", nullable=false, length = 50)
    private String firstName;

    @Column(name = "last_name", nullable=false, length = 50)
    private String lastName;

    @Column(nullable=false, length = 150, unique = true)
    private String email;

    @Column(nullable=false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;
    
    @Column(nullable=false)
    private String phone;
    

    @Column(nullable=false)
    private String country;

    @Column(name = "buyer_balance")
    private BigDecimal buyerBalance;

    @OneToMany(mappedBy = "targetBuyer", fetch = FetchType.LAZY)
    private Set<Discount> coupons = new HashSet<>();


    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", updatable = false)
    @CreationTimestamp
    private Instant createdAt;
    
    @Column(name = "last_login")
    private Instant lastLogin;

    @OneToMany(mappedBy="buyer", fetch = FetchType.LAZY)
    private Set<Review> buyerReviews = new HashSet<>();

    @OneToMany(mappedBy="seller", fetch = FetchType.LAZY)
    private Set<Review> sellerReviews = new HashSet<>();

    @OneToMany(mappedBy = "buyer", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Order> orders;

    @OneToMany(mappedBy = "seller",
                fetch = FetchType.LAZY,         
                orphanRemoval = false)    
    private Set<Product> products = new HashSet<>();


    @OneToMany(mappedBy = "targetSeller", fetch = FetchType.LAZY)
    private Set<Discount> discounts = new HashSet<>();

    public void addProduct(Product p) {
        products.add(p);
        p.setSeller(this);
    }

    public void removeProduct(Product p) {
        products.remove(p);
        p.setSeller(null);
    }

    @Override
    @JsonIgnore
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    @JsonIgnore
    public boolean isEnabled() { return active; }

    @Override
    @JsonIgnore
    public String getUsername() {
        return this.email;
    }

}