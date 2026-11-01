package com.example.botservice;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.*;
import static org.springframework.http.HttpMethod.*;

@Component
public class BotCommands {
    private final BackendClient api;
    private final ObjectMapper mapper;

    public BotCommands(BackendClient api, ObjectMapper mapper) {
        this.api = api;
        this.mapper = mapper;
    }

    public String execute(long telegramId, String name, String text) {
        try {
            String[] command = text.trim().split("\\s+", 2);
            String action = command[0].split("@", 2)[0].toLowerCase(Locale.ROOT);
            String args = command.length == 2 ? command[1].trim() : "";
            if (Set.of("/help", "/cancel").contains(action)) return HELP;
            if ("/link".equals(action) && args.isBlank()) return LINK_HELP;
            if ("/link".equals(action) && args.toLowerCase(Locale.ROOT).startsWith("отключить")) {
                if (!"отключить ДА".equalsIgnoreCase(args)) {
                    return "Для отключения: /link отключить ДА. Сначала сохраните другой способ входа в аккаунт.";
                }
                BackendClient.Session session = api.login(telegramId, name, false);
                api.request("users", DELETE, "/auth/telegram", session, null);
                return "Telegram отключён. Для подключения существующего аккаунта: /link код.";
            }
            if ("/link".equals(action) || ("/start".equals(action) && !args.isBlank())) {
                BackendClient.Session session = api.link(telegramId, parts(args, 1)[0]);
                return "Telegram подключён. Ваш ID: " + session.userId() + "\n" + HELP;
            }
            if (!COMMANDS.contains(action)) return "Неизвестная команда.\n" + HELP;
            BackendClient.Session session = api.login(telegramId, name, "/start".equals(action));
            String userPath = "/users/" + session.userId();
            return switch (action) {
                case "/start" -> "Готово. Ваш ID: " + session.userId() + "\n" + HELP;
                case "/id" -> "Ваш ID: " + session.userId();
                case "/me" -> BotFormatter.profile(api.request("users", GET, userPath, session, null));
                case "/profile" -> {
                    String[] p = parts(args, 4);
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("name", p[0]);
                    body.put("timezone", nullable(p[1]));
                    body.put("currency", nullable(p[2]));
                    body.put("amount", "-".equals(p[3]) ? null : new BigDecimal(p[3]));
                    yield BotFormatter.profile(api.request("users", PUT, userPath, session, body));
                }
                case "/currency", "/balance" -> {
                    String value = parts(args, 1)[0];
                    JsonNode profile = api.request("users", GET, userPath, session, null);
                    Map<String, Object> body = new LinkedHashMap<>();
                    for (String field : List.of("name", "timezone", "currency", "amount")) {
                        body.put(field, profile.get(field));
                    }
                    body.put("/currency".equals(action) ? "currency" : "amount",
                            "/currency".equals(action) ? value.toUpperCase(Locale.ROOT) : new BigDecimal(value));
                    yield BotFormatter.profile(api.request("users", PUT, userPath, session, body));
                }
                case "/account_delete" -> {
                    confirm(parts(args, 1)[0]);
                    api.request("users", DELETE, userPath, session, null);
                    yield "Аккаунт удалён.";
                }
                case "/trips" -> BotFormatter.trips(api.request("trips", GET, "/trips", session, null));
                case "/trip" -> BotFormatter.trip(api.request("trips", GET, trip(parts(args, 1)[0]), session, null));
                case "/trip_create" -> {
                    String[] p = parts(args, 3);
                    yield BotFormatter.trip(api.request("trips", POST, "/trips", session,
                            Map.of("name", p[0], "description", p[1], "destinationPoint", p[2])));
                }
                case "/trip_update", "/leg_add", "/leg_remove" -> editTrip(action, args, session);
                case "/trip_delete" -> {
                    String[] p = parts(args, 2);
                    confirm(p[1]);
                    api.request("trips", DELETE, trip(p[0]), session, null);
                    yield "Поездка удалена.";
                }
                case "/members" -> BotFormatter.members(api.request("trips", GET,
                        trip(parts(args, 1)[0]) + "/participants", session, null));
                case "/member_add", "/member_role" -> {
                    String[] p = parts(args, 3);
                    String path = trip(p[0]) + "/participants";
                    Object body;
                    if ("/member_add".equals(action)) {
                        body = Map.of("userId", id(p[1]), "role", role(p[2]));
                    } else {
                        path += "/" + id(p[1]) + "/role";
                        body = Map.of("role", role(p[2]));
                    }
                    yield BotFormatter.trip(api.request("trips",
                            "/member_add".equals(action) ? POST : PUT, path, session, body));
                }
                case "/member_remove" -> {
                    String[] p = parts(args, 2);
                    api.request("trips", DELETE, trip(p[0]) + "/participants/" + id(p[1]), session, null);
                    yield "Участник удалён из поездки.";
                }
                case "/expenses", "/my_expenses", "/summary", "/my_summary" -> {
                    String path = trip(parts(args, 1)[0]) + "/expenses";
                    path += switch (action) {
                        case "/my_expenses" -> "/me";
                        case "/summary" -> "/summary";
                        case "/my_summary" -> "/summary/me";
                        default -> "";
                    };
                    JsonNode response = api.request("expenses", GET, path, session, null);
                    yield action.contains("summary") ? BotFormatter.summary(response) : BotFormatter.expenses(response);
                }
                case "/expense_add" -> {
                    String[] p = parts(args, 3, 4);
                    String[] payment = p[1].split("\\s+");
                    if (payment.length != 2) throw new BotException("Укажите сумму и валюту: 1200 RUB");
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("amount", new BigDecimal(payment[0].replace(',', '.')));
                    body.put("currency", payment[1].toUpperCase(Locale.ROOT));
                    body.put("description", p[2]);
                    if (p.length == 4) {
                        body.put("participantIds", Arrays.stream(p[3].split(",", -1)).map(this::id).toList());
                    }
                    yield BotFormatter.expense(api.request("expenses", POST, trip(p[0]) + "/expenses", session, body));
                }
                case "/expense_delete" -> {
                    String[] p = parts(args, 2);
                    api.request("expenses", DELETE, trip(p[0]) + "/expenses/" + id(p[1]), session, null);
                    yield "Расход удалён. Сводка будет пересчитана сервисом.";
                }
                default -> HELP;
            };
        } catch (BotException ex) {
            return ex.getMessage();
        } catch (IllegalArgumentException | IndexOutOfBoundsException ex) {
            return "Неверный формат аргументов. Разделяйте поля символом |. Формат команд: /help";
        } catch (Exception ex) {
            return "Не удалось обработать команду. Попробуйте позже.";
        }
    }

