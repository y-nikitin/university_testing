using System;

namespace UniversityTesting.Variant02
{
    public sealed class OrderService
    {
        public sealed record Request(int Quantity, int UnitPrice, bool Premium, bool CouponClaimed);

        public sealed record Result(string Status, int Quantity, int DiscountPercent, long Goods, long Delivery, long Total);

        public interface IStockProvider
        {
            int AvailableUnits();
        }

        public interface ICouponValidator
        {
            bool IsValid();
        }

        public interface IOrderRepository
        {
            void Save(Request request, Result result);
        }

        private readonly IStockProvider _stock;
        private readonly ICouponValidator _coupons;
        private readonly IOrderRepository _orders;

        public OrderService(IStockProvider stock, ICouponValidator coupons, IOrderRepository orders)
        {
            _stock = stock ?? throw new ArgumentNullException(nameof(stock), "Залежність не задано");
            _coupons = coupons ?? throw new ArgumentNullException(nameof(coupons), "Залежність не задано");
            _orders = orders ?? throw new ArgumentNullException(nameof(orders), "Залежність не задано");
        }

        public Result Process(Request request)
        {
            if (request is null)
                throw new ArgumentException("Заявку не задано", nameof(request));
            CheckRange(request.Quantity, 1, 19, "Кількість товару");
            CheckRange(request.UnitPrice, 100, 100_000, "Ціна одиниці");
            int stock = _stock.AvailableUnits();
            if (stock < 0)
                throw new InvalidOperationException("Від’ємний залишок товару");
            if (stock < request.Quantity)
                return Rejected("Недостатньо товару");
            bool coupon = request.CouponClaimed && _coupons.IsValid();
            int discount;
            if (request.Premium && coupon)
                discount = 15;
            else if (request.Premium)
                discount = 10;
            else if (coupon)
                discount = 5;
            else
                discount = 0;
            long subtotal = request.Quantity * (long)request.UnitPrice;
            long goods = subtotal - subtotal * discount / 100;
            long delivery = goods > 50_000 ? 0 : 5_000;
            var result = new Result("Оформлено", request.Quantity, discount, goods, delivery, goods + delivery);
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
