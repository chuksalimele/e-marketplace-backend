package com.aliwudi.marketplace.backend.notification.service;

import com.aliwudi.marketplace.backend.common.exception.NotificationException;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Call;
import com.twilio.type.PhoneNumber;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import jakarta.annotation.PostConstruct;
import java.net.URI; // For TwiML URL if you use it

/**
 * Twilio implementation of PhoneCallService for voice messages.
 */
@Service
@Slf4j
public class TwilioPhoneCallService implements PhoneCallService {

    @Value("${twilio.account.sid}")
    private String accountSid;

    @Value("${twilio.auth.token}")
    private String authToken;

    @Value("${twilio.phone.number}")
    private String twilioPhoneNumber;

    // Optional: If you use a TwiML Bin or hosted TwiML. For simple 'say', we can construct it directly.
    // @Value("${twilio.twiml.base-url:}")
    // private String twimlBaseUrl;

    @PostConstruct
    public void init() {
        Twilio.init(accountSid, authToken);
    }

    @Override
    public Mono<Void> initiateCall(String toPhoneNumber, String messageBody) {
        return Mono.fromCallable(() -> {
            try {
                // For a simple text-to-speech message, we can use a TwiML string directly
                // or a simple HTTP POST to Twilio's /2010-04-01/Accounts/{AccountSid}/Calls.json endpoint
                // by providing the <Say> verb in the URL.
                // A better approach for complex voice messages is to host TwiML or use Twilio Functions.
                String twiml = "<Response><Say voice=\"woman\">" + messageBody + "</Say></Response>";
                // Or if you have a pre-hosted TwiML: URI.create(twimlBaseUrl + "/voice-otp?otp=" + messageBody)

                Call call = Call.creator(
                                new PhoneNumber(toPhoneNumber), // to
                                new PhoneNumber(twilioPhoneNumber), // from
                                URI.create("http://twimlets.com/message?Message%5B0%5D=" + java.net.URLEncoder.encode(messageBody, "UTF-8"))) // TwiML URL to speak the message
                                // OR for a hosted TwiML URL: URI.create("https://example.com/twiml/say?message=" + URLEncoder.encode(messageBody, "UTF-8")))
                                // OR if you have a /voice endpoint that returns TwiML: URI.create("http://your-app-domain.com/api/voice/otp?otp=" + messageBody)
                        .create();
                log.info("Phone call initiated successfully to {} with SID: {}", toPhoneNumber, call.getSid());
                return null;
            } catch (Exception e) {
                log.error("Failed to initiate phone call to {}: {}", toPhoneNumber, e.getMessage(), e);
                throw new NotificationException("Failed to initiate phone call to " + toPhoneNumber, e);
            }
        })
        .onErrorResume(NotificationException.class, Mono::error)
        .onErrorResume(e -> {
            log.error("An unexpected error occurred during phone call initiation for {}: {}", toPhoneNumber, e.getMessage(), e);
            return Mono.error(new NotificationException("An unexpected error occurred during phone call initiation.", e));
        })
        .then()
        .subscribeOn(Schedulers.boundedElastic());
    }
}