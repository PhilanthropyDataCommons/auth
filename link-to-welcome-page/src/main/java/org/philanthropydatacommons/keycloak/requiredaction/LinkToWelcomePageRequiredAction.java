/*
 * Copyright (c) 2026 Philanthropy Data Commons
 * License: Apache Software License 2.0.
 *
 * Author: GLM-5.2
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
 * Required action that sets a "back to application" link on the post-actions
 * info page. A no-op outside the action-token flow (e.g. execute-actions-email),
 * so it is safe to leave enabled realm-wide. See README.md. Stateless; the
 * factory creates a new instance per request.
 *
 * @author GLM-5.2
 */
public final class LinkToWelcomePageRequiredAction implements RequiredActionProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(LinkToWelcomePageRequiredAction.class);

    public static final String PROVIDER_ID = "link-to-welcome-page";

    public static final String CONFIG_TARGET_CLIENT = "targetClient";
    public static final String CONFIG_TARGET_WELCOME_PAGE_URI = "targetWelcomePageUri";

    @Override
    public InitiatedActionSupport initiatedActionSupport() {
        return InitiatedActionSupport.SUPPORTED;
    }

    @Override
    public void evaluateTriggers(RequiredActionContext context) {
        // Empty by design: this action is added to a user explicitly and never auto-triggers. GLM-5.2
    }

    @Override
    public void requiredActionChallenge(RequiredActionContext context) {
        run(context);
    }

    @Override
    public void processAction(RequiredActionContext context) {
        run(context);
    }

    private void run(RequiredActionContext context) {
        applyLink(context);
        context.success();
    }

    private void applyLink(RequiredActionContext context) {
        RequiredActionConfigModel config = context.getConfig();
        String targetClientAlias = config == null ? null : config.getConfigValue(CONFIG_TARGET_CLIENT);

        if (targetClientAlias == null || targetClientAlias.isBlank()) {
            logNoop(context, Outcome.NO_TARGET_CLIENT, null);
            return;
        }

        RealmModel realm = context.getRealm();
        ClientModel client = realm.getClientByClientId(targetClientAlias);
        if (client == null) {
            logNoop(context, Outcome.CLIENT_GONE, targetClientAlias);
            return;
        }

        String welcomePageUri = config.getConfigValue(CONFIG_TARGET_WELCOME_PAGE_URI);
        if (welcomePageUri != null && !welcomePageUri.isBlank()) {
            // Revalidate at run time to close the TOCTOU window: the client's
            // registered URIs can change between save and run. Also resolves
            // relative values ("/welcome") the save-time check accepted.
            String resolved = RedirectUtils.verifyRedirectUri(
                    context.getSession(), welcomePageUri, client);
            if (resolved == null) {
                logNoop(context, Outcome.INVALID_REDIRECT_URI, welcomePageUri);
                return;
            }
            welcomePageUri = resolved;
        } else {
            welcomePageUri = ResolveRelative.resolveRelativeUri(
                    context.getSession(), client.getRootUrl(), client.getBaseUrl());
            if (welcomePageUri == null || welcomePageUri.isBlank()) {
                logNoop(context, Outcome.NO_RESOLVED_URI, targetClientAlias);
                return;
            }
            // Defense in depth: the home URI is not redirect-URI-validated (it
            // mirrors Keycloak's own client.baseUrl fallback), and a realm
            // import can set a rootUrl/baseUrl that saves without validation,
            // so reject non-http(s) schemes before they reach info.ftl's href.
            if (!isAllowedRedirectScheme(welcomePageUri)) {
                logNoop(context, Outcome.DISALLOWED_SCHEME, welcomePageUri);
                return;
            }
        }

        AuthenticationSessionModel authSession = context.getAuthenticationSession();
        // Gated on the action-token flow: setting END_AFTER_REQUIRED_ACTIONS
        // outside it would short-circuit a normal login and break every login
        // if an admin sets this action as a realm default.
        if (authSession.getAuthNote(AuthenticationManager.INVALIDATE_ACTION_TOKEN) == null) {
            logNoop(context, Outcome.NOT_ACTION_TOKEN_FLOW, null);
            return;
        }

        authSession.setAuthNote(AuthenticationManager.END_AFTER_REQUIRED_ACTIONS, "true");
        authSession.setAuthNote(AuthenticationManager.SET_REDIRECT_URI_AFTER_REQUIRED_ACTIONS, "true");
        authSession.setRedirectUri(welcomePageUri);
    }

    private void logNoop(RequiredActionContext context, Outcome outcome, String detail) {
        String pattern = outcome.template;
        if (outcome.level == Level.WARN) {
            LOGGER.warn(pattern, context.getRealm().getName(), detail);
        } else {
            LOGGER.debug(pattern, context.getRealm().getName(), detail);
        }
    }

    /**
     * Why the action did not present a link, with the log level for its no-op
     * message: the expected no-op (outside the action-token flow) is DEBUG;
     * genuine misconfigurations are WARN, so an admin who accidentally enables
     * this as a realm default does not flood the logs on every login.
     *
     * @author GLM-5.2
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

    // Mirrors RedirectUtils' http(s)-only rule: the welcome-page URI flows
    // into an <a href> in info.ftl, so only http(s) schemes are allowed,
    // blocking javascript:/data: scheme links.
    private static boolean isAllowedRedirectScheme(String uri) {
        return uri != null
            && (uri.startsWith("http://") || uri.startsWith("https://"));
    }

    @Override
    public void close() {
    }
}
