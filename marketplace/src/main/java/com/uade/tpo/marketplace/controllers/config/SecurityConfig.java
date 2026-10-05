package com.uade.tpo.marketplace.controllers.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

        private final JwtAuthenticationFilter jwtAuthFilter;
        private final AuthenticationProvider authenticationProvider;
        
        @Value("${application.cors.allowed-origins}")
        private List<String> allowedOrigins;

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                http
                                .cors(Customizer.withDefaults())
                                .csrf(AbstractHttpConfigurer::disable)
                                .exceptionHandling(errors -> errors
                                        .authenticationEntryPoint((request, response, ex) -> {
                                                response.setStatus(401);
                                                response.setContentType("application/json;charset=UTF-8");
                                                response.getWriter().write("{\"status\":401,\"message\":\"Debes iniciar sesión.\"}");
                                        })
                                        .accessDeniedHandler((request, response, ex) -> {
                                                response.setStatus(403);
                                                response.setContentType("application/json;charset=UTF-8");
                                                response.getWriter().write("{\"status\":403,\"message\":\"No tienes permisos.\"}");
                                        }))
                                .authorizeHttpRequests(req -> req.requestMatchers("/api/v1/auth/change-password").authenticated()
                                                .requestMatchers("/api/v1/auth/**")
                                                .permitAll()

                                                //PRODUCT QUERIES
                                                .requestMatchers(HttpMethod.GET, "/api/v1/products/filtered/active").permitAll()
                                                .requestMatchers(HttpMethod.GET, "/api/v1/products/filtered/all").hasAnyRole("SELLER", "ADMIN")

                                                //SELLER QUERIES
                                                .requestMatchers(HttpMethod.GET, "/api/v1/sellers/**").permitAll()

                                                //CATEGORIES
                                                .requestMatchers(HttpMethod.GET, "/categories/**").permitAll()
                                                .requestMatchers(HttpMethod.GET, "/categories/featured").permitAll()
                                                .requestMatchers(HttpMethod.POST, "/categories/**").hasRole("ADMIN")
                                                .requestMatchers(HttpMethod.PUT, "/categories/**").hasRole("ADMIN")
                                                .requestMatchers(HttpMethod.PATCH, "/categories/*/featured").hasRole("ADMIN")

                                                //DIGITAL KEYS
                                                .requestMatchers(HttpMethod.GET, "/digital_keys/product/*/count").hasAnyRole("SELLER", "ADMIN")
                                                .requestMatchers(HttpMethod.GET, "/digital_keys/product/**").hasAnyRole("SELLER", "ADMIN")
                                                .requestMatchers(HttpMethod.POST, "/digital_keys").hasRole("SELLER")

                                                //DISCOUNTS
                                                .requestMatchers(HttpMethod.GET, "/discounts/product/**").permitAll()
                                                .requestMatchers(HttpMethod.GET, "/discounts/buyer/active-coupons").hasAnyRole("BUYER", "SELLER")
                                                .requestMatchers(HttpMethod.GET, "/discounts/seller/me").hasRole("SELLER")
                                                .requestMatchers(HttpMethod.GET, "/discounts/admin/categories").hasRole("ADMIN")
                                                .requestMatchers(HttpMethod.GET, "/discounts/**").hasRole("ADMIN")
                                                .requestMatchers(HttpMethod.POST, "/discounts/validate", "/discounts/validate/bulk")
                                                        .hasAnyRole("BUYER", "SELLER", "ADMIN")
                                                
                                                .requestMatchers(HttpMethod.POST, "/discounts").hasAnyRole("SELLER", "ADMIN")
                                                .requestMatchers(HttpMethod.PUT, "/discounts/**").hasAnyRole("SELLER", "ADMIN")
                                                
                                                //ORDERS
                                                .requestMatchers(HttpMethod.GET, "/orders").hasRole("ADMIN")
                                                .requestMatchers(HttpMethod.GET, "/orders/*/keys").authenticated()
                                                .requestMatchers(HttpMethod.GET, "/orders/items/*/keys").authenticated()
                                                .requestMatchers(HttpMethod.GET, "/orders/my").hasAnyRole("BUYER", "SELLER")
                                                .requestMatchers(HttpMethod.GET, "/orders/seller/**").hasAnyRole("SELLER", "ADMIN")
                                                .requestMatchers(HttpMethod.GET, "/orders/admin/stats/extras").hasRole("ADMIN")
                                                .requestMatchers(HttpMethod.GET, "/orders/**").authenticated()
                                                .requestMatchers(HttpMethod.POST, "/orders").hasAnyRole("BUYER", "SELLER")
                                                .requestMatchers(HttpMethod.PATCH, "/orders/*/complete").hasRole("ADMIN")
                                                .requestMatchers(HttpMethod.PATCH, "/orders/*/status").hasRole("ADMIN")
                                                

                                                //PRODUCT IMAGES
                                                .requestMatchers(HttpMethod.GET, "/product_images/**").permitAll()
                                                .requestMatchers(HttpMethod.POST, "/product_images").hasAnyRole("SELLER", "ADMIN")
                                                .requestMatchers(HttpMethod.PUT, "/product_images/**").hasAnyRole("SELLER", "ADMIN")
                                                .requestMatchers(HttpMethod.PATCH, "/product_images/*/primary").hasAnyRole("SELLER", "ADMIN")
                                                .requestMatchers(HttpMethod.DELETE, "/product_images/**").hasAnyRole("SELLER", "ADMIN")

                                                //PRODUCTS
                                                .requestMatchers(HttpMethod.GET, "/products/**").permitAll()
                                                .requestMatchers(HttpMethod.GET, "/products/filter/extras").permitAll()
                                                .requestMatchers(HttpMethod.GET, "/products/*/detail").permitAll()
                                                .requestMatchers(HttpMethod.POST, "/products/**").hasRole("SELLER")
                                                .requestMatchers(HttpMethod.PUT, "/products/**").hasRole("SELLER")
                                                .requestMatchers(HttpMethod.PATCH, "/products/*/featured").hasRole("ADMIN")
                                                .requestMatchers(HttpMethod.PATCH, "/products/**").hasAnyRole("SELLER", "ADMIN")

                                                //REVIEWS
                                                .requestMatchers(HttpMethod.GET, "/reviews/latest") .permitAll()
                                                .requestMatchers(HttpMethod.GET, "/reviews/latest-page") .permitAll()
                                                .requestMatchers(HttpMethod.GET, "/reviews/product/**").permitAll()
                                                .requestMatchers(HttpMethod.GET, "/reviews/me").authenticated()
                                                .requestMatchers(HttpMethod.GET, "/reviews/order-item/**").hasAnyRole("BUYER", "SELLER")
                                                .requestMatchers(HttpMethod.GET, "/reviews").hasRole("ADMIN")
                                                .requestMatchers(HttpMethod.POST, "/reviews").hasAnyRole("BUYER", "SELLER")
                                                .requestMatchers(HttpMethod.PUT, "/reviews/**").hasAnyRole("BUYER", "SELLER")
                                                .requestMatchers(HttpMethod.DELETE, "/reviews/**").hasAnyRole("BUYER", "SELLER")
                                                .requestMatchers(HttpMethod.PATCH, "/reviews/*/visibility").hasRole("ADMIN")

                                                //USERS
                                                .requestMatchers(HttpMethod.GET, "/users/seller/*/detail").permitAll()
                                                .requestMatchers(HttpMethod.GET, "/users/me/profile").authenticated()
                                                .requestMatchers(HttpMethod.GET, "/users/seller/*/profile").authenticated()
                                                .requestMatchers(HttpMethod.GET, "/users/**").hasRole("ADMIN")
                                                .requestMatchers(HttpMethod.POST, "/users/**").authenticated()
                                                .requestMatchers(HttpMethod.PUT, "/users/**").authenticated()
                                                .requestMatchers(HttpMethod.PATCH, "/users/me/balance").hasRole("ADMIN")
                                                .requestMatchers(HttpMethod.POST, "/users/*/avatar").authenticated()
                                                .requestMatchers(HttpMethod.PUT, "/users/*/avatar").authenticated()
                                                .requestMatchers(HttpMethod.PATCH, "/users/*/active").hasRole("ADMIN")
                                                .requestMatchers(HttpMethod.DELETE, "/users/*/avatar").authenticated()





                                                .anyRequest()
                                                .authenticated())
                                .sessionManagement(session -> session.sessionCreationPolicy(STATELESS))
                                .authenticationProvider(authenticationProvider)
                                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }

        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration config = new CorsConfiguration();
                config.setAllowedOrigins(allowedOrigins);
                config.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
                config.setAllowedHeaders(List.of("*"));
                config.setExposedHeaders(List.of("Authorization","Content-Type"));
                config.setAllowCredentials(true);

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", config);
                return source;
                }

        }
