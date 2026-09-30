package com.expensetracker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.time.LocalDate;

public final class Json {
    private Json() {}

    public record ErrorBody(String error) {}
    public record Ok(boolean ok) {}

    private static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(LocalDate.class, new TypeAdapter<LocalDate>() {
            @Override public void write(JsonWriter out, LocalDate v) throws IOException {
                if (v == null) out.nullValue(); else out.value(v.toString());
            }
            @Override public LocalDate read(JsonReader in) throws IOException {
                if (in.peek() == JsonToken.NULL) { in.nextNull(); return null; }
                return LocalDate.parse(in.nextString());
            }
        }).create();

    /** Parses the request body; any malformed input becomes a 400. */
    public static <T> T read(HttpServletRequest req, Class<T> type) throws IOException {
        try (BufferedReader r = req.getReader()) {
            T v = GSON.fromJson(r, type);
            if (v == null) throw new ApiServlet.ApiException(400, "Request body is required");
            return v;
        } catch (ApiServlet.ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ApiServlet.ApiException(400, "Invalid request body");
        }
    }

    public static void write(HttpServletResponse resp, int status, Object body) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.getWriter().write(GSON.toJson(body));
    }

    public static void error(HttpServletResponse resp, int status, String message) throws IOException {
        write(resp, status, new ErrorBody(message));
    }
}
