using Microsoft.AspNetCore.Mvc;
namespace Rental;

[ApiController]
[Route("rentals")]
public class RentalsController : ControllerBase {
    private readonly RentalService service;
    private readonly IRentalRepository rentals;
    public RentalsController(RentalService service, IRentalRepository rentals) {
        this.service = service;
        this.rentals = rentals;
    }
    public record ReturnInput(int OverdueDays);

    [HttpPost]
    public ActionResult<RentalOrder> Create(RentalRequest request) {
        var saved = service.Rent(request);
        return CreatedAtAction(nameof(Get), new { id = saved.Id }, saved);
    }

    [HttpGet("{id:long}")]
    public ActionResult<RentalOrder> Get(long id) {
        return rentals.FindById(id) ?? throw new ResourceNotFoundException("Оренду не знайдено");
    }

    [HttpPost("{id:long}/return")]
    public ActionResult<RentalOrder> Return(long id, ReturnInput request) {
        return service.ReturnRental(id, request.OverdueDays);
    }
}
