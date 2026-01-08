package com.lyshra.open.app.core.engine.message;

import java.util.Locale;
import java.util.Map;

public interface ILyshraOpenAppPluginMessageSource {

    default String getMessage(String messageTemplate, Locale locale) {
        return getMessage(messageTemplate, locale, Map.of());
    }

    String getMessage(String messageTemplate, Locale locale, Map<String, String> arguments);
}
