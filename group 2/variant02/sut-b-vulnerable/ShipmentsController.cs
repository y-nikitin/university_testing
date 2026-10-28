using Microsoft.AspNetCore.Mvc;
namespace Delivery;

[ApiController]
[Route("shipments")]
public class ShipmentsController : ControllerBase {
    private readonly System.Data.Common.DbConnection db;
    private readonly ShippingService service;
    private readonly IShipmentRepository repository;
    public ShipmentsController(ShippingService service, IShipmentRepository repository, System.Data.Common.DbConnection db) {
        this.db = db;
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

    [HttpGet("search")]
    public object Search([FromQuery] string reference) {
        using var cmd = db.CreateCommand();
        cmd.CommandText = "SELECT id,reference,total,status FROM shipments WHERE reference = '" + reference + "'";
        using var reader = cmd.ExecuteReader();
        var result = new List<object>();
        while (reader.Read()) result.Add(new { id=reader.GetInt64(0), reference=reader.GetString(1), total=reader.GetInt64(2), status=reader.GetString(3) });
        return result;
    }
    public record EditInput(string? Reference, long? Total, long? Adjustment, string? Status);
    [HttpPatch("{id:long}")]
    public Shipment Edit(long id, EditInput input) {
        var old = repository.FindById(id) ?? throw new ResourceNotFoundException("Відправлення не знайдено");
        using var cmd = db.CreateCommand();
        cmd.CommandText = "UPDATE shipments SET reference=@reference,total=@total,adjustment=@adjustment,status=@status WHERE id=@id";
        foreach (var pair in new Dictionary<string,object> {
            ["reference"]=input.Reference ?? old.Reference, ["total"]=input.Total ?? old.Total,
            ["adjustment"]=input.Adjustment ?? old.Adjustment, ["status"]=input.Status ?? old.Status.ToString(), ["id"]=id }) {
            var parameter = cmd.CreateParameter(); parameter.ParameterName=pair.Key; parameter.Value=pair.Value; cmd.Parameters.Add(parameter);
        }
        cmd.ExecuteNonQuery();
        return repository.FindById(id)!;
    }
}
