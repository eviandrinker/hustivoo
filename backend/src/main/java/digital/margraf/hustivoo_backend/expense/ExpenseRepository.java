package digital.margraf.hustivoo_backend.expense;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.*;
import java.util.*;

/** All entry points are business-scoped; monetary columns become exact decimal strings. */
@Repository
public class ExpenseRepository {
    final JdbcTemplate jdbc;
    public ExpenseRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public List<Map<String,Object>> query(String sql,Object... args) {
        return jdbc.query(sql,(rs,index)->row(rs),args);
    }
    private Map<String,Object> row(ResultSet rs) throws SQLException {
        Map<String,Object> row=new LinkedHashMap<>();
        var meta=rs.getMetaData();
        for(int i=1;i<=meta.getColumnCount();i++) {
            String label=meta.getColumnLabel(i); StringBuilder key=new StringBuilder(); boolean upper=false;
            for(char c:label.toCharArray()) { if(c=='_') upper=true; else {key.append(upper?Character.toUpperCase(c):c);upper=false;} }
            Object value=rs.getObject(i);
            if(value instanceof java.math.BigDecimal number) value=number.toPlainString();
            else if(value instanceof Timestamp stamp) value=stamp.toInstant().toString();
            else if(value instanceof java.sql.Date date) value=date.toLocalDate().toString();
            else if(value instanceof UUID uuid) value=uuid.toString();
            row.put(key.toString(),value);
        }
        return row;
    }
    public Map<String,Object> scoped(String table, UUID business, UUID id, boolean lock) {
        // Table is an internal constant, never taken from a request.
        return query("select * from "+table+" where business_id=? and id=?"+(lock?" for update":""),business,id).stream().findFirst()
            .orElseThrow(ExpenseService::missing);
    }
    public java.math.BigDecimal sum(String sql,Object... args) { return jdbc.queryForObject(sql,java.math.BigDecimal.class,args); }
    public int update(String sql,Object... args) { return jdbc.update(sql,args); }
}
