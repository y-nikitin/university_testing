package ua.course.rental;

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
class RentalRegressionIT {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6-alpine");

    static JdbcTemplate jdbc;
    static EquipmentRepository equipment;
    static RentalRepository rentals;
    static RentalService service;
    static long rentalId;

    @BeforeAll
    static void prepareDatabase() {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").executeInTransaction(false).load().migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        equipment = new EquipmentRepository(jdbc);
        rentals = new RentalRepository(jdbc);
        service = new RentalService(equipment, rentals, (item, quantity, days) -> true);
    }
    private Equipment item() throws Exception {
        return equipment.create(new Equipment(0, UUID.randomUUID().toString(), 10000, 5));
    }
    private RentalRequest request(Equipment item) {
        return new RentalRequest(UUID.randomUUID().toString(), item.id(), 1, 2, false);
    }

    @Test
    @DisplayName("Оренда зберігається у БД")
    void createRental() throws Exception {
        var saved = service.rent(request(item()));
        rentalId = saved.id();
        assertEquals(RentalStatus.Active, saved.status());
        assertEquals(saved, rentals.findById(saved.id()));
    }
    @Test
    @DisplayName("Повернення обладнання змінює статус")
    void returnRental() throws Exception {
        var returned = service.returnRental(rentalId);
        assertEquals(RentalStatus.Returned, returned.status());
        assertEquals(0, returned.lateFee());
        assertEquals(returned, rentals.findById(rentalId));
    }
    @Test
    @DisplayName("Картка обладнання зберігає залишок")
    void equipmentStock() throws Exception {
        var saved = equipment.create(new Equipment(0, "REGRESSION-EQUIPMENT", 10000, 5));
        assertEquals(5, equipment.findById(saved.id()).stock());
    }
    @Test
    @DisplayName("Картка обладнання зберігає добову ціну")
    void equipmentRate() throws Exception {
        var saved = equipment.create(new Equipment(0, "REGRESSION-EQUIPMENT", 15000, 3));
        assertEquals(15000, equipment.findById(saved.id()).dailyRate());
    }
    @Test
    @DisplayName("Оренда після зовнішнього дозволу")
    void rentalAfterPolicyCheck() throws Exception {
        var item = item();
        var request = request(item);
        var delayed = new RentalService(equipment, rentals, (value, quantity, days) -> {
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
                try { return delayed.rent(request); }
                catch (Exception exception) { throw new CompletionException(exception); }
            }, executor);
            Thread.sleep(50);
            var saved = rentals.findRentals(item.id(), RentalStatus.Active);
            assertEquals(1, saved.size());
            assertEquals(request.reference(), saved.getFirst().reference());
            assertEquals(20000, saved.getFirst().total());
        }
    }
    @Test
    @DisplayName("Вибірка містить дві оренди обладнання")
    void rentalSelection() throws Exception {
        var item = item();
        var prefix = UUID.randomUUID().toString().substring(0, 8);
        var references = List.of(prefix + "-A", prefix + "-B");
        var fixtureOrder = Boolean.getBoolean("regression.reverseFixture") ? references.reversed() : references;
        for (var reference : fixtureOrder) service.rent(new RentalRequest(reference, item.id(), 1, 2, false));
        var actual = jdbc.queryForList("SELECT r.reference FROM rentals r JOIN rental_items i ON i.rental_id=r.id WHERE i.equipment_id=?", String.class, item.id());
        assertEquals(references, actual);
    }
    @Test
    @DisplayName("Оренда зберігає суму й позицію")
    void rentalAmount() throws Exception {
        var item = item();
        var saved = service.rent(request(item));
        var stored = rentals.findById(saved.id());
        assertEquals(20000, stored.total());
        assertEquals(item.id(), stored.item().equipmentId());
        assertEquals(1, stored.item().quantity());
        assertEquals(10000, stored.item().dailyRate());
    }
    @Test
    @DisplayName("Нестача обладнання не створює оренду")
    void stockRejection() throws Exception {
        var item = item();
        var request = new RentalRequest(UUID.randomUUID().toString(), item.id(), 6, 2, false);
        assertThrows(RentalRejectedException.class, () -> service.rent(request));
        assertTrue(rentals.findRentals(item.id(), RentalStatus.Active).isEmpty());
    }
    @Test
    @DisplayName("Зовнішня відмова не створює оренду")
    void policyRejection() throws Exception {
        var item = item();
        var denied = new RentalService(equipment, rentals, (value, quantity, days) -> false);
        assertThrows(RentalRejectedException.class, () -> denied.rent(request(item)));
        assertTrue(rentals.findRentals(item.id(), RentalStatus.Active).isEmpty());
    }
    @Test
    @DisplayName("Повторне повернення не змінює оренду")
    void repeatedReturn() throws Exception {
        var saved = service.rent(request(item()));
        var returned = service.returnRental(saved.id());
        assertThrows(RentalRejectedException.class, () -> service.returnRental(saved.id()));
        assertEquals(returned, rentals.findById(saved.id()));
    }
}
