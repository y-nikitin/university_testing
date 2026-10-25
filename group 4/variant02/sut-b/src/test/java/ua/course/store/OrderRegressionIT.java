package ua.course.store;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@TestMethodOrder(MethodOrderer.Random.class)
class OrderRegressionIT {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6-alpine");

    static JdbcTemplate jdbc;
    static ProductRepository product;
    static OrderRepository orders;
    static OrderService service;
    static long orderId;

    @BeforeAll
    static void prepareDatabase() {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").executeInTransaction(false).load().migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        product = new ProductRepository(jdbc);
        orders = new OrderRepository(jdbc);
        service = new OrderService(product, orders, (item, quantity) -> true);
    }
    private Product item() throws Exception {
        return product.create(new Product(0, UUID.randomUUID().toString(), 10000, 5, false));
    }
    private OrderRequest request(Product item) {
        return new OrderRequest(UUID.randomUUID().toString(), item.id(), 1, false, false);
    }

    @Test
    @DisplayName("Замовлення зберігається у БД")
    void createOrder() throws Exception {
        var saved = service.placeOrder(request(item()));
        orderId = saved.id();
        assertEquals(OrderStatus.CONFIRMED, saved.status());
        assertEquals(saved, orders.findById(saved.id()));
    }
    @Test
    @DisplayName("Повернення товару змінює статус")
    void returnOrder() throws Exception {
        var returned = service.returnOrder(orderId);
        assertEquals(OrderStatus.RETURNED, returned.status());
        assertEquals(returned, orders.findById(orderId));
    }
    @Test
    @DisplayName("Картка товару зберігає залишок")
    void productStock() throws Exception {
        var saved = product.create(new Product(0, "REGRESSION-EQUIPMENT", 10000, 5, false));
        assertEquals(5, product.findById(saved.id()).stock());
    }
    @Test
    @DisplayName("Картка товару зберігає ціну одиниці")
    void productRate() throws Exception {
        var saved = product.create(new Product(0, "REGRESSION-EQUIPMENT", 15000, 3, false));
        assertEquals(15000, product.findById(saved.id()).unitPrice());
    }
    @Test
    @DisplayName("Замовлення після зовнішнього дозволу")
    void orderAfterPolicyCheck() throws Exception {
        var item = item();
        var request = request(item);
        var delayed = new OrderService(product, orders, (value, quantity) -> {
            try {
                Thread.sleep(Long.getLong("regression.policyDelayMs", 200L));
                return true;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Перевірку перервано", exception);
            }
        });
        try (var executor = Executors.newSingleThreadExecutor()) {
            var operation = CompletableFuture.supplyAsync(() -> {
                try { return delayed.placeOrder(request); }
                catch (Exception exception) { throw new CompletionException(exception); }
            }, executor);
            Thread.sleep(50);
            var saved = orders.findOrders(item.id(), OrderStatus.CONFIRMED);
            assertEquals(1, saved.size());
            assertEquals(request.reference(), saved.getFirst().reference());
            assertEquals(10000, saved.getFirst().total());
        }
    }
    @Test
    @DisplayName("Вибірка містить дві замовлення товару")
    void orderSelection() throws Exception {
        var item = item();
        var prefix = UUID.randomUUID().toString().substring(0, 8);
        var references = List.of(prefix + "-A", prefix + "-B");
        var fixtureOrder = Boolean.getBoolean("regression.reverseFixture") ? references.reversed() : references;
        for (var reference : fixtureOrder) service.placeOrder(new OrderRequest(reference, item.id(), 1, false, false));
        var actual = jdbc.queryForList("SELECT r.reference FROM orders r JOIN order_items i ON i.order_id=r.id WHERE i.product_id=?", String.class, item.id());
        assertEquals(references, actual);
    }
    @Test
    @DisplayName("Замовлення зберігає суму й позицію")
    void orderAmount() throws Exception {
        var item = item();
        var saved = service.placeOrder(request(item));
        var stored = orders.findById(saved.id());
        assertEquals(10000, stored.total());
        assertEquals(item.id(), stored.item().productId());
        assertEquals(1, stored.item().quantity());
        assertEquals(10000, stored.item().unitPrice());
    }
    @Test
    @DisplayName("Нестача товару не створює замовлення")
    void stockRejection() throws Exception {
        var item = item();
        var request = new OrderRequest(UUID.randomUUID().toString(), item.id(), 6, false, false);
        assertThrows(OrderRejectedException.class, () -> service.placeOrder(request));
        assertTrue(orders.findOrders(item.id(), OrderStatus.CONFIRMED).isEmpty());
    }
    @Test
    @DisplayName("Зовнішня відмова не створює замовлення")
    void policyRejection() throws Exception {
        var item = item();
        var denied = new OrderService(product, orders, (value, quantity) -> false);
        assertThrows(OrderRejectedException.class, () -> denied.placeOrder(request(item)));
        assertTrue(orders.findOrders(item.id(), OrderStatus.CONFIRMED).isEmpty());
    }
    @Test
    @DisplayName("Повторне повернення не змінює замовлення")
    void repeatedReturn() throws Exception {
        var saved = service.placeOrder(request(item()));
        var returned = service.returnOrder(saved.id());
        assertThrows(OrderRejectedException.class, () -> service.returnOrder(saved.id()));
        assertEquals(returned, orders.findById(saved.id()));
    }
}
