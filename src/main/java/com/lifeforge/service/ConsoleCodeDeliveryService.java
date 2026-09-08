package com.lifeforge.service;

import com.lifeforge.config.AppConfig;

/**
 * Development/testing delivery channel: prints the verification code to the
 * application console ONLY when LIFEFORGE_DEV_CODE_LOG (or the
 * lifeforge.dev.code.log system property) is explicitly enabled.
 *
 * <p>With no email provider configured this is how a local tester learns the
 * code. In production (the flag is off) this service produces no output and
 * never exposes the code in the UI.
 */
public class ConsoleCodeDeliveryService implements CodeDeliveryService {

    @Override
    public void deliver(String destination, String code) {
        if (!AppConfig.isDevResetCodeLoggingEnabled()) {
            return;
        }
        System.out.println("[LifeForge DEV] password-reset code for "
                + destination + ": " + code);
    }
}