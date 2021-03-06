package org.entando.kubernetes.config;

import static org.awaitility.Awaitility.await;

import io.fabric8.kubernetes.client.KubernetesClient;
import java.time.Duration;
import org.awaitility.core.ConditionFactory;
import org.entando.kubernetes.client.K8SServiceClientTestDouble;
import org.entando.kubernetes.client.k8ssvc.K8SServiceClient;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@TestConfiguration
@Profile("test")
public class TestKubernetesConfig {

    @Bean
    public KubernetesClient client() {
        return Mockito.mock(KubernetesClient.class);
    }

    @Bean
    @Primary
    public K8SServiceClient k8SServiceClient() {
        return new K8SServiceClientTestDouble();
    }

    @Bean
    JwtDecoder jwtDecoder() {
        return Mockito.mock(JwtDecoder.class);
    }

    @Bean
    @Primary
    public ConditionFactory waitingConditionFactory() {
        return await()
                .atMost(Duration.ofSeconds(5))
                .pollDelay(Duration.ZERO)
                .pollInterval(Duration.ofSeconds(1));
    }

}
