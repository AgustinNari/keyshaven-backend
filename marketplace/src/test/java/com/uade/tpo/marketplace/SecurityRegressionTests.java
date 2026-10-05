package com.uade.tpo.marketplace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uade.tpo.marketplace.entity.basic.User;
import com.uade.tpo.marketplace.entity.enums.Role;
import com.uade.tpo.marketplace.controllers.config.JwtService;
import com.uade.tpo.marketplace.repository.interfaces.IUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityRegressionTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired IUserRepository users;
    @Autowired JwtService jwt;
    @Autowired PasswordEncoder passwords;

    private User buyer() {
        return users.save(new User("regression-buyer", "Test", "Buyer", "regression@example.test",
            passwords.encode("TestPassword123!"), Role.BUYER, "123456789", null, "Argentina"));
    }

    @Test void publicRegistrationCannotCreateAdmin() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("displayName", "test-admin", "firstName", "Test",
                "lastName", "Admin", "email", "admin@example.test", "password", "TestPassword123!",
                "role", "ADMIN", "phone", "123456789", "country", "Argentina"))))
            .andExpect(status().isBadRequest());
    }

    @Test void malformedTokenReturns401() throws Exception {
        mvc.perform(get("/users/me/profile").header("Authorization", "Bearer invalid.jwt"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
    }

    @Test void anonymousProfileReturns401() throws Exception {
        mvc.perform(get("/users/me/profile")).andExpect(status().isUnauthorized());
    }

    @Test void buyerCannotListEveryoneOrders() throws Exception {
        mvc.perform(get("/orders").header("Authorization", "Bearer " + jwt.generateToken(buyer())))
            .andExpect(status().isForbidden());
    }

    @Test void disabledUserCannotUseExistingJwt() throws Exception {
        User user = buyer();
        String token = jwt.generateToken(user);
        user.setActive(false);
        users.saveAndFlush(user);
        mvc.perform(get("/users/me/profile").header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized());
    }

    @Test void orderValidatesNestedQuantity() throws Exception {
        mvc.perform(post("/orders").header("Authorization", "Bearer " + jwt.generateToken(buyer()))
            .contentType(MediaType.APPLICATION_JSON).content("{\"items\":[{\"productId\":1,\"quantity\":0}]}"))
            .andExpect(status().isBadRequest());
    }
}
