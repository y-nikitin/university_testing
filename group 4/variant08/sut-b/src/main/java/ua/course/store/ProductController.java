package ua.course.store;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.net.URI;
import java.sql.SQLException;

@RestController
@RequestMapping("/products")
public class ProductController {
    private final ProductRepository products;
    public ProductController(ProductRepository products) { this.products = products; }
    public record ProductInput(String sku, long unitPrice, int stock, boolean onSale) {}
    @PostMapping
    public ResponseEntity<Product> create(@RequestBody ProductInput request) throws SQLException {
        Product product = products.create(new Product(0, request.sku(), request.unitPrice(), request.stock(), request.onSale()));
        return ResponseEntity.created(URI.create("/products/" + product.id())).body(product);
    }
    @GetMapping("/{id}")
    public Product get(@PathVariable("id") long id) throws SQLException {
        Product product = products.findById(id);
        if (product == null) throw new ResourceNotFoundException("Товар не знайдено");
        return product;
    }
}
