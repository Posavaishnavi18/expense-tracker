package com.expensetracker;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;

/** Session gate: /api/* needs a logged-in session (except login/register/logout); dashboard.html redirects to login. */
@WebFilter(urlPatterns = {"/api/*", "/dashboard.html"})
public class AuthFilter implements Filter {
    @Override
    public void doFilter(ServletRequest sreq, ServletResponse sres, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) sreq;
        HttpServletResponse resp = (HttpServletResponse) sres;
        req.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        resp.setHeader("X-Content-Type-Options", "nosniff");

        String path = req.getRequestURI().substring(req.getContextPath().length());
        HttpSession s = req.getSession(false);
        boolean loggedIn = s != null && s.getAttribute("userId") != null;

        if (!path.startsWith("/api/")) {
            if (loggedIn) chain.doFilter(req, resp);
            else resp.sendRedirect(req.getContextPath() + "/login.html");
            return;
        }

        // CSRF defence in depth: browsers won't let other sites add a custom header to a cross-site request.
        String m = req.getMethod();
        boolean safe = m.equals("GET") || m.equals("HEAD") || m.equals("OPTIONS");
        if (!safe && req.getHeader("X-Requested-With") == null) {
            Json.error(resp, 403, "Missing X-Requested-With header");
            return;
        }

        boolean open = path.equals("/api/auth/login") || path.equals("/api/auth/register") || path.equals("/api/auth/logout");
        if (open || loggedIn) chain.doFilter(req, resp);
        else Json.error(resp, 401, "Please log in");
    }
}
