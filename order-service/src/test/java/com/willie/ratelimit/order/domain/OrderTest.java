package com.willie.ratelimit.order.domain;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.willie.ratelimit.order.common.InvalidOrderException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
/**
    案例：
 * 1. place() 品名空白   -> 丟 InvalidOrderException
 * 2. place() 數量 <= 0  -> 丟 InvalidOrderException
 * 3. place() 單價 <= 0  -> 丟 InvalidOrderException
 * 4. place() 正常       -> status 是 CREATED、id 是 null、createdAt 不是 null
 * 5. totalPrice()       -> 單價 1500 x 數量 2 = 3000
 * 
 * 6. cancel()           -> status 變成 CANCELLED
 * 7. cancel() 連兩次    -> 第二次丟 IllegalStateException
 */
class OrderTest {

    @Test
    void emptyProductName() {
        String item = " ";
        int quantity = 1;
        BigDecimal unitPrice = BigDecimal.valueOf(1000);
        assertThatThrownBy(() -> Order.place(item, quantity, unitPrice))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("Item must not be blank");
    }
    @ParameterizedTest
    @ValueSource(ints = {0, -1, -100})
    void nonPositiveQuantityIsRejected(int quantity) {
        BigDecimal unitPrice = BigDecimal.valueOf(1000);

        assertThatThrownBy(() -> Order.place("Product A", quantity, unitPrice))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("Quantity must be positive");
    }
    @Test
    void nonPositiveUnitPrice() {
        String item = "Product A";
        int quantity = 1;
        BigDecimal unitPrice = BigDecimal.valueOf(0);
        assertThatThrownBy(() -> Order.place(item, quantity, unitPrice))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("Unit price must be positive");
    }
    @Test 
    void placeOrderSuccessfully() {
        String item = "Product A";
        int quantity = 2;
        BigDecimal unitPrice = BigDecimal.valueOf(1500);
        Order order = Order.place(item, quantity, unitPrice);
        // Assert
        assertThat(order.getId()).isNull();                        
        assertThat(order.getItem()).isEqualTo(item);
        assertThat(order.getQuantity()).isEqualTo(quantity);
        assertThat(order.getUnitPrice()).isEqualByComparingTo(unitPrice);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.getCreatedAt()).isNotNull();
    }   
    @Test 
    void calculateTotalPrice() {
        String item = "Product A";
        int quantity = 2;
        BigDecimal unitPrice = BigDecimal.valueOf(1500);
        assertThat(Order.place(item, quantity, unitPrice).totalPrice())
                .isEqualByComparingTo("3000");
    }
    @Test 
    void cancelOrderSuccessfully() {
        String item = "Product A";
        int quantity = 2;
        BigDecimal unitPrice = BigDecimal.valueOf(1500);
        Order order = Order.place(item, quantity, unitPrice);
        order.cancel();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }
    @Test
    void cancelOrderTwice() {
        String item = "Product A";
        int quantity = 2;
        BigDecimal unitPrice = BigDecimal.valueOf(1500);
        Order order = Order.place(item, quantity, unitPrice);
        order.cancel();
        assertThatThrownBy(() -> order.cancel())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Order is already cancelled");    
    }

}
