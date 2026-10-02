package ir.clubcore.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Development SMS provider: messages are only logged (and stored in sms_logs). */
public class MockSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(MockSmsSender.class);

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public boolean send(String phone, String message) {
        log.info("[MOCK SMS] to={} message={}", phone, message);
        return true;
    }
}
