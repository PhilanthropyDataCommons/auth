/*
 * Copyright (c) 2026 MacArthur Foundation
 * License: Expat (MIT) license.
 */

package org.philanthropydatacommons.auth.twilio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.http.impl.client.HttpClients;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.connections.httpclient.HttpClientProvider;
import org.keycloak.models.KeycloakSession;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** HTTP behavior tests for {@link TwilioMessagesApi} against the JDK's built-in HTTP server. */
@ExtendWith(MockitoExtension.class)
class TwilioMessagesApiTest {
  private static final String ACCOUNT_SID = "AC7001";
  private static final String AUTH_TOKEN = "auth-token-7003";
  private static final String FROM_NUMBER = "+15558670003";
  private static final String TO_NUMBER = "+15558671009";
  private static final String MESSAGE = "PDC verification code 1009.";

  @Mock private KeycloakSession mockKeycloakSession;
  @Mock private HttpClientProvider mockHttpClientProvider;

  private HttpServer server;
  private CapturingHandler handler;

  @BeforeEach
  void startLocalHttpServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    handler = new CapturingHandler();
    server.createContext("/", handler);
    server.start();
    // SimpleHttp.create(KeycloakSession) obtains its client from HttpClientProvider, so hand the
    // mock session a real client and run the whole SimpleHttp path against the local server.
    when(mockKeycloakSession.getProvider(HttpClientProvider.class))
        .thenReturn(mockHttpClientProvider);
    when(mockHttpClientProvider.getHttpClient()).thenReturn(HttpClients.createDefault());
    when(mockHttpClientProvider.getMaxConsumedResponseSize()).thenReturn(104729L);
  }

  @AfterEach
  void stopLocalHttpServer() {
    server.stop(0);
  }

  /** Creates an API client pointed at the local HTTP server. */
  private TwilioMessagesApi apiForLocalServer() {
    String host = server.getAddress().getAddress().getHostAddress();
    if (host.contains(":")) {
      host = "[" + host + "]"; // Bracket an IPv6 literal so the URL parses.
    }
    int port = server.getAddress().getPort();
    return new TwilioMessagesApi(mockKeycloakSession, "http://" + host + ":" + port);
  }

  @Test
  void sendMessagePostsFormEncodedParamsWithBasicAuthAndReturnsSid() throws IOException {
    handler.respondWith(201, "{\"sid\":\"SM7001\",\"status\":\"queued\"}");
    TwilioMessagesApi api = apiForLocalServer();
    assertEquals(
        "SM7001", api.sendMessage(ACCOUNT_SID, AUTH_TOKEN, FROM_NUMBER, TO_NUMBER, MESSAGE));
    CapturedRequest request = handler.captured.get();
    assertEquals("POST", request.method);
    assertEquals("/2010-04-01/Accounts/" + ACCOUNT_SID + "/Messages.json", request.path);
    assertEquals(
        "Basic "
            + Base64.getEncoder()
                .encodeToString((ACCOUNT_SID + ":" + AUTH_TOKEN).getBytes(StandardCharsets.UTF_8)),
        request.authorization);
    assertTrue(
        request.contentType.startsWith("application/x-www-form-urlencoded"),
        "Expected a form-encoded request but got: " + request.contentType);
    Map<String, String> params = parseFormBody(request.body);
    assertEquals(TO_NUMBER, params.get("To"));
    assertEquals(FROM_NUMBER, params.get("From"));
    assertEquals(MESSAGE, params.get("Body"));
  }

  @Test
  void sendMessageThrowsOnRejectedRequest() {
    handler.respondWith(
        401,
        "{\"code\":20003,\"message\":\"Authentication Error - No credentials provided\","
            + "\"status\":401}");
    TwilioMessagesApi api = apiForLocalServer();
    TwilioMessagesApi.MessageFailedException thrown =
        assertThrows(
            TwilioMessagesApi.MessageFailedException.class,
            () -> api.sendMessage(ACCOUNT_SID, AUTH_TOKEN, FROM_NUMBER, TO_NUMBER, MESSAGE));
    assertEquals(
        "Send SMS failed. HTTP status: 401. Error code: 20003. Error message: "
            + "'Authentication Error - No credentials provided'.",
        thrown.getMessage());
  }

  @Test
  void sendMessageThrowsOnRedirectStatus() {
    handler.respondWith(302, "{\"message\":\"Found\"}");
    TwilioMessagesApi api = apiForLocalServer();
    TwilioMessagesApi.MessageFailedException thrown =
        assertThrows(
            TwilioMessagesApi.MessageFailedException.class,
            () -> api.sendMessage(ACCOUNT_SID, AUTH_TOKEN, FROM_NUMBER, TO_NUMBER, MESSAGE));
    assertEquals(
        "Send SMS failed. HTTP status: 302. Error message: 'Found'.", thrown.getMessage());
  }

  @Test
  void sendMessageThrowsOnFailedMessageStatus() {
    handler.respondWith(
        201,
        "{\"sid\":\"SM1009\",\"status\":\"failed\",\"error_code\":1009,"
            + "\"error_message\":\"Phone number is invalid\"}");
    TwilioMessagesApi api = apiForLocalServer();
    TwilioMessagesApi.MessageFailedException thrown =
        assertThrows(
            TwilioMessagesApi.MessageFailedException.class,
            () -> api.sendMessage(ACCOUNT_SID, AUTH_TOKEN, FROM_NUMBER, TO_NUMBER, MESSAGE));
    assertEquals(
        "Send SMS failed. Sid: SM1009 Error code: 1009. Error message: "
            + "'Phone number is invalid'.",
        thrown.getMessage());
  }

  @Test
  void sendMessageOmitsAbsentFailureFields() {
    handler.respondWith(
        201,
        "{\"sid\":\"SM1013\",\"status\":\"failed\",\"error_code\":null,\"error_message\":null}");
    TwilioMessagesApi api = apiForLocalServer();
    TwilioMessagesApi.MessageFailedException thrown =
        assertThrows(
            TwilioMessagesApi.MessageFailedException.class,
            () -> api.sendMessage(ACCOUNT_SID, AUTH_TOKEN, FROM_NUMBER, TO_NUMBER, MESSAGE));
    assertEquals("Send SMS failed. Sid: SM1013", thrown.getMessage());
  }

  @Test
  void sendMessageThrowsOnEmptyResponseBody() {
    handler.respondWith(200, "");
    TwilioMessagesApi api = apiForLocalServer();
    TwilioMessagesApi.MessageFailedException thrown =
        assertThrows(
            TwilioMessagesApi.MessageFailedException.class,
            () -> api.sendMessage(ACCOUNT_SID, AUTH_TOKEN, FROM_NUMBER, TO_NUMBER, MESSAGE));
    assertEquals(
        "Send SMS failed. HTTP status: 200. Error message: 'No response body'.",
        thrown.getMessage());
  }

  @Test
  void sendMessageThrowsOnMissingSid() {
    handler.respondWith(201, "{\"status\":\"queued\"}");
    TwilioMessagesApi api = apiForLocalServer();
    TwilioMessagesApi.MessageFailedException thrown =
        assertThrows(
            TwilioMessagesApi.MessageFailedException.class,
            () -> api.sendMessage(ACCOUNT_SID, AUTH_TOKEN, FROM_NUMBER, TO_NUMBER, MESSAGE));
    assertEquals(
        "Send SMS failed. HTTP status: 201. Error message: 'No sid in response'.",
        thrown.getMessage());
  }

  @Test
  void sendMessageTreatsConnectionFailuresAsSendFailures() {
    TwilioMessagesApi unreachable =
        new TwilioMessagesApi(mockKeycloakSession, "http://127.0.0.1:1");
    TwilioMessagesApi.MessageFailedException thrown =
        assertThrows(
            TwilioMessagesApi.MessageFailedException.class,
            () ->
                unreachable.sendMessage(ACCOUNT_SID, AUTH_TOKEN, FROM_NUMBER, TO_NUMBER, MESSAGE));
    assertInstanceOf(IOException.class, thrown.getCause());
  }

  /** Decodes an application/x-www-form-urlencoded body into ordered parameter pairs. */
  private static Map<String, String> parseFormBody(String body) {
    Map<String, String> params = new LinkedHashMap<>();
    int start = 0;
    while (start < body.length()) {
      int ampersand = body.indexOf('&', start);
      int end = ampersand == -1 ? body.length() : ampersand;
      int equals = body.indexOf('=', start);
      String key = URLDecoder.decode(body.substring(start, equals), StandardCharsets.UTF_8);
      String value = URLDecoder.decode(body.substring(equals + 1, end), StandardCharsets.UTF_8);
      params.put(key, value);
      start = end + 1;
    }
    return params;
  }

  /** Captures the request the client sent and serves a canned JSON response. */
  private static final class CapturingHandler implements HttpHandler {
    private volatile int status;
    private volatile String jsonBody = "";
    private final AtomicReference<CapturedRequest> captured = new AtomicReference<>();

    /** Configures the JSON response the handler serves. */
    void respondWith(int status, String jsonBody) {
      this.status = status;
      this.jsonBody = jsonBody;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
      captured.set(new CapturedRequest(exchange));
      byte[] responseBytes = jsonBody.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(status, responseBytes.length);
      exchange.getResponseBody().write(responseBytes);
      exchange.close();
    }
  }

  /** The parts of the sent request the tests assert on. */
  private static final class CapturedRequest {
    private final String method;
    private final String path;
    private final String authorization;
    private final String contentType;
    private final String body;

    CapturedRequest(HttpExchange exchange) throws IOException {
      this.method = exchange.getRequestMethod();
      this.path = exchange.getRequestURI().getPath();
      this.authorization = exchange.getRequestHeaders().getFirst("Authorization");
      this.contentType = exchange.getRequestHeaders().getFirst("Content-Type");
      this.body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
