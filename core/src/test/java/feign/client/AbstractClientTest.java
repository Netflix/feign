/**
 * Copyright 2012-2021 The Feign Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package feign.client;

import static feign.Util.UTF_8;
import static org.assertj.core.api.Assertions.*;
import static org.junit.Assert.assertEquals;
import feign.Client;
import feign.CollectionFormat;
import feign.Feign.Builder;
import feign.FeignException;
import feign.Headers;
import feign.Logger;
import feign.Param;
import feign.RequestLine;
import feign.Response;
import feign.Util;
import feign.assertj.MockWebServerAssertions;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 * {@link AbstractClientTest} can be extended to run a set of tests against any {@link Client}
 * implementation.
 */
public abstract class AbstractClientTest {

  @Rule
  public final ExpectedException thrown = ExpectedException.none();
  @Rule
  public final MockWebServer server = new MockWebServer();

  /**
   * Create a Feign {@link Builder} with a client configured
   */
  public abstract Builder newBuilder();

  /**
   * Some client implementation tests should override this test if the PATCH operation is
   * unsupported.
   */
  @Test
  public void testPatch() throws Exception {
    server.enqueue(new MockResponse().setBody("foo"));
    server.enqueue(new MockResponse());

    TestInterface api = newBuilder()
        .target(TestInterface.class, "http://localhost:" + server.getPort());

    assertEquals("foo", api.patch(""));

    MockWebServerAssertions.assertThat(server.takeRequest())
        .hasHeaders(entry("Accept", Collections.singletonList("text/plain")),
            entry("Content-Length", Collections.singletonList("0")))
        .hasNoHeaderNamed("Content-Type")
        .hasMethod("PATCH");
  }

  @Test
  public void parsesRequestAndResponse() throws IOException, InterruptedException {
    server.enqueue(new MockResponse().setBody("foo").addHeader("Foo: Bar"));

    TestInterface api = newBuilder()
        .target(TestInterface.class, "http://localhost:" + server.getPort());

    Response response = api.post("foo");

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.reason()).isEqualTo("OK");
    assertThat(response.headers())
        .hasEntrySatisfying("Content-Length", value -> {
          assertThat(value).contains("3");
        })
        .hasEntrySatisfying("Foo", value -> {
          assertThat(value).contains("Bar");
        });
    assertThat(response.body().asInputStream())
        .hasSameContentAs(new ByteArrayInputStream("foo".getBytes(UTF_8)));

