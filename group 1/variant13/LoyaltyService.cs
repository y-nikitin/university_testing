using System;

namespace UniversityTesting.Variant13
{
    public sealed class LoyaltyService
    {
        public sealed record Request(int PurchaseAmount, int PointsToSpend, bool Gold, bool Promotion);

        public sealed record Result(string Status, long AmountDue, int PointsSpent, long PointsEarned, long Balance);

        public interface IMemberStatus
        {
            bool IsActive();
        }

        public interface IPointsBalance
        {
            long CurrentBalance();
        }

        public interface ILoyaltyRepository
        {
            void Save(Request request, Result result);
        }

        private readonly IMemberStatus _members;
        private readonly IPointsBalance _points;
        private readonly ILoyaltyRepository _operations;

        public LoyaltyService(IMemberStatus members, IPointsBalance points, ILoyaltyRepository operations)
        {
            _members = members ?? throw new ArgumentNullException(nameof(members), "Залежність не задано");
            _points = points ?? throw new ArgumentNullException(nameof(points), "Залежність не задано");
            _operations = operations ?? throw new ArgumentNullException(nameof(operations), "Залежність не задано");
        }

        public Result Process(Request request)
        {
            if (request is null)
                throw new ArgumentException("Заявку не задано", nameof(request));
            CheckRange(request.PurchaseAmount, 100, 1_000_000, "Сума покупки");
            CheckRange(request.PointsToSpend, 0, 4_999, "Бали для списання");
            if (!_members.IsActive())
                return Rejected("Рахунок неактивний");
            long balance = _points.CurrentBalance();
            if (balance < 0)
                throw new InvalidOperationException("Від’ємний баланс балів");
            if (request.PointsToSpend > balance)
                return Rejected("Недостатньо балів");
            if (request.PointsToSpend >= request.PurchaseAmount / 200)
                return Rejected("Перевищено частку оплати");
            long amountDue = request.PurchaseAmount - request.PointsToSpend * 100L;
            int multiplier;
            if (request.Gold && request.Promotion)
                multiplier = 4;
            else if (request.Gold || request.Promotion)
                multiplier = 2;
            else
                multiplier = 1;
            long earned = amountDue / 10_000 * multiplier;
            long updatedBalance = checked(balance - request.PointsToSpend + earned);
            var result = new Result("Виконано", amountDue, request.PointsToSpend, earned, updatedBalance);
            _operations.Save(request, result);
            return result;
        }

        private static Result Rejected(string status) => new Result(status, 0, 0, 0, 0);

        private static void CheckRange(int value, int minimum, int maximum, string field)
        {
            if (value < minimum || value > maximum)
                throw new ArgumentException("Недопустиме значення: " + field);
        }
    }
}
