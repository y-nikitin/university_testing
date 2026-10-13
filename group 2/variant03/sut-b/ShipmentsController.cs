using Microsoft.AspNetCore.Mvc;
namespace Delivery;

[ApiController]
[Route("shipments")]
public class ShipmentsController : ControllerBase {
    private readonly ShippingService service;
    private readonly IShipmentRepository repository;
    public ShipmentsController(ShippingService service, IShipmentRepository repository) {
        this.service = service;
        this.repository = repository;
    }
    [HttpPost]
    public ActionResult<Shipment> Create(ShipmentRequest input) {
        var saved = service.CreateShipment(input);
        return Created($"/shipments/{saved.Id}", saved);
    }
    [HttpGet("{id:long}")]
    public ActionResult<Shipment> Get(long id) =>
        repository.FindById(id) ?? throw new ResourceNotFoundException("Відправлення не знайдено");
    [HttpPost("{id:long}/dispatch")]
    public ActionResult<Shipment> Dispatch(long id) => service.Dispatch(id);
    [HttpPost("{id:long}/deliver")]
    public ActionResult<Shipment> Deliver(long id) => service.Deliver(id);
    [HttpPost("{id:long}/cancel")]
    public ActionResult<Shipment> Cancel(long id) => service.Cancel(id);
}
