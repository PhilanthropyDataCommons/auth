/*
 * Copyright (c) 2020 Niko Köbler
 * Copyright (c) 2023 Open Tech Strategies, LLC
 * License: Expat (MIT) license.
 */
package org.philanthropydatacommons.auth.twilio.authenticator;

import jakarta.ws.rs.core.Response;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.common.util.SecretGenerator;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.theme.Theme;
import org.philanthropydatacommons.auth.twilio.SmsSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.MessageDigest;
import java.time.*;
import java.util.Locale;

import static java.nio.charset.StandardCharsets.UTF_8;

public class SmsAuthenticator implements Authenticator {
    private static final Logger LOGGER = LoggerFactory.getLogger(SmsAuthenticator.class);
    private static final String TPL_CODE = "login-sms.ftl";
    private static final String TTL = "ttl";
    private static final String EXPIRATION_EPOCH_MILLIS = "expirationEpochMillis";
    private static final String CODE = "code";
    static final String SMS_FORMAT_PROPERTY_NAME = "smsAuthText";
    static final String SENDER_ID_PROPERTY_NAME = "senderId";

    static {
        LOGGER.debug("Initializing the SmsAuthenticator class.");
    }

    private final SmsSender smsSender;

    SmsAuthenticator(SmsSender smsSender) {
        this.smsSender = smsSender;
    }

    public SmsAuthenticator() {
        this.smsSender = new SmsSender();
    }

    /**
     * Create an SMS text message using the given context, OTP code, and TTL.
     * @param context The current authentication context.
     * @param code The One Time Passcode to send to the user.
     * @param ttl The duration of time the code is valid.
     * @return A localized text message containing the given information and sender from config.
     * @throws KeycloakThemeConfigurationException When getting theme/properties throws exception.
     */
    String getLocalizedSmsText(AuthenticationFlowContext context, String code, Duration ttl) {
        AuthenticatorConfigModel config = context.getAuthenticatorConfig();
        UserModel user = context.getUser();
        KeycloakSession session = context.getSession();
        String senderId = config.getConfig().get(SENDER_ID_PROPERTY_NAME);
        Locale locale = session.getContext().resolveLocale(user);
        try {
            Theme theme = session.theme().getTheme(Theme.Type.LOGIN);
            String smsAuthText = theme.getMessages(locale).getProperty(SMS_FORMAT_PROPERTY_NAME);
            return String.format(smsAuthText, senderId, code, ttl.toMinutes());
        } catch(IOException ioe) {
            throw new KeycloakThemeConfigurationException("Expected to find the `" +
                    SMS_FORMAT_PROPERTY_NAME
                    + "` property in the LOGIN theme but lookup failed.", ioe);
        }
    }

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        AuthenticatorConfigModel config = context.getAuthenticatorConfig();
        UserModel user = context.getUser();

        String mobileNumber = user.getFirstAttribute("mobile_number");
        // mobileNumber of course has to be further validated on proper format, country code, ...

        int length = Integer.parseInt(config.getConfig().get("length"));
        Duration ttl = Duration.ofSeconds(Integer.parseInt(config.getConfig().get(TTL)));
        Instant expiration = Instant.now().plus(ttl);
        String code = SecretGenerator.getInstance().randomString(length, SecretGenerator.DIGITS);
        AuthenticationSessionModel authSession = context.getAuthenticationSession();
        authSession.setAuthNote(CODE, code);
        authSession.setAuthNote(EXPIRATION_EPOCH_MILLIS, String.valueOf(expiration.toEpochMilli()));

        try {
            String smsText = this.getLocalizedSmsText(context, code, ttl);
            this.getSmsSender().send(mobileNumber, smsText);
            LOGGER.debug("Sent OTP via SMS to user '{}'", user.getId());
            context.challenge(context.form().setAttribute("realm", context.getRealm()).createForm(TPL_CODE));
        } catch (RuntimeException e) {
            context.failureChallenge(AuthenticationFlowError.INTERNAL_ERROR,
                    context.form().setError("smsAuthSmsNotSent", e.getMessage())
                            .createErrorPage(Response.Status.INTERNAL_SERVER_ERROR));
        }
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        String enteredCode = context.getHttpRequest().getDecodedFormParameters().getFirst(CODE);

        AuthenticationSessionModel authSession = context.getAuthenticationSession();
        String code = authSession.getAuthNote(CODE);
        String expirationEpochMillis = authSession.getAuthNote(EXPIRATION_EPOCH_MILLIS);

        if (code == null || expirationEpochMillis == null) {
            context.failureChallenge(AuthenticationFlowError.INTERNAL_ERROR,
                    context.form().createErrorPage(Response.Status.INTERNAL_SERVER_ERROR));
            return;
        }

        Instant now = Instant.now();
        Instant expiration = Instant.ofEpochMilli(Long.parseLong(expirationEpochMillis));
        if (now.isAfter(expiration)) {
            // expired
            context.failureChallenge(AuthenticationFlowError.EXPIRED_CODE,
                    context.form().setError("smsAuthCodeExpired").createErrorPage(Response.Status.BAD_REQUEST));
            return;
        }
        // Use a constant-time byte comparison to mitigate a timing attack.
        boolean codeMatches = MessageDigest.isEqual(code.getBytes(UTF_8), enteredCode.getBytes(UTF_8));
        if (codeMatches) {
            // valid
            context.success();
        } else {
            // invalid
            AuthenticationExecutionModel execution = context.getExecution();
            if (execution.isRequired()) {
                context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS,
                        context.form().setAttribute("realm", context.getRealm())
                                .setError("smsAuthCodeInvalid").createForm(TPL_CODE));
            } else if (execution.isConditional() || execution.isAlternative()) {
                context.attempted();
            }
        }
    }

    @Override
    public boolean requiresUser() {
        return true;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return user.getFirstAttribute("mobile_number") != null;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
        // this will only work if you have the required action from here configured:
        // https://github.com/dasniko/keycloak-extensions-demo/tree/main/requiredaction
        user.addRequiredAction("mobile-number-ra");
    }

    @Override
    public void close() {
    }

    private SmsSender getSmsSender() {
        return this.smsSender;
    }
}
