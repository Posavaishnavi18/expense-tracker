package com.expensetracker;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.SessionCookieConfig;
import jakarta.servlet.annotation.WebListener;

@WebListener
public class AppListener implements ServletContextListener {
    @Override
    public void contextInitialized(ServletContextEvent sce) {
        Db.init(); // fails fast (app won't start) if MySQL is unreachable
        SessionCookieConfig cookie = sce.getServletContext().getSessionCookieConfig();
        cookie.setHttpOnly(true);
        cookie.setAttribute("SameSite", "Lax");
        // When you deploy behind HTTPS, also call: cookie.setSecure(true);
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) { Db.close(); }
}
