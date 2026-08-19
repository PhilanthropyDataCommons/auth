/*
 * Copyright (c) 2026 Philanthropy Data Commons
 * License: Apache Software License 2.0.
 *
 * Author: GLM-5.2
 *
 * A reusable Keycloak required action that, after the other required actions in
 * the flow complete, presents Keycloak's standard "account updated" info page
 * with a "back to application" link to a configured welcome-page URL. It does
 * NOT issue an HTTP redirect; the user clicks the link. See README.md for full
 * behavior, configuration, and idempotency notes.
 *
 * Place it LAST in Authentication -> Required Actions (drag to bottom). In the
 * action-token flow (e.g. execute-actions-email) it sets the same auth-session
 * notes Keycloak's own action-token handlers use, so finishedRequiredActions
 * renders the "account updated" info page with pageRedirectUri = the
 * configured URL (rendered as a link by info.ftl). The configured
 * targetWelcomePageUri is revalidated at run time via
 * RedirectUtils.verifyRedirectUri (the same check applied at save time), which
 * resolves relative URIs against the target client and enforces the http(s)
 * scheme. No-op when no target client is set, when the URI is not a valid
 * redirect URI for the client, or outside the action-token flow (so it is safe
 * to leave enabled realm-wide and will not break normal logins).
 */
package org.philanthropydatacommons.keycloak.requiredaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.keycloak.authentication.InitiatedActionSupport;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.models.ClientModel;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RequiredActionConfigModel;
import org.keycloak.protocol.oidc.utils.RedirectUtils;
import org.keycloak.services.managers.AuthenticationManager;
import org.keycloak.services.util.ResolveRelative;
import org.keycloak.sessions.AuthenticationSessionModel;

/**
 * Required action that sets up the "back to application" link. Stateless; the
 * factory creates a new instance per request (matching Keycloak's own
 * required-action factories, e.g. WebAuthnRegisterFactory).
 *
 * @author GLM-5.2
 */
