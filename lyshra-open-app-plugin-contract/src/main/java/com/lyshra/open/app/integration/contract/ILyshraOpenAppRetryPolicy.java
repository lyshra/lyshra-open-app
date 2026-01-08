package com.lyshra.open.app.integration.contract;

public interface ILyshraOpenAppRetryPolicy {
    int getMaxAttempts();
    long getInitialIntervalMs();
    double getMultiplier();
    long getMaxIntervalMs();
}
