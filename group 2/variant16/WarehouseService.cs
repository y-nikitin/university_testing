using System;

namespace UniversityTesting.Variant16
{
    public sealed class WarehouseService
    {
        public sealed record Request(int Quantity, int Reserved, bool Urgent, bool AllowPartial);

        public sealed record Result(string Status, int Requested, int Issued, long Stock, int Reserved, long Fee);

        public interface IReleasePolicy
        {
            bool IsAllowed();
        }

        public interface IStockProvider
        {
            long PhysicalStock();
        }

        public interface IWarehouseRepository
        {
            void Save(Request request, Result result);
        }

        private readonly IReleasePolicy _policy;
        private readonly IStockProvider _stock;
        private readonly IWarehouseRepository _orders;

        public WarehouseService(IReleasePolicy policy, IStockProvider stock, IWarehouseRepository orders)
        {
            _policy = policy ?? throw new ArgumentNullException(nameof(policy), "Залежність не задано");
            _stock = stock ?? throw new ArgumentNullException(nameof(stock), "Залежність не задано");
            _orders = orders ?? throw new ArgumentNullException(nameof(orders), "Залежність не задано");
        }

        public Result Process(Request request)
        {
            if (request is null)
                throw new ArgumentException("Заявку не задано", nameof(request));
            CheckRange(request.Quantity, 1, 99, "Запитана кількість");
            CheckRange(request.Reserved, 0, 100, "Попередні резерви");
            if (!_policy.IsAllowed())
                return Rejected("Видачу заблоковано");
            long stock = _stock.PhysicalStock();
            if (stock < 0)
                throw new InvalidOperationException("Від’ємний фізичний залишок");
            if (request.Reserved > stock)
                throw new InvalidOperationException("Резерви перевищують фізичний залишок");
            long available = stock - request.Reserved;
            if (available == 0)
                return Rejected("Немає доступного товару");
            if (available < request.Quantity && !request.AllowPartial)
                return Rejected("Недостатньо товару");
            int issued = (int)Math.Min(available, request.Quantity);
            bool partial = issued < request.Quantity;
            long surcharge;
            if (request.Urgent && partial)
                surcharge = 5_000;
            else if (request.Urgent)
                surcharge = 5_000;
            else if (partial)
                surcharge = 1_000;
            else
                surcharge = 0;
            long remaining = stock - issued - (partial ? request.Reserved : 0);
            var result = new Result(partial ? "Виконано частково" : "Виконано повністю",
                request.Quantity, issued, remaining, request.Reserved, issued * 500L + surcharge);
            _orders.Save(request, result);
            return result;
        }

        private static Result Rejected(string status) => new Result(status, 0, 0, 0, 0, 0);

        private static void CheckRange(int value, int minimum, int maximum, string field)
        {
            if (value < minimum || value > maximum)
                throw new ArgumentException("Недопустиме значення: " + field);
        }
    }
}
