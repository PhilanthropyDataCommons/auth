package org.philanthropydatacommons.auth.twilio.authenticator;

/** Thrown when the Keycloak LOGIN theme lacks the SMS format property or looking it up fails. */
public class KeycloakThemeConfigurationException extends RuntimeException {
  KeycloakThemeConfigurationException(String message, Throwable cause) {
    super(message, cause);
  }
}
