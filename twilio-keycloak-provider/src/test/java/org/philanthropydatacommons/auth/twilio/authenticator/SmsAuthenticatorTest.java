package org.philanthropydatacommons.auth.twilio.authenticator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.models.*;
import org.keycloak.theme.Theme;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.philanthropydatacommons.auth.twilio.authenticator.SmsAuthenticator.SENDER_ID_PROPERTY_NAME;
import static org.philanthropydatacommons.auth.twilio.authenticator.SmsAuthenticator.SMS_FORMAT_PROPERTY_NAME;

@ExtendWith(MockitoExtension.class)
public class SmsAuthenticatorTest {
    @Mock private AuthenticationFlowContext mockAuthenticationFlowContext;
    @Mock private AuthenticatorConfigModel mockAuthenticatorConfigModel;
    @Mock private KeycloakSession mockKeycloakSession;
    @Mock private KeycloakContext mockKeycloakContext;
    @Mock private Theme mockTheme;
    @Mock private ThemeManager mockThemeManager;
    @Mock private UserModel mockUserModel;

    @BeforeEach void setup() throws IOException {
        when(mockKeycloakContext.resolveLocale(any(UserModel.class))).thenReturn(Locale.ENGLISH);
        when(mockThemeManager.getTheme(Theme.Type.LOGIN)).thenReturn(mockTheme);
        when(mockKeycloakSession.theme()).thenReturn(mockThemeManager);
        when(mockKeycloakSession.getContext()).thenReturn(mockKeycloakContext);
        when(mockAuthenticationFlowContext.getAuthenticatorConfig()).thenReturn(mockAuthenticatorConfigModel);
        when(mockAuthenticationFlowContext.getUser()).thenReturn(mockUserModel);
        when(mockAuthenticationFlowContext.getSession()).thenReturn(mockKeycloakSession);
    }

    @Test void getLocalizedSmsTextCorrectlyFormatsMessage() throws IOException {
        // Set up message properties to be returned from mock keycloak.
        Properties properties = new Properties(1);
        properties.setProperty(SMS_FORMAT_PROPERTY_NAME, "%1$s: code=%2$s validity=%3$d");
        when(mockTheme.getMessages(any(Locale.class))).thenReturn(properties);
        when(mockAuthenticatorConfigModel.getConfig())
                .thenReturn(Map.of(SENDER_ID_PROPERTY_NAME, "Testing"));
        // Create the args to send.
        String code = "939391";
        Duration ttl = Duration.ofMinutes(19);
        // SmsAuthenticator is the class under test.
        SmsAuthenticator smsAuthenticator = new SmsAuthenticator(null);
        // Here we go: call the method with our mocked keycloak object tree.
        String smsText = smsAuthenticator.getLocalizedSmsText(this.mockAuthenticationFlowContext, code, ttl);
        assertEquals("Testing: code=939391 validity=19", smsText);
    }
}
