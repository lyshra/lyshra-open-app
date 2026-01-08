package com.lyshra.open.app.core.engine.documentation;

import com.lyshra.open.app.integration.models.LyshraOpenAppPluginIdentifier;

import java.util.function.Function;

public interface ILyshraOpenAppPluginDocumentationService {
    String getMarkdownDocumentation(LyshraOpenAppPluginIdentifier identifier);
    String getHtmlDocumentation(LyshraOpenAppPluginIdentifier identifier);
    String getWrappedDocumentation(LyshraOpenAppPluginIdentifier identifier);
    String getWrappedDocumentation(LyshraOpenAppPluginIdentifier identifier, Function<String, String> wrapperFunction);
}
