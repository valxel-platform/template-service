package com.vrixel.service.flags;

import static org.assertj.core.api.Assertions.assertThat;

import dev.openfeature.sdk.NoOpProvider;
import dev.openfeature.sdk.OpenFeatureAPI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class FlagsConfigTest {

    @AfterEach
    void noFlags() {
        OpenFeatureAPI.getInstance().setProviderAndWait(new NoOpProvider());
    }

    @Test
    void withoutAUrlFlagsStayAtTheirDefaults() {
        FlagsProperties props = new FlagsProperties(null, null, null, null);
        assertThat(props.url()).isEmpty();
        assertThat(FlagsConfig.provider(props)).isNull();
        assertThat(new FlagsConfig().featureFlagClient(props, noBuilder()).getBooleanValue("emr.x", false)).isFalse();
    }

    @Test
    void withAUrlFlagsComeFromTheProductsFlipt() {
        FlagsProperties props = new FlagsProperties(" http://flipt.emr.svc:8080 ", "production", "emr", "");
        assertThat(FlagsConfig.provider(props)).isInstanceOf(OfrepProvider.class);
        new FlagsConfig().featureFlagClient(props, noBuilder());
        assertThat(OpenFeatureAPI.getInstance().getProviderMetadata().getName()).isEqualTo("flipt-ofrep");
    }

    /** No Spring-made builder in a unit test: the provider falls back to RestClient.builder(). */
    private static org.springframework.beans.factory.ObjectProvider<org.springframework.web.client.RestClient.Builder> noBuilder() {
        return new org.springframework.beans.factory.support.StaticListableBeanFactory()
                .getBeanProvider(org.springframework.web.client.RestClient.Builder.class);
    }
}
