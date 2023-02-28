/*
 * Copyright (c) 2023 Open Tech Strategies, LLC
 * License: Expat (MIT) license.
 */
package org.philanthropydatacommons.auth.twilio;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Allows a caller to send SMS messages by calling {@link #send(String, String)}
 */
public class SmsSender {
    private static final Logger LOGGER = LoggerFactory.getLogger(SmsSender.class);
    /** The name of the environment variable holding a Twilio Account SID. */
    private static final String TWILIO_ACCOUNT_SID_ENV_VAR = "TWILIO_ACCOUNT_SID";
    /** The name of the environment variable holding a Twilio Auth Token. */
    private static final String TWILIO_AUTH_TOKEN_ENV_VAR = "TWILIO_AUTH_TOKEN";
    /** The name of the environment variable holding a Twilio (from) Phone Number. */
    private static final String TWILIO_PHONE_NUMBER_ENV_VAR = "TWILIO_PHONE_NUMBER";
    private static final PhoneNumber FROM;

    static final class ConfigurationFailedException extends RuntimeException {
        public ConfigurationFailedException(String message) {
            super(message);
        }
    }
    private static final class MessageFailedException extends RuntimeException {
        private static String createErrorString(Message failedMessage) {
            if (failedMessage.getStatus() != Message.Status.FAILED) {
                throw new IllegalArgumentException("Only failed messages allowed. Got something else.");
            }

            String message = "Send SMS failed.";

            if (failedMessage.getSid() != null) {
                message += " Sid: " + failedMessage.getSid();
            }

            if (failedMessage.getErrorCode() != null) {
                message += " Error code: " + failedMessage.getErrorCode() + ".";
            }

            if (failedMessage.getErrorMessage() != null && !failedMessage.getErrorMessage().isBlank()) {
                message += " Error message: '" + failedMessage.getErrorMessage() + "'.";
            }

            return message;
        }
        public MessageFailedException(Message failedMessage) {
            super(MessageFailedException.createErrorString(failedMessage));
        }
    }

    static {
        String accountSid = System.getenv(TWILIO_ACCOUNT_SID_ENV_VAR);
        String authToken = System.getenv(TWILIO_AUTH_TOKEN_ENV_VAR);
        String phoneNumberRaw = System.getenv(TWILIO_PHONE_NUMBER_ENV_VAR);
        List<String> missingEnvVars = new ArrayList<>(3);

        if (accountSid == null || accountSid.isBlank()) {
            missingEnvVars.add(TWILIO_ACCOUNT_SID_ENV_VAR);
        }
        if (authToken == null || authToken.isBlank()) {
            missingEnvVars.add(TWILIO_AUTH_TOKEN_ENV_VAR);
        }
        if (phoneNumberRaw == null || phoneNumberRaw.isBlank()) {
            missingEnvVars.add(TWILIO_PHONE_NUMBER_ENV_VAR);
        }
        if (!missingEnvVars.isEmpty()) {
            throw new ConfigurationFailedException("Expected these Twilio environment variables: " +
                    missingEnvVars);
        }

        Twilio.init(accountSid, authToken);
        FROM = new PhoneNumber(phoneNumberRaw);
    }

    /** We would like this to be an instance variable but Twilio SDK is all static for now. */
    private PhoneNumber getFrom() {
        return FROM;
    }

    /**
     * Sends an SMS with the given message to the given "to" number.
     * @param to The number to whom to send the message.
     * @param message The message to send.
     * @throws MessageFailedException When resulting message status is {@link Message.Status#FAILED}
     */
    public void send(String to, String message) {
        Message result = Message.creator(new PhoneNumber(to), this.getFrom(), message)
                .create();
        LOGGER.info("Sent an SMS with sid='{}'.", result.getSid());
        if (result.getStatus().equals(Message.Status.FAILED)) {
            throw new MessageFailedException(result);
        }
    }
}
