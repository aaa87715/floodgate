package com.willie.ratelimit.order.adapter.in.web;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesRegex;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.willie.ratelimit.order.application.port.in.command.PlaceOrderCommand;
import com.willie.ratelimit.order.application.usecase.PlaceOrderUseCase;
import com.willie.ratelimit.order.domain.Order;

/**
 * 端到端走完 HTTP -> controller -> use case -> JPA -> H2，沒有任何 mock
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PlaceOrderUseCase placeOrderUseCase;

    @Test
    void placingOrderReturns201WithLocationHeader() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"item\":\"keyboard\",\"quantity\":2}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesRegex(".*/api/orders/\\d+$")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.item").value("keyboard"))
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.unitPrice").value(1500))
                .andExpect(jsonPath("$.totalPrice").value(3000))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    /** ProductNotFoundException 繼承 NotFoundException，由 GlobalExceptionHandler 轉成 404 */
    @Test
    void placingOrderForUnknownProductReturns404WithErrorCode() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"item\":\"no-such-product\",\"quantity\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ProductNotFound"))
                .andExpect(jsonPath("$.message").value(containsString("no-such-product")));
    }

    /**
     * quantity=0 違反 OrderRequest 的 @Positive，由 Spring 內建的
     * MethodArgumentNotValidException handler 擋下回 400
     * 注意 body 不是我們自訂的 ErrorResponse 格式 —— 這個不一致是已知的，還沒統一
     */
    @Test
    void placingOrderWithNonPositiveQuantityReturns400() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"item\":\"keyboard\",\"quantity\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void placingOrderWithBlankItemReturns400() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"item\":\"\",\"quantity\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void gettingExistingOrderReturns200() throws Exception {
        Order saved = placeOrderUseCase.placeOrder(new PlaceOrderCommand("mouse", 3));

        mockMvc.perform(get("/api/orders/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.item").value("mouse"))
                .andExpect(jsonPath("$.quantity").value(3))
                .andExpect(jsonPath("$.totalPrice").value(2400));
    }

    @Test
    void gettingMissingOrderReturns404() throws Exception {
        mockMvc.perform(get("/api/orders/{id}", 999_999L))
                .andExpect(status().isNotFound());
    }

    @Test
    void listingOrdersReturnsAllSavedOrders() throws Exception {
        placeOrderUseCase.placeOrder(new PlaceOrderCommand("keyboard", 1));
        placeOrderUseCase.placeOrder(new PlaceOrderCommand("mouse", 1));

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].item").value(
                        containsInAnyOrder("keyboard", "mouse")));
    }
}
