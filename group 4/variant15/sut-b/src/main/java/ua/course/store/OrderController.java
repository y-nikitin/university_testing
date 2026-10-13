package ua.course.store;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.net.URI;
import java.sql.SQLException;

@RestController
@RequestMapping("/orders")
public class OrderController {
    private final OrderService service;
    private final OrderRepository orders;
    public OrderController(OrderService service, OrderRepository orders) {
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
}
