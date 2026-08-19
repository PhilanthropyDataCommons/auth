/*
 * Copyright (c) 2026 Philanthropy Data Commons
 * License: Apache Software License 2.0.
 */

package org.philanthropydatacommons.keycloak.requiredaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RequiredActionConfigModel;
import org.keycloak.services.managers.AuthenticationManager;
import org.keycloak.services.util.ResolveRelative;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link LinkToWelcomePageRequiredAction}. */
@ExtendWith(MockitoExtension.class)
public class LinkToWelcomePageRequiredActionTest {

  @Mock private RequiredActionContext context;
  @Mock private RequiredActionConfigModel config;
  @Mock private RealmModel realm;
  @Mock private ClientModel client;
  @Mock private KeycloakSession session;
  @Mock private AuthenticationSessionModel authSession;

  private final LinkToWelcomePageRequiredAction action = new LinkToWelcomePageRequiredAction();

  @Test
  void noOpWhenConfigIsNull() {
    when(context.getConfig()).thenReturn(null);
    when(context.getRealm()).thenReturn(realm);
    when(realm.getName()).thenReturn("test-realm");
    action.requiredActionChallenge(context);
    verify(context).success();
    verifyNoInteractions(authSession);
  }

  @Test
  void noOpWhenTargetClientBlank() {
    when(context.getConfig()).thenReturn(config);
    when(config.getConfigValue(LinkToWelcomePageRequiredAction.CONFIG_TARGET_CLIENT))
        .thenReturn(" ");
    when(context.getRealm()).thenReturn(realm);
    when(realm.getName()).thenReturn("test-realm");
    action.requiredActionChallenge(context);
    verify(context).success();
    verifyNoInteractions(authSession);
  }

  @Test
  void setsClientHomeUriAndAuthNotes() {
    when(context.getConfig()).thenReturn(config);
    when(config.getConfigValue(LinkToWelcomePageRequiredAction.CONFIG_TARGET_CLIENT))
        .thenReturn("my-client");
    when(context.getRealm()).thenReturn(realm);
    when(realm.getClientByClientId("my-client")).thenReturn(client);
    when(context.getSession()).thenReturn(session);
    when(client.getRootUrl()).thenReturn("https://app.example.com");
    when(client.getBaseUrl()).thenReturn("/");
    when(context.getAuthenticationSession()).thenReturn(authSession);
    when(authSession.getAuthNote(AuthenticationManager.INVALIDATE_ACTION_TOKEN))
        .thenReturn("action-token-key");

    try (MockedStatic<ResolveRelative> resolveRelative =
        Mockito.mockStatic(ResolveRelative.class)) {
      resolveRelative
          .when(() -> ResolveRelative.resolveRelativeUri(any(), anyString(), anyString()))
          .thenReturn("https://app.example.com/");

      action.requiredActionChallenge(context);

      resolveRelative.verify(
          () ->
              ResolveRelative.resolveRelativeUri(
                  eq(session), eq("https://app.example.com"), eq("/")));
    }

    verify(authSession).setRedirectUri("https://app.example.com/");
    verify(authSession)
        .setAuthNote(eq(AuthenticationManager.END_AFTER_REQUIRED_ACTIONS), eq("true"));
    verify(authSession)
        .setAuthNote(eq(AuthenticationManager.SET_REDIRECT_URI_AFTER_REQUIRED_ACTIONS), eq("true"));
    verify(context).success();
  }

  @Test
  void noOpWhenConfiguredClientNoLongerExists() {
    when(context.getConfig()).thenReturn(config);
    when(config.getConfigValue(LinkToWelcomePageRequiredAction.CONFIG_TARGET_CLIENT))
        .thenReturn("gone");
    when(context.getRealm()).thenReturn(realm);
    when(realm.getName()).thenReturn("test-realm");
    when(realm.getClientByClientId("gone")).thenReturn(null);

    action.requiredActionChallenge(context);

    verify(context).success();
    verifyNoInteractions(authSession);
  }

  @Test
  void noOpWhenTargetClientHasNoHomeUrl() {
    when(context.getConfig()).thenReturn(config);
    when(config.getConfigValue(LinkToWelcomePageRequiredAction.CONFIG_TARGET_CLIENT))
        .thenReturn("my-client");
    when(context.getRealm()).thenReturn(realm);
    when(realm.getName()).thenReturn("test-realm");
    when(realm.getClientByClientId("my-client")).thenReturn(client);
    when(context.getSession()).thenReturn(session);
    when(client.getRootUrl()).thenReturn(null);
    when(client.getBaseUrl()).thenReturn(null);

    try (MockedStatic<ResolveRelative> resolveRelative =
        Mockito.mockStatic(ResolveRelative.class)) {
      resolveRelative
          .when(() -> ResolveRelative.resolveRelativeUri(any(), any(), any()))
          .thenReturn(null);

      action.requiredActionChallenge(context);
    }

    verify(context).success();
    verifyNoInteractions(authSession);
  }

