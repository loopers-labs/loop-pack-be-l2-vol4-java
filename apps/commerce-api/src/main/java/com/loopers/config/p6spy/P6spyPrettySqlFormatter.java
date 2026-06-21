package com.loopers.config.p6spy;

import com.p6spy.engine.spy.appender.MessageFormattingStrategy;
import org.hibernate.engine.jdbc.internal.FormatStyle;
import org.hibernate.engine.jdbc.internal.Formatter;

public class P6spyPrettySqlFormatter implements MessageFormattingStrategy {

    private static final Formatter FORMATTER = FormatStyle.BASIC.getFormatter();

    @Override
    public String formatMessage(int connectionId, String now, long elapsed,
                                String category, String prepared, String sql, String url) {
        if (sql == null || sql.trim().isEmpty()) {
            return "";
        }
        return "[" + elapsed + "ms]" + FORMATTER.format(sql);
    }
}
