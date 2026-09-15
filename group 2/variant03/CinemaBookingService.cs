using System;

namespace UniversityTesting.Variant03
{
    public sealed class CinemaBookingService
    {
        public sealed record Request(int Tickets, int Age, bool Evening, bool StudentClaimed);

        public sealed record Result(string Status, int Tickets, long Total);

        public interface ISeatAvailability
        {
            int FreeSeats();
        }

        public interface IStudentRegistry
        {
            bool IsEligible();
        }

        public interface ITicketRepository
        {
            void Save(Request request, Result result);
        }

        private readonly ISeatAvailability _seats;
        private readonly IStudentRegistry _students;
        private readonly ITicketRepository _tickets;

        public CinemaBookingService(ISeatAvailability seats, IStudentRegistry students, ITicketRepository tickets)
        {
            _seats = seats ?? throw new ArgumentNullException(nameof(seats), "Залежність не задано");
            _students = students ?? throw new ArgumentNullException(nameof(students), "Залежність не задано");
            _tickets = tickets ?? throw new ArgumentNullException(nameof(tickets), "Залежність не задано");
        }

        public Result Process(Request request)
        {
            if (request is null)
                throw new ArgumentException("Заявку не задано", nameof(request));
            CheckRange(request.Tickets, 1, 7, "Кількість квитків");
            CheckRange(request.Age, 0, 120, "Вік");
            if (request.Age < 11 && request.Evening)
                return Rejected("Вікове обмеження");
            int available = _seats.FreeSeats();
            if (available < 0)
                throw new InvalidOperationException("Некоректні відомості про вільні місця");
            if (available < request.Tickets)
                return Rejected("Недостатньо місць");
            int discount;
            if (request.Age < 12)
                discount = 50;
            else if (request.StudentClaimed && _students.IsEligible())
                discount = 20;
            else
                discount = 0;
            long basePrice = request.Tickets * (request.Evening ? 15_000L : 10_000L);
            long total = basePrice - basePrice * discount / 100;
            if (request.Tickets > 5)
                total -= 2_000;
            var result = new Result("Заброньовано", request.Tickets, total);
            _tickets.Save(request, result);
            return result;
        }

        private static Result Rejected(string status) => new Result(status, 0, 0);

        private static void CheckRange(int value, int minimum, int maximum, string field)
        {
            if (value < minimum || value > maximum)
                throw new ArgumentException("Недопустиме значення: " + field);
        }
    }
}
