package com.lyshra.open.app.integration.contract;

import com.lyshra.open.app.integration.contract.api.ILyshraOpenAppApis;
import com.lyshra.open.app.integration.contract.i18n.ILyshraOpenAppI18nConfig;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessors;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflows;

public interface ILyshraOpenAppPlugin {
    ILyshraOpenAppPluginIdentifier getIdentifier();
    String getDocumentationResourcePath();
    ILyshraOpenAppApis getApis();
    ILyshraOpenAppProcessors getProcessors();
    ILyshraOpenAppWorkflows getWorkflows();
    ILyshraOpenAppI18nConfig getI18n();
}
