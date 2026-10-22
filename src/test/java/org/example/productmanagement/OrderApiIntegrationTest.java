package org.example.productmanagement;

import org.example.productmanagement.dto.ErrorCode;
import org.example.productmanagement.entity.Product;
import org.example.productmanagement.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 啟動完整應用程式並連真的 H2（記憶體模式，不碰 ./data 的開發資料庫）：
 * 驗證 HTTP → Service → JPA 整條路線，包含交易回滾與冪等。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:order-api-test")
@AutoConfigureMockMvc
class OrderApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void createOrderThenFetchIt() throws Exception {
        Long keyboard = newProduct("Keyboard", "1990", 10);
        Long mouse = newProduct("Mouse", "499.50", 5);

        String body = createOrder("it-create", keyboard, 2, mouse, 1)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.totalAmount").value(4479.5))
                .andReturn().getResponse().getContentAsString();
        String orderId = body.replaceAll(".*\"orderId\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/orders/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].productName").value("Keyboard"))
                .andExpect(jsonPath("$.items[1].subtotal").value(499.5))
                .andExpect(jsonPath("$.totalAmount").value(4479.5));

        assertThat(stockOf(keyboard)).isEqualTo(8);
        assertThat(stockOf(mouse)).isEqualTo(4);
    }

    @Test
    void replayWithSameKeyReturnsSameOrderAndDecreasesStockOnce() throws Exception {
        Long keyboard = newProduct("Keyboard", "1990", 10);
        Long mouse = newProduct("Mouse", "499.50", 5);

        String first = createOrder("it-replay", keyboard, 2, mouse, 1)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String replay = createOrder("it-replay", keyboard, 2, mouse, 1)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat(replay).isEqualTo(first);
        assertThat(stockOf(keyboard)).isEqualTo(8);
        assertThat(stockOf(mouse)).isEqualTo(4);
    }

    @Test
    void sameKeyWithDifferentContentReturns422() throws Exception {
        Long keyboard = newProduct("Keyboard", "1990", 10);
        Long mouse = newProduct("Mouse", "499.50", 5);

        createOrder("it-reuse", keyboard, 2, mouse, 1).andExpect(status().isCreated());

        createOrder("it-reuse", keyboard, 3, mouse, 1)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value(ErrorCode.IDEMPOTENCY_KEY_REUSED.name()));
        assertThat(stockOf(keyboard)).isEqualTo(8);
    }

    @Test
    void insufficientStockRollsBackEarlierItems() throws Exception {
        Long keyboard = newProduct("Keyboard", "1990", 10);
        Long mouse = newProduct("Mouse", "499.50", 1);

        createOrder("it-rollback", keyboard, 2, mouse, 2)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PRODUCT_INSUFFICIENT_STOCK.name()));

        // 第一項（鍵盤）原本已經扣了 2，交易回滾後要還原
        assertThat(stockOf(keyboard)).isEqualTo(10);
        assertThat(stockOf(mouse)).isEqualTo(1);
    }

    @Test
    void failedRequestCanBeRetriedWithSameKey() throws Exception {
        Long keyboard = newProduct("Keyboard", "1990", 10);
        Long mouse = newProduct("Mouse", "499.50", 1);

        createOrder("it-retry", keyboard, 1, mouse, 2).andExpect(status().isBadRequest());
        productRepository.save(new Product(mouse, "Mouse", null, new BigDecimal("499.50"), 5, true));

        createOrder("it-retry", keyboard, 1, mouse, 2).andExpect(status().isCreated());
        assertThat(stockOf(keyboard)).isEqualTo(9);
        assertThat(stockOf(mouse)).isEqualTo(3);
    }

    private ResultActions createOrder(String key, Long productA, int quantityA, Long productB, int quantityB) throws Exception {
        return mockMvc.perform(post("/orders")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"items":[{"productId":%d,"quantity":%d},{"productId":%d,"quantity":%d}]}
                        """.formatted(productA, quantityA, productB, quantityB)));
    }

    private Long newProduct(String name, String price, int stock) {
        return productRepository.save(new Product(null, name, null, new BigDecimal(price), stock, true)).getId();
    }

    private int stockOf(Long productId) {
        return productRepository.findById(productId).orElseThrow().getStock();
    }
}
