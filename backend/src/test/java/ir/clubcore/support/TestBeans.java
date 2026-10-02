package ir.clubcore.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class TestBeans {

    @Bean
    @Primary
    FakeLlmClient fakeLlmClient() {
        return new FakeLlmClient();
    }
}
