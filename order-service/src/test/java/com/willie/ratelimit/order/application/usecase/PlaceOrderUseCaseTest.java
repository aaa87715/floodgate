package com.willie.ratelimit.order.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.willie.ratelimit.order.application.exception.ProductNotFoundException;
import com.willie.ratelimit.order.application.port.in.command.PlaceOrderCommand;
import com.willie.ratelimit.order.application.port.out.LoadProductPort;
import com.willie.ratelimit.order.application.port.out.SaveOrderPort;
import com.willie.ratelimit.order.domain.Order;
import com.willie.ratelimit.order.domain.OrderStatus;


@ExtendWith(MockitoExtension.class)
class PlaceOrderUseCaseTest {

    private static final BigDecimal KEYBOARD_PRICE = new BigDecimal("1500");

    @Mock
    private LoadProductPort loadProductPort;

    @Mock
    private SaveOrderPort saveOrderPort;

    @InjectMocks
    private PlaceOrderUseCase placeOrderUseCase;

    @Test
    void placingOrderForUnknownProductIsRejected() {
        PlaceOrderCommand command = new PlaceOrderCommand("unknown-item", 1);

        assertThatThrownBy(() -> placeOrderUseCase.placeOrder(command))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("unknown-item");
    }

  
    @Test
    void unknownProductLeavesNothingSaved() {
        PlaceOrderCommand command = new PlaceOrderCommand("unknown-item", 1);

        assertThatThrownBy(() -> placeOrderUseCase.placeOrder(command))
                .isInstanceOf(ProductNotFoundException.class);

        then(saveOrderPort).shouldHaveNoInteractions();
    }

    @Test
    void placedOrderCarriesPriceFromProductPort() {
        given(loadProductPort.findPrice("keyboard")).willReturn(Optional.of(KEYBOARD_PRICE));
        // save() 原封不動回傳它收到的 Order（真實實作會回傳帶 id 的版本，這支測試不在意 id）
        given(saveOrderPort.save(any(Order.class))).willAnswer(invocation -> invocation.getArgument(0));

        Order result = placeOrderUseCase.placeOrder(new PlaceOrderCommand("keyboard", 2));

        assertThat(result.getItem()).isEqualTo("keyboard");
        assertThat(result.getQuantity()).isEqualTo(2);
        assertThat(result.getUnitPrice()).isEqualByComparingTo("1500");
        assertThat(result.totalPrice()).isEqualByComparingTo("3000");
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CREATED);
    }


    @Test
    void savedOrderIsBuiltFromCommandAndLookedUpPrice() {
        given(loadProductPort.findPrice("keyboard")).willReturn(Optional.of(KEYBOARD_PRICE));

        placeOrderUseCase.placeOrder(new PlaceOrderCommand("keyboard", 3));

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        then(saveOrderPort).should().save(captor.capture());

        Order saved = captor.getValue();
        assertThat(saved.getId()).isNull();
        assertThat(saved.getItem()).isEqualTo("keyboard");
        assertThat(saved.getQuantity()).isEqualTo(3);
        assertThat(saved.getUnitPrice()).isEqualByComparingTo("1500");
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(saved.getCreatedAt()).isNotNull();
    }
}
