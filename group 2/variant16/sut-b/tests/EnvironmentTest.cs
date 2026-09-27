using Xunit;
namespace Delivery.Tests;
public class EnvironmentTest {
    [Fact(DisplayName = "Підключення до тестової бази даних")]
    public void ConnectsToDatabase() {
        using var connection = Database.Connect();
        using var command = connection.CreateCommand(); command.CommandText = "SELECT 1";
        Assert.Equal(1, System.Convert.ToInt32(command.ExecuteScalar()));
    }
}
