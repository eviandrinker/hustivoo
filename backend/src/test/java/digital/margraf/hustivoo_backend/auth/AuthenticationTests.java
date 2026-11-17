package digital.margraf.hustivoo_backend.auth;

import digital.margraf.hustivoo_backend.support.PostgresIntegrationTest;
import digital.margraf.hustivoo_backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthenticationTests extends PostgresIntegrationTest {
    private static final String PASSWORD = "ExamplePassword123!";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    @Value("${local.server.port}") int port;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    class Browser {
        final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();
        String token;
        String header;

        HttpResponse<String> get(String path) throws Exception {
            return client.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> post(String path, String body, boolean csrf) throws Exception {
            var request = HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            if (csrf) request.header(header, token);
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
        void csrf() throws Exception {
            var response = get("/api/v1/auth/csrf");
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("cache-control").orElse("")).contains("no-store");
            JsonNode body = JSON.readTree(response.body());
            token = body.get("token").asText();
            header = body.get("headerName").asText();
        }
        String session() {
            return cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("JSESSIONID"))
                    .findFirst().orElseThrow().getValue();
        }
    }
    URI uri(String path) { return URI.create("http://localhost:" + port + path); }
    String email() { return UUID.randomUUID() + "@example.com"; }
    String credentials(String email, String password) {
        return JSON.writeValueAsString(java.util.Map.of("email", email, "password", password));
    }
    Browser registered(String email) throws Exception {
        Browser browser = new Browser();
        browser.csrf();
        assertThat(browser.post("/api/v1/auth/register", credentials(email, PASSWORD), true).statusCode()).isEqualTo(201);
        return browser;
    }

    @Test
    void registrationNormalizesHashesAndDoesNotLogin() throws Exception {
        String email = email();
        Browser browser = new Browser();
        browser.csrf();
        var response = browser.post("/api/v1/auth/register", credentials(" " + email.toUpperCase() + " ", PASSWORD), true);
        assertThat(response.statusCode()).isEqualTo(201);
        JsonNode body = JSON.readTree(response.body());
        assertThat(body.size()).isEqualTo(2);
        assertThat(body.get("email").asText()).isEqualTo(email);
        assertThat(body.get("id").asText()).isNotBlank();
        assertThat(response.body()).doesNotContain("password", PASSWORD);
        var user = users.findByEmail(email).orElseThrow();
        assertThat(user.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(encoder.matches(PASSWORD, user.getPasswordHash())).isTrue();
        assertThat(browser.get("/api/v1/auth/me").statusCode()).isEqualTo(401);
    }

    @Test
    void duplicateEmailReturnsConflict() throws Exception {
        String email = email();
        Browser browser = registered(email);
        assertThat(browser.post("/api/v1/auth/register", credentials(email.toUpperCase(), PASSWORD), true).statusCode()).isEqualTo(409);
    }

    @Test
    void concurrentRegistrationHasOneWinner() throws Exception {
        String email = email();
        Browser first = new Browser(); first.csrf();
        Browser second = new Browser(); second.csrf();
        var a = CompletableFuture.supplyAsync(() -> registerStatus(first, email));
        var b = CompletableFuture.supplyAsync(() -> registerStatus(second, email));
        assertThat(java.util.List.of(a.join(), b.join())).containsExactlyInAnyOrder(201, 409);
    }
    int registerStatus(Browser browser, String email) {
        try { return browser.post("/api/v1/auth/register", credentials(email, PASSWORD), true).statusCode(); }
        catch (Exception exception) { throw new RuntimeException(exception); }
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"email\":\"invalid\",\"password\":\"ExamplePassword123!\"}",
            "{\"email\":\"a@example.com\",\"password\":\"short\"}", "not-json"})
    void invalidRegistrationIsRejected(String body) throws Exception {
        Browser browser = new Browser(); browser.csrf();
        var response = browser.post("/api/v1/auth/register", body, true);
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).isEqualTo("{\"error\":\"Invalid request\"}");
    }

    @Test
    void rejectsPasswordsExceedingBcryptByteLimit() throws Exception {
        Browser browser = new Browser(); browser.csrf();
        assertThat(browser.post("/api/v1/auth/register", credentials(email(), "é".repeat(40)), true).statusCode()).isEqualTo(400);
    }

    @Test
    void loginRotatesSessionPersistsIdentityAndLogoutInvalidatesIt() throws Exception {
        String email = email();
        Browser browser = registered(email);
        String oldSession = browser.session();
        String oldToken = browser.token;
        var login = browser.post("/api/v1/auth/login", credentials(email.toUpperCase(), PASSWORD), true);
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(login.body()).doesNotContain("password", PASSWORD);
        assertThat(browser.session()).isNotEqualTo(oldSession);
        assertThat(login.headers().allValues("set-cookie").toString()).contains("HttpOnly", "SameSite=Lax");
        assertThat(browser.get("/api/v1/auth/me").body()).isEqualTo(login.body());
        var stale = HttpClient.newHttpClient().send(HttpRequest.newBuilder(uri("/api/v1/auth/me"))
                .header("Cookie", "JSESSIONID=" + oldSession).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(stale.statusCode()).isEqualTo(401);
        // Successful login clears the previous CSRF token.
        browser.token = oldToken;
        assertThat(browser.post("/api/v1/auth/logout", "{}", true).statusCode()).isEqualTo(403);
        browser.csrf();
        String authenticatedSession = browser.session();
        var logout = browser.post("/api/v1/auth/logout", "{}", true);
        assertThat(logout.statusCode()).isEqualTo(204);
        assertThat(logout.headers().allValues("set-cookie").stream()
                .flatMap(value -> java.net.HttpCookie.parse(value).stream())
                .anyMatch(cookie -> cookie.getName().equals("JSESSIONID") && cookie.hasExpired())).isTrue();
        assertThat(browser.get("/api/v1/auth/me").statusCode()).isEqualTo(401);
        var replay = HttpClient.newHttpClient().send(HttpRequest.newBuilder(uri("/api/v1/auth/me"))
                .header("Cookie", "JSESSIONID=" + authenticatedSession).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(replay.statusCode()).isEqualTo(401);
    }

    @Test
    void invalidCredentialsAreGeneric() throws Exception {
        String email = email();
        Browser browser = registered(email);
        var wrongPassword = browser.post("/api/v1/auth/login", credentials(email, "WrongPassword123!"), true);
        var unknownUser = browser.post("/api/v1/auth/login", credentials(email(), PASSWORD), true);
        assertThat(wrongPassword.statusCode()).isEqualTo(401);
        assertThat(unknownUser.statusCode()).isEqualTo(401);
        assertThat(wrongPassword.body()).isEqualTo(unknownUser.body()).contains("Invalid email or password");
        assertThat(browser.get("/api/v1/auth/me").statusCode()).isEqualTo(401);
    }

    @ParameterizedTest
    @ValueSource(strings = {"register", "login", "logout"})
    void stateChangesRequireValidCsrf(String endpoint) throws Exception {
        Browser browser = new Browser(); browser.csrf();
        String path = "/api/v1/auth/" + endpoint;
        assertThat(browser.post(path, credentials(email(), PASSWORD), false).statusCode()).isEqualTo(403);
        browser.token = "invalid";
        assertThat(browser.post(path, credentials(email(), PASSWORD), true).statusCode()).isEqualTo(403);
    }

    @Test
    void healthIsPublicAndOtherApiPathsRequireAuthentication() throws Exception {
        Browser browser = new Browser();
        assertThat(browser.get("/api/v1/health").statusCode()).isEqualTo(200);
        assertThat(browser.get("/api/v1/auth/me").statusCode()).isEqualTo(401);
        assertThat(browser.get("/api/v1/businesses").statusCode()).isEqualTo(401);
    }
}
