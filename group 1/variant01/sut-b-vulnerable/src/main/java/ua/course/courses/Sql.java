package ua.course.courses;
import java.sql.*;
final class Sql {
    private Sql() {}
    static PreparedStatement command(Connection connection,String text,Object... values) throws SQLException {
        var statement=connection.prepareStatement(text);
        try {
            for(int i=0;i<values.length;i++) statement.setObject(i+1,values[i]);
            return statement;
        } catch(SQLException | RuntimeException error) {
            try { statement.close(); } catch(SQLException closeError) { error.addSuppressed(closeError); }
            throw error;
        }
    }
}
