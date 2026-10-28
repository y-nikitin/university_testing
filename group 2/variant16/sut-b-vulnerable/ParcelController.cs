using Microsoft.AspNetCore.Mvc;
namespace Delivery;

[ApiController]
[Route("parcels")]
public class ParcelController : ControllerBase {
    private readonly IParcelRepository repository;
    public ParcelController(IParcelRepository repository) { this.repository = repository; }
    public record Input(string Code, int WeightGrams, long DeclaredValue);

    [HttpPost]
    public ActionResult<Parcel> Create(Input input) {
        var saved = repository.Create(new Parcel(0, input.Code, input.WeightGrams, input.DeclaredValue));
        return Created($"/parcels/{saved.Id}", saved);
    }

    [HttpGet("{id:long}")]
    public ActionResult<Parcel> Get(long id) =>
        repository.FindById(id) ?? throw new ResourceNotFoundException("Посилку не знайдено");
}
