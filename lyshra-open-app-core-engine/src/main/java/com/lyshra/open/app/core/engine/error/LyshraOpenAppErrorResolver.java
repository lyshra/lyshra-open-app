package com.lyshra.open.app.core.engine.error;

import com.lyshra.open.app.integration.constant.LyshraOpenAppConstants;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowStepOnErrorConfig;
import com.lyshra.open.app.integration.exception.LyshraOpenAppProcessorRuntimeException;

import java.util.Map;
import java.util.concurrent.TimeoutException;

public final class LyshraOpenAppErrorResolver {

    public static String resolveKey(
            Throwable error,
            Map<String, ILyshraOpenAppWorkflowStepOnErrorConfig> errorConfigMap
    ) {

        if (error instanceof LyshraOpenAppProcessorRuntimeException ex && ex.getErrorInfo() != null) {
            String code = ex.getErrorInfo().getErrorCode();
            if (errorConfigMap.containsKey(code)) {
                return code;
            }
        }

        if (error instanceof TimeoutException && errorConfigMap.containsKey(LyshraOpenAppConstants.TIMEOUT_ERROR_CONFIG_KEY)) {
            return LyshraOpenAppConstants.TIMEOUT_ERROR_CONFIG_KEY;
        }

        return LyshraOpenAppConstants.DEFAULT_ERROR_CONFIG_KEY;
    }
}