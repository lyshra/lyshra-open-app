package com.lyshra.open.app.integration.contract.api;

import java.util.Map;

public interface ILyshraOpenAppApis {
    Map<String, ILyshraOpenAppApiAuthProfile> getAuthProfiles();
    Map<String, ILyshraOpenAppApiService> getServices();
    Map<String, ILyshraOpenAppApiEndpoint> getEndPoints();
}
