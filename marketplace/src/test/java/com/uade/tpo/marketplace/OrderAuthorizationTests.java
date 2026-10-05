package com.uade.tpo.marketplace;

import com.uade.tpo.marketplace.entity.basic.Order;
import com.uade.tpo.marketplace.entity.basic.User;
import com.uade.tpo.marketplace.exceptions.UnauthorizedException;
import com.uade.tpo.marketplace.repository.interfaces.IOrderRepository;
import com.uade.tpo.marketplace.extra.mappers.OrderMapper;
import com.uade.tpo.marketplace.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderAuthorizationTests {
    @Mock IOrderRepository orders;
    @Mock OrderMapper mapper;
    @InjectMocks OrderService service;

    @Test void unrelatedUserCannotReadOrderOrKeys() {
        User buyer = new User(); buyer.setId(1L);
        Order order = new Order(); order.setBuyer(buyer);
        when(orders.findOrderWithItemsAndKeys(10L)).thenReturn(Optional.of(order));
        assertThrows(UnauthorizedException.class, () -> service.getOrderById(10L, 2L));
        verifyNoInteractions(mapper);
    }

    @Test void buyerCanReadOwnOrder() {
        User buyer = new User(); buyer.setId(1L);
        Order order = new Order(); order.setBuyer(buyer);
        when(orders.findOrderWithItemsAndKeys(10L)).thenReturn(Optional.of(order));
        when(mapper.toResponse(order, true)).thenReturn(mock(com.uade.tpo.marketplace.entity.dto.response.OrderResponseDto.class));
        service.getOrderById(10L, 1L);
        verify(mapper).toResponse(order, true);
    }
}