    RecordedRequest recordedRequest = server.takeRequest();
    assertThat(recordedRequest.getMethod()).isEqualToIgnoringCase("POST");
    assertThat(recordedRequest.getHeader("Foo")).isEqualToIgnoringCase("Bar, Baz");
    assertThat(recordedRequest.getHeader("Accept")).isEqualToIgnoringCase("*/*");
    assertThat(recordedRequest.getHeader("Content-Length")).isEqualToIgnoringCase("3");
    assertThat(recordedRequest.getBody().readUtf8()).isEqualToIgnoringCase("foo");
  }

  @Test
  public void reasonPhraseIsOptional() throws IOException, InterruptedException {
    server.enqueue(new MockResponse().setStatus("HTTP/1.1 " + 200));

    TestInterface api = newBuilder()
        .target(TestInterface.class, "http://localhost:" + server.getPort());

    Response response = api.post("foo");

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.reason()).isNullOrEmpty();
  }

  @Test
  public void parsesErrorResponse() {
    thrown.expect(FeignException.class);
    thrown.expectMessage(
        "[500 Server Error] during [GET] to [http://localhost:" + server.getPort()
            + "/] [TestInterface#get()]: [ARGHH]");

    server.enqueue(new MockResponse().setResponseCode(500).setBody("ARGHH"));

    TestInterface api = newBuilder()
        .target(TestInterface.class, "http://localhost:" + server.getPort());

    api.get();
  }

  @Test
  public void parsesErrorResponseBody() {
    String expectedResponseBody = "ARGHH";

    server.enqueue(new MockResponse().setResponseCode(500).setBody("ARGHH"));

    TestInterface api = newBuilder()
        .target(TestInterface.class, "http://localhost:" + server.getPort());

    try {
      api.get();
    } catch (FeignException e) {
      assertThat(e.contentUTF8()).isEqualTo(expectedResponseBody);
    }
  }

  @Test
  public void parsesUnauthorizedResponseBody() {
    String expectedResponseBody = "ARGHH";

    server.enqueue(new MockResponse().setResponseCode(401).setBody("ARGHH"));

    TestInterface api = newBuilder()
        .target(TestInterface.class, "http://localhost:" + server.getPort());

    try {
      api.postForString("HELLO");
    } catch (FeignException e) {
      assertThat(e.contentUTF8()).isEqualTo(expectedResponseBody);
    }
  }

  @Test
  public void safeRebuffering() {
    server.enqueue(new MockResponse().setBody("foo"));

    TestInterface api = newBuilder()
        .logger(new Logger() {
          @Override
          protected void log(String configKey, String format, Object... args) {}
        })
        .logLevel(Logger.Level.FULL) // rebuffers the body
        .target(TestInterface.class, "http://localhost:" + server.getPort());

    api.post("foo");
  }

  /** This shows that is a no-op or otherwise doesn't cause an NPE when there's no content. */
  @Test
  public void safeRebuffering_noContent() {
    server.enqueue(new MockResponse().setResponseCode(204));

    TestInterface api = newBuilder()
        .logger(new Logger() {
          @Override
          protected void log(String configKey, String format, Object... args) {}
        })
        .logLevel(Logger.Level.FULL) // rebuffers the body
        .target(TestInterface.class, "http://localhost:" + server.getPort());

    api.post("foo");
  }

  @Test
  public void noResponseBodyForPost() {
    server.enqueue(new MockResponse());

    TestInterface api = newBuilder()
        .target(TestInterface.class, "http://localhost:" + server.getPort());

    api.noPostBody();
  }

  @Test
  public void noResponseBodyForPut() {
    server.enqueue(new MockResponse());

    TestInterface api = newBuilder()
        .target(TestInterface.class, "http://localhost:" + server.getPort());

    api.noPutBody();
  }

  /**
   * Some client implementation tests should override this test if the PATCH operation is
   * unsupported.
   */
  @Test
  public void noResponseBodyForPatch() {
    server.enqueue(new MockResponse());

    TestInterface api = newBuilder()
        .target(TestInterface.class, "http://localhost:" + server.getPort());

    api.noPatchBody();
  }

  @Test
  public void parsesResponseMissingLength() throws IOException {
    server.enqueue(new MockResponse().setChunkedBody("foo", 1));

    TestInterface api = newBuilder()
        .target(TestInterface.class, "http://localhost:" + server.getPort());

    Response response = api.post("testing");
    assertThat(response.status()).isEqualTo(200);
    assertThat(response.reason()).isEqualTo("OK");
    assertThat(response.body().length()).isNull();
    assertThat(response.body().asInputStream())
        .hasSameContentAs(new ByteArrayInputStream("foo".getBytes(UTF_8)));
  }

  @Test
  public void postWithSpacesInPath() throws InterruptedException {
    server.enqueue(new MockResponse().setBody("foo"));

    TestInterface api = newBuilder()
        .target(TestInterface.class, "http://localhost:" + server.getPort());
    api.post("current documents", "foo");

    MockWebServerAssertions.assertThat(server.takeRequest()).hasMethod("POST")
        .hasPath("/path/current%20documents/resource")
        .hasBody("foo");
  }

  @Test
  public void testVeryLongResponseNullLength() {
    server.enqueue(new MockResponse()
        .setBody("AAAAAAAA")
        .addHeader("Content-Length", Long.MAX_VALUE));
    TestInterface api = newBuilder()
        .target(TestInterface.class, "http://localhost:" + server.getPort());

    Response response = api.post("foo");
    // Response length greater than Integer.MAX_VALUE should be null
    assertThat(response.body().length()).isNull();
  }

  @Test
  public void testResponseLength() {
    server.enqueue(new MockResponse()
        .setBody("test"));
    TestInterface api = newBuilder()
        .target(TestInterface.class, "http://localhost:" + server.getPort());

    Integer expected = 4;
    Response response = api.post("");
    Integer actual = response.body().length();
    assertEquals(expected, actual);
  }

  @Test
  public void testContentTypeWithCharset() throws Exception {
    server.enqueue(new MockResponse()
        .setBody("AAAAAAAA"));
    TestInterface api = newBuilder()
        .target(TestInterface.class, "http://localhost:" + server.getPort());

    Response response = api.postWithContentType("foo", "text/plain;charset=utf-8");
    // Response length should not be null
    assertEquals("AAAAAAAA", Util.toString(response.body().asReader(UTF_8)));
  }

  @Test
  public void testContentTypeWithoutCharset() throws Exception {
    server.enqueue(new MockResponse()
        .setBody("AAAAAAAA"));
    TestInterface api = newBuilder()
        .target(TestInterface.class, "http://localhost:" + server.getPort());

    Response response = api.postWithContentType("foo", "text/plain");
    // Response length should not be null
    assertEquals("AAAAAAAA", Util.toString(response.body().asReader(UTF_8)));
  }

  @Test
  public void testContentTypeDefaultsToRequestCharset() throws Exception {
    server.enqueue(new MockResponse().setBody("foo"));
    TestInterface api = newBuilder()
        .target(TestInterface.class, "http://localhost:" + server.getPort());

    // should use utf-8 encoding by default
    api.postWithContentType("àáâãäåèéêë", "text/plain; charset=UTF-8");

    String body = server.takeRequest().getBody().readUtf8();
    assertThat(body).isEqualToIgnoringCase("àáâãäåèéêë");
  }

  @Test
  public void testDefaultCollectionFormat() throws Exception {
    server.enqueue(new MockResponse().setBody("body"));

    TestInterface api = newBuilder()
        .target(TestInterface.class, "http://localhost:" + server.getPort());

    Response response = api.get(Arrays.asList("bar", "baz"));

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.reason()).isEqualTo("OK");

    MockWebServerAssertions.assertThat(server.takeRequest()).hasMethod("GET")
        .hasPath("/?foo=bar&foo=baz");
  }

  @Test
  public void testHeadersWithNullParams() throws InterruptedException {
    server.enqueue(new MockResponse().setBody("body"));

    TestInterface api = newBuilder()
        .target(TestInterface.class, "http://localhost:" + server.getPort());

    Response response = api.getWithHeaders(null);

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.reason()).isEqualTo("OK");

    MockWebServerAssertions.assertThat(server.takeRequest()).hasMethod("GET")
        .hasPath("/").hasNoHeaderNamed("Authorization");
  }

  @Test
  public void testHeadersWithNotEmptyParams() throws InterruptedException {
    server.enqueue(new MockResponse().setBody("body"));

    TestInterface api = newBuilder()
        .target(TestInterface.class, "http://localhost:" + server.getPort());

    Response response = api.getWithHeaders("token");

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.reason()).isEqualTo("OK");

    MockWebServerAssertions.assertThat(server.takeRequest()).hasMethod("GET")
        .hasPath("/").hasHeaders(entry("authorization", Collections.singletonList("token")));
  }

  @Test
  public void testAlternativeCollectionFormat() throws Exception {
    server.enqueue(new MockResponse().setBody("body"));

    TestInterface api = newBuilder()
        .target(TestInterface.class, "http://localhost:" + server.getPort());

    Response response = api.getCSV(Arrays.asList("bar", "baz"));

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.reason()).isEqualTo("OK");

    // Some HTTP libraries percent-encode commas in query parameters and others don't.
    MockWebServerAssertions.assertThat(server.takeRequest()).hasMethod("GET")
        .hasOneOfPath("/?foo=bar,baz", "/?foo=bar%2Cbaz");
  }

  @SuppressWarnings("UnusedReturnValue")
  public interface TestInterface {

    @RequestLine("POST /?foo=bar&foo=baz&qux=")
    @Headers({"Foo: Bar", "Foo: Baz", "Qux: ", "Content-Type: text/plain"})
    Response post(String body);

    @RequestLine("POST /path/{to}/resource")
    @Headers("Accept: text/plain")
    Response post(@Param("to") String to, String body);

    @RequestLine("POST /?foo=bar&foo=baz&qux=")
    @Headers({"Foo: Bar", "Foo: Baz", "Qux: ", "Content-Type: text/plain"})
    String postForString(String body);

    @RequestLine("GET /")
    @Headers("Accept: text/plain")
    String get();

    @RequestLine("GET /?foo={multiFoo}")
    Response get(@Param("multiFoo") List<String> multiFoo);

    @Headers({
        "Authorization: {authorization}"
    })
    @RequestLine("GET /")
    Response getWithHeaders(@Param("authorization") String authorization);

    @RequestLine(value = "GET /?foo={multiFoo}", collectionFormat = CollectionFormat.CSV)
    Response getCSV(@Param("multiFoo") List<String> multiFoo);

    @RequestLine("PATCH /")
    @Headers("Accept: text/plain")
    String patch(String body);

    @RequestLine("POST")
    String noPostBody();

    @RequestLine("PUT")
    String noPutBody();

    @RequestLine("PATCH")
    String noPatchBody();

    @RequestLine("POST /?foo=bar&foo=baz&qux=")
    @Headers({"Foo: Bar", "Foo: Baz", "Qux: ", "Content-Type: {contentType}"})
    Response postWithContentType(String body, @Param("contentType") String contentType);
  }

}
