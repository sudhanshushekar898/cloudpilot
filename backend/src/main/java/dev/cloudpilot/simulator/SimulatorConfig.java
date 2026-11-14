package dev.cloudpilot.simulator;

import java.util.Random;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SimulatorProperties.class)
public class SimulatorConfig {

    @Bean
    public MetricGenerator metricGenerator() {
        return new MetricGenerator(new Random());
    }

    @Bean
    public LogGenerator logGenerator() {
        return new LogGenerator(new Random());
    }
}
