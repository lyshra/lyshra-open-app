package com.lyshra.open.app.core.processors.plugin;

import com.lyshra.open.app.core.processors.plugin.processors.IfProcessor;
import com.lyshra.open.app.core.processors.plugin.processors.JavaScriptProcessor;
import com.lyshra.open.app.core.processors.plugin.processors.SwitchProcessor;
import com.lyshra.open.app.core.processors.plugin.processors.api.ApiProcessor;
import com.lyshra.open.app.core.processors.plugin.processors.SwitchProcessor;
import com.lyshra.open.app.core.processors.plugin.processors.date.DateAddProcessor;
import com.lyshra.open.app.core.processors.plugin.processors.date.DateCompareProcessor;
import com.lyshra.open.app.core.processors.plugin.processors.list.ListCustomComparatorSortProcessor;
import com.lyshra.open.app.core.processors.plugin.processors.list.ListFilterProcessor;
import com.lyshra.open.app.core.processors.plugin.processors.list.ListRemoveDuplicatesProcessor;
import com.lyshra.open.app.core.processors.plugin.processors.list.ListSortProcessor;
import com.lyshra.open.app.core.processors.plugin.processors.list.ListSummarizeProcessor;
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

                        // API operations
                        .processor(ApiProcessor::build)

                        // List operations
                        .processor(ListFilterProcessor::build)
                        .processor(ListRemoveDuplicatesProcessor::build)
                        .processor(ListSortProcessor::build)
                        .processor(ListCustomComparatorSortProcessor::build)
                        .processor(ListSummarizeProcessor::build)

                        // Date operations
                        .processor(DateAddProcessor::build)
                        .processor(DateCompareProcessor::build)
                            // TODO: is between, is today, is weekend, is weekday, isDayInWeek(list of days mon, tue)
                            // TODO: is first day of month, is last day of month, is nth day of month
                            // TODO: format a date
                            // TODO: extract part of a date

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
