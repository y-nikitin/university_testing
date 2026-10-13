using Microsoft.AspNetCore.Mvc;
namespace Courses;

[ApiController]
[Route("students")]
public class StudentController : ControllerBase {
    private readonly IStudentRepository repository;
    public StudentController(IStudentRepository repository) { this.repository = repository; }
    public record Input(string Code, int Age, int EntryScore, bool Regular = false);
    [HttpPost]
    public ActionResult<Student> Create(Input input) {
        var saved = repository.Create(new Student(0, input.Code, input.Age, input.EntryScore, input.Regular));
        return Created($"/students/{saved.Id}", saved);
    }
    [HttpGet("{id:long}")]
    public ActionResult<Student> Get(long id) =>
        repository.FindById(id) ?? throw new ResourceNotFoundException("Слухача не знайдено");
}