public final class LinkToWelcomePageRequiredAction implements RequiredActionProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(LinkToWelcomePageRequiredAction.class);

    public static final String PROVIDER_ID = "link-to-welcome-page";

    /** Config key: the client id whose context is used (and whose home URI is the default welcome page). */
    public static final String CONFIG_TARGET_CLIENT = "targetClient";

    /** Config key (optional): the URL the link points to. Defaults to the target client's home URI. */
    public static final String CONFIG_TARGET_WELCOME_PAGE_URI = "targetWelcomePageUri";

    @Override
    public InitiatedActionSupport initiatedActionSupport() {
        return InitiatedActionSupport.SUPPORTED;
    }

    @Override
    public void evaluateTriggers(RequiredActionContext context) {
        // Intentionally empty: this action must be added to a user explicitly
        // (e.g. selected in the credential-reset dialog, or set as a default
        // required action). It never auto-adds itself.
    }

    @Override
    public void requiredActionChallenge(RequiredActionContext context) {
        // No form: set up the link and complete immediately. The framework
        // treats Status.SUCCESS from the challenge phase as an auto-completed
        // action (AuthenticationManager ~line 1390), so when this is the last
        // action, finishedRequiredActions runs and renders the info page with
        // the "back to application" link via pageRedirectUri.
        run(context);
    }

    @Override
    public void processAction(RequiredActionContext context) {
        // Defensive: processAction only runs after a challenge is sent, which
        // we never do. Kept identical to requiredActionChallenge for robustness.
        run(context);
    }

    /**
     * Set up the link (if configured) and complete the action. Each no-op path
     * logs a single line at the level its {@link Outcome} carries, so a
     * misconfigured action does not silently do nothing while an expected no-op
     * (e.g. running outside the action-token flow) does not warn on every
     * login.
     */
    private void run(RequiredActionContext context) {
        applyLink(context);
        context.success();
    }

    /**
     * Resolve the welcome-page URL and set it on the auth session so that
     * finishedRequiredActions renders it as the "back to application" link.
     * On any no-op path, logs a single line at the level the corresponding
     * {@link Outcome} carries (WARN for misconfiguration, DEBUG for the
     * expected non-action-token-flow no-op) and returns without setting notes.
     */
    private void applyLink(RequiredActionContext context) {
        RequiredActionConfigModel config = context.getConfig();
        String targetClientAlias = config == null ? null : config.getConfigValue(CONFIG_TARGET_CLIENT);

        if (targetClientAlias == null || targetClientAlias.isBlank()) {
            logNoop(context, Outcome.NO_TARGET_CLIENT, null);
            return;
        }

        RealmModel realm = context.getRealm();
        ClientModel client = realm.getClientByClientId(targetClientAlias);
        // If the named client no longer exists, do nothing rather than leave the
        // user stuck; the misconfiguration is surfaced at save time, but this
        // guards against the client being removed between save and run.
        if (client == null) {
            logNoop(context, Outcome.CLIENT_GONE, targetClientAlias);
            return;
        }

        String welcomePageUri = config.getConfigValue(CONFIG_TARGET_WELCOME_PAGE_URI);
        if (welcomePageUri != null && !welcomePageUri.isBlank()) {
            // Configured: revalidate at run time. verifyRedirectUri resolves a
            // relative URI against the client's root URL to an absolute http(s)
            // URL (RedirectUtils ~lines 135-140) and enforces the http(s)
            // scheme (~lines 143-147) — the same check applied at save time.
            // Re-running it here closes the TOCTOU window (the client's
            // registered URIs or root URL can change between save and run) and
            // resolves relative values the save-time check accepted, so a
            // configured "/welcome" is not rejected by a raw-value scheme
            // check. Returns null when the URI is not a valid redirect URI for
            // the client (unregistered, bad scheme, forbidden params, ...).
            String resolved = RedirectUtils.verifyRedirectUri(
                    context.getSession(), welcomePageUri, client);
            if (resolved == null) {
                logNoop(context, Outcome.INVALID_REDIRECT_URI, welcomePageUri);
                return;
            }
            welcomePageUri = resolved;
        } else {
            // Default: the target client's home URI. This is the same
            // resolution Keycloak uses for the org-invite fallback
            // (root URL + base URL, frontend-URL-aware), so relative base
            // URLs and the ${authBaseUrl} placeholder are handled identically
            // to built-in behavior.
            welcomePageUri = ResolveRelative.resolveRelativeUri(
                    context.getSession(), client.getRootUrl(), client.getBaseUrl());
            // A client with neither root URL nor base URL resolves to null.
            // Treat that as a no-op rather than setRedirectUri(null), so the
            // flow degrades predictably (no link) instead of overwriting any
            // prior redirect URI with null.
            if (welcomePageUri == null || welcomePageUri.isBlank()) {
                logNoop(context, Outcome.NO_RESOLVED_URI, targetClientAlias);
                return;
            }
            // Defense in depth: the home URI is not redirect-URI-validated (it
            // mirrors Keycloak's own client.baseUrl fallback in info.ftl), and
            // a realm import can set a rootUrl/baseUrl that saves without
            // validation, so reject anything that is not http(s) before it
            // reaches info.ftl's <a href="${pageRedirectUri}">.
            if (!isAllowedRedirectScheme(welcomePageUri)) {
                logNoop(context, Outcome.DISALLOWED_SCHEME, welcomePageUri);
                return;
            }
        }

        AuthenticationSessionModel authSession = context.getAuthenticationSession();
        // Only meaningful in the action-token flow (e.g. execute-actions-email),
        // where finishedRequiredActions renders the "account updated" info page
        // with a "back to application" link. Keycloak sets
        // INVALIDATE_ACTION_TOKEN on the auth session for that flow
        // (LoginActionsService ~line 710) and END_AFTER_REQUIRED_ACTIONS in
        // AbstractActionTokenHandler (~line 100). In a normal login the auth
        // session instead proceeds to issue an authorization code; setting
        // END_AFTER_REQUIRED_ACTIONS here would short-circuit that and break
        // every login (e.g. if an admin sets this action as a realm default),
        // so outside the action-token flow the action is a no-op — logged at
        // DEBUG, since this is the expected case for a realm-wide default.
        if (authSession.getAuthNote(AuthenticationManager.INVALIDATE_ACTION_TOKEN) == null) {
            logNoop(context, Outcome.NOT_ACTION_TOKEN_FLOW, null);
            return;
        }

        // SET_REDIRECT_URI_AFTER_REQUIRED_ACTIONS + getRedirectUri() drive the
        // pageRedirectUri attribute finishedRequiredActions sets on the info
        // page (AuthenticationManager ~line 1104); info.ftl renders it as the
        // "back to application" link.
        authSession.setAuthNote(AuthenticationManager.END_AFTER_REQUIRED_ACTIONS, "true");
        authSession.setAuthNote(AuthenticationManager.SET_REDIRECT_URI_AFTER_REQUIRED_ACTIONS, "true");
        authSession.setRedirectUri(welcomePageUri);
    }

    /**
     * Log a no-op at the level {@code outcome} carries, using the slf4j
     * placeholder idiom: the realm is the first {@code {}} and the optional
     * {@code detail} the second (slf4j silently ignores an arg with no matching
     * placeholder). No user/PII is logged.
     */
    private void logNoop(RequiredActionContext context, Outcome outcome, String detail) {
        String pattern = outcome.template;
        if (outcome.level == Level.WARN) {
            LOGGER.warn(pattern, context.getRealm().getName(), detail);
        } else {
            LOGGER.debug(pattern, context.getRealm().getName(), detail);
        }
    }

    /**
     * Why the action did not present a link. Each constant carries the log
     * level for its no-op message, so the expected/normal no-op (running
     * outside the action-token flow) is DEBUG while genuine misconfigurations
     * are WARN — an admin who accidentally enables this as a realm default does
     * not flood the logs with warnings on every login. Each {@code template} is
     * a complete slf4j pattern: {@code {}} for the realm and (where present) a
     * second {@code {}} for the detail value.
     */
    private enum Outcome {
        NO_TARGET_CLIENT(
            """
            link-to-welcome-page did not present a link; the action was a no-op. \
            Realm '{}'. Reason: no target client is configured for this required \
            action (set the 'targetClient' config property).""",
            Level.WARN),
        CLIENT_GONE(
            """
            link-to-welcome-page did not present a link; the action was a no-op. \
            Realm '{}'. Reason: configured target client '{}' no longer exists.""",
            Level.WARN),
        NO_RESOLVED_URI(
            """
            link-to-welcome-page did not present a link; the action was a no-op. \
            Realm '{}'. Reason: could not resolve a welcome-page URI: target \
            welcome-page URI is not configured and target client '{}' has neither \
            a root URL nor a base URL to default to.""",
            Level.WARN),
        DISALLOWED_SCHEME(
            """
            link-to-welcome-page did not present a link; the action was a no-op. \
            Realm '{}'. Reason: resolved welcome-page URI '{}' is not an http(s) \
            URL; refusing to set it as the link target (info.ftl renders \
            pageRedirectUri in an href).""",
            Level.WARN),
        INVALID_REDIRECT_URI(
            """
            link-to-welcome-page did not present a link; the action was a no-op. \
            Realm '{}'. Reason: configured welcome-page URI '{}' is not a valid \
            redirect URI for the target client at run time \
            (RedirectUtils.verifyRedirectUri returned null).""",
            Level.WARN),
        NOT_ACTION_TOKEN_FLOW(
            """
            link-to-welcome-page did not present a link; the action was a no-op. \
            Realm '{}'. Reason: not running in an action-token flow (the \
            INVALIDATE_ACTION_TOKEN auth note is absent); this action only \
            presents a link after action-token-driven required actions (e.g. \
            execute-actions-email), and is a no-op in a normal login flow.""",
            Level.DEBUG);

        private final Level level;
        private final String template;

        Outcome(String template, Level level) {
            this.level = level;
            this.template = template;
        }
    }

    /**
     * Used only on the default home-URI path (the configured path is
     * scheme-validated by RedirectUtils.verifyRedirectUri). Mirrors
     * RedirectUtils' http(s)-only rule (see RedirectUtils line ~323): the
     * welcome-page URI flows into an {@code <a href>} in info.ftl, so only
     * http(s) schemes are allowed, blocking {@code javascript:}/{@code data:}
     * scheme links.
     */
    private static boolean isAllowedRedirectScheme(String uri) {
        return uri != null
            && (uri.startsWith("http://") || uri.startsWith("https://"));
    }

    @Override
    public void close() {
    }
}
