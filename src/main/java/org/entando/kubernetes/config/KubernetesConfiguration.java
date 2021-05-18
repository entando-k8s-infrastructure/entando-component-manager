package org.entando.kubernetes.config;

import static org.awaitility.Awaitility.await;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.time.Duration;
import org.awaitility.core.ConditionFactory;
import org.entando.kubernetes.client.k8ssvc.DefaultK8SServiceClient;
import org.entando.kubernetes.client.k8ssvc.K8SServiceClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
public class KubernetesConfiguration {

    @Value("${entando.k8s.service.url}")
    private String k8sServiceUrl;
    @Value("${entando.k8s.plugin-readiness-timeout-in-minutes:5}")
    private long pluginReadinessTimeoutInMinutes;
    @Value("${entando.k8s.service-account.token-filepath}")
    private String serviceAccountTokenPath;

    @Bean
    public KubernetesClient client() {
        final Config config = new ConfigBuilder().build();
        return new DefaultKubernetesClient(config);
    }

    @Bean
    public K8SServiceClient k8SServiceClient() {
        return new DefaultK8SServiceClient(k8sServiceUrl, serviceAccountTokenPath);
    }

    @Bean
    public ConditionFactory k8SServiceWaitingConditionFactory() {
        return await().atMost(Duration.ofMinutes(pluginReadinessTimeoutInMinutes))
                .pollDelay(Duration.ZERO)
                .pollInterval(Duration.ofSeconds(10));
    }

}
