using System;

namespace UniversityTesting.Variant06
{
    public sealed class LibraryService
    {
        public sealed record Request(int Days, int CurrentLoans, bool ReferenceBook, bool Renewal);

        public sealed record Result(string Status, int Days, long Fee, int LoanCount);

        public interface ICatalogAvailability
        {
            int AvailableCopies();
        }

        public interface IHoldRegistry
        {
            bool HasWaitingReader();
        }

        public interface ILoanRepository
        {
            void Save(Request request, Result result);
        }

        private readonly ICatalogAvailability _catalog;
        private readonly IHoldRegistry _holds;
        private readonly ILoanRepository _loans;

        public LibraryService(ICatalogAvailability catalog, IHoldRegistry holds, ILoanRepository loans)
        {
            _catalog = catalog ?? throw new ArgumentNullException(nameof(catalog), "Залежність не задано");
            _holds = holds ?? throw new ArgumentNullException(nameof(holds), "Залежність не задано");
            _loans = loans ?? throw new ArgumentNullException(nameof(loans), "Залежність не задано");
        }

        public Result Process(Request request)
        {
            if (request is null)
                throw new ArgumentException("Заявку не задано", nameof(request));
            CheckRange(request.Days, 1, 29, "Строк позики");
            CheckRange(request.CurrentLoans, 0, 10, "Кількість позик");
            if (request.Renewal && request.CurrentLoans == 0)
                throw new ArgumentException("Немає чинної позики для продовження");
            if (!request.Renewal && request.CurrentLoans >= 5)
                return Rejected("Ліміт позик");
            if (request.ReferenceBook && (request.Renewal && request.Days > 7))
                return Rejected("Обмеження довідкового видання");
            if (request.Renewal)
            {
                if (_holds.HasWaitingReader())
                    return Rejected("Є черга");
            }
            else
            {
                int copies = _catalog.AvailableCopies();
                if (copies < 0)
                    throw new InvalidOperationException("Від’ємна кількість примірників");
                if (copies == 0)
                    return Rejected("Немає примірника");
            }
            long fee = Math.Max(0, request.Days - 15) * 100L;
            int count = request.Renewal ? request.CurrentLoans : request.CurrentLoans + 1;
            var result = new Result(request.Renewal ? "Продовжено" : "Видано", request.Days, fee, count);
            _loans.Save(request, result);
            return result;
        }

        private static Result Rejected(string status) => new Result(status, 0, 0, 0);

        private static void CheckRange(int value, int minimum, int maximum, string field)
        {
            if (value < minimum || value > maximum)
                throw new ArgumentException("Недопустиме значення: " + field);
        }
    }
}
