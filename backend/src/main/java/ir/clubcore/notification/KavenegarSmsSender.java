package ir.clubcore.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/** Kavenegar REST API: https://api.kavenegar.com/v1/{API-KEY}/sms/send.json */
public class KavenegarSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(KavenegarSmsSender.class);

    private final RestClient client;
    private final String apiKey;
    private final String sender;

    public KavenegarSmsSender(RestClient.Builder builder, String apiKey, String sender) {
        this.client = builder.baseUrl("https://api.kavenegar.com").build();
        this.apiKey = apiKey;
        this.sender = sender;
    }

    @Override
    public String name() {
        return "kavenegar";
    }

    @Override
    public boolean send(String phone, String message) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("receptor", phone);
        form.add("message", message);
        if (sender != null && !sender.isBlank()) {
            form.add("sender", sender);
        }
        try {
            client.post().uri("/v1/{key}/sms/send.json", apiKey)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.warn("Kavenegar SMS failed: {}", e.getMessage());
            return false;
        }
    }
}
