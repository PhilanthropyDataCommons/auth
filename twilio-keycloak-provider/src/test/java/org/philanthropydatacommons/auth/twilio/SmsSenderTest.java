/*
 * Copyright (c) 2023-2026 MacArthur Foundation
 * License: Expat (MIT) license.
 */

package org.philanthropydatacommons.auth.twilio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SmsSenderTest {
  /** Tests class init via instance creation, so make sure no SmsSender exists yet. */
  @Test
  void constructSmsThrowsErrorWhenEnvVarsNotPresent() {
    Throwable t =
        assertThrows(
            ExceptionInInitializerError.class,
            SmsSender::new,
            "Expected an Error on init because the config was not present.");
    Throwable cause = t.getCause();
    assertNotNull(cause, "Expected the Error to have a cause.");
    assertEquals(
        SmsSender.ConfigurationFailedException.class,
        cause.getClass(),
        "Expected the cause of the Error to be our ConfigurationFailedException.");
  }
}
