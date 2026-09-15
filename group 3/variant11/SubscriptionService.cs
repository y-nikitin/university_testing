using System;

namespace UniversityTesting.Variant11
{
    public sealed class SubscriptionService
    {
        public sealed record Request(int Users, int Months, bool NonprofitClaimed, bool AutoRenew);

        public sealed record Result(string Status, long Total, long RestorationFee, string SubscriptionState);

        public interface ISubscriptionState
        {
            string CurrentStatus();
        }

        public interface INonprofitRegistry
        {
            bool IsEligible();
        }

        public interface IBillingRepository
        {
            void Save(Request request, Result result);
        }

        private readonly ISubscriptionState _subscriptions;
        private readonly INonprofitRegistry _nonprofits;
        private readonly IBillingRepository _billing;

        public SubscriptionService(ISubscriptionState subscriptions, INonprofitRegistry nonprofits, IBillingRepository billing)
        {
            _subscriptions = subscriptions ?? throw new ArgumentNullException(nameof(subscriptions), "Залежність не задано");
            _nonprofits = nonprofits ?? throw new ArgumentNullException(nameof(nonprofits), "Залежність не задано");
            _billing = billing ?? throw new ArgumentNullException(nameof(billing), "Залежність не задано");
        }

        public Result Process(Request request)
        {
            if (request is null)
                throw new ArgumentException("Заявку не задано", nameof(request));
            CheckRange(request.Users, 1, 49, "Кількість користувачів");
            CheckRange(request.Months, 1, 12, "Строк продовження");
            string state = _subscriptions.CurrentStatus();
            if (state != "Активна" && state != "Прострочена" && state != "Заблокована")
                throw new InvalidOperationException("Невідомий стан підписки");
            if (state == "Заблокована")
                return new Result("Підписку заблоковано", 0, 0, state);
            bool nonprofit = request.NonprofitClaimed && _nonprofits.IsEligible();
            int discount;
            if (nonprofit && request.AutoRenew)
                discount = 20;
            else if (nonprofit)
                discount = 20;
            else if (request.AutoRenew)
                discount = 5;
            else
                discount = 0;
            long basePrice = request.Users * request.Months * 20_000L;
            long total = basePrice - basePrice * discount / 100;
            if (request.Months >= 11)
                total = Math.Max(0, total - 10_000);
            long restorationFee = state == "Прострочена" ? 5_000 : 0;
            var result = new Result("Продовжено", total + restorationFee, restorationFee, "Активна");
            _billing.Save(request, result);
            return result;
        }

        private static void CheckRange(int value, int minimum, int maximum, string field)
        {
            if (value < minimum || value > maximum)
                throw new ArgumentException("Недопустиме значення: " + field);
        }
    }
}
