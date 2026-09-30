package com.expensetracker;

import com.expensetracker.Models.*;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** All SQL lives here. Every expense/category query is scoped by user_id. */
public final class Dao {
    private Dao() {}

    public static final class Users {
        private Users() {}

        public static Optional<User> findByEmail(String email) {
            try (Connection c = Db.get();
                 PreparedStatement ps = c.prepareStatement("SELECT id, name, email, password_hash FROM users WHERE email = ?")) {
                ps.setString(1, email);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next()
                        ? Optional.of(new User(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4)))
                        : Optional.empty();
                }
            } catch (SQLException e) { throw Db.wrap(e); }
        }

        public static Optional<UserView> findById(int id) {
            try (Connection c = Db.get();
                 PreparedStatement ps = c.prepareStatement("SELECT id, name, email FROM users WHERE id = ?")) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next()
                        ? Optional.of(new UserView(rs.getInt(1), rs.getString(2), rs.getString(3)))
                        : Optional.empty();
                }
            } catch (SQLException e) { throw Db.wrap(e); }
        }

        public static int create(String name, String email, String hash) {
            try (Connection c = Db.get();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO users (name, email, password_hash) VALUES (?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, name);
                ps.setString(2, email);
                ps.setString(3, hash);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) { rs.next(); return rs.getInt(1); }
            } catch (SQLException e) { throw Db.wrap(e); }
        }
    }

    public static final class Categories {
        private Categories() {}
        private static final String[][] DEFAULTS = {
            {"Food", "#f97316"}, {"Travel", "#0ea5e9"}, {"Bills", "#ef4444"}, {"Shopping", "#a855f7"},
            {"Health", "#22c55e"}, {"Entertainment", "#eab308"}, {"Other", "#64748b"}};

        public static void createDefaults(int uid) {
            try (Connection c = Db.get();
                 PreparedStatement ps = c.prepareStatement("INSERT INTO categories (user_id, name, color) VALUES (?, ?, ?)")) {
                for (String[] d : DEFAULTS) {
                    ps.setInt(1, uid); ps.setString(2, d[0]); ps.setString(3, d[1]);
                    ps.addBatch();
                }
                ps.executeBatch();
            } catch (SQLException e) { throw Db.wrap(e); }
        }

        public static List<Category> list(int uid) {
            try (Connection c = Db.get();
                 PreparedStatement ps = c.prepareStatement("SELECT id, name, color FROM categories WHERE user_id = ? ORDER BY name")) {
                ps.setInt(1, uid);
                try (ResultSet rs = ps.executeQuery()) {
                    List<Category> out = new ArrayList<>();
                    while (rs.next()) out.add(new Category(rs.getInt(1), rs.getString(2), rs.getString(3)));
                    return out;
                }
            } catch (SQLException e) { throw Db.wrap(e); }
        }

        public static boolean exists(int uid, int id) {
            try (Connection c = Db.get();
                 PreparedStatement ps = c.prepareStatement("SELECT 1 FROM categories WHERE id = ? AND user_id = ?")) {
                ps.setInt(1, id); ps.setInt(2, uid);
                try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
            } catch (SQLException e) { throw Db.wrap(e); }
        }

        public static Category create(int uid, String name, String color) {
            try (Connection c = Db.get();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO categories (user_id, name, color) VALUES (?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, uid); ps.setString(2, name); ps.setString(3, color);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) { rs.next(); return new Category(rs.getInt(1), name, color); }
            } catch (SQLException e) { throw Db.wrap(e); }
        }

        public static boolean update(int uid, int id, String name, String color) {
            try (Connection c = Db.get();
                 PreparedStatement ps = c.prepareStatement("UPDATE categories SET name = ?, color = ? WHERE id = ? AND user_id = ?")) {
                ps.setString(1, name); ps.setString(2, color); ps.setInt(3, id); ps.setInt(4, uid);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) { throw Db.wrap(e); }
        }

        public static boolean delete(int uid, int id) {
            try (Connection c = Db.get();
                 PreparedStatement ps = c.prepareStatement("DELETE FROM categories WHERE id = ? AND user_id = ?")) {
                ps.setInt(1, id); ps.setInt(2, uid);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) { throw Db.wrap(e); }
        }
    }

    public static final class Expenses {
        private Expenses() {}
        private static final String SELECT =
            "SELECT e.id, e.title, e.amount, e.expense_date, e.note, e.category_id, c.name, c.color "
          + "FROM expenses e LEFT JOIN categories c ON c.id = e.category_id";
        private static final String BASE = SELECT + " WHERE e.user_id = ?";
        private static final java.util.Map<String, String> SORTS =
            java.util.Map.of("date", "e.expense_date", "amount", "e.amount", "title", "e.title");

        private static ExpenseView map(ResultSet rs) throws SQLException {
            return new ExpenseView(rs.getInt(1), rs.getString(2), rs.getBigDecimal(3), rs.getObject(4, LocalDate.class),
                rs.getString(5), rs.getObject(6, Integer.class), rs.getString(7), rs.getString(8));
        }

        private static void bind(PreparedStatement ps, ExpenseInput in) throws SQLException {
            ps.setString(1, in.title());
            ps.setBigDecimal(2, in.amount());
            if (in.categoryId() == null) ps.setNull(3, Types.INTEGER); else ps.setInt(3, in.categoryId());
            ps.setObject(4, in.date());
            ps.setString(5, in.note());
        }

        private static void bindAll(PreparedStatement ps, List<Object> args) throws SQLException {
            for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));
        }

        /** Filtering, sorting and paging all happen in SQL. Sort column comes from a fixed whitelist. */
        public static ExpenseList search(int uid, ExpenseFilter f) {
            StringBuilder where = new StringBuilder(" WHERE e.user_id = ?");
            List<Object> args = new ArrayList<>();
            args.add(uid);
            if (f.categoryId() != null) {
                if (f.categoryId() == 0) where.append(" AND e.category_id IS NULL");
                else { where.append(" AND e.category_id = ?"); args.add(f.categoryId()); }
            }
            if (f.from() != null) { where.append(" AND e.expense_date >= ?"); args.add(f.from()); }
            if (f.to() != null)   { where.append(" AND e.expense_date <= ?"); args.add(f.to()); }
            if (f.min() != null)  { where.append(" AND e.amount >= ?"); args.add(f.min()); }
            if (f.max() != null)  { where.append(" AND e.amount <= ?"); args.add(f.max()); }
            if (f.q() != null) {
                String like = "%" + f.q().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                where.append(" AND (e.title LIKE ? OR e.note LIKE ?)");
                args.add(like); args.add(like);
            }
            String order = SORTS.getOrDefault(f.sort(), "e.expense_date") + (f.asc() ? " ASC" : " DESC") + ", e.id DESC";

            try (Connection c = Db.get()) {
                int count;
                BigDecimal total;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT COUNT(*), COALESCE(SUM(e.amount), 0) FROM expenses e" + where)) {
                    bindAll(ps, args);
                    try (ResultSet rs = ps.executeQuery()) { rs.next(); count = rs.getInt(1); total = rs.getBigDecimal(2); }
                }
                int pages = Math.max(1, (int) Math.ceil(count / (double) f.size()));
                int page = Math.min(f.page(), pages);
                List<ExpenseView> rows = new ArrayList<>();
                try (PreparedStatement ps = c.prepareStatement(SELECT + where + " ORDER BY " + order + " LIMIT ? OFFSET ?")) {
                    List<Object> a2 = new ArrayList<>(args);
                    a2.add(f.size());
                    a2.add((page - 1) * f.size());
                    bindAll(ps, a2);
                    try (ResultSet rs = ps.executeQuery()) { while (rs.next()) rows.add(map(rs)); }
                }
                return new ExpenseList(rows, total, count, page, pages);
            } catch (SQLException e) { throw Db.wrap(e); }
        }

        public static Optional<ExpenseView> find(int uid, int id) {
            try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(BASE + " AND e.id = ?")) {
                ps.setInt(1, uid); ps.setInt(2, id);
                try (ResultSet rs = ps.executeQuery()) { return rs.next() ? Optional.of(map(rs)) : Optional.empty(); }
            } catch (SQLException e) { throw Db.wrap(e); }
        }

        public static int create(int uid, ExpenseInput in) {
            try (Connection c = Db.get();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO expenses (title, amount, category_id, expense_date, note, user_id) VALUES (?, ?, ?, ?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
                bind(ps, in);
                ps.setInt(6, uid);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) { rs.next(); return rs.getInt(1); }
            } catch (SQLException e) { throw Db.wrap(e); }
        }

        public static boolean update(int uid, int id, ExpenseInput in) {
            try (Connection c = Db.get();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE expenses SET title = ?, amount = ?, category_id = ?, expense_date = ?, note = ? WHERE id = ? AND user_id = ?")) {
                bind(ps, in);
                ps.setInt(6, id); ps.setInt(7, uid);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) { throw Db.wrap(e); }
        }

        public static boolean delete(int uid, int id) {
            try (Connection c = Db.get();
                 PreparedStatement ps = c.prepareStatement("DELETE FROM expenses WHERE id = ? AND user_id = ?")) {
                ps.setInt(1, id); ps.setInt(2, uid);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) { throw Db.wrap(e); }
        }
    }
}
