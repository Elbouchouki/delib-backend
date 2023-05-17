package ma.enset.semestreservice.client;

import ma.enset.semestreservice.exception.ApiClientException;
import ma.enset.semestreservice.exception.InternalErrorException;
import ma.enset.semestreservice.exception.handler.dto.ExceptionResponse;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration(proxyBeanMethods = false)
public class WebClientConfig {
    private final String FILIERE_SERVICE_NAME = "FILIERE-SERVICE";
    private final String MODULE_SERVICE_NAME = "MODULE-SERVICE";

    @Bean
    @LoadBalanced
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder()
            .defaultStatusHandler(
                HttpStatusCode::is4xxClientError,
                clientResponse -> clientResponse
                    .bodyToMono(ExceptionResponse.class)
                    .map(ApiClientException::new)
            )
            .defaultStatusHandler(
                HttpStatusCode::is5xxServerError,
                clientResponse -> clientResponse
                    .createException()
                    .map(exception -> new InternalErrorException(
                        exception.getMessage(), exception.getCause()
                    ))
            );
    }

    @Bean
    public FiliereClient filiereClient(WebClient.Builder builder) {
        WebClient filiereWebClient = builder
            .baseUrl("http://" + FILIERE_SERVICE_NAME)
            .build();

        HttpServiceProxyFactory factory = HttpServiceProxyFactory
            .builder(WebClientAdapter.forClient(filiereWebClient))
            .build();

        return factory.createClient(FiliereClient.class);
    }

    @Bean
    public ModuleClient moduleClient(WebClient.Builder builder) {
        WebClient moduleWebClient = builder
            .baseUrl("http://" + MODULE_SERVICE_NAME)
            .build();

        HttpServiceProxyFactory factory = HttpServiceProxyFactory
            .builder(WebClientAdapter.forClient(moduleWebClient))
            .build();

        return factory.createClient(ModuleClient.class);
    }
}
