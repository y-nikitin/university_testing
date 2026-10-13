using Microsoft.AspNetCore.Mvc;
namespace Courses;

[ApiController]
[Route("enrollments")]
public class EnrollmentsController : ControllerBase {
    private readonly EnrollmentService service;
    private readonly IEnrollmentRepository repository;
    public EnrollmentsController(EnrollmentService service, IEnrollmentRepository repository) {
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
}
