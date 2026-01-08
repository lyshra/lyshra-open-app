package com.lyshra.open.app.core.processors.plugin;

import com.lyshra.open.app.core.processors.plugin.processors.IfProcessor;
import com.lyshra.open.app.core.processors.plugin.processors.JavaScriptProcessor;
import com.lyshra.open.app.core.processors.plugin.processors.list.ListFilterProcessor;
import com.lyshra.open.app.core.processors.plugin.processors.list.ListRemoveDuplicatesProcessor;
import com.lyshra.open.app.core.processors.plugin.processors.SwitchProcessor;
import com.lyshra.open.app.integration.ILyshraOpenAppPluginProvider;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppPlugin;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppPluginFacade;
import com.lyshra.open.app.integration.models.LyshraOpenAppPlugin;
import com.lyshra.open.app.integration.models.LyshraOpenAppPluginIdentifier;

public class LyshraOpenAppCoreProcessorsPlugin implements ILyshraOpenAppPluginProvider {

    public static LyshraOpenAppPluginIdentifier getPluginIdentifier() {
        return new LyshraOpenAppPluginIdentifier("com.lyshra.open.app", "core", "1.0.0");
    }

    @Override
    public ILyshraOpenAppPlugin create(ILyshraOpenAppPluginFacade lyshraOpenAppFacade) {

        return LyshraOpenAppPlugin
                .builder()
                .identifier(getPluginIdentifier())
                .documentationResourcePath("documentation.md")
                .processors(builder -> builder
                        .processor(IfProcessor::build)
                        .processor(SwitchProcessor::build)
                        .processor(JavaScriptProcessor::build)

                         // List operations
                        .processor(ListFilterProcessor::build)
                        .processor(ListRemoveDuplicatesProcessor::build)
                            // sort
                            // summarize : count, min, max, avg, sum

                        // stop & error
                        // sleep, delay
                        // noop
                        // date manipulation
                            // add, minus
                            // if before, is after, is same, is today, is between
                            // is today, is weekend, is weekday, isDayInWeek(list of days mon, tue)
                            // is first day of month, is last day of month, is nth day of month
                            // format a date
                            // extract part of a date

                        // html templates

                        // read & write files
                )
                .i18n(builder -> builder
                        .resourceBundleBasenamePath("i18n/messages")
                )
                .build();
    }

}
