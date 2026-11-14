package com.vaadinbaseapp.msusers.controller;

import com.vaadinbaseapp.msusers.dto.UserRequest;
import com.vaadinbaseapp.msusers.entity.UserType;
import com.vaadinbaseapp.msusers.repository.UserTypeRepository;
import com.vaadinbaseapp.msusers.support.PostgresTestContainerBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.BadJwtException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserControllerIT extends PostgresTestContainerBase {

    @TestConfiguration
    static class JwtTestConfig {
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> switch (token) {
                case "admin-token" -> buildJwt(token, "ADMINISTRATOR");
                case "user-token" -> buildJwt(token, "USER");
                case "service-token" -> buildJwt(token, "SERVICE");
                default -> throw new BadJwtException("Unknown test token");
            };
        }

        private Jwt buildJwt(String token, String role) {
            Instant now = Instant.now();
            return Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .claim("sub", "test@example.com")
                    .claim("role", role)
                    .issuedAt(now)
                    .expiresAt(now.plusSeconds(3600))
                    .build();
        }
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserTypeRepository userTypeRepository;

    private Long userTypeId;

    @BeforeEach
    void setUp() {
        // The default JDK HttpURLConnection factory throws on a 401 for requests with a body
        // ("cannot retry due to server authentication, in streaming mode").
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());

        userTypeId = userTypeRepository.findAll().stream()
                .filter(t -> t.getName().equals("User"))
                .findFirst()
                .orElseGet(() -> {
                    UserType type = new UserType();
                    type.setName("User");
                    type.setDescription("Standard access");
                    return userTypeRepository.save(type);
                })
                .getId();
    }

    private UserRequest buildValidRequest() {
        UserRequest request = new UserRequest();
        long unique = System.nanoTime();
        request.setUsername("jane.doe." + unique);
        request.setFirstName("Jane");
        request.setLastName("Doe");
        request.setEmail("jane.doe." + unique + "@example.com");
        request.setUserTypeId(userTypeId);
        return request;
    }

    private HttpEntity<UserRequest> withAdminToken(UserRequest body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("admin-token");
        return new HttpEntity<>(body, headers);
    }

    private HttpEntity<UserRequest> withUserToken(UserRequest body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("user-token");
        return new HttpEntity<>(body, headers);
    }

    private HttpEntity<Void> withToken(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }

    @Test
    void fullUserCrudFlowAsAdmin() {
        UserRequest request = buildValidRequest();

        ResponseEntity<String> created = restTemplate.exchange(
                "/users", HttpMethod.POST, withAdminToken(request), String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).contains(request.getEmail());
    }

    @Test
    void createWithoutTokenReturns401() {
        UserRequest request = buildValidRequest();

        ResponseEntity<String> response = restTemplate.postForEntity("/users", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("UNAUTHORIZED");
    }

    @Test
    void createWithNonAdminTokenReturns403() {
        UserRequest request = buildValidRequest();

        ResponseEntity<String> response = restTemplate.exchange(
                "/users", HttpMethod.POST, withUserToken(request), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("FORBIDDEN");
    }

    @Test
    void createWithInvalidEmailReturns400() {
        UserRequest request = buildValidRequest();
        request.setEmail("not-an-email");

        ResponseEntity<String> response = restTemplate.exchange(
                "/users", HttpMethod.POST, withAdminToken(request), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("VALIDATION");
    }

    @Test
    void getNonexistentUserReturns404() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/users/999999", HttpMethod.GET, withToken("user-token"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("NOT_FOUND");
    }

    @Test
    void listUsersWithoutTokenReturns401() {
        ResponseEntity<String> response = restTemplate.getForEntity("/users", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("UNAUTHORIZED");
    }

    @Test
    void getUserByIdWithoutTokenReturns401() {
        ResponseEntity<String> response = restTemplate.getForEntity("/users/1", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void listUsersWithNonAdminTokenReturns200AndIncludesSeededAdmin() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/users?size=100", HttpMethod.GET, withToken("user-token"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("admin@local");
    }

    @Test
    void updateWithoutTokenReturns401() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/users/1", HttpMethod.PUT, new HttpEntity<>(buildValidRequest()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void deleteWithoutTokenReturns401() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/users/1", HttpMethod.DELETE, HttpEntity.EMPTY, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void updateWithNonAdminTokenReturns403() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/users/1", HttpMethod.PUT, withUserToken(buildValidRequest()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("FORBIDDEN");
    }

    @Test
    void deleteWithNonAdminTokenReturns403() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/users/1", HttpMethod.DELETE, withToken("user-token"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void createWithDuplicateEmailReturns409() {
        UserRequest request = buildValidRequest();
        restTemplate.exchange("/users", HttpMethod.POST, withAdminToken(request), String.class);

        ResponseEntity<String> secondResponse = restTemplate.exchange(
                "/users", HttpMethod.POST, withAdminToken(request), String.class);

        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void getByEmailFindsCreatedUser() {
        UserRequest request = buildValidRequest();
        restTemplate.exchange("/users", HttpMethod.POST, withAdminToken(request), String.class);

        ResponseEntity<String> response = restTemplate.exchange(
                "/users/by-email?email=" + request.getEmail(), HttpMethod.GET, withToken("service-token"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains(request.getEmail());
    }

    @Test
    void getByEmailWithoutTokenReturns401() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/users/by-email?email=jane.doe@example.com", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void listUserTypesWithTokenIncludesAdministratorAndUser() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/user-types", HttpMethod.GET, withToken("user-token"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Administrator").contains("User");
    }

    @Test
    void listUserTypesWithServiceTokenReturns200() {
        // ms-security's UserTypeCache reads /user-types with a SERVICE-role token.
        ResponseEntity<String> response = restTemplate.exchange(
                "/user-types", HttpMethod.GET, withToken("service-token"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void listUserTypesWithoutTokenReturns401() {
        ResponseEntity<String> response = restTemplate.getForEntity("/user-types", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("UNAUTHORIZED");
    }

    @Test
    void apiDocsArePublicWithoutProdProfile() {
        ResponseEntity<String> response = restTemplate.getForEntity("/v3/api-docs", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"openapi\"");
    }
}
