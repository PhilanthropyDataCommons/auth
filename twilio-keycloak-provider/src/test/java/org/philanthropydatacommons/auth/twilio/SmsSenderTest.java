/*
 * Copyright (c) 2023-2026 MacArthur Foundation
 * License: Expat (MIT) license.
 */

package org.philanthropydatacommons.auth.twilio;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SmsSenderTest {
  /** The constructor validates the Twilio environment variables. */
  @Test
  void constructSmsThrowsErrorWhenEnvVarsNotPresent() {
    assertThrows(
        SmsSender.ConfigurationFailedException.class,
        SmsSender::new,
        "Expected a ConfigurationFailedException because the config was not present.");
  }
}
