package com.lyshra.open.app.core.engine.documentation.impl;

import com.lyshra.open.app.core.engine.ILyshraOpenAppFacade;
import com.lyshra.open.app.core.engine.LyshraOpenAppFacade;
import com.lyshra.open.app.core.engine.documentation.ILyshraOpenAppPluginDocumentationService;
import com.lyshra.open.app.core.engine.plugin.ILyshraOpenAppPluginDescriptor;
import com.lyshra.open.app.core.engine.plugin.ILyshraOpenAppPluginFactory;
import com.lyshra.open.app.core.exception.plugin.LyshraOpenAppPluginDocumentationLoadFailed;
import com.lyshra.open.app.core.exception.plugin.LyshraOpenAppPluginDocumentationNotFound;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppPlugin;
import com.lyshra.open.app.integration.models.LyshraOpenAppPluginIdentifier;
import com.vladsch.flexmark.ext.admonition.AdmonitionExtension;
import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.definition.DefinitionExtension;
import com.vladsch.flexmark.ext.footnotes.FootnoteExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;
import com.vladsch.flexmark.ext.superscript.SuperscriptExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.ext.toc.TocExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;

public class LyshraOpenAppPluginDocumentationService implements ILyshraOpenAppPluginDocumentationService {
    private final ILyshraOpenAppFacade facade;
    private final Parser parser;
    private final HtmlRenderer renderer;

    private LyshraOpenAppPluginDocumentationService() {
        this.facade = LyshraOpenAppFacade.getInstance();
        MutableDataSet options = new MutableDataSet();
        options.set(Parser.EXTENSIONS, List.of(
                TablesExtension.create(),
                StrikethroughExtension.create(),
                TaskListExtension.create(),
                FootnoteExtension.create(),
                TocExtension.create(),
                DefinitionExtension.create(),
                AdmonitionExtension.create(),
                SuperscriptExtension.create(),
                AutolinkExtension.create()
        ));
        parser = Parser.builder(options).build();
        renderer = HtmlRenderer.builder(options).build();
    }

    private static class SingletonHolder {
        private static final LyshraOpenAppPluginDocumentationService INSTANCE = new LyshraOpenAppPluginDocumentationService();
    }

    public static LyshraOpenAppPluginDocumentationService getInstance() {
        return SingletonHolder.INSTANCE;
    }

    @Override
    public String getMarkdownDocumentation(LyshraOpenAppPluginIdentifier identifier) {
        ILyshraOpenAppPluginFactory pluginFactory = facade.getPluginFactory();
        ILyshraOpenAppPluginDescriptor pluginDescriptor = pluginFactory.getPluginDescriptor(identifier);
        ILyshraOpenAppPlugin plugin = pluginDescriptor.getPlugin();
        String documentation = plugin.getDocumentationResourcePath();

        try(InputStream inputStream = pluginDescriptor.getClassLoader().findResource(documentation).openStream()) {
            // InputStream to string
            if (inputStream == null) {
                throw new LyshraOpenAppPluginDocumentationNotFound(identifier);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new LyshraOpenAppPluginDocumentationLoadFailed(identifier, e);
        }
    }

    @Override
    public String getHtmlDocumentation(LyshraOpenAppPluginIdentifier identifier) {
        return renderer.render(parser.parse(getMarkdownDocumentation(identifier)));
    }

    @Override
    public String getWrappedDocumentation(LyshraOpenAppPluginIdentifier identifier) {
        return getWrappedDocumentation0(getHtmlDocumentation(identifier), HtmlWrapper::githubLike);
    }

    @Override
    public String getWrappedDocumentation(LyshraOpenAppPluginIdentifier identifier, Function<String, String> wrapperFunction) {
        return getWrappedDocumentation0(getHtmlDocumentation(identifier), wrapperFunction);
    }

    private String getWrappedDocumentation0(String htmlDocumentation, Function<String, String> wrapperFunction) {
        return wrapperFunction.apply(htmlDocumentation);
    }

    private static class HtmlWrapper {

        private HtmlWrapper() {}

        public static String githubLike(String markdownHtml) {
            return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <title>Markdown Preview</title>

            <!-- GitHub Markdown CSS -->
            <link rel="stylesheet"
                  href="https://cdnjs.cloudflare.com/ajax/libs/github-markdown-css/5.5.0/github-markdown.min.css">

            <!-- Syntax Highlighting -->
            <link rel="stylesheet"
                  href="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/github.min.css">
            <script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/highlight.min.js"></script>
            <script>hljs.highlightAll();</script>

            <style>
                body {
                    background: #ffffff;
                }
                .markdown-body {
                    box-sizing: border-box;
                    min-width: 200px;
                    max-width: 980px;
                    margin: 0 auto;
                    padding: 45px;
                }
            </style>
        </head>
        <body>
            <article class="markdown-body">
                %s
            </article>
        </body>
        </html>
        """.formatted(markdownHtml);
        }

    }
}
