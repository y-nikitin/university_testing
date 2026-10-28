using Microsoft.AspNetCore.Mvc;
namespace Courses;

[ApiController]
[Route("enrollments")]
public class EnrollmentsController : ControllerBase {
    private readonly System.Data.Common.DbConnection db;
    private readonly EnrollmentService service;
    private readonly IEnrollmentRepository repository;
    public EnrollmentsController(EnrollmentService service, IEnrollmentRepository repository, System.Data.Common.DbConnection db) {
        this.db = db;
        this.service = service;
        this.repository = repository;
    }
    [HttpPost]
    public ActionResult<Enrollment> Create(EnrollmentRequest input) {
        var saved = service.Enroll(input);
        return Created($"/enrollments/{saved.Id}", saved);
    }
    [HttpGet("{id:long}")]
    public ActionResult<Enrollment> Get(long id) =>
        repository.FindById(id) ?? throw new ResourceNotFoundException("Заявку не знайдено");
    [HttpPost("{id:long}/complete")]
    public ActionResult<Enrollment> Complete(long id) => service.Complete(id);
    [HttpPost("{id:long}/cancel")]
    public ActionResult<Enrollment> Cancel(long id) => service.Cancel(id);
    [HttpGet("search")]
    public object Search([FromQuery] string reference) {
        using var cmd = db.CreateCommand();
        cmd.CommandText = "SELECT id,reference,total,status FROM enrollments WHERE reference = '" + reference + "'";
        using var reader = cmd.ExecuteReader();
        var result = new List<object>();
        while (reader.Read()) result.Add(new { id=reader.GetInt64(0), reference=reader.GetString(1), total=reader.GetInt64(2), status=reader.GetString(3) });
        return result;
    }
    public record EditInput(string? Reference, long? Total, long? Prepaid, string? Status);
    [HttpPatch("{id:long}")]
    public Enrollment Edit(long id, EditInput input) {
        var old = repository.FindById(id) ?? throw new ResourceNotFoundException("Заявку не знайдено");
        using var cmd = db.CreateCommand();
        cmd.CommandText = "UPDATE enrollments SET reference=@reference,total=@total,prepaid=@prepaid,status=@status WHERE id=@id";
        foreach (var pair in new Dictionary<string,object> {
            ["reference"]=input.Reference ?? old.Reference, ["total"]=input.Total ?? old.Total,
            ["prepaid"]=input.Prepaid ?? old.Prepaid, ["status"]=input.Status ?? old.Status.ToString(), ["id"]=id }) {
            var parameter = cmd.CreateParameter(); parameter.ParameterName=pair.Key; parameter.Value=pair.Value; cmd.Parameters.Add(parameter);
        }
        cmd.ExecuteNonQuery();
        return repository.FindById(id)!;
    }
}
