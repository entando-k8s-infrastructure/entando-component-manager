package org.entando.kubernetes.client.k8ssvc;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.fabric8.kubernetes.api.model.extensions.IngressRule;
import io.fabric8.kubernetes.client.KubernetesClientException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.entando.kubernetes.client.model.AnalysisReport;
import org.entando.kubernetes.controller.digitalexchange.job.model.Status;
import org.entando.kubernetes.exception.k8ssvc.K8SServiceClientException;
import org.entando.kubernetes.model.bundle.ComponentType;
import org.entando.kubernetes.model.bundle.reportable.Reportable;
import org.entando.kubernetes.model.debundle.EntandoDeBundle;
import org.entando.kubernetes.model.link.EntandoAppPluginLink;
import org.entando.kubernetes.model.plugin.EntandoPlugin;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.client.Hop;
import org.springframework.hateoas.client.Traverson;
import org.springframework.hateoas.mediatype.hal.Jackson2HalModule;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.oauth2.client.OAuth2RestTemplate;
import org.springframework.security.oauth2.client.resource.OAuth2ProtectedResourceDetails;
import org.springframework.security.oauth2.client.token.grant.client.ClientCredentialsResourceDetails;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

public class DefaultK8SServiceClient implements K8SServiceClient {

    public static final String APPS_ENDPOINT = "apps";
    public static final String PLUGINS_ENDPOINT = "plugins";
    public static final String BUNDLES_ENDPOINT = "bundles";
    public static final String APP_PLUGIN_LINKS_ENDPOINT = "app-plugin-links";
    private static final Logger LOGGER = Logger.getLogger(DefaultK8SServiceClient.class.getName());
    private final String k8sServiceUrl;
    private Path tokenFilePath;
    private RestTemplate restTemplate;
    private RestTemplate noAuthRestTemplate;
    private Traverson traverson;

    public DefaultK8SServiceClient(String k8sServiceUrl, String tokenFilePath) {
        this.k8sServiceUrl = k8sServiceUrl;
        this.tokenFilePath = Paths.get(tokenFilePath);
        this.restTemplate = newRestTemplate();
        this.traverson = newTraverson();
        this.noAuthRestTemplate = newNoAuthRestTemplate();
    }

    public Traverson newTraverson() {
        return new Traverson(URI.create(k8sServiceUrl), MediaTypes.HAL_JSON, MediaType.APPLICATION_JSON)
                .setRestOperations(getRestTemplate());
    }

    public RestTemplate getRestTemplate() {
        return restTemplate;
    }

    public void setRestTemplate(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        this.traverson = newTraverson();
    }

    public void setNoAuthRestTemplate(RestTemplate restTemplate) {
        this.noAuthRestTemplate = restTemplate;
    }

    @Override
    public List<EntandoAppPluginLink> getAppLinks(String entandoAppName) {
        return tryOrThrow(() -> {
            CollectionModel<EntityModel<EntandoAppPluginLink>> links = traverson
                    .follow(APP_PLUGIN_LINKS_ENDPOINT)
                    .follow(Hop.rel("app-links").withParameter("app", entandoAppName))
                    .toObject(new ParameterizedTypeReference<CollectionModel<EntityModel<EntandoAppPluginLink>>>() {
                    });
            assert links != null;
            return links.getContent().stream()
                    .map(EntityModel::getContent)
                    .collect(Collectors.toList());
        });
    }

    @Override
    public EntandoPlugin getPluginForLink(EntandoAppPluginLink el) {
        return tryOrThrow(() -> traverson.follow(APP_PLUGIN_LINKS_ENDPOINT)
                .follow(Hop.rel("app-plugin-link").withParameter("name", el.getMetadata().getName()))
                .follow("plugin")
                .toObject(new ParameterizedTypeReference<EntityModel<EntandoPlugin>>() {
                })
                .getContent(), "get plugin associated with link " + el.getMetadata().getName());
    }

