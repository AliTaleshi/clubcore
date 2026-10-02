package ir.clubcore.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

    @Bean
    Clock clock(AppProperties props) {
        return Clock.system(ZoneId.of(props.zone()));
    }
}
