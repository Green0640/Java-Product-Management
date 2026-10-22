package org.example.productmanagement.service;

import org.example.productmanagement.entity.Order;
import org.example.productmanagement.entity.OrderItem;
import org.example.productmanagement.entity.Product;
import org.example.productmanagement.repository.OrderRepository;
import org.example.productmanagement.repository.ProductRepository;
import org.example.productmanagement.service.exception.IdempotencyKeyReusedException;
import org.example.productmanagement.service.exception.OrderNotFoundException;
import org.example.productmanagement.service.exception.ProductNoStockException;
import org.example.productmanagement.service.exception.ProductNotFoundException;
import org.example.productmanagement.service.exception.ProductNotOnSaleException;
import org.example.productmanagement.service.model.CreateOrderCommand;
import org.example.productmanagement.service.model.OrderResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 訂單業務邏輯：建立訂單（含冪等處理、扣庫存、計算總額）與查詢訂單。
 */
@Service
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    public OrderService(OrderRepository orderRepository, ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
    }

    /**
     * 同一個 Idempotency-Key 重送相同內容時，直接回傳第一次建立的訂單，不會重複扣庫存；
     * 內容不同則拒絕。任何一項商品失敗，整筆交易回滾，已扣的庫存也會還原。
     */
    public OrderResult create(CreateOrderCommand command) {
        String requestHash = hash(command.items());

        Optional<Order> existing = orderRepository.findByIdempotencyKey(command.idempotencyKey());
        if (existing.isPresent()) {
            if (!existing.get().getRequestHash().equals(requestHash)) {
                throw new IdempotencyKeyReusedException(command.idempotencyKey());
            }
            return OrderResult.from(existing.get());
        }

        List<OrderItem> items = command.items().stream()
                .map(this::snapshot)
                .toList();
        // 扣庫存會鎖住商品列直到交易結束。一律依 productId 排序後再扣，
        // 每筆交易都以相同順序上鎖，就不會發生 [A, B] 與 [B, A] 互相等待對方鎖的 deadlock
        command.items().stream()
                .sorted(Comparator.comparing(CreateOrderCommand.Item::productId))
                .forEach(this::decreaseStock);
        Order order = Order.create(command.idempotencyKey(), requestHash, items);
        return OrderResult.from(orderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public OrderResult getById(UUID orderId) {
        return orderRepository.findById(orderId)
                .map(OrderResult::from)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    /**
     * 保存下單當下的商品名稱與單價。
     */
    private OrderItem snapshot(CreateOrderCommand.Item item) {
        Product product = findOnSaleProduct(item.productId());
        return new OrderItem(product.getId(), product.getName(), product.getPrice(), item.quantity());
    }

    private void decreaseStock(CreateOrderCommand.Item item) {
        if (!productRepository.decreaseStock(item.productId(), item.quantity())) {
            // 扣除失敗時重新讀取，區分是剛好被下架 / 刪除，還是庫存不足
            findOnSaleProduct(item.productId());
            throw new ProductNoStockException("Insufficient stock for product: " + item.productId());
        }
    }

    private Product findOnSaleProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        if (!product.isOnSale()) {
            throw new ProductNotOnSaleException("Product not on sale: " + productId);
        }
        return product;
    }

    /**
     * 請求內容的指紋，用來判斷同一個 Idempotency-Key 是否被用在不同內容上。
     */
    private static String hash(List<CreateOrderCommand.Item> items) {
        String canonical = items.stream()
                .map(item -> item.productId() + ":" + item.quantity())
                .collect(Collectors.joining(","));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
