package ua.course.delivery;

import java.net.URI;
import java.sql.SQLException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/shipments")
public class ShipmentsController {
    private final ShippingService service;
    private final ShipmentRepository repository;
    public ShipmentsController(ShippingService service, ShipmentRepository repository) {
        this.service = service;
        this.repository = repository;
    }
    @PostMapping
    public ResponseEntity<Shipment> create(@RequestBody ShipmentRequest input) throws SQLException {
        var saved = service.createShipment(input);
        return ResponseEntity.created(URI.create("/shipments/" + saved.id())).body(saved);
    }
    @GetMapping("/{id}")
    public Shipment get(@PathVariable long id) throws SQLException {
        var saved = repository.findById(id);
        if (saved == null) throw new ResourceNotFoundException("Відправлення не знайдено");
        return saved;
    }
    @PostMapping("/{id}/dispatch")
    public Shipment dispatch(@PathVariable long id) throws SQLException { return service.dispatch(id); }
    @PostMapping("/{id}/deliver")
    public Shipment deliver(@PathVariable long id) throws SQLException { return service.deliver(id); }
    @PostMapping("/{id}/cancel")
    public Shipment cancel(@PathVariable long id) throws SQLException { return service.cancel(id); }
}