    private String editTrip(String action, String args, BackendClient.Session session) {
        String[] p = parts(args, "/trip_update".equals(action) ? 3 : "/leg_add".equals(action) ? 7 : 2);
        JsonNode existing = api.request("trips", GET, trip(p[0]), session, null);
        ArrayNode legs = (ArrayNode) existing.path("legs").deepCopy();
        String name = existing.path("name").asText();
        String description = existing.path("description").asText("");
        if ("/trip_update".equals(action)) {
            name = p[1];
            description = p[2];
        } else if ("/leg_add".equals(action)) {
            legs.add(mapper.valueToTree(leg(p, 1)));
        } else {
            int index = Integer.parseInt(p[1]) - 1;
            if (index < 0 || index >= legs.size()) throw new BotException("Нет участка маршрута с таким номером.");
            legs.remove(index);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("description", description);
        body.put("legs", legs);
        if (existing.hasNonNull("destinationPoint")) body.put("destinationPoint", existing.get("destinationPoint"));
        return BotFormatter.trip(api.request("trips", PUT, trip(p[0]), session, body));
    }

    private Map<String, String> leg(String[] p, int start) {
        return Map.of("departurePoint", p[start], "destinationPoint", p[start + 1],
                "departureTime", dateTime(p[start + 2]), "departureZone", p[start + 3],
                "arrivalTime", dateTime(p[start + 4]), "arrivalZone", p[start + 5]);
    }

    private String dateTime(String value) {
        try {
            return LocalDateTime.parse(value).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm")
                        .withResolverStyle(ResolverStyle.STRICT)).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (DateTimeParseException ex) {
                throw new BotException("Дата и время: 01.11.2026 10:00");
            }
        }
    }

