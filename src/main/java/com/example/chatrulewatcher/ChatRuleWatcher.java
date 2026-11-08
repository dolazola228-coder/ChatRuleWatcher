package com.example.chatrulewatcher;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import com.mojang.authlib.GameProfile;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class ChatRuleWatcher implements ClientModInitializer {
    private static final MinecraftClient MC = MinecraftClient.getInstance();
    private static final Map<String, RepeatState> REPEATS = new ConcurrentHashMap<>();
    private static final Pattern URL_OR_IP = Pattern.compile("(?i)(https?://|www\\.|discord\\.gg/|t\\.me/|[a-z0-9-]+\\.(com|net|org|ru|gg|me)\\b|\\b(?:\\d{1,3}\\.){3}\\d{1,3}:?\\d{0,5}\\b)");
    private static final Pattern WORDS = Pattern.compile("[\\p{L}\\p{N}]+", Pattern.UNICODE_CHARACTER_CLASS);

    // Rule 3.1 exclusions from the server's rules image. These words are intentionally ignored
    // as standalone insults; they do not disable detection of other insults in the same message.
    private static final String[] EXCEPTIONS = {
        "ноль", "нулина", "нуб", "бот", "рак", "слитый", "слабый", "анскил", "нефор",
        "позорище", "лох", "дурак", "дурень", "бездарь", "лоускил", "руинер", "чушпан",
        "тюфяк", "шляпа", "тормоз", "воздухан", "чайник", "трус", "сопляк"
    };
    private static final String[] INSULTS = {
        "идиот", "кретин", "мразь", "урод", "дебил", "ничтожество", "сука", "тварь",
        "мудак", "гнида", "пидор", "пидорас", "еблан", "чмо", "гондон", "залупа"
    };
    private static final String[] RELATIVES = {
        "мать", "мама", "мамка", "мамку", "мамой", "отец", "папа", "папка", "батя",
        "родители", "родных", "сестра", "брат", "бабушка", "дедушка", "семья"
    };
    private static final String[] POLITICS = {
        "политика", "политический", "выборы", "президент", "война", "национальность",
        "нацист", "фашист", "расист", "нация", "народ", "религия", "террорист"
    };
    private static final String[] SERVER_CRITICISM = {
        "сервер говно", "сервер плохой", "плохой сервер", "админы говно", "администрация говно",
        "админ тупой", "модеры тупые", "модерация говно", "худший сервер", "сервер лагает",
        "админы бесполезны", "администрация бесполезна"
    };
    private static final String[] IMPERSONATION = {
        "я админ", "я администратор", "я модератор", "я владелец", "я создатель сервера",
        "я из администрации", "я helper", "я staff", "я оператор", "я оп"
    };
    private static final String[] PROVOCATION = {
        "нарушай правила", "обойди правила", "ломай правила", "давайте заспамим",
        "спровоцируй", "получи мут", "сделай так чтобы забанили", "обойди мут", "обойди бан"
    };
    private static final String[] BEGGING = {
        "дай денег", "дайте денег", "дай ресурсы", "дай вещи", "подари", "подарите",
        "дай донат", "купи мне", "выдай мне", "можно бесплатно", "дай ранг", "дай привилегию",
        "скинь денег", "пожалуйста дай"
    };

    @Override
    public void onInitializeClient() {
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            if (sender == null || message == null) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;
            if (sender.getId() != null && sender.getId().equals(client.player.getUuid())) return;
            String raw = message.getString();
            String normalized = normalize(raw);
            if (normalized.isBlank()) return;

            String rule = detectRule(sender.getName(), raw, normalized, receptionTimestamp == null ? Instant.now() : receptionTimestamp);
            if (rule != null) notifyModerator(sender.getName(), rule, raw);
        });
    }

    private static String detectRule(String sender, String raw, String normalized, Instant now) {
        if (isCapsViolation(raw)) return "3.2 — Более 70% CAPS";

        if (containsPhrase(normalized, IMPERSONATION)) return "3.7 — Выдавать себя за администрацию";
        if (URL_OR_IP.matcher(raw).find()) return "3.5 — Возможная реклама другого проекта";
        if (containsPhrase(normalized, SERVER_CRITICISM)) return "3.6 — Критика сервера/администрации";
        if (containsPhrase(normalized, PROVOCATION)) return "3.9 — Провокация на нарушение правил";
        if (containsPhrase(normalized, POLITICS)) return "3.8 — Возможная политическая агитация/рознь";
        if (containsAnyWord(normalized, RELATIVES)) return "3.3 — Упоминание родных";
        if (hasNonExcludedInsult(normalized)) return "3.1 — Оскорбление игроков";
        if (containsPhrase(normalized, BEGGING)) return "3.10 — Попрошайничество";
        if (isSpam(sender, normalized, now)) return "3.4 — Флуд или спам";
        return null;
    }

    private static boolean isCapsViolation(String raw) {
        int letters = 0, uppercase = 0;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isLetter(c)) {
                letters++;
                if (Character.isUpperCase(c)) uppercase++;
            }
        }
        return letters >= 8 && uppercase * 100.0 / letters > 70.0;
    }

    private static boolean hasNonExcludedInsult(String normalized) {
        for (String insult : INSULTS) {
            if (containsWord(normalized, insult)) {
                // If an excluded term is present too, it is ignored; a separate listed insult still counts.
                return true;
            }
        }
        return false;
    }

    private static boolean containsAnyWord(String text, String[] words) {
        for (String word : words) if (containsWord(text, word)) return true;
        return false;
    }

    private static boolean containsPhrase(String text, String[] phrases) {
        for (String phrase : phrases) if (text.contains(phrase)) return true;
        return false;
    }

    private static boolean containsWord(String text, String word) {
        return Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(word) + "(?![\\p{L}\\p{N}])", Pattern.UNICODE_CHARACTER_CLASS).matcher(text).find();
    }

    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replace('ё', 'е').replaceAll("§[0-9a-fk-or]", "").replaceAll("\\s+", " ").trim();
    }

    private static boolean isSpam(String sender, String message, Instant now) {
        long current = now.toEpochMilli();
        RepeatState state = REPEATS.computeIfAbsent(sender, key -> new RepeatState());
        synchronized (state) {
            while (!state.times.isEmpty() && current - state.times.peekFirst() > 8000) state.times.removeFirst();
            if (message.equals(state.lastMessage)) state.sameCount++; else state.sameCount = 1;
            state.lastMessage = message;
            state.times.addLast(current);
            while (state.times.size() > 10) state.times.removeFirst();
            return state.sameCount >= 3 || state.times.size() >= 6;
        }
    }

    private static void notifyModerator(String player, String rule, String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        String excerpt = message.replaceAll("\\s+", " ").trim();
        if (excerpt.length() > 100) excerpt = excerpt.substring(0, 97) + "...";
        client.player.sendMessage(Text.literal("§c[МОДЕРАЦИЯ] §f" + player + " §7— §e" + rule + "\n§8Сообщение: §7" + excerpt), false);
    }

    private static final class RepeatState {
        private String lastMessage = "";
        private int sameCount = 0;
        private final Deque<Long> times = new ArrayDeque<>();
    }
}
