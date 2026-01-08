package com.lyshra.open.app.integration.contract;

import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHttpStatus;

public interface ILyshraOpenAppErrorInfo {
    String getErrorCode();
    LyshraOpenAppHttpStatus getHttpStatus();
    String getErrorTemplate();
    String getResolutionTemplate();
}
