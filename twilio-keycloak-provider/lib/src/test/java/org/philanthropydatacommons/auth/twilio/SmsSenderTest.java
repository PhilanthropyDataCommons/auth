/*
 * Copyright (c) 2023 Open Tech Strategies, LLC
 * License: Expat (MIT) license.
 */
package org.philanthropydatacommons.auth.twilio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SmsSenderTest {
    /** Tests class init via instance creation, so make sure no SmsSender exists yet. */
    @Test void constructSmsThrowsErrorWhenEnvVarsNotPresent() {
        Throwable t = assertThrows(ExceptionInInitializerError.class,
                SmsSender::new,
                "Expected an Error on init because the config was not present.");
        assertEquals(SmsSender.ConfigurationFailedException.class, t.getCause().getClass(),
                "Expected the cause of the Error to be our ConfigurationFailedException.");
    }
}
