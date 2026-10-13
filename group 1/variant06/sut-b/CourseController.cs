using Microsoft.AspNetCore.Mvc;
namespace Courses;

[ApiController]
[Route("courses")]
public class CourseController : ControllerBase {
    private readonly ICourseRepository repository;
    public CourseController(ICourseRepository repository) { this.repository = repository; }
    public record Input(string Code, long Price, int Capacity, int Level = 1);
    [HttpPost]
    public ActionResult<Course> Create(Input input) {
        var saved = repository.Create(new Course(0, input.Code, input.Price, input.Capacity, input.Level));
        return Created($"/courses/{saved.Id}", saved);
    }
    [HttpGet("{id:long}")]
    public ActionResult<Course> Get(long id) =>
        repository.FindById(id) ?? throw new ResourceNotFoundException("Курс не знайдено");
}
