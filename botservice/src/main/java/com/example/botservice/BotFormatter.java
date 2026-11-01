package com.example.botservice;

import tools.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class BotFormatter {
    private BotFormatter() {}

    public static String profile(JsonNode p) {
        return "Пользователь #" + value(p, "id") + ": " + value(p, "name")
                + "\nЧасовой пояс: " + value(p, "timezone") + "\nВалюта: " + value(p, "currency")
                + "\nСумма профиля: " + value(p, "amount");
    }

    public static String trips(JsonNode items) {
        if (items.isEmpty()) return "Поездок пока нет. Создать: /trip_create";
        StringBuilder out = new StringBuilder("Мои поездки:\n");
        items.forEach(t -> out.append("#").append(value(t, "id")).append(" — ")
                .append(value(t, "name")).append("\n"));
        return out.toString();
    }

    public static String trip(JsonNode t) {
        StringBuilder out = new StringBuilder("Поездка #" + value(t, "id") + ": " + value(t, "name")
                + "\n" + value(t, "description") + "\nКуда: " + value(t, "destinationPoint") + "\nМаршрут:\n");
        if (t.path("legs").isEmpty()) out.append("Пока не заполнен. Добавить участок: /leg_add\n");
        int index = 1;
        for (JsonNode leg : t.path("legs")) {
            out.append(index++).append(". ").append(value(leg, "departurePoint")).append(" → ")
                    .append(value(leg, "destinationPoint")).append("\n")
                    .append(dateTime(value(leg, "departureTime"))).append(" (").append(value(leg, "departureZone"))
                    .append(") → ").append(dateTime(value(leg, "arrivalTime"))).append(" (")
                    .append(value(leg, "arrivalZone")).append(")\n");
        }
        return out.append(members(t.path("participants"))).toString();
    }

    public static String members(JsonNode participants) {
        StringBuilder out = new StringBuilder("Участники:\n");
        participants.forEach(p -> out.append("#").append(value(p, "userId")).append(" — ")
                .append(value(p, "role")).append("\n"));
        return out.toString();
    }

    public static String expense(JsonNode e) {
        StringBuilder out = new StringBuilder("Расход #" + value(e, "id") + ": " + value(e, "description")
                + "\n" + value(e, "spentOn") + "; " + value(e, "amount") + " " + value(e, "currency")
                + "\nОплатил #" + value(e, "paidBy") + "; запись добавил #" + value(e, "createdBy") + "\nДоли:\n");
        e.path("shares").forEach(s -> out.append("#").append(value(s, "userId")).append(": ")
                .append(value(s, "amount")).append(" ").append(value(e, "currency")).append("\n"));
        return out.toString();
    }

    public static String expenses(JsonNode items) {
        if (items.isEmpty()) return "Расходов пока нет.";
        StringBuilder out = new StringBuilder();
        items.forEach(e -> out.append(expense(e)).append("\n"));
        return out.toString();
    }

    public static String summary(JsonNode currencies) {
        if (currencies.isEmpty()) return "Расходов пока нет.";
        StringBuilder out = new StringBuilder();
        currencies.forEach(c -> {
            out.append(value(c, "currency")).append("\n");
            c.path("participants").forEach(p -> out.append("#").append(value(p, "userId"))
                    .append(": оплатил ").append(value(p, "paid")).append(", доля ").append(value(p, "share"))
                    .append(", баланс ").append(value(p, "balance")).append("\n"));
            out.append("Долги (должник → получатель):\n");
            if (c.path("debts").isEmpty()) out.append("Нет долгов.\n");
            c.path("debts").forEach(d -> out.append("#").append(value(d, "fromUserId")).append(" → #")
                    .append(value(d, "toUserId")).append(": ").append(value(d, "amount")).append("\n"));
        });
        return out.toString();
    }

    private static String value(JsonNode node, String field) {
        return node.path(field).asText("—");
    }

    private static String dateTime(String value) {
        try {
            return LocalDateTime.parse(value).format(DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm"));
        } catch (java.time.format.DateTimeParseException ex) {
            return value;
        }
    }
}
