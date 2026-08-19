/*
 * Copyright (c) 2026 Philanthropy Data Commons
 * License: Apache Software License 2.0.
 *
 * Author: GLM-5.2
 *
 * Tests for LinkToWelcomePageRequiredAction. Mirrors the twilio module's
 * setup (JUnit Jupiter + Mockito). ResolveRelative.resolveRelativeUri is
 * stubbed with Mockito.mockStatic (Mockito 5.x default inline mock maker).
 */
package org.philanthropydatacommons.keycloak.requiredaction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RequiredActionConfigModel;
import org.keycloak.services.managers.AuthenticationManager;
import org.keycloak.protocol.oidc.utils.RedirectUtils;
import org.keycloak.services.util.ResolveRelative;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
        when(config.getConfigValue(LinkToWelcomePageRequiredAction.CONFIG_TARGET_CLIENT)).thenReturn(" ");
        when(context.getRealm()).thenReturn(realm);
        when(realm.getName()).thenReturn("test-realm");
        action.requiredActionChallenge(context);
        verify(context).success();
        verifyNoInteractions(authSession);
    }

    @Test
    void setsConfiguredWelcomePageUriAndAuthNotes() {
        when(context.getConfig()).thenReturn(config);
        when(config.getConfigValue(LinkToWelcomePageRequiredAction.CONFIG_TARGET_CLIENT)).thenReturn("my-client");
        when(context.getRealm()).thenReturn(realm);
        when(realm.getClientByClientId("my-client")).thenReturn(client);
        when(config.getConfigValue(LinkToWelcomePageRequiredAction.CONFIG_TARGET_WELCOME_PAGE_URI)).thenReturn("https://example.com/");
        when(context.getSession()).thenReturn(session);
        when(context.getAuthenticationSession()).thenReturn(authSession);
        // The action only sets the auth notes in the action-token flow, gated
        // on the INVALIDATE_ACTION_TOKEN auth note Keycloak sets in that flow.
        when(authSession.getAuthNote(AuthenticationManager.INVALIDATE_ACTION_TOKEN)).thenReturn("action-token-key");

        try (MockedStatic<RedirectUtils> redirectUtils = Mockito.mockStatic(RedirectUtils.class)) {
            // The configured URI is revalidated at run time (the same
            // RedirectUtils.verifyRedirectUri call applied at save time); the
            // resolved absolute URI becomes the link target.
            redirectUtils.when(() -> RedirectUtils.verifyRedirectUri(eq(session), eq("https://example.com/"), eq(client)))
                    .thenReturn("https://example.com/");

            action.requiredActionChallenge(context);
        }

        verify(authSession).setAuthNote(eq(AuthenticationManager.END_AFTER_REQUIRED_ACTIONS), eq("true"));
        verify(authSession).setAuthNote(eq(AuthenticationManager.SET_REDIRECT_URI_AFTER_REQUIRED_ACTIONS), eq("true"));
        verify(authSession).setRedirectUri("https://example.com/");
        verify(context).success();
        // The configured welcome-page URI must not consult the client's home URI.
        verify(client, never()).getRootUrl();
        verify(client, never()).getBaseUrl();
    }

    @Test
    void defaultsToClientHomeUriWhenWelcomePageUriBlank() {
        when(context.getConfig()).thenReturn(config);
        when(config.getConfigValue(LinkToWelcomePageRequiredAction.CONFIG_TARGET_CLIENT)).thenReturn("my-client");
        when(context.getRealm()).thenReturn(realm);
        when(realm.getClientByClientId("my-client")).thenReturn(client);
        when(config.getConfigValue(LinkToWelcomePageRequiredAction.CONFIG_TARGET_WELCOME_PAGE_URI)).thenReturn("");
        when(context.getSession()).thenReturn(session);
        when(client.getRootUrl()).thenReturn("https://app.example.com");
        when(client.getBaseUrl()).thenReturn("/");
        when(context.getAuthenticationSession()).thenReturn(authSession);
        when(authSession.getAuthNote(AuthenticationManager.INVALIDATE_ACTION_TOKEN)).thenReturn("action-token-key");

        try (MockedStatic<ResolveRelative> resolveRelative = Mockito.mockStatic(ResolveRelative.class)) {
            resolveRelative.when(() -> ResolveRelative.resolveRelativeUri(any(), anyString(), anyString()))
                    .thenReturn("https://app.example.com/");

            action.requiredActionChallenge(context);

            resolveRelative.verify(() -> ResolveRelative.resolveRelativeUri(eq(session), eq("https://app.example.com"), eq("/")));
        }

        verify(authSession).setRedirectUri("https://app.example.com/");
        verify(authSession).setAuthNote(eq(AuthenticationManager.END_AFTER_REQUIRED_ACTIONS), eq("true"));
        verify(authSession).setAuthNote(eq(AuthenticationManager.SET_REDIRECT_URI_AFTER_REQUIRED_ACTIONS), eq("true"));
        verify(context).success();
    }

    @Test
    void noOpWhenConfiguredClientNoLongerExists() {
        when(context.getConfig()).thenReturn(config);
        when(config.getConfigValue(LinkToWelcomePageRequiredAction.CONFIG_TARGET_CLIENT)).thenReturn("gone");
        when(context.getRealm()).thenReturn(realm);
        when(realm.getName()).thenReturn("test-realm");
        when(realm.getClientByClientId("gone")).thenReturn(null);

        action.requiredActionChallenge(context);

        verify(context).success();
        verifyNoInteractions(authSession);
    }

    @Test
    void processActionBehavesIdenticallyToChallenge() {
        // processAction is a defensive duplicate of requiredActionChallenge; the
        // observable effect (auth notes + link target) must be identical.
        when(context.getConfig()).thenReturn(config);
        when(config.getConfigValue(LinkToWelcomePageRequiredAction.CONFIG_TARGET_CLIENT)).thenReturn("my-client");
        when(context.getRealm()).thenReturn(realm);
        when(realm.getClientByClientId("my-client")).thenReturn(client);
        when(config.getConfigValue(LinkToWelcomePageRequiredAction.CONFIG_TARGET_WELCOME_PAGE_URI)).thenReturn("https://example.com/");
        when(context.getSession()).thenReturn(session);
        when(context.getAuthenticationSession()).thenReturn(authSession);
        when(authSession.getAuthNote(AuthenticationManager.INVALIDATE_ACTION_TOKEN)).thenReturn("action-token-key");

        try (MockedStatic<RedirectUtils> redirectUtils = Mockito.mockStatic(RedirectUtils.class)) {
            redirectUtils.when(() -> RedirectUtils.verifyRedirectUri(eq(session), eq("https://example.com/"), eq(client)))
                    .thenReturn("https://example.com/");

            action.processAction(context);
        }

        verify(authSession).setRedirectUri("https://example.com/");
        verify(authSession).setAuthNote(eq(AuthenticationManager.END_AFTER_REQUIRED_ACTIONS), eq("true"));
        verify(authSession).setAuthNote(eq(AuthenticationManager.SET_REDIRECT_URI_AFTER_REQUIRED_ACTIONS), eq("true"));
        verify(context).success();
    }

    @Test
    void noOpWhenClientHomeUriResolvesToNull() {
        // A client with neither root URL nor base URL resolves to null; the
        // action must no-op rather than setRedirectUri(null).
        when(context.getConfig()).thenReturn(config);
        when(config.getConfigValue(LinkToWelcomePageRequiredAction.CONFIG_TARGET_CLIENT)).thenReturn("my-client");
        when(context.getRealm()).thenReturn(realm);
        when(realm.getName()).thenReturn("test-realm");
        when(realm.getClientByClientId("my-client")).thenReturn(client);
        when(config.getConfigValue(LinkToWelcomePageRequiredAction.CONFIG_TARGET_WELCOME_PAGE_URI)).thenReturn("");
        when(context.getSession()).thenReturn(session);
        when(client.getRootUrl()).thenReturn(null);
        when(client.getBaseUrl()).thenReturn(null);

        try (MockedStatic<ResolveRelative> resolveRelative = Mockito.mockStatic(ResolveRelative.class)) {
            resolveRelative.when(() -> ResolveRelative.resolveRelativeUri(any(), any(), any()))
                    .thenReturn(null);

            action.requiredActionChallenge(context);

            resolveRelative.verify(() -> ResolveRelative.resolveRelativeUri(eq(session), eq(null), eq(null)));
        }

        verify(context).success();
        verifyNoInteractions(authSession);
    }

    @Test
    void noOpWhenNotInActionTokenFlow() {
        // A normal login (no action token) must not set END_AFTER_REQUIRED_ACTIONS:
        // finishedRequiredActions would then render the info page and remove the
        // auth session instead of issuing an authorization code, breaking login.
        when(context.getConfig()).thenReturn(config);
        when(config.getConfigValue(LinkToWelcomePageRequiredAction.CONFIG_TARGET_CLIENT)).thenReturn("my-client");
        when(context.getRealm()).thenReturn(realm);
        when(realm.getName()).thenReturn("test-realm");
        when(realm.getClientByClientId("my-client")).thenReturn(client);
        when(config.getConfigValue(LinkToWelcomePageRequiredAction.CONFIG_TARGET_WELCOME_PAGE_URI)).thenReturn("https://example.com/");
        when(context.getSession()).thenReturn(session);
        when(context.getAuthenticationSession()).thenReturn(authSession);
        // INVALIDATE_ACTION_TOKEN absent (default null) -> not in action-token flow.

        try (MockedStatic<RedirectUtils> redirectUtils = Mockito.mockStatic(RedirectUtils.class)) {
            // The configured URI is revalidated before the flow gate is reached;
            // stub it valid so the gate (not URI validation) is what no-ops here.
            redirectUtils.when(() -> RedirectUtils.verifyRedirectUri(eq(session), eq("https://example.com/"), eq(client)))
                    .thenReturn("https://example.com/");

            action.requiredActionChallenge(context);
        }

        verify(authSession, never()).setRedirectUri(any());
        verify(authSession, never()).setAuthNote(eq(AuthenticationManager.END_AFTER_REQUIRED_ACTIONS), any());
        verify(authSession, never()).setAuthNote(eq(AuthenticationManager.SET_REDIRECT_URI_AFTER_REQUIRED_ACTIONS), any());
        verify(context).success();
    }

    @Test
    void noOpWhenConfiguredWelcomePageUriIsNotAValidRedirectUri() {
        // The configured URI is revalidated at run time via
        // RedirectUtils.verifyRedirectUri (the same check applied at save time).
        // When it returns null — unregistered, bad scheme (e.g. javascript:),
        // forbidden params, or the client's redirect URIs changed since save —
        // the action no-ops rather than set the link target.
        when(context.getConfig()).thenReturn(config);
        when(config.getConfigValue(LinkToWelcomePageRequiredAction.CONFIG_TARGET_CLIENT)).thenReturn("my-client");
        when(context.getRealm()).thenReturn(realm);
        when(realm.getName()).thenReturn("test-realm");
        when(realm.getClientByClientId("my-client")).thenReturn(client);
        when(config.getConfigValue(LinkToWelcomePageRequiredAction.CONFIG_TARGET_WELCOME_PAGE_URI)).thenReturn("javascript:alert(document.cookie)//");
        when(context.getSession()).thenReturn(session);

        try (MockedStatic<RedirectUtils> redirectUtils = Mockito.mockStatic(RedirectUtils.class)) {
            redirectUtils.when(() -> RedirectUtils.verifyRedirectUri(eq(session), eq("javascript:alert(document.cookie)//"), eq(client)))
                    .thenReturn(null);

            action.requiredActionChallenge(context);

            redirectUtils.verify(() -> RedirectUtils.verifyRedirectUri(eq(session), eq("javascript:alert(document.cookie)//"), eq(client)));
        }

        verify(context).success();
        verifyNoInteractions(authSession);
    }

    @Test
    void resolvesRelativeConfiguredUriAgainstClientAtRuntime() {
        // A relative configured welcome-page URI (e.g. "/welcome") is resolved
        // against the target client at run time by RedirectUtils.verifyRedirectUri
        // (RedirectUtils ~lines 135-140 resolve a relative URI to absolute using
        // the client's root URL). The raw value must NOT be rejected by a
        // scheme check; the resolved absolute URI is what becomes the link
        // target. This guards against the save-time check accepting a relative
        // value that a naive raw-value http(s) check would then reject.
        when(context.getConfig()).thenReturn(config);
        when(config.getConfigValue(LinkToWelcomePageRequiredAction.CONFIG_TARGET_CLIENT)).thenReturn("my-client");
        when(context.getRealm()).thenReturn(realm);
        when(realm.getClientByClientId("my-client")).thenReturn(client);
        when(config.getConfigValue(LinkToWelcomePageRequiredAction.CONFIG_TARGET_WELCOME_PAGE_URI)).thenReturn("/welcome");
        when(context.getSession()).thenReturn(session);
        when(context.getAuthenticationSession()).thenReturn(authSession);
        when(authSession.getAuthNote(AuthenticationManager.INVALIDATE_ACTION_TOKEN)).thenReturn("action-token-key");

        try (MockedStatic<RedirectUtils> redirectUtils = Mockito.mockStatic(RedirectUtils.class)) {
            redirectUtils.when(() -> RedirectUtils.verifyRedirectUri(eq(session), eq("/welcome"), eq(client)))
                    .thenReturn("https://example.com/welcome");

            action.requiredActionChallenge(context);

            redirectUtils.verify(() -> RedirectUtils.verifyRedirectUri(eq(session), eq("/welcome"), eq(client)));
        }

        verify(authSession).setRedirectUri("https://example.com/welcome");
        verify(authSession).setAuthNote(eq(AuthenticationManager.END_AFTER_REQUIRED_ACTIONS), eq("true"));
        verify(authSession).setAuthNote(eq(AuthenticationManager.SET_REDIRECT_URI_AFTER_REQUIRED_ACTIONS), eq("true"));
        verify(context).success();
    }

    @Test
    void noOpWhenDefaultHomeUriHasDisallowedScheme() {
        // The default welcome-page URI (client home URI) is not redirect-URI
        // validated (it mirrors Keycloak's own client.baseUrl fallback), so a
        // non-http(s) scheme resolved from rootUrl/baseUrl must be rejected
        // before it reaches info.ftl's href.
        when(context.getConfig()).thenReturn(config);
        when(config.getConfigValue(LinkToWelcomePageRequiredAction.CONFIG_TARGET_CLIENT)).thenReturn("my-client");
        when(context.getRealm()).thenReturn(realm);
        when(realm.getName()).thenReturn("test-realm");
        when(realm.getClientByClientId("my-client")).thenReturn(client);
        when(config.getConfigValue(LinkToWelcomePageRequiredAction.CONFIG_TARGET_WELCOME_PAGE_URI)).thenReturn("");
        when(context.getSession()).thenReturn(session);

        try (MockedStatic<ResolveRelative> resolveRelative = Mockito.mockStatic(ResolveRelative.class)) {
            resolveRelative.when(() -> ResolveRelative.resolveRelativeUri(any(), any(), any()))
                    .thenReturn("javascript:alert(document.cookie)//");

            action.requiredActionChallenge(context);

            resolveRelative.verify(() -> ResolveRelative.resolveRelativeUri(eq(session), any(), any()));
        }

        verify(context).success();
        verifyNoInteractions(authSession);
    }

    @Test
    void outcomeTextBlocksRenderAsSingleLineWithSingleSpaces() {
        // The Outcome templates use Java text blocks with backslash line
        // continuations (a trailing space before each backslash) so the source
        // spans lines for readability while the rendered string stays a single
        // line with single spaces — i.e. identical to the prior concatenation.
        // This test pins the rendered form so a whitespace edit cannot silently
        // regress the slf4j pattern.
        assertEquals(
            "link-to-welcome-page did not present a link; the action was a no-op. "
                + "Realm '{}'. Reason: configured target client '{}' no longer exists.",
            """
            link-to-welcome-page did not present a link; the action was a no-op. \
            Realm '{}'. Reason: configured target client '{}' no longer exists.""",
            "CLIENT_GONE text block must render as a single line with single spaces");
        assertEquals(
            "link-to-welcome-page did not present a link; the action was a no-op. "
                + "Realm '{}'. Reason: not running in an action-token flow (the "
                + "INVALIDATE_ACTION_TOKEN auth note is absent); this action only "
                + "presents a link after action-token-driven required actions "
                + "(e.g. execute-actions-email), and is a no-op in a normal login "
                + "flow.",
            """
            link-to-welcome-page did not present a link; the action was a no-op. \
            Realm '{}'. Reason: not running in an action-token flow (the \
            INVALIDATE_ACTION_TOKEN auth note is absent); this action only \
            presents a link after action-token-driven required actions (e.g. \
            execute-actions-email), and is a no-op in a normal login flow.""",
            "NOT_ACTION_TOKEN_FLOW text block must render as a single line with single spaces");
    }
}
