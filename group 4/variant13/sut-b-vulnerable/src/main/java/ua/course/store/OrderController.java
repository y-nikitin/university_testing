package ua.course.store;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.net.URI;
import java.sql.SQLException;

@RestController
@RequestMapping("/orders")
public class OrderController {
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final OrderService service;
    private final OrderRepository orders;
    public OrderController(OrderService service, OrderRepository orders, org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.service = service;
        this.orders = orders;
    }
    @PostMapping
    public ResponseEntity<Order> create(@RequestBody OrderRequest request) throws SQLException {
        Order order = service.placeOrder(request);
        return ResponseEntity.created(URI.create("/orders/" + order.id())).body(order);
    }
    @GetMapping("/{id}")
    public Order get(@PathVariable("id") long id) throws SQLException {
        Order order = orders.findById(id);
        if (order == null) throw new ResourceNotFoundException("Замовлення не знайдено");
        return order;
    }
    @PostMapping("/{id}/return")
    public Order returnOrder(@PathVariable("id") long id) throws SQLException {
        return service.returnOrder(id);
    }

    @GetMapping("/search")
    public java.util.List<java.util.Map<String,Object>> search(@RequestParam String reference) {
        return jdbc.queryForList("SELECT id,reference,total,status FROM orders WHERE reference = '" + reference + "'");
    }
    public record EditInput(String reference, Long total, OrderStatus status) {}
    @PatchMapping("/{id}")
    public Order edit(@PathVariable long id, @RequestBody EditInput input) throws SQLException {
        Order old = get(id);
        jdbc.update("UPDATE orders SET reference=?,total=?,status=? WHERE id=?",
            input.reference()==null ? old.reference() : input.reference(),
            input.total()==null ? old.total() : input.total(),
            (input.status()==null ? old.status() : input.status()).name(),id);
        return get(id);
    }
}
