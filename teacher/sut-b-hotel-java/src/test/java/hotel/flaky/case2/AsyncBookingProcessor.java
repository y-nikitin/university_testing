package hotel.flaky.case2;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Демо-залежність для кейсу 2. У справжньому BookingService обробка синхронна
 * (бронювання одразу CONFIRMED), тому асинхронну поведінку з лекції імітуємо окремим класом:
 * бронювання спершу PENDING, а через processingDelayMs фоновий потік підтверджує його.
 */
public final class AsyncBookingProcessor implements AutoCloseable {
    public enum Status { PENDING, CONFIRMED }

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final Map<Long, Status> statuses = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();
    private final long processingDelayMs;

    public AsyncBookingProcessor(long processingDelayMs) {
        this.processingDelayMs = processingDelayMs;
    }

    public long initiateBooking(long roomId, String guest) {
        long id = ids.incrementAndGet();
        statuses.put(id, Status.PENDING);
        executor.schedule(() -> statuses.put(id, Status.CONFIRMED), processingDelayMs, TimeUnit.MILLISECONDS);
        return id;
    }

    public Status getBookingStatus(long id) {
        return statuses.get(id);
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
