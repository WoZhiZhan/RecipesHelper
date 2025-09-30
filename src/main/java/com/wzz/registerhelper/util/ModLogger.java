package com.wzz.registerhelper.util;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public class ModLogger {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static Logger getLogger() {
        return LOGGER;
    }

    public static void println(String format) {
        LOGGER.info(format);
    }
}
