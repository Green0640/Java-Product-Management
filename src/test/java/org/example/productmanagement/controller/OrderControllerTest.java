package org.example.productmanagement.controller;

import org.example.productmanagement.dto.ErrorCode;
import org.example.productmanagement.entity.OrderStatus;
import org.example.productmanagement.service.OrderService;
import org.example.productmanagement.service.exception.IdempotencyKeyConflictException;
import org.example.productmanagement.service.exception.IdempotencyKeyReusedException;
import org.example.productmanagement.service.exception.OrderNotFoundException;
import org.example.productmanagement.service.exception.ProductNoStockException;
import org.example.productmanagement.service.model.CreateOrderCommand;
import org.example.productmanagement.service.model.OrderResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 只啟動 Web 層，OrderService 用 mock 取代：驗證 /orders 的 HTTP 轉換、Header 與輸入驗證、錯誤代碼。
 */
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    private static final String VALID_BODY = """
            {"items":[{"productId":1,"quantity":2},{"productId":2,"quantity":1}]}
            """;

    private static final UUID ORDER_ID = UUID.fromString("3f2a9c4e-8b1d-4e6f-9a7c-2d5b8e1f0a34");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @Test
    void createReturns201WithItemsAndTotal() throws Exception {
        when(orderService.create(any())).thenReturn(sampleOrder());

        mockMvc.perform(createRequest("key-1", VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(ORDER_ID.toString()))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].productName").value("Keyboard"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(1990))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].subtotal").value(3980))
                .andExpect(jsonPath("$.totalAmount").value(4479.5))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void headerAndItemsAreConvertedToCommand() throws Exception {
        when(orderService.create(any())).thenReturn(sampleOrder());

        mockMvc.perform(createRequest("key-1", VALID_BODY)).andExpect(status().isCreated());

        ArgumentCaptor<CreateOrderCommand> captor = ArgumentCaptor.forClass(CreateOrderCommand.class);
        verify(orderService).create(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new CreateOrderCommand("key-1", List.of(
                new CreateOrderCommand.Item(1L, 2),
                new CreateOrderCommand.Item(2L, 1))));
    }

    @Test
    void missingIdempotencyKeyReturns400() throws Exception {
        assertRejected(mockMvc.perform(post("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY)), ErrorCode.MISSING_HEADER);
    }

    @Test
    void blankIdempotencyKeyReturns400() throws Exception {
        assertRejected(mockMvc.perform(createRequest("  ", VALID_BODY)), ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void tooLongIdempotencyKeyReturns400() throws Exception {
        assertRejected(mockMvc.perform(createRequest("k".repeat(256), VALID_BODY)), ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void emptyItemsReturns400() throws Exception {
        assertRejected(mockMvc.perform(createRequest("key-1", """
                {"items":[]}
                """)), ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void missingItemsReturns400() throws Exception {
        assertRejected(mockMvc.perform(createRequest("key-1", "{}")), ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void zeroQuantityReturns400() throws Exception {
        assertRejected(mockMvc.perform(createRequest("key-1", """
                {"items":[{"productId":1,"quantity":0}]}
                """)), ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void missingProductIdReturns400() throws Exception {
        assertRejected(mockMvc.perform(createRequest("key-1", """
                {"items":[{"quantity":1}]}
                """)), ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void nullItemReturns400() throws Exception {
        assertRejected(mockMvc.perform(createRequest("key-1", """
                {"items":[null]}
                """)), ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void reusedKeyWithDifferentContentReturns422() throws Exception {
        when(orderService.create(any())).thenThrow(new IdempotencyKeyReusedException("key-1"));

        assertError(mockMvc.perform(createRequest("key-1", VALID_BODY)), ErrorCode.IDEMPOTENCY_KEY_REUSED);
    }

    @Test
    void concurrentSameKeyReturns409() throws Exception {
        when(orderService.create(any())).thenThrow(new IdempotencyKeyConflictException("key-1", null));

        assertError(mockMvc.perform(createRequest("key-1", VALID_BODY)), ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
    }

    @Test
    void insufficientStockReturns400() throws Exception {
        when(orderService.create(any())).thenThrow(new ProductNoStockException("no stock"));

        assertError(mockMvc.perform(createRequest("key-1", VALID_BODY)), ErrorCode.PRODUCT_INSUFFICIENT_STOCK);
    }

    @Test
    void getByIdReturns200() throws Exception {
        when(orderService.getById(ORDER_ID)).thenReturn(sampleOrder());

        mockMvc.perform(get("/orders/" + ORDER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(ORDER_ID.toString()))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.totalAmount").value(4479.5));
    }

    @Test
    void getUnknownOrderReturns404() throws Exception {
        UUID unknown = UUID.randomUUID();
        when(orderService.getById(unknown)).thenThrow(new OrderNotFoundException(unknown));

        assertError(mockMvc.perform(get("/orders/" + unknown)), ErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    void getWithInvalidUuidReturns400() throws Exception {
        assertError(mockMvc.perform(get("/orders/not-a-uuid")), ErrorCode.INVALID_PARAMETER);
        verify(orderService, never()).getById(any());
    }

    private static MockHttpServletRequestBuilder createRequest(String idempotencyKey, String body) {
        return post("/orders")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private void assertRejected(ResultActions result, ErrorCode expected) throws Exception {
        assertError(result, expected);
        verify(orderService, never()).create(any());
    }

    private static void assertError(ResultActions result, ErrorCode expected) throws Exception {
        result.andExpect(status().is(expected.getStatus().value()))
                .andExpect(jsonPath("$.code").value(expected.name()))
                .andExpect(jsonPath("$.message").value(expected.getMessage()));
    }

    private static OrderResult sampleOrder() {
        return new OrderResult(ORDER_ID, OrderStatus.CREATED, List.of(
                new OrderResult.Item(1L, "Keyboard", new BigDecimal("1990"), 2, new BigDecimal("3980")),
                new OrderResult.Item(2L, "Mouse", new BigDecimal("499.50"), 1, new BigDecimal("499.50"))
        ), new BigDecimal("4479.50"), Instant.parse("2026-10-22T08:00:00Z"));
    }
}
