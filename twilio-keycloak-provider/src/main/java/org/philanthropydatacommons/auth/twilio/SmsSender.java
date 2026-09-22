/*
 * Copyright (c) 2023-2026 MacArthur Foundation
 * License: Expat (MIT) license.
 */

package org.philanthropydatacommons.auth.twilio;

import java.util.ArrayList;
import java.util.List;
import org.keycloak.models.KeycloakSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Allows a caller to send SMS messages by calling
 * {@link #send(KeycloakSession, String, String)}.
 */
public class SmsSender {
  private static final Logger LOGGER = LoggerFactory.getLogger(SmsSender.class);

  /** The name of the environment variable holding a Twilio Account SID. */
  private static final String TWILIO_ACCOUNT_SID_ENV_VAR = "TWILIO_ACCOUNT_SID";

  /** The name of the environment variable holding a Twilio Auth Token. */
  private static final String TWILIO_AUTH_TOKEN_ENV_VAR = "TWILIO_AUTH_TOKEN";

  /** The name of the environment variable holding a Twilio (from) Phone Number. */
  private static final String TWILIO_PHONE_NUMBER_ENV_VAR = "TWILIO_PHONE_NUMBER";

  /** The Twilio Account SID, used in the API URL path and as the Basic auth username. */
  private final String accountSid;

  /** The Twilio Auth Token, used as the Basic auth password. */
  private final String authToken;

  /** The Twilio (from) phone number. */
  private final String from;

  static final class ConfigurationFailedException extends RuntimeException {
    public ConfigurationFailedException(String message) {
      super(message);
    }
  }

  /** Creates a sender configured from the TWILIO_* environment variables. */
  public SmsSender() {
    String accountSid = System.getenv(TWILIO_ACCOUNT_SID_ENV_VAR);
    String authToken = System.getenv(TWILIO_AUTH_TOKEN_ENV_VAR);
    String from = System.getenv(TWILIO_PHONE_NUMBER_ENV_VAR);
    List<String> missingEnvVars = new ArrayList<>(3);

    if (accountSid == null || accountSid.isBlank()) {
      missingEnvVars.add(TWILIO_ACCOUNT_SID_ENV_VAR);
    }
    if (authToken == null || authToken.isBlank()) {
      missingEnvVars.add(TWILIO_AUTH_TOKEN_ENV_VAR);
    }
    if (from == null || from.isBlank()) {
      missingEnvVars.add(TWILIO_PHONE_NUMBER_ENV_VAR);
    }
    if (!missingEnvVars.isEmpty()) {
      throw new ConfigurationFailedException(
          "Expected these Twilio environment variables: " + missingEnvVars);
    }

    this.accountSid = accountSid;
    this.authToken = authToken;
    this.from = from;
  }

  /**
   * Sends an SMS with the given message to the given "to" number.
   *
   * @param session The Keycloak session, used to obtain the managed HTTP client.
   * @param to The number to whom to send the message.
   * @param message The message to send.
   * @throws MessageFailedException When Twilio rejects the request or the message fails to send.
   */
  public void send(KeycloakSession session, String to, String message) {
    TwilioMessagesApi twilio = new TwilioMessagesApi(session);
    String sid = twilio.sendMessage(accountSid, authToken, from, to, message);
    LOGGER.info("Sent an SMS with sid='{}'.", sid);
  }
}
