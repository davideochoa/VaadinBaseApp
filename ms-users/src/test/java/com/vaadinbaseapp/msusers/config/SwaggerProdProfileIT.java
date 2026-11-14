package com.vaadinbaseapp.msusers.config;

import com.vaadinbaseapp.msusers.support.PostgresTestContainerBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * With the "prod" profile springdoc is disabled and SecurityConfig no longer lists the Swagger
 * paths as public, so they fall under authenticated() and an anonymous request gets 401.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("prod")
class SwaggerProdProfileIT extends PostgresTestContainerBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void apiDocsAreNotAvailableInProd() {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());

        assertThat(restTemplate.getForEntity("/v3/api-docs", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(restTemplate.getForEntity("/swagger-ui/index.html", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void healthStaysPublicInProd() {
        assertThat(restTemplate.getForEntity("/actuator/health", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }
}