    @Override
    public Optional<EntandoPlugin> getPluginByName(String name) {
        EntandoPlugin plugin = null;
        try {
            plugin = traverson.follow(PLUGINS_ENDPOINT)
                    .follow(Hop.rel("plugin").withParameter("name", name))
                    .toObject(new ParameterizedTypeReference<EntityModel<EntandoPlugin>>() {
                    })
                    .getContent();
        } catch (RestClientResponseException ex) {
            if (ex.getRawStatusCode() != 404) {
                throw new KubernetesClientException("An error occurred while retrieving plugin with name " + name, ex);
            }
        }
        return Optional.ofNullable(plugin);
    }

    /**
     * UPDATES A PLUGIN.
     * <p>
     * updates a plugin CR given the CR object
     * </p>
     */
    @Override
    public EntandoPlugin updatePlugin(EntandoPlugin plugin) {
        String pluginName = plugin.getMetadata().getName();
        URI updateURI = traverson.follow(PLUGINS_ENDPOINT)
                .follow(Hop.rel("create-or-replace-plugin").withParameter("name", pluginName))
                .asLink().toUri();

        return tryOrThrow(() -> {
            RequestEntity<EntandoPlugin> request = RequestEntity
                    .put(updateURI)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(plugin);

            ResponseEntity<EntityModel<EntandoPlugin>> response = restTemplate
                    .exchange(request, new ParameterizedTypeReference<EntityModel<EntandoPlugin>>() {
                    });

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody().getContent();
            } else {
                throw new RestClientResponseException("Update process failed",
                        response.getStatusCodeValue(), response.getStatusCode().getReasonPhrase(),
                        null, null, null);
            }
        }, String.format("while updating plugin %s", pluginName));

    }

    @Override
    public void unlink(EntandoAppPluginLink el) {
        String linkName = el.getMetadata().getName();
        String appName = el.getSpec().getEntandoAppName();
        String pluginName = el.getSpec().getEntandoAppNamespace().orElse(el.getMetadata().getNamespace());
        Link unlinkHref = traverson.follow(APP_PLUGIN_LINKS_ENDPOINT)
                .follow(Hop.rel("app-plugin-link").withParameter("name", linkName))
                .asLink();
        tryOrThrow(() -> restTemplate.delete(unlinkHref.toUri()),
                String.format("unlink app %s and plugin %s", appName, pluginName));
    }

    @Override
    public EntandoAppPluginLink linkAppWithPlugin(String name, String namespace, EntandoPlugin plugin) {
        URI linkToCall = tryOrThrow(() -> {
            Link linkToApp = traverson.follow("apps")
                    .follow(Hop.rel("app").withParameter("name", name))
                    .asLink();
            return UriComponentsBuilder.fromUri(linkToApp.toUri()).pathSegment("links").build(Collections.emptyMap());
        });

        return tryOrThrow(() -> {
                    RequestEntity<EntandoPlugin> request = RequestEntity
                            .post(linkToCall)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(plugin);
                    ResponseEntity<EntityModel<EntandoAppPluginLink>> resp = restTemplate
                            .exchange(request, new ParameterizedTypeReference<EntityModel<EntandoAppPluginLink>>() {
                            });
                    if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                        return resp.getBody().getContent();
                    }
                    throw new RestClientResponseException("Linking process failed",
                            resp.getStatusCodeValue(), resp.getStatusCode().getReasonPhrase(),
                            null, null, null);
                },
                String.format("linking app %s to plugin %s", name, plugin.getMetadata().getName())
        );
    }

    @Override
    public Optional<EntandoAppPluginLink> getLinkByName(String linkName) {
        EntandoAppPluginLink link = null;
        try {
            link = traverson.follow(APP_PLUGIN_LINKS_ENDPOINT)
                    .follow(Hop.rel("app-plugin-link").withParameter("name", linkName))
                    .toObject(new ParameterizedTypeReference<EntityModel<EntandoAppPluginLink>>() {
                    })
                    .getContent();
        } catch (RestClientResponseException ex) {
            if (ex.getRawStatusCode() != 404) {
                throw new KubernetesClientException(
                        "An error occurred while retrieving entando-app-plugin-link with name " + linkName, ex);
            }
        }
        return Optional.ofNullable(link);
    }

    @Override
    public List<EntandoDeBundle> getBundlesInObservedNamespaces() {
        return tryOrThrow(() -> traverson.follow(BUNDLES_ENDPOINT)
                .toObject(new ParameterizedTypeReference<CollectionModel<EntityModel<EntandoDeBundle>>>() {
                })
                .getContent()
                .stream().map(EntityModel::getContent)
                .collect(Collectors.toList()));
    }

    @Override
    public List<EntandoDeBundle> getBundlesInNamespace(String namespace) {
        return tryOrThrow(() -> traverson.follow(BUNDLES_ENDPOINT)
                .follow(Hop.rel("bundles-in-namespace").withParameter("namespace", namespace))
                .toObject(new ParameterizedTypeReference<CollectionModel<EntityModel<EntandoDeBundle>>>() {
                })
                .getContent()
                .stream().map(EntityModel::getContent)
                .collect(Collectors.toList()));
    }

    @Override
    public List<EntandoDeBundle> getBundlesInNamespaces(List<String> namespaces) {
        @SuppressWarnings("unchecked")
        CompletableFuture<List<EntandoDeBundle>>[] futures = namespaces.stream()
                .map(n -> CompletableFuture.supplyAsync(() -> getBundlesInNamespace(n))
                        .exceptionally(ex -> {
                            LOGGER.log(Level.SEVERE, "An error occurred while retrieving bundle from a namespace", ex);
                            return Collections.emptyList();
                        }))
                .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(futures).thenApply(v -> Arrays.stream(futures)
                .map(CompletableFuture::join)
                .flatMap(Collection::stream)
                .collect(Collectors.toList())
        ).join();
    }

    @Override
    public Optional<EntandoDeBundle> getBundleWithName(String name) {
        EntandoDeBundle bundle = null;
        try {
            bundle = traverson.follow(BUNDLES_ENDPOINT)
                    .follow(Hop.rel("bundle").withParameter("name", name))
                    .toObject(new ParameterizedTypeReference<EntityModel<EntandoDeBundle>>() {
                    })
                    .getContent();

        } catch (RestClientResponseException ex) {
            if (ex.getRawStatusCode() != 404) {
                throw new KubernetesClientException("An error occurred while retrieving bundle with name " + name, ex);
            }
        } catch (Exception ex) {
            throw new KubernetesClientException("An error occurred while retrieving bundle with name " + name, ex);
        }

        return Optional.ofNullable(bundle);
    }

    @Override
    public Optional<EntandoDeBundle> getBundleWithNameAndNamespace(String name, String namespace) {
        return getBundlesInNamespace(namespace).stream()
                .filter(b -> b.getMetadata().getName().equals(name))
                .findFirst();
    }

    @Override
    public boolean isPluginReadyToServeApp(EntandoPlugin plugin, String appName) {
        if (plugin.getSpec().getIngressPath() == null) {
            return false;
        }
        Ingress appIngress = getAppIngress(appName);
        IngressRule ingressRule = appIngress.getSpec().getRules().stream().findFirst().<RuntimeException>orElseThrow(
                () -> {
                    throw new K8SServiceClientException(
                            "EntandoApp ingress " + appIngress.getMetadata().getName() + " does not have an host");
                });

        String appHost = ingressRule.getHost();
        UriComponents pluginHealthCheck = UriComponentsBuilder.newInstance()
                .scheme(appIngress.getSpec().getTls().isEmpty() ? "http" : "https")
                .host(appHost)
                .path(plugin.getSpec().getIngressPath())
                .path(plugin.getSpec().getHealthCheckPath())
                .build();
        RequestEntity<?> request = RequestEntity
                .get(URI.create(pluginHealthCheck.toUriString()))
                .accept(MediaType.APPLICATION_JSON)
                .build();
        try {
            ResponseEntity<Object> response = this.noAuthRestTemplate.exchange(request, Object.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (RestClientResponseException e) {
            HttpStatus status = HttpStatus.valueOf(e.getRawStatusCode());
            if (status.equals(HttpStatus.NOT_FOUND) || status.equals(HttpStatus.SERVICE_UNAVAILABLE)) {
                return false;
            }
            throw e;
        } catch (RestClientException e) {
            throw e;
        }

    }

    @Override
    public AnalysisReport getAnalysisReport(List<Reportable> reportableList) {

        Map<String, Status> pluginStatusMap = reportableList.stream()
                .filter(reportable -> reportable.getComponentType() == ComponentType.PLUGIN)
                .flatMap(reportable -> reportable.getCodes().stream())
                .map(name ->
                        getPluginByName(name)
                                .map(plugin -> new SimpleEntry<>(name, Status.DIFF))
                                .orElseGet(() -> new SimpleEntry<>(name, Status.NEW)))
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue));

        return new AnalysisReport().setPlugins(pluginStatusMap);
    }

    private Ingress getAppIngress(String appName) {
        return tryOrThrow(() -> traverson.follow(APPS_ENDPOINT)
                .follow(Hop.rel("app").withParameter("name", appName))
                .follow("app-ingress")
                .toObject(new ParameterizedTypeReference<EntityModel<Ingress>>() {
                })
                .getContent());
    }


    private RestTemplate newRestTemplate() {

        final OAuth2RestTemplate template = new OAuth2RestTemplate(new ClientCredentialsResourceDetails());
        template.setRequestFactory(getRequestFactory());
        template.setAccessTokenProvider(new FromFileTokenProvider(this.tokenFilePath));
        return setMessageConverters(template);
    }

    private RestTemplate newNoAuthRestTemplate() {

        final RestTemplate template = new RestTemplate();
        template.setRequestFactory(getRequestFactory());
        return setMessageConverters(template);
    }

    private RestTemplate setMessageConverters(RestTemplate restTemplate) {
        List<HttpMessageConverter<?>> messageConverters = Traverson
                .getDefaultMessageConverters(MediaType.APPLICATION_JSON, MediaTypes.HAL_JSON);
        if (messageConverters.stream()
                .noneMatch(mc -> mc.getSupportedMediaTypes().contains(MediaType.APPLICATION_JSON))) {
            messageConverters.add(0, getJsonConverter());
        }
        restTemplate.setMessageConverters(messageConverters);

        return restTemplate;
    }

    private HttpMessageConverter<?> getJsonConverter() {
        final List<MediaType> supportedMediatypes = Arrays.asList(MediaType.APPLICATION_JSON);
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new Jackson2HalModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();

        converter.setObjectMapper(mapper);
        converter.setSupportedMediaTypes(supportedMediatypes);

        return converter;
    }


    private ClientHttpRequestFactory getRequestFactory() {
        final HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
        final int timeout = 10000;

        requestFactory.setConnectionRequestTimeout(timeout);
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        return requestFactory;
    }

    public void tryOrThrow(Runnable runnable, String actionDescription) {
        try {
            runnable.run();
        } catch (RestClientResponseException ex) {
            throw new KubernetesClientException(
                    String.format("An error occurred while %s: %d - %s",
                            actionDescription,
                            ex.getRawStatusCode(),
                            ex.getResponseBodyAsString()),
                    ex);
        } catch (Exception ex) {
            throw new KubernetesClientException("A generic error occurred while " + actionDescription, ex);
        }
    }

    public <T> T tryOrThrow(Supplier<T> supplier) {
        return tryOrThrow(supplier, "talking with k8s-service");
    }


    public <T> T tryOrThrow(Supplier<T> supplier, String action) {
        try {
            return supplier.get();
        } catch (RestClientResponseException ex) {
            throw new KubernetesClientException(
                    String.format("An error occurred while %s: %d - %s",
                            action,
                            ex.getRawStatusCode(),
                            ex.getResponseBodyAsString()),
                    ex);
        } catch (Exception ex) {
            throw new KubernetesClientException("A generic error occurred while " + action, ex);
        }
    }

}
