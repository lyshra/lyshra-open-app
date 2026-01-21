package com.lyshra.open.app.integration.contract.api;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppRetryPolicy;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHttpMethod;

import java.util.Map;

public interface ILyshraOpenAppApiEndpoint {
    String getName();
    String getDescription();
    String getServiceName();
    LyshraOpenAppHttpMethod getMethod();
    String getPath();
    Map<String, String> getHeaders();
    Map<String, String> getPathVariables();
    Map<String, String> getQueryParams();
    Object getBody();
    int getTimeoutMs();
    Class getOutputType();
    ILyshraOpenAppApiResponseStatusIdentifier getResponseStatusIdentifier();
    ILyshraOpenAppRetryPolicy getRetryPolicy();
}
