package ua.course.delivery;

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
class ShipmentRegressionIT {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6-alpine");

    static JdbcTemplate jdbc;
    static CustomerRepository customers;
    static ParcelRepository parcels;
    static ShipmentRepository shipments;
    static ShippingService service;
    static long shipmentId;

    @BeforeAll
    static void prepareDatabase() {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .executeInTransaction(false)
                .load()
                .migrate();
        var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(source);
        customers = new CustomerRepository(jdbc);
        parcels = new ParcelRepository(jdbc);
        shipments = new ShipmentRepository(jdbc);
        service = new ShippingService(customers, parcels, shipments, (customer, parcel, distance) -> true);
    }

    private Customer customer(boolean regular) throws Exception {
        return customers.create(new Customer(0, UUID.randomUUID().toString(), regular));
    }

    private Parcel parcel() throws Exception {
        return parcels.create(new Parcel(0, UUID.randomUUID().toString(), 1000, 0, false));
    }

    private ShipmentRequest request(Customer customer, Parcel parcel, boolean coupon) {
        return new ShipmentRequest(UUID.randomUUID().toString(), customer.id(), parcel.id(), 100, false, false, coupon);
    }

    @Test
    @DisplayName("Відправлення зберігається у БД")
    void createShipment() throws Exception {
        var saved = service.createShipment(request(customer(false), parcel(), false));
        shipmentId = saved.id();
        assertEquals(saved, shipments.findById(saved.id()));
        assertEquals(ShipmentStatus.Registered, saved.status());
    }

    @Test
    @DisplayName("Передача відправлення в доставку")
    void dispatchShipment() throws Exception {
        var saved = service.dispatch(shipmentId);
        assertEquals(ShipmentStatus.Dispatched, saved.status());
        assertEquals(saved, shipments.findById(shipmentId));
    }

    @Test
    @DisplayName("Картка посилки зберігає вагу")
    void parcelWeight() throws Exception {
        var saved = parcels.create(new Parcel(0, "REGRESSION-PARCEL", 1000, 0, false));
        assertEquals(1000, parcels.findById(saved.id()).weightGrams());
    }

    @Test
    @DisplayName("Картка посилки зберігає оголошену вартість")
    void parcelValue() throws Exception {
        var saved = parcels.create(new Parcel(0, "REGRESSION-PARCEL", 2000, 10000, false));
        assertEquals(10000, parcels.findById(saved.id()).declaredValue());
    }

    @Test
    @DisplayName("Відправлення після зовнішнього дозволу")
    void shipmentAfterPolicyCheck() throws Exception {
        var customer = customer(false);
        var request = request(customer, parcel(), false);
        var delayedService = new ShippingService(customers, parcels, shipments, (person, value, distance) -> {
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
                try {
                    return delayedService.createShipment(request);
                } catch (Exception exception) {
                    throw new CompletionException(exception);
                }
            }, executor);
            Thread.sleep(50);
            var saved = shipments.findShipments(customer.id(), ShipmentStatus.Registered);
            assertEquals(1, saved.size());
            assertEquals(request.reference(), saved.getFirst().reference());
            assertEquals(7000, saved.getFirst().total());
        }
    }

    @Test
    @DisplayName("Вибірка містить два відправлення клієнта")
    void shipmentSelection() throws Exception {
        var customer = customer(false);
        String prefix = UUID.randomUUID().toString().substring(0, 8);
        var references = List.of(prefix + "-A", prefix + "-B");
        var fixtureOrder = Boolean.getBoolean("regression.reverseFixture") ? references.reversed() : references;
        for (String reference : fixtureOrder) {
            service.createShipment(new ShipmentRequest(reference, customer.id(), parcel().id(), 100, false, false, false));
        }
        var actual = jdbc.queryForList("SELECT reference FROM shipments WHERE customer_id=?", String.class, customer.id());
        assertEquals(references, actual);
    }

    @Test
    @DisplayName("Завершення доставки зберігає стан")
    void delivery() throws Exception {
        var saved = service.createShipment(request(customer(false), parcel(), false));
        service.dispatch(saved.id());
        var delivered = service.deliver(saved.id());
        assertEquals(ShipmentStatus.Delivered, delivered.status());
        assertEquals(delivered, shipments.findById(saved.id()));
    }

    @Test
    @DisplayName("Завершення до відправлення відхиляється")
    void prematureDelivery() throws Exception {
        var saved = service.createShipment(request(customer(false), parcel(), false));
        assertThrows(ShipmentRejectedException.class, () -> service.deliver(saved.id()));
        assertEquals(saved, shipments.findById(saved.id()));
    }

    @Test
    @DisplayName("Зовнішня відмова не створює відправлення")
    void policyRejection() throws Exception {
        var customer = customer(false);
        var request = request(customer, parcel(), false);
        var denied = new ShippingService(customers, parcels, shipments, (c, p, distance) -> false);
        assertThrows(ShipmentRejectedException.class, () -> denied.createShipment(request));
        assertTrue(shipments.findShipments(customer.id(), ShipmentStatus.Registered).isEmpty());
    }

    @Test
    @DisplayName("Повторне скасування не змінює відправлення")
    void repeatedCancellation() throws Exception {
        var saved = service.createShipment(request(customer(false), parcel(), false));
        var cancelled = service.cancel(saved.id());
        assertThrows(ShipmentRejectedException.class, () -> service.cancel(saved.id()));
        assertEquals(cancelled, shipments.findById(saved.id()));
    }
}
