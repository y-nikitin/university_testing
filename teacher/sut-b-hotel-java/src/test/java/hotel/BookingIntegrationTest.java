package hotel;

import org.junit.jupiter.api.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.UUID;
import static hotel.BookingStatus.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookingIntegrationTest {
    private Database database;
    private Connection connection;
    private String schema;
    private JdbcRoomRepository rooms;
    private JdbcBookingRepository repository;
    private BookingPolicy policy;
    private BookingService service;
    private Room room;

    @BeforeEach
    void prepareEnvironment() throws Exception {
        database = Database.fromEnvironment();
        connection = database.connect();
        // Окрема схема кожного тесту дозволяє безпечно перевіряти порожній findAll().
        schema = "lecture_" + UUID.randomUUID().toString().replace("-", "");
        try (var sql = connection.createStatement()) {
            sql.execute("CREATE SCHEMA " + schema);
        }
        connection.setSchema(schema);
        try (var sql = connection.createStatement()) {
            sql.execute(Files.readString(Path.of("migrations/V1__create_tables.sql")));
            sql.execute(Files.readString(Path.of("migrations/V2__seed_statuses.sql")));
        }
        rooms = new JdbcRoomRepository(connection);
        repository = new JdbcBookingRepository(connection);
        policy = mock(BookingPolicy.class);
        service = new BookingService(rooms, repository, policy);
        room = rooms.create("101", 2, 120_000);
    }

    @AfterEach
    void cleanEnvironment() throws Exception {
        if (connection == null) return;
        try {
            // Після помилки SQL завершуємо можливу незавершену транзакцію міграції.
            try (var sql = connection.createStatement()) { sql.execute("ROLLBACK"); }
            connection.setSchema("public");
            try (var sql = connection.createStatement()) {
                sql.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        } finally { connection.close(); }
    }

    private Booking testBooking() {
        return new Booking(0, "B1", room.id(), 2, 2, 240_000, CONFIRMED);
    }

    private BookingRequest request(int nights) {
        return new BookingRequest("B1", room.id(), nights, 2);
    }

    private Booking readStored(long id) throws Exception {
        try (var fresh = database.connect()) {
            fresh.setSchema(schema);
            return new JdbcBookingRepository(fresh).findById(id);
        }
    }

    @Test
    @DisplayName("Тест 1: запис і читання всіх полів бронювання")
    void createAndRead() throws Exception {
        Booking booking = testBooking();

        long id = repository.create(booking);
        Booking saved = repository.findById(id);

        assertTrue(id > 0);
        assertEquals(new Booking(id, "B1", room.id(), 2, 2, 240_000, CONFIRMED), saved);
        assertEquals(saved, readStored(id));
    }

    @Test
    @DisplayName("Тест 2: видалення підтверджується повторним читанням")
    void deleteAndReadMissing() throws Exception {
        long id = repository.create(testBooking());

        repository.delete(id);

        assertNull(readStored(id));
    }

    private List<Long> queryData() throws Exception {
        Room other = rooms.create("202", 2, 100_000);
        long a = repository.create(testBooking());
        repository.create(new Booking(0, "B", room.id(), 1, 1, 120_000, CANCELLED));
        repository.create(new Booking(0, "C", other.id(), 1, 1, 100_000, CONFIRMED));
        repository.create(new Booking(0, "D", other.id(), 1, 1, 100_000, CANCELLED));
        return List.of(a);
    }

    @Test
    @DisplayName("Тест 3: обидві умови фільтра мають виконуватися")
    void queryIncludesOnlyBothMatchingConditions() throws Exception {
        List<Long> expected = queryData();
        var result = repository.findBookings(room.id(), CONFIRMED);
        assertEquals(expected, result.stream().map(Booking::id).toList());
        assertEquals(List.of(readStored(expected.getFirst())), result);
    }

    @Test
    @DisplayName("Фільтр повертає всі збіги за зростанням ідентифікатора")
    void queryReturnsSeveralMatches() throws Exception {
        long first = queryData().getFirst();
        long second = repository.create(new Booking(0, "E", room.id(), 3, 1, 360_000, CONFIRMED));
        assertEquals(List.of(readStored(first), readStored(second)), repository.findBookings(room.id(), CONFIRMED));
    }

    @Test
    @DisplayName("Фільтр повертає порожній список за відсутності збігів")
    void queryReturnsNoMatches() throws Exception {
        queryData();
        Room empty = rooms.create("303", 1, 50_000);
        assertTrue(repository.findBookings(empty.id(), CONFIRMED).isEmpty());
    }

    @Test
    @DisplayName("Тест 21: успішний сервісний сценарій з реальною БД")
    void serviceCreatesPersistedBooking() throws Exception {
        when(policy.isAllowed(room, 2, 2)).thenReturn(true);
        Booking created = service.createBooking(request(2));
        Booking stored = readStored(created.id());
        assertEquals(CONFIRMED, stored.status());
        assertEquals(240_000, stored.total());
        assertEquals(created, stored);
        verify(policy).isAllowed(room, 2, 2);
        verifyNoMoreInteractions(policy);
    }

    @Test
    @DisplayName("Слайд 25: відмова політики не створює записів")
    void policyDenialLeavesDatabaseEmpty() throws Exception {
        when(policy.isAllowed(room, 2, 2)).thenReturn(false);
        assertThrows(BookingRejectedException.class, () -> service.createBooking(request(2)));
        assertTrue(repository.findAll().isEmpty());
        verify(policy).isAllowed(room, 2, 2);
    }

    @Test
    @DisplayName("Помилка зовнішньої залежності не створює бронювання")
    void policyFailureLeavesDatabaseEmpty() throws Exception {
        var failure = new IllegalStateException("Зовнішня перевірка недоступна");
        when(policy.isAllowed(room, 2, 2)).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> service.createBooking(request(2))));
        assertTrue(repository.findAll().isEmpty());
    }

    @Test
    @DisplayName("Знижка починається із сьомої ночі та зберігається в БД")
    void discountBoundary() throws Exception {
        when(policy.isAllowed(eq(room), anyInt(), eq(2))).thenReturn(true);
        Booking six = service.createBooking(request(6));
        Booking seven = service.createBooking(new BookingRequest("B2", room.id(), 7, 2));
        assertEquals(720_000, readStored(six.id()).total());
        assertEquals(798_000, readStored(seven.id()).total());
    }

    @Test
    @DisplayName("Скасування змінює статус у БД без зовнішньої перевірки")
    void cancellationPersistsStatus() throws Exception {
        long id = repository.create(testBooking());
        Booking cancelled = service.cancelBooking(id);
        assertEquals(CANCELLED, readStored(id).status());
        assertEquals(240_000, readStored(id).total());
        assertEquals(cancelled, readStored(id));
        verifyNoInteractions(policy);
    }
}
