package com.expensetracker;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/** Connection pool (HikariCP). Settings come from db.properties; env vars DB_URL, DB_USER, DB_PASSWORD override them. */
public final class Db {
    private static HikariDataSource ds;
    private Db() {}

    public static class DataAccessException extends RuntimeException {
        public DataAccessException(Throwable cause) { super(cause); }
    }
    public static class DuplicateException extends RuntimeException {}

    public static RuntimeException wrap(SQLException e) {
        return e.getErrorCode() == 1062 ? new DuplicateException() : new DataAccessException(e);
    }

    public static synchronized void init() {
        if (ds != null) return;
        Properties p = new Properties();
        try (InputStream in = Db.class.getClassLoader().getResourceAsStream("db.properties")) {
            if (in == null) throw new IllegalStateException("db.properties not found on the classpath");
            p.load(in);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        HikariConfig c = new HikariConfig();
        c.setDriverClassName("com.mysql.cj.jdbc.Driver");
        c.setJdbcUrl(pick("DB_URL", p.getProperty("db.url")));
        c.setUsername(pick("DB_USER", p.getProperty("db.user")));
        c.setPassword(pick("DB_PASSWORD", p.getProperty("db.password")));
        c.setMaximumPoolSize(10);
        ds = new HikariDataSource(c);
    }

    private static String pick(String env, String fallback) {
        String v = System.getenv(env);
        return v != null && !v.isBlank() ? v : fallback;
    }

    public static Connection get() throws SQLException { return ds.getConnection(); }

    public static synchronized void close() {
        if (ds != null) { ds.close(); ds = null; }
    }
}
