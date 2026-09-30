package com.expensetracker;

import com.expensetracker.Models.ExpenseInput;
import com.expensetracker.Models.ExpenseFilter;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** GET/POST /api/expenses, GET/PUT/DELETE /api/expenses/{id}. Always scoped to the session's user. */
@WebServlet("/api/expenses/*")
public class ExpenseServlet extends ApiServlet {
    private static final BigDecimal MAX = new BigDecimal("9999999999.99");

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        int uid = userId(req);
        Integer id = pathId(req);
        if (id != null) {
            Json.write(resp, 200, Dao.Expenses.find(uid, id).orElseThrow(() -> new ApiException(404, "Expense not found")));
            return;
        }
        Json.write(resp, 200, Dao.Expenses.search(uid, filterFrom(req)));
    }

    private static ExpenseFilter filterFrom(HttpServletRequest req) {
        LocalDate from = dateParam(req, "from"), to = dateParam(req, "to");
        if (from != null && to != null && from.isAfter(to)) throw new ApiException(400, "'From' date is after 'To' date");
        String q = req.getParameter("q");
        q = q == null || q.isBlank() ? null : q.trim();
        if (q != null && q.length() > 100) throw new ApiException(400, "Search text is too long (max 100 characters)");
        String sort = req.getParameter("sort") == null ? "date" : req.getParameter("sort");
        boolean asc = "asc".equalsIgnoreCase(req.getParameter("dir"));
        Integer page = intParam(req, "page"), size = intParam(req, "size");
        return new ExpenseFilter(intParam(req, "categoryId"), from, to, decimalParam(req, "min"), decimalParam(req, "max"),
            q, sort, asc, Math.max(1, page == null ? 1 : page), Math.min(50, Math.max(1, size == null ? 10 : size)));
    }

    private static String param(HttpServletRequest req, String name) {
        String v = req.getParameter(name);
        return v == null || v.isBlank() ? null : v.trim();
    }
    private static Integer intParam(HttpServletRequest req, String name) {
        String v = param(req, name);
        try { return v == null ? null : Integer.valueOf(v); }
        catch (NumberFormatException e) { throw new ApiException(400, "Invalid " + name); }
    }
    private static BigDecimal decimalParam(HttpServletRequest req, String name) {
        String v = param(req, name);
        try { return v == null ? null : new BigDecimal(v); }
        catch (NumberFormatException e) { throw new ApiException(400, "Invalid " + name); }
    }
    private static LocalDate dateParam(HttpServletRequest req, String name) {
        String v = param(req, name);
        try { return v == null ? null : LocalDate.parse(v); }
        catch (DateTimeParseException e) { throw new ApiException(400, "Invalid " + name); }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        int uid = userId(req);
        if (pathId(req) != null) throw new ApiException(404, "Not found");
        ExpenseInput in = clean(Json.read(req, ExpenseInput.class), uid);
        int id = Dao.Expenses.create(uid, in);
        Json.write(resp, 201, Dao.Expenses.find(uid, id).orElseThrow());
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        int uid = userId(req);
        Integer id = pathId(req);
        if (id == null) throw new ApiException(400, "Expense id is required");
        ExpenseInput in = clean(Json.read(req, ExpenseInput.class), uid);
        if (!Dao.Expenses.update(uid, id, in)) throw new ApiException(404, "Expense not found");
        Json.write(resp, 200, Dao.Expenses.find(uid, id).orElseThrow());
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        int uid = userId(req);
        Integer id = pathId(req);
        if (id == null) throw new ApiException(400, "Expense id is required");
        if (!Dao.Expenses.delete(uid, id)) throw new ApiException(404, "Expense not found");
        Json.write(resp, 200, new Json.Ok(true));
    }

    private static ExpenseInput clean(ExpenseInput in, int uid) {
        String title = in.title() == null ? "" : in.title().trim();
        if (title.isEmpty() || title.length() > 150) throw new ApiException(400, "Title is required (max 150 characters)");
        if (in.amount() == null || in.amount().compareTo(MAX) > 0 || in.amount().signum() <= 0)
            throw new ApiException(400, "Enter a valid amount greater than 0");
        BigDecimal amount = in.amount().setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() <= 0) throw new ApiException(400, "Enter a valid amount greater than 0");
        if (in.date() == null) throw new ApiException(400, "Date is required");
        String note = in.note() == null || in.note().isBlank() ? null : in.note().trim();
        if (note != null && note.length() > 500) throw new ApiException(400, "Note is too long (max 500 characters)");
        if (in.categoryId() != null && !Dao.Categories.exists(uid, in.categoryId()))
            throw new ApiException(400, "Unknown category");
        return new ExpenseInput(title, amount, in.date(), in.categoryId(), note);
    }
}
