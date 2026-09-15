using System;

namespace UniversityTesting.Variant07
{
    public sealed class ParcelDeliveryService
    {
        public sealed record Request(int WeightGrams, int DistanceKm, bool Express, bool Insured);

        public sealed record Result(string Status, int BillableKilograms, long BasePrice, long DistanceFee, long OptionsFee, long Total);

        public interface IRouteCapacity
        {
            int AvailableGrams();
        }

        public interface IExpressPolicy
        {
            bool IsSupported();
        }

        public interface IShipmentRepository
        {
            void Save(Request request, Result result);
        }

        private readonly IRouteCapacity _capacity;
        private readonly IExpressPolicy _express;
        private readonly IShipmentRepository _shipments;

        public ParcelDeliveryService(IRouteCapacity capacity, IExpressPolicy express, IShipmentRepository shipments)
        {
            _capacity = capacity ?? throw new ArgumentNullException(nameof(capacity), "Залежність не задано");
            _express = express ?? throw new ArgumentNullException(nameof(express), "Залежність не задано");
            _shipments = shipments ?? throw new ArgumentNullException(nameof(shipments), "Залежність не задано");
        }

        public Result Process(Request request)
        {
            if (request is null)
                throw new ArgumentException("Заявку не задано", nameof(request));
            CheckRange(request.WeightGrams, 1, 29_999, "Вага посилки");
            CheckRange(request.DistanceKm, 1, 3_000, "Відстань");
            int capacity = _capacity.AvailableGrams();
            if (capacity < 0)
                throw new InvalidOperationException("Від’ємна доступна вантажність");
            if (capacity < request.WeightGrams)
                return Rejected("Недостатня вантажність");
            if (request.Express && !_express.IsSupported())
                return Rejected("Експрес-доставка недоступна");
            int kilograms = (request.WeightGrams + 999) / 1_000;
            long basePrice = 4_000 + kilograms * 1_000L;
            long distanceFee = request.DistanceKm >= 500 ? 3_000 : 0;
            long options;
            if (request.Express && request.Insured)
                options = 6_000;
            else if (request.Express)
                options = 4_000;
            else if (request.Insured)
                options = 2_000;
            else
                options = 0;
            var result = new Result("Прийнято", kilograms, basePrice, distanceFee, options,
                basePrice + distanceFee + options);
            _shipments.Save(request, result);
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
