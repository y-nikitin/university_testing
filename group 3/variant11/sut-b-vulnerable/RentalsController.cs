using Microsoft.AspNetCore.Mvc;
namespace Rental;

[ApiController]
[Route("rentals")]
public class RentalsController : ControllerBase {
    private readonly System.Data.Common.DbConnection db;
    private readonly RentalService service;
    private readonly IRentalRepository rentals;
    public RentalsController(RentalService service, IRentalRepository rentals, System.Data.Common.DbConnection db) {
        this.db = db;
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

    [HttpGet("search")]
    public object Search([FromQuery] string reference) {
        using var cmd = db.CreateCommand();
        cmd.CommandText = "SELECT id,reference,total,status FROM rentals WHERE reference = '" + reference + "'";
        using var reader = cmd.ExecuteReader();
        var result = new List<object>();
        while (reader.Read()) result.Add(new { id=reader.GetInt64(0), reference=reader.GetString(1), total=reader.GetInt64(2), status=reader.GetString(3) });
        return result;
    }
    public record EditInput(string? Reference, long? Total, string? Status);
    [HttpPatch("{id:long}")]
    public RentalOrder Edit(long id, EditInput input) {
        var old = rentals.FindById(id) ?? throw new ResourceNotFoundException("Оренду не знайдено");
        using var cmd = db.CreateCommand();
        cmd.CommandText = "UPDATE rentals SET reference=@reference,total=@total,status=@status WHERE id=@id";
        foreach (var pair in new Dictionary<string,object> {
            ["reference"]=input.Reference ?? old.Reference, ["total"]=input.Total ?? old.Total,
            ["status"]=input.Status ?? old.Status.ToString(), ["id"]=id }) {
            var parameter = cmd.CreateParameter(); parameter.ParameterName=pair.Key; parameter.Value=pair.Value; cmd.Parameters.Add(parameter);
        }
        cmd.ExecuteNonQuery();
        return rentals.FindById(id)!;
    }
}
