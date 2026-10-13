using Microsoft.AspNetCore.Mvc;
namespace Rental;

[ApiController]
[Route("equipment")]
public class EquipmentController : ControllerBase {
    private readonly IEquipmentRepository equipment;
    public EquipmentController(IEquipmentRepository equipment) { this.equipment = equipment; }
    public record EquipmentInput(string Code, long DailyRate, int Stock);

    [HttpPost]
    public ActionResult<Equipment> Create(EquipmentInput request) {
        var saved = equipment.Create(new Equipment(0, request.Code, request.DailyRate, request.Stock));
        return CreatedAtAction(nameof(Get), new { id = saved.Id }, saved);
    }

    [HttpGet("{id:long}")]
    public ActionResult<Equipment> Get(long id) {
        return equipment.FindById(id) ?? throw new ResourceNotFoundException("Обладнання не знайдено");
    }
}
