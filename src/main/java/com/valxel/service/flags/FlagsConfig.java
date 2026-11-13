package com.valxel.service.flags;

import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.OpenFeatureAPI;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Points OpenFeature at the product's Flipt, or leaves every flag at its default. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FlagsProperties.class)
public class FlagsConfig {

    private static final Logger log = LoggerFactory.getLogger(FlagsConfig.class);

    @Bean
    Client featureFlagClient(FlagsProperties props, ObjectProvider<RestClient.Builder> builder) {
        OpenFeatureAPI api = OpenFeatureAPI.getInstance();
        // Spring's builder: flag calls are traced and timed like every other outbound call.
        FeatureProvider provider = provider(props, builder.getIfAvailable(RestClient::builder));
        if (provider == null) {
            log.info("app.flags.url is not set: every feature flag reads as its default (off)");
        } else {
            api.setProvider(provider);
        }
        return api.getClient();
    }

    /** Users with each feature on against off, logged once a minute for the SDLC tool's gradual releases. */
    @Bean(destroyMethod = "close")
    FlagCohorts flagCohorts() {
        return FlagCohorts.reporting();
    }

    @Bean
    FlagCohortFilter flagCohortFilter(FlagCohorts cohorts) {
        return new FlagCohortFilter(cohorts);
    }

    static FeatureProvider provider(FlagsProperties props) {
        return provider(props, RestClient.builder());
    }

    /**
     * A slow Flipt costs a request at most a quarter of a second (the flag then reads as its
     * default); with the answers cached in {@link Flags}, most requests don't call Flipt at all.
     */
    static FeatureProvider provider(FlagsProperties props, RestClient.Builder builder) {
        if (props.url().isEmpty()) {
            return null;
        }
        SimpleClientHttpRequestFactory http = new SimpleClientHttpRequestFactory();
        http.setConnectTimeout(Duration.ofMillis(250));
        http.setReadTimeout(Duration.ofMillis(250));
        return new OfrepProvider(builder.requestFactory(http), props.url(), props.environment(),
                props.namespace(), props.token());
    }
}
