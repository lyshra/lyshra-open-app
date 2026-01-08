package com.lyshra.open.app.integration.contract.api;

import com.lyshra.open.app.integration.enumerations.LyshraOpenAppApiAuthType;

public interface ILyshraOpenAppApiAuthProfile {
    String getName();
    LyshraOpenAppApiAuthType getType();
}
