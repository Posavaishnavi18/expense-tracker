package com.expensetracker;

import com.expensetracker.Models.Category;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;

/** GET/POST /api/categories, PUT/DELETE /api/categories/{id}. Deleting a category leaves its expenses uncategorized. */
@WebServlet("/api/categories/*")
public class CategoryServlet extends ApiServlet {
    private static final Pattern COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");
    public record CategoryInput(String name, String color) {}

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Json.write(resp, 200, Dao.Categories.list(userId(req)));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        int uid = userId(req);
        if (pathId(req) != null) throw new ApiException(404, "Not found");
        CategoryInput in = Json.read(req, CategoryInput.class);
        String name = cleanName(in), color = cleanColor(in);
        try {
            Category c = Dao.Categories.create(uid, name, color);
            Json.write(resp, 201, c);
        } catch (Db.DuplicateException e) {
            throw new ApiException(409, "You already have a category with that name");
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        int uid = userId(req);
        Integer id = pathId(req);
        if (id == null) throw new ApiException(400, "Category id is required");
        CategoryInput in = Json.read(req, CategoryInput.class);
        String name = cleanName(in), color = cleanColor(in);
        try {
            if (!Dao.Categories.update(uid, id, name, color)) throw new ApiException(404, "Category not found");
        } catch (Db.DuplicateException e) {
            throw new ApiException(409, "You already have a category with that name");
        }
        Json.write(resp, 200, new Category(id, name, color));
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        int uid = userId(req);
        Integer id = pathId(req);
        if (id == null) throw new ApiException(400, "Category id is required");
        if (!Dao.Categories.delete(uid, id)) throw new ApiException(404, "Category not found");
        Json.write(resp, 200, new Json.Ok(true));
    }

    private static String cleanName(CategoryInput in) {
        String name = in.name() == null ? "" : in.name().trim();
        if (name.isEmpty() || name.length() > 50) throw new ApiException(400, "Category name is required (max 50 characters)");
        return name;
    }

    private static String cleanColor(CategoryInput in) {
        String color = in.color() == null ? "#6366f1" : in.color().trim();
        if (!COLOR.matcher(color).matches()) throw new ApiException(400, "Color must look like #1a2b3c");
        return color.toLowerCase();
    }
}
