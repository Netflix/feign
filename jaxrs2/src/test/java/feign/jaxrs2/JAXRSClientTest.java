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
package feign.jaxrs2;

import feign.*;
import feign.Feign.Builder;
import feign.assertj.MockWebServerAssertions;
import feign.client.AbstractClientTest;
import feign.jaxrs.JAXRSContract;
import okhttp3.mockwebserver.MockResponse;
import org.assertj.core.data.MapEntry;
import org.junit.Assume;
import org.junit.Test;
import javax.ws.rs.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;
import static feign.Util.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

/**
 * Tests client-specific behavior, such as ensuring Content-Length is sent when specified.
 */
public class JAXRSClientTest extends AbstractClientTest {

  @Override
  public Builder newBuilder() {
    return Feign.builder().client(new JAXRSClient());
  }

  @Override
  public void testPatch() throws Exception {
    try {
      super.testPatch();
    } catch (final ProcessingException e) {
      Assume.assumeNoException("JaxRS client do not support PATCH requests", e);
    }
  }

  @Override
  public void noResponseBodyForPut() {
    try {
      super.noResponseBodyForPut();
    } catch (final IllegalStateException e) {
      Assume.assumeNoException("JaxRS client do not support empty bodies on PUT", e);
    }
  }

  @Override
  public void noResponseBodyForPatch() {
    try {
      super.noResponseBodyForPatch();
    } catch (final IllegalStateException e) {
      Assume.assumeNoException("JaxRS client do not support PATCH requests", e);
    }
  }

  @Test
  public void reasonPhraseIsOptional() throws IOException, InterruptedException {
    server.enqueue(new MockResponse().setStatus("HTTP/1.1 " + 200));

    final TestInterface api = newBuilder()
        .target(TestInterface.class, "http://localhost:" + server.getPort());

    final Response response = api.post("foo");

    assertThat(response.status()).isEqualTo(200);
    // jaxrsclient is creating a reason when none is present
    // assertThat(response.reason()).isNullOrEmpty();
  }

  @Test
  public void parsesRequestAndResponse() throws IOException, InterruptedException {
    server.enqueue(new MockResponse().setBody("foo").addHeader("Foo: Bar"));

    final TestInterface api = newBuilder()
        .target(TestInterface.class, "http://localhost:" + server.getPort());

    final Response response = api.post("foo");

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.reason()).isEqualTo("OK");
    assertThat(response.headers())
        .hasEntrySatisfying("Content-Length", value -> {
          assertThat(value).contains("3");
        }).hasEntrySatisfying("Foo", value -> {
          assertThat(value).contains("Bar");
        });
    assertThat(response.body().asInputStream())
        .hasSameContentAs(new ByteArrayInputStream("foo".getBytes(UTF_8)));

    /* queries with no values are omitted from the uri. See RFC 6750 */
    MockWebServerAssertions.assertThat(server.takeRequest()).hasMethod("POST")
        .hasPath("/?foo=bar&foo=baz&qux")
        .hasBody("foo");
  }

  @Test
  public void testContentTypeWithoutCharset2() throws Exception {
    server.enqueue(new MockResponse()
        .setBody("AAAAAAAA"));
    final JaxRSClientTestInterface api = newBuilder()
        .target(JaxRSClientTestInterface.class, "http://localhost:" + server.getPort());

    final Response response = api.getWithContentType();
    // Response length should not be null
    assertEquals("AAAAAAAA", Util.toString(response.body().asReader(UTF_8)));

    MockWebServerAssertions.assertThat(server.takeRequest())
        .hasHeaders(
            MapEntry.entry("Accept", Collections.singletonList("text/plain")),
            MapEntry.entry("Content-Type", Collections.singletonList("text/plain")))
        .hasMethod("GET");
  }

  @Test
  public void testConsumesMultipleWithContentTypeHeaderAndBody() throws Exception {
    server.enqueue(new MockResponse().setBody("AAAAAAAA"));
    final JaxRSClientTestInterfaceWithJaxRsContract api = newBuilder()
        .contract(new JAXRSContract()) // use JAXRSContract
        .target(JaxRSClientTestInterfaceWithJaxRsContract.class,
            "http://localhost:" + server.getPort());

    final Response response =
        api.consumesMultipleWithContentTypeHeaderAndBody("application/json;charset=utf-8", "body");
    assertEquals("AAAAAAAA", Util.toString(response.body().asReader(UTF_8)));

    MockWebServerAssertions.assertThat(server.takeRequest())
        .hasHeaders(MapEntry.entry("Content-Type",
            Collections.singletonList("application/json;charset=utf-8")))
        .hasMethod("POST");
  }

  public interface JaxRSClientTestInterface {

    @RequestLine("GET /")
    @Headers({"Accept: text/plain", "Content-Type: text/plain"})
    Response getWithContentType();
  }

  public interface JaxRSClientTestInterfaceWithJaxRsContract {
    @Path("/")
    @POST
    @Consumes({"application/xml", "application/json"})
    Response consumesMultipleWithContentTypeHeaderAndBody(@HeaderParam("Content-Type") String contentType,
                                                          String body);
  }
}