    private String[] parts(String args, int count) {
        return parts(args, count, count);
    }

    private String[] parts(String args, int min, int max) {
        String[] parts = Arrays.stream(args.split("\\|", -1)).map(String::trim).toArray(String[]::new);
        if (args.isBlank() || parts.length < min || parts.length > max) {
            throw new BotException("Проверьте поля команды. Примеры: /help");
        }
        return parts;
    }

    private Long id(String text) {
        long value = Long.parseLong(text.trim());
        if (value <= 0) throw new BotException("ID должен быть положительным числом.");
        return value;
    }

    private String trip(String id) { return "/trips/" + id(id); }
    private Object nullable(String value) { return "-".equals(value) ? null : value; }
    private String role(String value) {
        String role = value.toUpperCase(Locale.ROOT);
        return "READONLY".equals(role) ? "READ_ONLY" : role;
    }
    private void confirm(String value) {
        if (!"ДА".equalsIgnoreCase(value)) throw new BotException("Для подтверждения последним аргументом укажите ДА.");
    }

    private static final Set<String> COMMANDS = Set.of("/start", "/id", "/me", "/profile", "/currency",
            "/balance",
            "/account_delete", "/trips", "/trip", "/trip_create", "/trip_update", "/trip_delete",
            "/leg_add", "/leg_remove", "/members", "/member_add", "/member_role", "/member_remove",
            "/expenses", "/my_expenses", "/summary", "/my_summary", "/expense_add",
            "/expense_delete");

    public static final String HELP = """
            Команды TravelApp. Поля разделяются символом |.
            /start — начать работу
            /id — мой ID (сообщите его владельцу поездки)
            /me — профиль
            /profile имя | часовой пояс | валюта | сумма (для пустого поля: -)
            /currency RUB — валюта профиля
            /balance 1000 — сумма в профиле, без конвертации
            /link — подключить существующий аккаунт
            /account_delete ДА — удалить аккаунт

            /trips — мои поездки
            /trip ID — поездка
            /trip_create название | описание | куда
            /trip_update ID | название | описание
            /leg_add ID | откуда | куда | отправление | зона отправления | прибытие | зона прибытия
            /leg_remove ID | номер участка (с 1)
            /trip_delete ID | ДА
            /members ID — участники поездки
            /member_add поездка | userId | роль
            /member_role поездка | userId | роль
            /member_remove поездка | userId
            Роли: OWNER, MEMBER, READ_ONLY.

            /expenses ID — все расходы
            /my_expenses ID — мои расходы
            /summary ID — суммы и долги всех
            /my_summary ID — мои суммы и долги
            /expense_add поездка | сумма ВАЛЮТА | описание
            Например: /expense_add 1 | 1200 RUB | Ужин
            Оплатили вы, дата — сегодня, делим на всех.
            Для выбранных участников добавьте в конец: | 1,2,3
            /expense_delete поездка | расход
            Время участка маршрута: 01.11.2026 10:00.
            Часовой пояс: Europe/Moscow; валюта: RUB, USD, EUR и др.
            """;

    private static final String LINK_HELP = """
            Для работы только в Telegram достаточно /start — вход автоматический.
            Уже есть аккаунт? Получите в нём код подключения Telegram и отправьте /link код.
            Код действует 5 минут. Пароль в чат вводить не нужно.
            Для отключения: /link отключить ДА. Сначала сохраните другой способ входа.
            """;
}
