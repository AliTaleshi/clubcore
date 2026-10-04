package ir.clubcore.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** RestClient builders for third-party APIs (gateways, SMS) with bounded connect/read timeouts. */
@Component
public class OutboundHttp {

    private final RestClient.Builder builder;
    private final AppProperties.Http http;

    public OutboundHttp(RestClient.Builder builder, AppProperties props) {
        this.builder = builder;
        this.http = props.http() != null ? props.http() : new AppProperties.Http(5, 20);
    }

    public RestClient.Builder builder() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(http.connectTimeoutSeconds()))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofSeconds(http.readTimeoutSeconds()));
        return builder.clone().requestFactory(factory);
    }
}
