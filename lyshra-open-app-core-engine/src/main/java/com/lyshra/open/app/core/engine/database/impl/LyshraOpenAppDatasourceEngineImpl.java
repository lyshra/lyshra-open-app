package com.lyshra.open.app.core.engine.database.impl;

import com.lyshra.open.app.core.engine.database.ILyshraOpenAppDatasourceEngine;

public class LyshraOpenAppDatasourceEngineImpl implements ILyshraOpenAppDatasourceEngine {

    private LyshraOpenAppDatasourceEngineImpl() {}

    private static final class SingletonHelper {
        private static final ILyshraOpenAppDatasourceEngine INSTANCE = new LyshraOpenAppDatasourceEngineImpl();
    }

    public static ILyshraOpenAppDatasourceEngine getInstance() {
        return LyshraOpenAppDatasourceEngineImpl.SingletonHelper.INSTANCE;
    }

}
