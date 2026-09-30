package com.expensetracker;

import com.expensetracker.Models.User;
import com.expensetracker.Models.UserView;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.mindrot.jbcrypt.BCrypt;

/** POST /api/auth/register | login | logout, GET /api/auth/me */
@WebServlet("/api/auth/*")
public class AuthServlet extends ApiServlet {
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    // Used so a wrong email takes as long as a wrong password (prevents timing-based account discovery).
    private static final String DUMMY_HASH = BCrypt.hashpw("not-a-real-password", BCrypt.gensalt(12));

    public record RegisterInput(String name, String email, String password) {}
    public record LoginInput(String email, String password) {}

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!"/me".equals(req.getPathInfo())) throw new ApiException(404, "Not found");
        UserView u = Dao.Users.findById(userId(req)).orElseThrow(() -> new ApiException(401, "Please log in"));
        Json.write(resp, 200, u);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String action = req.getPathInfo() == null ? "" : req.getPathInfo();
        switch (action) {
            case "/register" -> register(req, resp);
            case "/login" -> login(req, resp);
            case "/logout" -> {
                HttpSession s = req.getSession(false);
                if (s != null) s.invalidate();
                Json.write(resp, 200, new Json.Ok(true));
            }
            default -> throw new ApiException(404, "Not found");
        }
    }

    private void register(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        RegisterInput in = Json.read(req, RegisterInput.class);
        String name = in.name() == null ? "" : in.name().trim();
        String email = normalize(in.email());
        String pw = in.password() == null ? "" : in.password();
        if (name.isEmpty() || name.length() > 100) throw new ApiException(400, "Name is required (max 100 characters)");
        if (email.length() > 255 || !EMAIL.matcher(email).matches()) throw new ApiException(400, "Enter a valid email address");
        if (pw.length() < 8 || pw.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ApiException(400, "Password must be 8 to 72 characters");

        int id;
        try {
            id = Dao.Users.create(name, email, BCrypt.hashpw(pw, BCrypt.gensalt(12)));
        } catch (Db.DuplicateException e) {
            throw new ApiException(409, "That email is already registered");
        }
        Dao.Categories.createDefaults(id);
        startSession(req, id);
        Json.write(resp, 201, new UserView(id, name, email));
    }

    private void login(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        LoginInput in = Json.read(req, LoginInput.class);
        if (in.email() == null || in.password() == null) throw new ApiException(400, "Email and password are required");
        String email = normalize(in.email());
        String key = email + "|" + req.getRemoteAddr();
        if (Throttle.isLocked(key)) throw new ApiException(429, "Too many failed attempts. Try again in a few minutes.");

        Optional<User> user = Dao.Users.findByEmail(email);
        boolean ok = BCrypt.checkpw(in.password(), user.map(User::passwordHash).orElse(DUMMY_HASH)) && user.isPresent();
        if (!ok) {
            Throttle.fail(key);
            throw new ApiException(401, "Invalid email or password");
        }
        Throttle.clear(key);
        startSession(req, user.get().id());
        Json.write(resp, 200, new UserView(user.get().id(), user.get().name(), user.get().email()));
    }

    /** Always issue a brand-new session on login (prevents session fixation). */
    private static void startSession(HttpServletRequest req, int userId) {
        HttpSession old = req.getSession(false);
        if (old != null) old.invalidate();
        HttpSession s = req.getSession(true);
        s.setAttribute("userId", userId);
        s.setMaxInactiveInterval(30 * 60);
    }

    private static String normalize(String email) { return email == null ? "" : email.trim().toLowerCase(); }

    /** 5 failed logins per email+IP locks that pair for 5 minutes (in memory; resets on restart). */
    private static final class Throttle {
        private record State(int fails, long start) {}
        private static final int MAX = 5;
        private static final long WINDOW_MS = 5 * 60_000L;
        private static final ConcurrentHashMap<String, State> MAP = new ConcurrentHashMap<>();

        static boolean isLocked(String key) {
            State s = MAP.get(key);
            return s != null && System.currentTimeMillis() - s.start() <= WINDOW_MS && s.fails() >= MAX;
        }
        static void fail(String key) {
            long now = System.currentTimeMillis();
            if (MAP.size() > 10_000) MAP.values().removeIf(s -> now - s.start() > WINDOW_MS);
            MAP.compute(key, (k, s) -> s == null || now - s.start() > WINDOW_MS ? new State(1, now) : new State(s.fails() + 1, s.start()));
        }
        static void clear(String key) { MAP.remove(key); }
    }
}
