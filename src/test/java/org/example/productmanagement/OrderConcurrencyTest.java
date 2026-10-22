package org.example.productmanagement;

import org.example.productmanagement.entity.Product;
import org.example.productmanagement.repository.ProductRepository;
import org.example.productmanagement.service.OrderService;
import org.example.productmanagement.service.exception.ProductNoStockException;
import org.example.productmanagement.service.model.CreateOrderCommand;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 多條執行緒同時下單，連真的 H2（記憶體模式）：驗證不會超賣，也不會因為鎖的順序不同而 deadlock。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:order-concurrency-test")
class OrderConcurrencyTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void concurrentOrdersNeverOversell() throws Exception {
        Long product = newProduct("Limited", 10);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger soldOut = new AtomicInteger();

        List<Throwable> unexpected = runConcurrently(30, i -> {
            try {
                orderService.create(command(item(product, 1)));
                succeeded.incrementAndGet();
            } catch (ProductNoStockException e) {
                soldOut.incrementAndGet();
            }
        });

        assertThat(unexpected).isEmpty();
        assertThat(succeeded).hasValue(10);
        assertThat(soldOut).hasValue(20);
        assertThat(stockOf(product)).isZero();
    }

    @Test
    void ordersLockingProductsInOppositeOrderDoNotDeadlock() throws Exception {
        Long a = newProduct("A", 1000);
        Long b = newProduct("B", 1000);
        int threads = 8;
        int ordersPerThread = 30;

        // 一半的執行緒送 [A, B]，另一半送 [B, A]：若照請求順序上鎖，就會互相等待對方手上的鎖
        List<Throwable> unexpected = runConcurrently(threads, i -> {
            for (int n = 0; n < ordersPerThread; n++) {
                orderService.create(i % 2 == 0
                        ? command(item(a, 1), item(b, 1))
                        : command(item(b, 1), item(a, 1)));
            }
        });

        assertThat(unexpected).isEmpty();
        assertThat(stockOf(a)).isEqualTo(1000 - threads * ordersPerThread);
        assertThat(stockOf(b)).isEqualTo(1000 - threads * ordersPerThread);
    }

    /**
     * 讓所有執行緒等在同一個起跑點後同時開始，回傳執行過程中丟出的非預期例外。
     */
    private static List<Throwable> runConcurrently(int threads, IntConsumer task) throws InterruptedException {
        CountDownLatch start = new CountDownLatch(1);
        Queue<Throwable> errors = new ConcurrentLinkedQueue<>();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            int index = i;
            executor.submit(() -> {
                try {
                    start.await();
                    task.accept(index);
                } catch (Throwable e) {
                    errors.add(e);
                }
            });
        }
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
        return List.copyOf(errors);
    }

    private Long newProduct(String name, int stock) {
        return productRepository.save(new Product(null, name, null, new BigDecimal("100"), stock, true)).getId();
    }

    private int stockOf(Long productId) {
        return productRepository.findById(productId).orElseThrow().getStock();
    }

    private static CreateOrderCommand command(CreateOrderCommand.Item... items) {
        return new CreateOrderCommand(UUID.randomUUID().toString(), List.of(items));
    }

    private static CreateOrderCommand.Item item(Long productId, int quantity) {
        return new CreateOrderCommand.Item(productId, quantity);
    }
}
