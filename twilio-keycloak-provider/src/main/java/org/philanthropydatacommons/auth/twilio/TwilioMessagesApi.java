/*
 * Copyright (c) 2026 MacArthur Foundation
 * License: Expat (MIT) license.
 */

package org.philanthropydatacommons.auth.twilio;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import org.keycloak.http.simple.SimpleHttp;
import org.keycloak.http.simple.SimpleHttpResponse;
import org.keycloak.models.KeycloakSession;

/**
 * Sends SMS messages via the Twilio Messages REST API using Keycloak's {@link SimpleHttp} wrapper
 * around the Keycloak-managed HTTP client.
 *
 * <p>Twilio's REST API needs no separate authentication step: requests carry HTTP Basic
 * credentials (the Account SID as the username, the auth token as the password) on the same
 * single request that creates the message.
 */
class TwilioMessagesApi {
  /** The global (non-region-pinned) base URL the Twilio SDK used by default. */
  static final String DEFAULT_BASE_URL = "https://api.twilio.com";

  /** The message status value Twilio reports when a message failed to send. */
  static final String FAILED_STATUS = "failed";

  private final KeycloakSession session;
  private final String baseUrl;

  /** Creates an API client pointed at Twilio's global REST API base URL. */
  TwilioMessagesApi(KeycloakSession session) {
    this(session, DEFAULT_BASE_URL);
  }

  /**
   * Creates an API client pointed at the given base URL.
   *
   * @param session The Keycloak session, used to obtain the managed HTTP client.
   * @param baseUrl The base URL of the Twilio REST API.
   */
  TwilioMessagesApi(KeycloakSession session, String baseUrl) {
    this.session = session;
    this.baseUrl = baseUrl;
  }

  /**
   * Sends an SMS with the given message to the given "to" number by POSTing to Twilio's Messages
   * endpoint.
   *
   * @param accountSid The Account SID, used both in the URL path and as the Basic auth username.
   * @param authToken The Basic auth password: the Auth Token.
   * @param from The (from) phone number.
   * @param to The number to whom to send the message.
   * @param message The message to send.
   * @return The sid Twilio assigned to the created message.
   * @throws MessageFailedException When the API rejects the request or the message fails to send.
   */
  String sendMessage(String accountSid, String authToken, String from, String to, String message) {
    // Same global (non-region-pinned) host the Twilio SDK used by default: one POST, form-encoded
    // body, Basic credentials on the same request, JSON in and JSON out.
    String url = baseUrl + "/2010-04-01/Accounts/" + accountSid + "/Messages.json";
    JsonNode json;
    int statusCode;
    try (SimpleHttpResponse response =
        SimpleHttp.create(session)
            .doPost(url)
            .param("To", to)
            .param("From", from)
            .param("Body", message)
            .authBasic(accountSid, authToken)
            .asResponse()) {
      statusCode = response.getStatus();
      String body = response.asString();
      if (body == null || body.isBlank()) {
        throw new MessageFailedException(
            "Send SMS failed. HTTP status: " + statusCode + ". Error message: 'No response body'.");
      }
      json = response.asJson();
    } catch (IOException | IllegalArgumentException e) {
      // Connection failures, timeouts, malformed URLs, and unparseable responses are send failures.
      throw new MessageFailedException("Send SMS failed. Error message: '" + e + "'.", e);
    }
    if (statusCode < 200 || statusCode >= 300) {
      throw new MessageFailedException(createHttpErrorString(statusCode, json));
    }
    if (FAILED_STATUS.equals(json.path("status").asText())) {
      throw new MessageFailedException(createFailedMessageString(json));
    }
    String sid = json.path("sid").asText();
    if (sid.isBlank()) {
      // A created message always carries a sid; a blank one is not a verified send.
      throw new MessageFailedException(
          "Send SMS failed. HTTP status: " + statusCode + ". Error message: 'No sid in response'.");
    }
    return sid;
  }

  /** Builds the error message for a response outside the 2xx success range. */
  private static String createHttpErrorString(int statusCode, JsonNode errorJson) {
    String message = "Send SMS failed. HTTP status: " + statusCode + ".";
    if (errorJson.hasNonNull("code")) {
      message += " Error code: " + errorJson.path("code").asText() + ".";
    }
    if (errorJson.hasNonNull("message") && !errorJson.path("message").asText().isBlank()) {
      message += " Error message: '" + errorJson.path("message").asText() + "'.";
    }
    return message;
  }

  /**
   * Builds the error message for a created message that reports a failed status, preserving the
   * message format the Twilio SDK integration produced.
   */
  private static String createFailedMessageString(JsonNode failedMessage) {
    String message = "Send SMS failed.";
    if (failedMessage.hasNonNull("sid")) {
      message += " Sid: " + failedMessage.path("sid").asText();
    }
    if (failedMessage.hasNonNull("error_code")) {
      message += " Error code: " + failedMessage.path("error_code").asText() + ".";
    }
    if (failedMessage.hasNonNull("error_message")
        && !failedMessage.path("error_message").asText().isBlank()) {
      message += " Error message: '" + failedMessage.path("error_message").asText() + "'.";
    }
    return message;
  }

  /** Indicates Twilio rejected the send request or the message failed to send. */
  static final class MessageFailedException extends RuntimeException {
    MessageFailedException(String message) {
      super(message);
    }

    MessageFailedException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
