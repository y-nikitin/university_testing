using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;
using Rental;
using Npgsql;
using Testcontainers.PostgreSql;
using Xunit;

namespace Rental.Regression;

public class RegressionDatabase : IAsyncLifetime {
    public PostgreSqlContainer Container { get; } = new PostgreSqlBuilder().WithImage("postgres:17.6-alpine").Build();
    public NpgsqlConnection Connection { get; private set; } = null!;
    public long RentalId;
    public async Task InitializeAsync() {
        try {
            await Container.StartAsync();
            Connection = new NpgsqlConnection(Container.GetConnectionString());
            await Connection.OpenAsync();
            using var command = new NpgsqlCommand(File.ReadAllText(Path.Combine(AppContext.BaseDirectory, "migrations/V1__create_tables.sql")), Connection);
            await command.ExecuteNonQueryAsync();
        } catch {
            if (Connection != null) await Connection.DisposeAsync();
            await Container.DisposeAsync();
            throw;
        }
    }
    public async Task DisposeAsync() {
        await Connection.DisposeAsync();
        await Container.DisposeAsync();
    }
}

public class RentalRegression : IClassFixture<RegressionDatabase> {
    private readonly RegressionDatabase db;
    private readonly EquipmentRepository equipment;
    private readonly RentalRepository rentals;
    private readonly RentalService service;
    public RentalRegression(RegressionDatabase db) {
        this.db = db;
        equipment = new EquipmentRepository(db.Connection);
        rentals = new RentalRepository(db.Connection);
        service = new RentalService(equipment, rentals, new Policy());
    }
    private class Policy : IRentalPolicy {
        public bool IsAllowed(Equipment item, int quantity, int days) => true;
    }
    private class DeniedPolicy : IRentalPolicy {
        public bool IsAllowed(Equipment item, int quantity, int days) => false;
    }
    private class DelayedPolicy : IRentalPolicy {
        public bool IsAllowed(Equipment item, int quantity, int days) {
            Thread.Sleep(int.Parse(Environment.GetEnvironmentVariable("REGRESSION_POLICY_DELAY_MS") ?? "200"));
            return true;
        }
    }
    private Equipment Item() => equipment.Create(new Equipment(0, Guid.NewGuid().ToString(), 10000, 5));
    private RentalRequest Request(Equipment item) => new(Guid.NewGuid().ToString(), item.Id, 1, 2);

    [Fact(DisplayName = "Оренда зберігається у БД")]
    public void CreateRental() {
        var saved = service.Rent(Request(Item()));
        db.RentalId = saved.Id;
        Assert.Equal(RentalStatus.Active, saved.Status);
        Assert.Equal(saved, rentals.FindById(saved.Id));
    }
    [Fact(DisplayName = "Повернення обладнання змінює статус")]
    public void ReturnRental() {
        var returned = service.ReturnRental(db.RentalId, 0);
        Assert.Equal(RentalStatus.Returned, returned.Status);
        Assert.Equal(0L, returned.LateFee);
        Assert.Equal(returned, rentals.FindById(db.RentalId));
    }
    [Fact(DisplayName = "Картка обладнання зберігає залишок")]
    public void EquipmentStock() {
        var saved = equipment.Create(new Equipment(0, "REGRESSION-EQUIPMENT", 10000, 5));
        Assert.Equal(5, equipment.FindById(saved.Id)!.Stock);
    }
    [Fact(DisplayName = "Картка обладнання зберігає добову ціну")]
    public void EquipmentRate() {
        var saved = equipment.Create(new Equipment(0, "REGRESSION-EQUIPMENT", 15000, 3));
        Assert.Equal(15000L, equipment.FindById(saved.Id)!.DailyRate);
    }
    [Fact(DisplayName = "Оренда після зовнішнього дозволу")]
    public async Task RentalAfterPolicyCheck() {
        var item = Item();
        var request = Request(item);
        var operation = Task.Run(() => {
            using var connection = new NpgsqlConnection(db.Container.GetConnectionString());
            connection.Open();
            var delayed = new RentalService(new EquipmentRepository(connection), new RentalRepository(connection), new DelayedPolicy());
            return delayed.Rent(request);
        });
        try {
            await Task.Delay(50);
            var saved = rentals.FindRentals(item.Id, RentalStatus.Active);
            Assert.Single(saved);
            Assert.Equal(request.Reference, saved[0].Reference);
            Assert.Equal(20000L, saved[0].Total);
        } finally { await operation; }
    }
    [Fact(DisplayName = "Вибірка містить дві оренди обладнання")]
    public void RentalSelection() {
        var item = Item();
        var prefix = Guid.NewGuid().ToString()[..8];
        var references = new[] { prefix + "-A", prefix + "-B" };
        var fixtureOrder = Environment.GetEnvironmentVariable("REGRESSION_REVERSE_FIXTURE") == "true"
            ? references.Reverse() : references.AsEnumerable();
        foreach (var reference in fixtureOrder) service.Rent(new RentalRequest(reference, item.Id, 1, 2));
        using var command = new NpgsqlCommand("SELECT r.reference FROM rentals r JOIN rental_items i ON i.rental_id=r.id WHERE i.equipment_id=@id", db.Connection);
        command.Parameters.AddWithValue("id", item.Id);
        using var rows = command.ExecuteReader();
        var actual = new List<string>();
        while (rows.Read()) actual.Add(rows.GetString(0));
        Assert.Equal(references, actual);
    }
    [Fact(DisplayName = "Оренда зберігає суму й позицію")]
    public void RentalAmount() {
        var item = Item();
        var saved = service.Rent(Request(item));
        var stored = rentals.FindById(saved.Id)!;
        Assert.Equal(20000L, stored.Total);
        Assert.Equal(item.Id, stored.Item.EquipmentId);
        Assert.Equal(1, stored.Item.Quantity);
        Assert.Equal(10000L, stored.Item.DailyRate);
    }
    [Fact(DisplayName = "Нестача обладнання не створює оренду")]
    public void StockRejection() {
        var item = Item();
        Assert.Throws<RentalRejectedException>(() => service.Rent(new RentalRequest(Guid.NewGuid().ToString(), item.Id, 6, 2)));
        Assert.Empty(rentals.FindRentals(item.Id, RentalStatus.Active));
    }
    [Fact(DisplayName = "Зовнішня відмова не створює оренду")]
    public void PolicyRejection() {
        var item = Item();
        var denied = new RentalService(equipment, rentals, new DeniedPolicy());
        Assert.Throws<RentalRejectedException>(() => denied.Rent(Request(item)));
        Assert.Empty(rentals.FindRentals(item.Id, RentalStatus.Active));
    }
    [Fact(DisplayName = "Повторне повернення не змінює оренду")]
    public void RepeatedReturn() {
        var saved = service.Rent(Request(Item()));
        var returned = service.ReturnRental(saved.Id, 0);
        Assert.Throws<RentalRejectedException>(() => service.ReturnRental(saved.Id, 0));
        Assert.Equal(returned, rentals.FindById(saved.Id));
    }
}
