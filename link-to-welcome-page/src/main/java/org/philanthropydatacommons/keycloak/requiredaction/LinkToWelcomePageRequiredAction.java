/*
 * Copyright (c) 2026 Philanthropy Data Commons
 * License: Apache Software License 2.0.
 */

package org.philanthropydatacommons.keycloak.requiredaction;

import org.jspecify.annotations.Nullable;
import org.keycloak.authentication.InitiatedActionSupport;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.models.ClientModel;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RequiredActionConfigModel;
import org.keycloak.services.managers.AuthenticationManager;
import org.keycloak.services.util.ResolveRelative;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

/**
 * Required action that sets a "back to application" link on the post-actions info page. A no-op
 * outside the action-token flow (e.g. execute-actions-email), so it is safe to leave enabled
 * realm-wide. See README.md. Stateless; the factory creates a new instance per request.
 */
public final class LinkToWelcomePageRequiredAction implements RequiredActionProvider {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(LinkToWelcomePageRequiredAction.class);

  public static final String PROVIDER_ID = "link-to-welcome-page";

  public static final String CONFIG_TARGET_CLIENT = "targetClient";

  @Override
  public InitiatedActionSupport initiatedActionSupport() {
    // NOT_SUPPORTED: the action only presents a link inside the action-token
    // flow (gated on INVALIDATE_ACTION_TOKEN) and is a no-op elsewhere, so
    // exposing it as an application-initiated action would let a user
    // self-trigger a no-op. It runs as a required action added by
    // execute-actions-email, not as an AIA.
    return InitiatedActionSupport.NOT_SUPPORTED;
  }

  @Override
  public void evaluateTriggers(RequiredActionContext context) {
    // Empty by design: this action is added to a user explicitly and never auto-triggers.
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

    String welcomePageUri =
        ResolveRelative.resolveRelativeUri(
            context.getSession(), client.getRootUrl(), client.getBaseUrl());
    if (welcomePageUri == null || welcomePageUri.isBlank()) {
      logNoop(context, Outcome.NO_RESOLVED_URI, targetClientAlias);
      return;
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

  private void logNoop(RequiredActionContext context, Outcome outcome, @Nullable String detail) {
    String pattern = outcome.template;
    if (outcome.level == Level.WARN) {
      LOGGER.warn(pattern, context.getRealm().getName(), detail);
    } else {
      LOGGER.debug(pattern, context.getRealm().getName(), detail);
    }
  }

  /**
   * Why the action did not present a link, with the log level for its no-op message: the expected
   * no-op (outside the action-token flow) is DEBUG; genuine misconfigurations are WARN, so an admin
   * who accidentally enables this as a realm default does not flood the logs on every login.
   *
   * <p>Each template is a single physical line: the source is broken across {@code +}-concatenated
   * fragments (not a text block, which would add a trailing {@code \n} the logger adds to again),
   * so each {@code LOGGER} call emits exactly one line for line-oriented log ingestion.
   *
   * <p>Package-private rather than {@code private} so the test in this package can exercise
   * the production {@code render} rather than a duplicate of the template.
   */
  enum Outcome {
    NO_TARGET_CLIENT(
        "link-to-welcome-page did not present a link; the action was a no-op. "
            + "Realm '{}'. Reason: no target client is configured for this required "
            + "action (set the 'targetClient' config property).",
        Level.WARN),
    CLIENT_GONE(
        "link-to-welcome-page did not present a link; the action was a no-op. "
            + "Realm '{}'. Reason: configured target client '{}' no longer exists.",
        Level.WARN),
    NO_RESOLVED_URI(
        "link-to-welcome-page did not present a link; the action was a no-op. "
            + "Realm '{}'. Reason: could not resolve a welcome-page URI: target "
            + "client '{}' has neither a root URL nor a base URL.",
        Level.WARN),
    NOT_ACTION_TOKEN_FLOW(
        "link-to-welcome-page did not present a link; the action was a no-op. "
            + "Realm '{}'. Reason: not running in an action-token flow (the "
            + "INVALIDATE_ACTION_TOKEN auth note is absent); this action only "
            + "presents a link after action-token-driven required actions (e.g. "
            + "execute-actions-email), and is a no-op in a normal login flow.",
        Level.DEBUG);

    private final Level level;
    private final String template;

    Outcome(String template, Level level) {
      this.level = level;
      this.template = template;
    }

    /**
     * Renders this outcome's log message the way slf4j would: each {@code {}} placeholder replaced
     * by the next argument's {@code toString()} (an absent detail renders as {@code "null"}, as an
     * extra slf4j vararg would). Used by the test to pin the rendered form to the real template
     * rather than a copy of it.
     */
    String render(String realm, @Nullable String detail) {
      return template.replaceFirst("\\{}", realm)
          .replaceFirst("\\{}", detail == null ? "null" : detail);
    }
  }

  @Override
  public void close() {}
}
