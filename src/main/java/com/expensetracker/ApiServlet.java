package com.expensetracker;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;

/** Base class: turns thrown ApiExceptions / DB errors into clean JSON error responses. */
public abstract class ApiServlet extends HttpServlet {

    public static class ApiException extends RuntimeException {
        final int status;
        public ApiException(int status, String message) { super(message); this.status = status; }
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            super.service(req, resp);
        } catch (ApiException e) {
            Json.error(resp, e.status, e.getMessage());
        } catch (Db.DuplicateException e) {
            Json.error(resp, 409, "Already exists");
        } catch (Db.DataAccessException e) {
            log("Database error", e);
            Json.error(resp, 500, "Something went wrong on the server");
        }
    }

    /** The logged-in user's id, taken from the session (never from the request). */
    protected static int userId(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        Object id = s == null ? null : s.getAttribute("userId");
        if (id == null) throw new ApiException(401, "Please log in");
        return (Integer) id;
    }

    /** "/12" -> 12, no path -> null, anything else -> 404. */
    protected static Integer pathId(HttpServletRequest req) {
        String p = req.getPathInfo();
        if (p == null || p.equals("/")) return null;
        try { return Integer.parseInt(p.substring(1)); }
        catch (NumberFormatException e) { throw new ApiException(404, "Not found"); }
    }
}
