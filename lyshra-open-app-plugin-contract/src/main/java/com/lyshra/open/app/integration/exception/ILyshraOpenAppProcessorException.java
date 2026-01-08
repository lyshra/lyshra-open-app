package com.lyshra.open.app.integration.exception;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppErrorInfo;

import java.util.Map;

public interface ILyshraOpenAppProcessorException {
    ILyshraOpenAppErrorInfo getErrorInfo();
    Map<String, String> getTemplateVariables();
    Throwable getRootCause();
    Object getAdditionalInfo();
}
