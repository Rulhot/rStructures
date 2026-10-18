package ru.rulhot.rStructures.util;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@UtilityClass
public class TimeUtil {

    private final @NotNull Pattern DURATION = Pattern.compile("(\\d{1,9})\\s*([smhd]?)");

    public long parseSeconds(@NotNull String raw) {
        Matcher matcher = DURATION.matcher(raw.trim().toLowerCase(Locale.ROOT));
        if (!matcher.matches()) {
            return -1L;
        }
        long value = Long.parseLong(matcher.group(1));
        return switch (matcher.group(2)) {
            case "m" -> TimeUnit.MINUTES.toSeconds(value);
            case "h" -> TimeUnit.HOURS.toSeconds(value);
            case "d" -> TimeUnit.DAYS.toSeconds(value);
            default -> value;
        };
    }

    public @NotNull String format(
            long totalSeconds,
            @NotNull String dayUnit,
            @NotNull String hourUnit,
            @NotNull String minuteUnit,
            @NotNull String secondUnit
    ) {
        long seconds = Math.max(0L, totalSeconds);
        long days = TimeUnit.SECONDS.toDays(seconds);
        long hours = TimeUnit.SECONDS.toHours(seconds) % 24L;
        long minutes = TimeUnit.SECONDS.toMinutes(seconds) % 60L;
        long rest = seconds % 60L;

        StringJoiner joiner = new StringJoiner(" ");
        if (days > 0L) {
            joiner.add(days + " " + dayUnit);
        }
        if (hours > 0L) {
            joiner.add(hours + " " + hourUnit);
        }
        if (minutes > 0L) {
            joiner.add(minutes + " " + minuteUnit);
        }
        if (rest > 0L || joiner.length() == 0) {
            joiner.add(rest + " " + secondUnit);
        }
        return joiner.toString();
    }
}
