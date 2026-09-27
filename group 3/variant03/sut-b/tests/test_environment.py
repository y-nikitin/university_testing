from rental.database import connect

def test_database_connection():
    with connect() as connection:
        assert connection.execute("SELECT 1").fetchone()[0] == 1
