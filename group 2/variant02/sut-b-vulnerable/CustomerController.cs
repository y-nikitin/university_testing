using Microsoft.AspNetCore.Mvc;
namespace Delivery;

[ApiController]
[Route("customers")]
public class CustomerController : ControllerBase {
    private readonly ICustomerRepository repository;
    public CustomerController(ICustomerRepository repository) { this.repository = repository; }
    public record Input(string Code, bool Regular = false);

    [HttpPost]
    public ActionResult<Customer> Create(Input input) {
        var saved = repository.Create(new Customer(0, input.Code, input.Regular));
        return Created($"/customers/{saved.Id}", saved);
    }

    [HttpGet("{id:long}")]
    public ActionResult<Customer> Get(long id) =>
        repository.FindById(id) ?? throw new ResourceNotFoundException("Клієнта не знайдено");
}
