package com.lyshra.open.app.core.engine.message;

import com.ibm.icu.text.MessageFormat;
import com.lyshra.open.app.core.util.CommonUtil;
import com.lyshra.open.app.integration.contract.i18n.ILyshraOpenAppI18nConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

@Slf4j
public final class LyshraOpenAppPluginMessageSource implements ILyshraOpenAppPluginMessageSource {
    private final MessageSource messageSource;

    public LyshraOpenAppPluginMessageSource(
            ILyshraOpenAppI18nConfig i18nConfig,
            ClassLoader pluginClassLoader) {
        this.messageSource = createMessageSource(i18nConfig.getResourceBundleBasenamePath(), pluginClassLoader);
    }

    private MessageSource createMessageSource(String baseName, ClassLoader pluginClassLoader) {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBundleClassLoader(pluginClassLoader);
        messageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());
        messageSource.setBasename(baseName);
        return messageSource;
    }

    @Override
    public String getMessage(String messageTemplate, Locale locale, Map<String, String> arguments) {
        String message = messageTemplate;
        if (CommonUtil.isNotBlank(messageTemplate)) {
            locale = (locale != null) ? locale : Locale.getDefault();
            try {
                // passing null in args, as we are using ICU4J formatter which allows named parameter & more
                message = messageSource.getMessage(messageTemplate, null, locale);
                if (!CommonUtil.nonNullMap(arguments).isEmpty()) {
                    MessageFormat mf = new MessageFormat(message, locale);
                    message = mf.format(arguments);
                }
            } catch (Exception e) {
                log.trace("Failed to load message bundle for key: [{}], error", messageTemplate, e);
            }
        }
        return message;
    }

}
