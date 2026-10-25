using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;
using Delivery;
using Npgsql;
using Testcontainers.PostgreSql;
using Xunit;

namespace Delivery.Regression;

public class RegressionDatabase : IAsyncLifetime {
    public PostgreSqlContainer Container { get; } = new PostgreSqlBuilder().WithImage("postgres:17.6-alpine").Build();
    public NpgsqlConnection Connection { get; private set; } = null!;
    public long ShipmentId;
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

public class ShipmentRegression : IClassFixture<RegressionDatabase> {
    private readonly RegressionDatabase db;
    private readonly CustomerRepository customers;
    private readonly ParcelRepository parcels;
    private readonly ShipmentRepository shipments;
    private readonly ShippingService service;
    public ShipmentRegression(RegressionDatabase db) {
        this.db = db;
        customers = new CustomerRepository(db.Connection);
        parcels = new ParcelRepository(db.Connection);
        shipments = new ShipmentRepository(db.Connection);
        service = new ShippingService(customers, parcels, shipments, new Policy());
    }
    private class Policy : IShippingPolicy {
        public bool IsAllowed(Customer customer, Parcel parcel, int distance) => true;
    }
    private class DeniedPolicy : IShippingPolicy {
        public bool IsAllowed(Customer customer, Parcel parcel, int distance) => false;
    }
    private class DelayedPolicy : IShippingPolicy {
        public bool IsAllowed(Customer customer, Parcel parcel, int distance) {
            Thread.Sleep(int.Parse(Environment.GetEnvironmentVariable("REGRESSION_POLICY_DELAY_MS") ?? "200"));
            return true;
        }
    }
    private Customer Customer() => customers.Create(new Customer(0, Guid.NewGuid().ToString(), false));
    private Parcel Parcel() => parcels.Create(new Parcel(0, Guid.NewGuid().ToString(), 1000, 0));
    private ShipmentRequest Request(Customer customer, Parcel parcel) => new(Guid.NewGuid().ToString(), customer.Id, parcel.Id, 100);

    [Fact(DisplayName = "Відправлення зберігається у БД")]
    public void CreateShipment() {
        var saved = service.CreateShipment(Request(Customer(), Parcel()));
        db.ShipmentId = saved.Id;
        Assert.Equal(ShipmentStatus.Registered, saved.Status);
        Assert.Equal(7000L, saved.Total);
        Assert.Equal(saved, shipments.FindById(saved.Id));
    }
    [Fact(DisplayName = "Передача відправлення в доставку")]
    public void DispatchShipment() {
        var saved = service.Dispatch(db.ShipmentId);
        Assert.Equal(ShipmentStatus.Dispatched, saved.Status);
        Assert.Equal(saved, shipments.FindById(db.ShipmentId));
    }
    [Fact(DisplayName = "Картка посилки зберігає вагу")]
    public void ParcelWeight() {
        var saved = parcels.Create(new Parcel(0, "REGRESSION-PARCEL", 1000, 0));
        Assert.Equal(1000, parcels.FindById(saved.Id)!.WeightGrams);
    }
    [Fact(DisplayName = "Картка посилки зберігає оголошену вартість")]
    public void ParcelValue() {
        var saved = parcels.Create(new Parcel(0, "REGRESSION-PARCEL", 2000, 10000));
        Assert.Equal(10000L, parcels.FindById(saved.Id)!.DeclaredValue);
    }
    [Fact(DisplayName = "Відправлення після зовнішнього дозволу")]
    public async Task ShipmentAfterPolicyCheck() {
        var customer = Customer();
        var request = Request(customer, Parcel());
        var operation = Task.Run(() => {
            using var connection = new NpgsqlConnection(db.Container.GetConnectionString());
            connection.Open();
            var delayed = new ShippingService(new CustomerRepository(connection), new ParcelRepository(connection),
                new ShipmentRepository(connection), new DelayedPolicy());
            return delayed.CreateShipment(request);
        });
        try {
            await Task.Delay(50);
            var saved = shipments.FindShipments(customer.Id, ShipmentStatus.Registered);
            Assert.Single(saved);
            Assert.Equal(request.Reference, saved[0].Reference);
            Assert.Equal(7000L, saved[0].Total);
        } finally {
            await operation;
        }
    }
    [Fact(DisplayName = "Вибірка містить два відправлення клієнта")]
    public void ShipmentSelection() {
        var customer = Customer();
        var prefix = Guid.NewGuid().ToString()[..8];
        var references = new[] { prefix + "-A", prefix + "-B" };
        var fixtureOrder = Environment.GetEnvironmentVariable("REGRESSION_REVERSE_FIXTURE") == "true"
            ? references.Reverse() : references.AsEnumerable();
        foreach (var reference in fixtureOrder) service.CreateShipment(new ShipmentRequest(reference, customer.Id, Parcel().Id, 100));
        using var command = new NpgsqlCommand("SELECT reference FROM shipments WHERE customer_id=@id", db.Connection);
        command.Parameters.AddWithValue("id", customer.Id);
        using var rows = command.ExecuteReader();
        var actual = new List<string>();
        while (rows.Read()) actual.Add(rows.GetString(0));
        Assert.Equal(references, actual);
    }
    [Fact(DisplayName = "Завершення доставки зберігає стан")]
    public void Delivery() {
        var saved = service.CreateShipment(Request(Customer(), Parcel()));
        service.Dispatch(saved.Id);
        var delivered = service.Deliver(saved.Id);
        Assert.Equal(ShipmentStatus.Delivered, delivered.Status);
        Assert.Equal(delivered, shipments.FindById(saved.Id));
    }
    [Fact(DisplayName = "Завершення до відправлення відхиляється")]
    public void PrematureDelivery() {
        var saved = service.CreateShipment(Request(Customer(), Parcel()));
        Assert.Throws<ShipmentRejectedException>(() => service.Deliver(saved.Id));
        Assert.Equal(saved, shipments.FindById(saved.Id));
    }
    [Fact(DisplayName = "Зовнішня відмова не створює відправлення")]
    public void PolicyRejection() {
        var customer = Customer(); var request = Request(customer, Parcel());
        var denied = new ShippingService(customers, parcels, shipments, new DeniedPolicy());
        Assert.Throws<ShipmentRejectedException>(() => denied.CreateShipment(request));
        Assert.Empty(shipments.FindShipments(customer.Id, ShipmentStatus.Registered));
    }
    [Fact(DisplayName = "Повторне скасування не змінює відправлення")]
    public void RepeatedCancellation() {
        var saved = service.CreateShipment(Request(Customer(), Parcel()));
        var cancelled = service.Cancel(saved.Id);
        Assert.Throws<ShipmentRejectedException>(() => service.Cancel(saved.Id));
        Assert.Equal(cancelled, shipments.FindById(saved.Id));
    }
}
