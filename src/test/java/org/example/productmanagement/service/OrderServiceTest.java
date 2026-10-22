package org.example.productmanagement.service;

import org.example.productmanagement.entity.OrderStatus;
import org.example.productmanagement.entity.Product;
import org.example.productmanagement.service.exception.IdempotencyKeyReusedException;
import org.example.productmanagement.service.exception.OrderNotFoundException;
import org.example.productmanagement.service.exception.ProductNoStockException;
import org.example.productmanagement.service.exception.ProductNotFoundException;
import org.example.productmanagement.service.exception.ProductNotOnSaleException;
import org.example.productmanagement.service.model.CreateOrderCommand;
import org.example.productmanagement.service.model.OrderResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 不啟動 Spring、不連資料庫：用記憶體版的 Repository 單獨測試下單流程。
 * 交易回滾（失敗時還原庫存）需要真的資料庫，放在 OrderApiIntegrationTest 驗證。
 */
class OrderServiceTest {

    private InMemoryProductRepository productRepository;
    private InMemoryOrderRepository orderRepository;
    private OrderService service;

    private Long keyboardId;
    private Long mouseId;

    @BeforeEach
    void setUp() {
        productRepository = new InMemoryProductRepository();
        orderRepository = new InMemoryOrderRepository();
        service = new OrderService(orderRepository, productRepository);

        keyboardId = productRepository.save(new Product(null, "Keyboard", null, new BigDecimal("1990"), 10, true)).getId();
        mouseId = productRepository.save(new Product(null, "Mouse", null, new BigDecimal("499.50"), 5, true)).getId();
    }

    @Test
    void createCalculatesSubtotalsAndTotal() {
        OrderResult result = service.create(command("key-1", item(keyboardId, 2), item(mouseId, 3)));

        assertThat(result.orderId()).isNotNull();
        assertThat(result.status()).isEqualTo(OrderStatus.CREATED);
        assertThat(result.items()).hasSize(2);
        assertThat(result.items().get(0).subtotal()).isEqualByComparingTo("3980");
        assertThat(result.items().get(1).subtotal()).isEqualByComparingTo("1498.50");
        assertThat(result.totalAmount()).isEqualByComparingTo("5478.50");
    }

    @Test
    void createSnapshotsProductNameAndPrice() {
        OrderResult result = service.create(command("key-1", item(keyboardId, 1)));

        OrderResult.Item item = result.items().get(0);
        assertThat(item.productId()).isEqualTo(keyboardId);
        assertThat(item.productName()).isEqualTo("Keyboard");
        assertThat(item.unitPrice()).isEqualByComparingTo("1990");
        assertThat(item.quantity()).isEqualTo(1);
    }

    @Test
    void itemsKeepRequestOrderEvenThoughStockIsDecreasedInProductIdOrder() {
        OrderResult result = service.create(command("key-1", item(mouseId, 1), item(keyboardId, 1)));

        assertThat(result.items()).extracting(OrderResult.Item::productName).containsExactly("Mouse", "Keyboard");
    }

    @Test
    void productTakenOffSaleBeforeStockIsDecreasedIsRejectedAsNotOnSale() {
        // 模擬「讀取商品之後、扣庫存之前」商品剛好被下架
        InMemoryProductRepository racingRepository = new InMemoryProductRepository() {
            @Override
            public boolean decreaseStock(Long productId, int quantity) {
                Product product = findById(productId).orElseThrow();
                save(new Product(product.getId(), product.getName(), product.getDescription(),
                        product.getPrice(), product.getStock(), false));
                return super.decreaseStock(productId, quantity);
            }
        };
        Long id = racingRepository.save(new Product(null, "Keyboard", null, new BigDecimal("1990"), 10, true)).getId();
        OrderService racingService = new OrderService(orderRepository, racingRepository);

        assertThatThrownBy(() -> racingService.create(command("key-1", item(id, 1))))
                .isInstanceOf(ProductNotOnSaleException.class);
        assertThat(racingRepository.findById(id).orElseThrow().getStock()).isEqualTo(10);
    }

    @Test
    void createDecreasesStock() {
        service.create(command("key-1", item(keyboardId, 2), item(mouseId, 5)));

        assertThat(stockOf(keyboardId)).isEqualTo(8);
        assertThat(stockOf(mouseId)).isZero();
    }

    @Test
    void sameKeyAndSameItemsReturnsOriginalOrderWithoutDecreasingStockAgain() {
        OrderResult first = service.create(command("key-1", item(keyboardId, 2)));

        OrderResult replay = service.create(command("key-1", item(keyboardId, 2)));

        assertThat(replay).isEqualTo(first);
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(stockOf(keyboardId)).isEqualTo(8);
    }

    @Test
    void sameKeyWithDifferentItemsIsRejected() {
        service.create(command("key-1", item(keyboardId, 2)));

        assertThatThrownBy(() -> service.create(command("key-1", item(keyboardId, 3))))
                .isInstanceOf(IdempotencyKeyReusedException.class);
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(stockOf(keyboardId)).isEqualTo(8);
    }

    @Test
    void differentKeysCreateSeparateOrders() {
        OrderResult first = service.create(command("key-1", item(keyboardId, 1)));
        OrderResult second = service.create(command("key-2", item(keyboardId, 1)));

        assertThat(second.orderId()).isNotEqualTo(first.orderId());
        assertThat(stockOf(keyboardId)).isEqualTo(8);
    }

    @Test
    void insufficientStockIsRejected() {
        assertThatThrownBy(() -> service.create(command("key-1", item(mouseId, 6))))
                .isInstanceOf(ProductNoStockException.class);
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    void productNotOnSaleIsRejected() {
        Long hiddenId = productRepository.save(new Product(null, "Hidden", null, BigDecimal.TEN, 10, false)).getId();

        assertThatThrownBy(() -> service.create(command("key-1", item(hiddenId, 1))))
                .isInstanceOf(ProductNotOnSaleException.class);
        assertThat(stockOf(hiddenId)).isEqualTo(10);
    }

    @Test
    void unknownProductIsRejected() {
        assertThatThrownBy(() -> service.create(command("key-1", item(999L, 1))))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void getByIdReturnsCreatedOrder() {
        OrderResult created = service.create(command("key-1", item(keyboardId, 1)));

        assertThat(service.getById(created.orderId())).isEqualTo(created);
    }

    @Test
    void getByIdOfUnknownOrderThrowsNotFound() {
        assertThatThrownBy(() -> service.getById(UUID.randomUUID())).isInstanceOf(OrderNotFoundException.class);
    }

    private int stockOf(Long productId) {
        return productRepository.findById(productId).orElseThrow().getStock();
    }

    private static CreateOrderCommand command(String key, CreateOrderCommand.Item... items) {
        return new CreateOrderCommand(key, List.of(items));
    }

    private static CreateOrderCommand.Item item(Long productId, int quantity) {
        return new CreateOrderCommand.Item(productId, quantity);
    }
}
