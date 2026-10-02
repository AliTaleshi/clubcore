package ir.clubcore.notification;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import ir.clubcore.config.AppProperties;

@Service
public class SmsService {

    private final SmsSender sender;
    private final SmsLogRepository logs;

    public SmsService(AppProperties props, RestClient.Builder builder, SmsLogRepository logs) {
        this.logs = logs;
        AppProperties.Sms sms = props.sms();
        if ("kavenegar".equalsIgnoreCase(sms.provider()) && sms.kavenegarApiKey() != null
                && !sms.kavenegarApiKey().isBlank()) {
            this.sender = new KavenegarSmsSender(builder, sms.kavenegarApiKey(), sms.kavenegarSender());
        } else {
            this.sender = new MockSmsSender();
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean send(String phone, String message) {
        boolean ok = sender.send(phone, message);
        SmsLog log = new SmsLog();
        log.setPhone(phone);
        log.setMessage(message);
        log.setProvider(sender.name());
        log.setSuccess(ok);
        logs.save(log);
        return ok;
    }

    public String providerName() {
        return sender.name();
    }
}
