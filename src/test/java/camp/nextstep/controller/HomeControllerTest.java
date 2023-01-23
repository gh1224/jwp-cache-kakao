package camp.nextstep.controller;

import camp.nextstep.support.ResourceVersion;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;

import static camp.nextstep.config.WebMvcConfig.PREFIX_STATIC_RESOURCES;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HomeControllerTest {

    private static final Logger log = LoggerFactory.getLogger(HomeControllerTest.class);

    @Autowired
    private ResourceVersion version;

    @Autowired
    private WebTestClient webTestClient;

    /**
     * HTTP 응답 헤더에 Cache-Control가 없어도 웹 브라우저가 휴리스틱 캐싱에 따른 암시적 캐싱을 합니다.
     *
     * 의도하지 않은 캐싱을 막기 위해 모든 응답의 헤더에 Cache-Control: no-cache를 명시해봅니다.
     * 또한, 쿠키나 사용자 개인 정보 유출을 막기 위해 private도 추가해봅니다.
     */
    @Test
    void testNoCachePrivate() {
        final var response = webTestClient
                .get()
                .uri("/")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().cacheControl(CacheControl.noCache().cachePrivate())
                .expectBody(String.class).returnResult();

        log.info("response body\n{}", response.getResponseBody());
    }

    /**
     * 스프링 부트 설정을 통해 gzip과 같은 HTTP 압축 알고리즘을 적용시킬 수 있습니다.
     * https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#howto.webserver.enable-response-compression
     *
     * min-response-size는 기본 2KB이기 때문에 작은 크기의 html 파일은 압축하지 않습니다. 값을 10으로 설정해봅니다.
     */
    @Test
    void testCompression() {
        final var response = webTestClient
                .get()
                .uri("/")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.TRANSFER_ENCODING, "chunked")
                .expectBody(String.class).returnResult();

        log.info("response body\n{}", response.getResponseBody());
    }

    /**
     * ETag와 If-None-Match를 사용하여 HTTP 캐싱을 적용해봅니다.
     *
     * 필터를 활용하여 "/etag" 경로도 ETag를 적용해봅니다.
     */
    @Test
    void testETag() {
        final var response = webTestClient
                .get()
                .uri("/etag")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists(HttpHeaders.ETAG)
                .expectBody(String.class).returnResult();

        log.info("response body\n{}", response.getResponseBody());
    }

    /**
     * 캐시 무효화(Caching Busting)는 콘텐츠가 변경될 때 URL을 변경하여 응답을 장기간 캐시하게 만드는 기술입니다.
     * 정적 리소스를 max-age 1년으로 설정한 후, JS나 CSS 리소스에 변경사항이 생기면 캐시가 제거되도록 할 때 활용합니다.
     *
     * http://localhost:8080/resource-versioning
     * 위 url의 html 파일에서 사용하는 js, css와 같은 정적 파일에 캐싱을 적용해봅니다.
     * ETag도 적용해봅니다.
     */
    @Test
    void testCacheBustingOfStaticResources() {
        final var uri = String.format("%s/%s/js/index.js", PREFIX_STATIC_RESOURCES, version.getVersion());

        // "/resource-versioning/js/index.js" 경로의 정적 파일에 ETag를 사용한 캐싱이 적용되었는지 확인한다.
        final var response = webTestClient
                .get()
                .uri(uri)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists(HttpHeaders.ETAG)
                .expectHeader().cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic())
                .expectBody(String.class).returnResult();

        log.info("response body\n{}", response.getResponseBody());

        final var etag = response.getResponseHeaders().getETag();

        // 캐싱되었다면 "/resource-versioning/js/index.js"로 다시 호출했을때 HTTP status는 304를 반환한다.
        webTestClient.get()
                .uri(uri)
                .header(HttpHeaders.IF_NONE_MATCH, etag)
                .exchange()
                .expectStatus()
                .isNotModified();
    }
}