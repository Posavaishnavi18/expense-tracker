package com.expensetracker;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class Models {
    private Models() {}
    public record User(int id, String name, String email, String passwordHash) {}
    public record UserView(int id, String name, String email) {}
    public record Category(int id, String name, String color) {}
    public record ExpenseInput(String title, BigDecimal amount, LocalDate date, Integer categoryId, String note) {}
    public record ExpenseView(int id, String title, BigDecimal amount, LocalDate date, String note,
                              Integer categoryId, String categoryName, String categoryColor) {}
    public record ExpenseList(List<ExpenseView> expenses, BigDecimal total, int count, int page, int pages) {}
    public record ExpenseFilter(Integer categoryId, LocalDate from, LocalDate to, BigDecimal min, BigDecimal max,
                                String q, String sort, boolean asc, int page, int size) {}
}