  @Test
  void noOpOutsideActionTokenFlow() {
    // A home URI is resolved but the action no-ops (at DEBUG) when not in the
    // action-token flow, so a realm-default enable does not short-circuit
    // normal logins.
    when(context.getConfig()).thenReturn(config);
    when(config.getConfigValue(LinkToWelcomePageRequiredAction.CONFIG_TARGET_CLIENT))
        .thenReturn("my-client");
    when(context.getRealm()).thenReturn(realm);
    when(realm.getName()).thenReturn("test-realm");
    when(realm.getClientByClientId("my-client")).thenReturn(client);
    when(context.getSession()).thenReturn(session);
    when(client.getRootUrl()).thenReturn("https://app.example.com");
    when(client.getBaseUrl()).thenReturn("/");
    when(context.getAuthenticationSession()).thenReturn(authSession);
    when(authSession.getAuthNote(AuthenticationManager.INVALIDATE_ACTION_TOKEN))
        .thenReturn(null);

    try (MockedStatic<ResolveRelative> resolveRelative =
        Mockito.mockStatic(ResolveRelative.class)) {
      resolveRelative
          .when(() -> ResolveRelative.resolveRelativeUri(any(), anyString(), anyString()))
          .thenReturn("https://app.example.com/");

      action.requiredActionChallenge(context);
    }

    verify(authSession, never()).setRedirectUri(any());
    verify(authSession, never())
        .setAuthNote(eq(AuthenticationManager.END_AFTER_REQUIRED_ACTIONS), any());
    verify(authSession, never())
        .setAuthNote(eq(AuthenticationManager.SET_REDIRECT_URI_AFTER_REQUIRED_ACTIONS), any());
    verify(context).success();
  }

  @Test
  void processActionBehavesIdenticallyToChallenge() {
    when(context.getConfig()).thenReturn(config);
    when(config.getConfigValue(LinkToWelcomePageRequiredAction.CONFIG_TARGET_CLIENT))
        .thenReturn("my-client");
    when(context.getRealm()).thenReturn(realm);
    when(realm.getClientByClientId("my-client")).thenReturn(client);
    when(context.getSession()).thenReturn(session);
    when(client.getRootUrl()).thenReturn("https://app.example.com");
    when(client.getBaseUrl()).thenReturn("/");
    when(context.getAuthenticationSession()).thenReturn(authSession);
    when(authSession.getAuthNote(AuthenticationManager.INVALIDATE_ACTION_TOKEN))
        .thenReturn("action-token-key");

    try (MockedStatic<ResolveRelative> resolveRelative =
        Mockito.mockStatic(ResolveRelative.class)) {
      resolveRelative
          .when(() -> ResolveRelative.resolveRelativeUri(any(), anyString(), anyString()))
          .thenReturn("https://app.example.com/");

      action.processAction(context);
    }

    verify(authSession).setRedirectUri("https://app.example.com/");
    verify(authSession)
        .setAuthNote(eq(AuthenticationManager.END_AFTER_REQUIRED_ACTIONS), eq("true"));
    verify(authSession)
        .setAuthNote(eq(AuthenticationManager.SET_REDIRECT_URI_AFTER_REQUIRED_ACTIONS), eq("true"));
    verify(context).success();
  }

  @Test
  void everyNoopReasonRendersAsOnePhysicalLine() {
    // Pins the rendered form, not by copying the template but by exercising
    // Outcome.render (the production formatting path), so a whitespace edit
    // to the real declarations fails here. Each emitted log line must be a
    // single physical line: no embedded newlines, no trailing newline (the
    // logger adds its own terminator), no trailing whitespace.
    for (LinkToWelcomePageRequiredAction.Outcome outcome :
        LinkToWelcomePageRequiredAction.Outcome.values()) {
      String rendered = outcome.render("test-realm", "my-client");
      assertFalse(rendered.contains("\n"), () -> outcome + " spans multiple lines");
      assertFalse(
          rendered.endsWith("\n") || rendered.endsWith(" ") || rendered.endsWith("\t"),
          () -> outcome + " ends with trailing whitespace");
      assertTrue(rendered.startsWith("link-to-welcome-page did not present a link; "),
          () -> outcome + " lost its shared prefix");
      // The realm {} placeholder must have been substituted, not left literal.
      assertFalse(rendered.contains("{}"), () -> outcome + " left a {} placeholder");
    }
  }
}
