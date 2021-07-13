/**
 * Copyright 2012-2020 The Feign Authors
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
package feign.http2client.test;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import feign.AsyncClient;
import feign.AsyncFeign;
import feign.Body;
import feign.ChildPojo;
import feign.Feign;
import feign.Feign.ResponseMappingDecoder;
import feign.FeignException;
import feign.HeaderMap;
import feign.Headers;
import feign.Param;
import feign.PropertyPojo;
import feign.QueryMap;
import feign.QueryMapEncoder;
import feign.Request;
import feign.Request.HttpMethod;
import feign.RequestInterceptor;
import feign.RequestLine;
import feign.RequestTemplate;
import feign.Response;
import feign.ResponseMapper;
import feign.Target;
import feign.Target.HardCodedTarget;
import feign.Util;
import feign.codec.DecodeException;
import feign.codec.Decoder;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import feign.codec.StringDecoder;
import feign.http2client.AsyncHttpClient;
import feign.querymap.BeanQueryMapEncoder;
import feign.querymap.FieldQueryMapEncoder;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.Buffer;
import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static feign.assertj.MockWebServerAssertions.assertThat;
import static org.assertj.core.data.MapEntry.entry;
import static org.hamcrest.CoreMatchers.isA;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AsyncHttpClientTest {
  @Rule
  public final ExpectedException thrown = ExpectedException.none();
  @Rule
  public final MockWebServer server = new MockWebServer();

  @Test
  public void iterableQueryParams() throws Exception {
    server.enqueue(new MockResponse().setBody("foo"));

    final TestInterfaceAsync api =
        newAsyncBuilder().target("http://localhost:" + server.getPort());

    api.queryParams("user", Arrays.asList("apple", "pear"));

    assertThat(server.takeRequest()).hasPath("/?1=user&2=apple&2=pear");
  }

  @Test
  public void postTemplateParamsResolve() throws Exception {
    server.enqueue(new MockResponse().setBody("foo"));

    final TestInterfaceAsync api =
        newAsyncBuilder().target("http://localhost:" + server.getPort());

    api.login("netflix", "denominator", "password");

    assertThat(server.takeRequest()).hasBody(
        "{\"customer_name\": \"netflix\", \"user_name\": \"denominator\", \"password\": \"password\"}");
  }

  @Test
  public void responseCoercesToStringBody() throws Throwable {
    server.enqueue(new MockResponse().setBody("foo"));

    final TestInterfaceAsync api =
        newAsyncBuilder().target("http://localhost:" + server.getPort());

    final Response response = unwrap(api.response());
    assertTrue(response.body().isRepeatable());
    assertEquals("foo", response.body().toString());
  }

  @Test
  public void postFormParams() throws Exception {
    server.enqueue(new MockResponse().setBody("foo"));

    final TestInterfaceAsync api =
        newAsyncBuilder().target("http://localhost:" + server.getPort());

    final CompletableFuture<?> cf = api.form("netflix", "denominator", "password");

    assertThat(server.takeRequest())
        .hasBody(
            "{\"customer_name\":\"netflix\",\"user_name\":\"denominator\",\"password\":\"password\"}");

    checkCFCompletedSoon(cf);
  }

  @Test
  public void postBodyParam() throws Exception {
    server.enqueue(new MockResponse().setBody("foo"));

    final TestInterfaceAsync api =
        newAsyncBuilder().target("http://localhost:" + server.getPort());

    final CompletableFuture<?> cf = api.body(Arrays.asList("netflix", "denominator", "password"));

    assertThat(server.takeRequest())
        .hasHeaders(entry("Content-Length", Collections.singletonList("32")))
        .hasBody("[netflix, denominator, password]");

    checkCFCompletedSoon(cf);
  }

  /**
   * The type of a parameter value may not be the desired type to encode as. Prefer the interface
   * type.
   */
  @Test
  public void bodyTypeCorrespondsWithParameterType() throws Exception {
    server.enqueue(new MockResponse().setBody("foo"));

    final AtomicReference<Type> encodedType = new AtomicReference<Type>();
    final TestInterfaceAsync api = newAsyncBuilder().encoder(new Encoder.Default() {
      @Override
      public void encode(Object object, Type bodyType, RequestTemplate template) {
        encodedType.set(bodyType);
      }
    }).target("http://localhost:" + server.getPort());

    final CompletableFuture<?> cf = api.body(Arrays.asList("netflix", "denominator", "password"));

    server.takeRequest();

    Assertions.assertThat(encodedType.get()).isEqualTo(new TypeToken<List<String>>() {}.getType());

    checkCFCompletedSoon(cf);
  }

  @Test
  public void singleInterceptor() throws Exception {
    server.enqueue(new MockResponse().setBody("foo"));

    final TestInterfaceAsync api =
        newAsyncBuilder().requestInterceptor(new ForwardedForInterceptor())
            .target("http://localhost:" + server.getPort());

    final CompletableFuture<?> cf = api.post();

    assertThat(server.takeRequest())
        .hasHeaders(entry("X-Forwarded-For", Collections.singletonList("origin.host.com")));

    checkCFCompletedSoon(cf);
  }

  @Test
  public void multipleInterceptor() throws Exception {
    server.enqueue(new MockResponse().setBody("foo"));

    final TestInterfaceAsync api =
        newAsyncBuilder().requestInterceptor(new ForwardedForInterceptor())
            .requestInterceptor(new UserAgentInterceptor())
            .target("http://localhost:" + server.getPort());

    final CompletableFuture<?> cf = api.post();

    assertThat(server.takeRequest()).hasHeaders(
        entry("X-Forwarded-For", Collections.singletonList("origin.host.com")),
        entry("User-Agent", Collections.singletonList("Feign")));

    checkCFCompletedSoon(cf);
  }

  @Test
  public void customExpander() throws Exception {
    server.enqueue(new MockResponse());

    final TestInterfaceAsync api =
        newAsyncBuilder().target("http://localhost:" + server.getPort());

    final CompletableFuture<?> cf = api.expand(new Date(1234l));

    assertThat(server.takeRequest()).hasPath("/?date=1234");

    checkCFCompletedSoon(cf);
  }

  @Test
  public void customExpanderListParam() throws Exception {
    server.enqueue(new MockResponse());

    final TestInterfaceAsync api =
        newAsyncBuilder().target("http://localhost:" + server.getPort());

    final CompletableFuture<?> cf =
        api.expandList(Arrays.asList(new Date(1234l), new Date(12345l)));

    assertThat(server.takeRequest()).hasPath("/?date=1234&date=12345");

    checkCFCompletedSoon(cf);
  }

  @Test
  public void customExpanderNullParam() throws Exception {
    server.enqueue(new MockResponse());

    final TestInterfaceAsync api =
        newAsyncBuilder().target("http://localhost:" + server.getPort());

    final CompletableFuture<?> cf = api.expandList(Arrays.asList(new Date(1234l), null));

    assertThat(server.takeRequest()).hasPath("/?date=1234");

    checkCFCompletedSoon(cf);
  }

  @Test
  public void headerMap() throws Exception {
    server.enqueue(new MockResponse());

    final TestInterfaceAsync api =
        newAsyncBuilder().target("http://localhost:" + server.getPort());

    final Map<String, Object> headerMap = new LinkedHashMap<String, Object>();
    headerMap.put("Content-Type", "myContent");
    headerMap.put("Custom-Header", "fooValue");
    final CompletableFuture<?> cf = api.headerMap(headerMap);

    assertThat(server.takeRequest()).hasHeaders(entry("Content-Type", Arrays.asList("myContent")),
        entry("Custom-Header", Arrays.asList("fooValue")));

    checkCFCompletedSoon(cf);
  }

  @Test
  public void headerMapWithHeaderAnnotations() throws Exception {
    server.enqueue(new MockResponse());

    final TestInterfaceAsync api =
        newAsyncBuilder().target("http://localhost:" + server.getPort());

    final Map<String, Object> headerMap = new LinkedHashMap<String, Object>();
    headerMap.put("Custom-Header", "fooValue");
    api.headerMapWithHeaderAnnotations(headerMap);

    // header map should be additive for headers provided by annotations
    assertThat(server.takeRequest()).hasHeaders(entry("Content-Encoding", Arrays.asList("deflate")),
        entry("Custom-Header", Arrays.asList("fooValue")));

    server.enqueue(new MockResponse());
    headerMap.put("Content-Encoding", "overrideFromMap");

    final CompletableFuture<?> cf = api.headerMapWithHeaderAnnotations(headerMap);

    /*
     * @HeaderMap map values no longer override @Header parameters. This caused confusion as it is
     * valid to have more than one value for a header.
     */
    assertThat(server.takeRequest()).hasHeaders(
        entry("Content-Encoding", Arrays.asList("deflate", "overrideFromMap")),
        entry("Custom-Header", Arrays.asList("fooValue")));

    checkCFCompletedSoon(cf);
  }

  @Test
  public void queryMap() throws Exception {
    server.enqueue(new MockResponse());

    final TestInterfaceAsync api =
        newAsyncBuilder().target("http://localhost:" + server.getPort());

    final Map<String, Object> queryMap = new LinkedHashMap<String, Object>();
    queryMap.put("name", "alice");
    queryMap.put("fooKey", "fooValue");
    final CompletableFuture<?> cf = api.queryMap(queryMap);

    assertThat(server.takeRequest()).hasPath("/?name=alice&fooKey=fooValue");

    checkCFCompletedSoon(cf);
  }

  @Test
  public void queryMapIterableValuesExpanded() throws Exception {
    server.enqueue(new MockResponse());

    final TestInterfaceAsync api =
        newAsyncBuilder().target("http://localhost:" + server.getPort());

    final Map<String, Object> queryMap = new LinkedHashMap<String, Object>();
    queryMap.put("name", Arrays.asList("Alice", "Bob"));
    queryMap.put("fooKey", "fooValue");
    queryMap.put("emptyListKey", new ArrayList<String>());
    queryMap.put("emptyStringKey", ""); // empty values are ignored.
    final CompletableFuture<?> cf = api.queryMap(queryMap);

    assertThat(server.takeRequest())
        .hasPath("/?name=Alice&name=Bob&fooKey=fooValue&emptyStringKey");

    checkCFCompletedSoon(cf);
  }

  @Test
  public void queryMapWithQueryParams() throws Exception {
    final TestInterfaceAsync api =
        newAsyncBuilder().target("http://localhost:" + server.getPort());

    server.enqueue(new MockResponse());
    Map<String, Object> queryMap = new LinkedHashMap<String, Object>();
    queryMap.put("fooKey", "fooValue");
    api.queryMapWithQueryParams("alice", queryMap);
    // query map should be expanded after built-in parameters
    assertThat(server.takeRequest()).hasPath("/?name=alice&fooKey=fooValue");

    server.enqueue(new MockResponse());
    queryMap = new LinkedHashMap<String, Object>();
    queryMap.put("name", "bob");
    api.queryMapWithQueryParams("alice", queryMap);
    // queries are additive
    assertThat(server.takeRequest()).hasPath("/?name=alice&name=bob");

    server.enqueue(new MockResponse());
    queryMap = new LinkedHashMap<String, Object>();
    queryMap.put("name", null);
    api.queryMapWithQueryParams("alice", queryMap);
    // null value for a query map key removes query parameter
    assertThat(server.takeRequest()).hasPath("/?name=alice");
  }

  @Test
  public void queryMapValueStartingWithBrace() throws Exception {
    final TestInterfaceAsync api =
        newAsyncBuilder().target("http://localhost:" + server.getPort());

    server.enqueue(new MockResponse());
    Map<String, Object> queryMap = new LinkedHashMap<String, Object>();
    queryMap.put("name", "{alice");
    api.queryMap(queryMap);
    assertThat(server.takeRequest()).hasPath("/?name=%7Balice");

    server.enqueue(new MockResponse());
    queryMap = new LinkedHashMap<String, Object>();
    queryMap.put("{name", "alice");
    api.queryMap(queryMap);
    assertThat(server.takeRequest()).hasPath("/?%7Bname=alice");

    server.enqueue(new MockResponse());
    queryMap = new LinkedHashMap<String, Object>();
    queryMap.put("name", "%7Balice");
    api.queryMapEncoded(queryMap);
    assertThat(server.takeRequest()).hasPath("/?name=%7Balice");

    server.enqueue(new MockResponse());
    queryMap = new LinkedHashMap<String, Object>();
    queryMap.put("%7Bname", "%7Balice");
    api.queryMapEncoded(queryMap);
    assertThat(server.takeRequest()).hasPath("/?%7Bname=%7Balice");
  }

  @Test
  public void queryMapPojoWithFullParams() throws Exception {
    final TestInterfaceAsync api =
        newAsyncBuilder().target("http://localhost:" + server.getPort());

    final CustomPojo customPojo = new CustomPojo("Name", 3);

    server.enqueue(new MockResponse());
    final CompletableFuture<?> cf = api.queryMapPojo(customPojo);
    assertThat(server.takeRequest()).hasQueryParams(Arrays.asList("name=Name", "number=3"));
    checkCFCompletedSoon(cf);
  }

  @Test
  public void queryMapPojoWithPartialParams() throws Exception {
    final TestInterfaceAsync api =
        newAsyncBuilder().target("http://localhost:" + server.getPort());

    final CustomPojo customPojo = new CustomPojo("Name", null);

    server.enqueue(new MockResponse());
    final CompletableFuture<?> cf = api.queryMapPojo(customPojo);
    assertThat(server.takeRequest()).hasPath("/?name=Name");

    checkCFCompletedSoon(cf);
  }

  @Test
  public void queryMapPojoWithEmptyParams() throws Exception {
    final TestInterfaceAsync api =
        newAsyncBuilder().target("http://localhost:" + server.getPort());

    final CustomPojo customPojo = new CustomPojo(null, null);

    server.enqueue(new MockResponse());
    api.queryMapPojo(customPojo);
    assertThat(server.takeRequest()).hasPath("/");
  }

  @Test
  public void configKeyFormatsAsExpected() throws Exception {
    assertEquals("TestInterfaceAsync#post()",
        Feign.configKey(TestInterfaceAsync.class,
            TestInterfaceAsync.class.getDeclaredMethod("post")));
    assertEquals("TestInterfaceAsync#uriParam(String,URI,String)",
        Feign.configKey(TestInterfaceAsync.class,
            TestInterfaceAsync.class.getDeclaredMethod("uriParam", String.class, URI.class,
                String.class)));
  }

  @Test
  public void configKeyUsesChildType() throws Exception {
    assertEquals("List#iterator()",
        Feign.configKey(List.class, Iterable.class.getDeclaredMethod("iterator")));
  }

  private <T> T unwrap(CompletableFuture<T> cf) throws Throwable {
    try {
      return cf.get(1, TimeUnit.SECONDS);
    } catch (final ExecutionException e) {
      throw e.getCause();
    }
  }

  @Test
  public void canOverrideErrorDecoder() throws Throwable {
    server.enqueue(new MockResponse().setResponseCode(400).setBody("foo"));
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("bad zone name");

    final TestInterfaceAsync api =
        newAsyncBuilder().errorDecoder(new IllegalArgumentExceptionOn400())
            .target("http://localhost:" + server.getPort());

    unwrap(api.post());
  }

  @Test
  public void overrideTypeSpecificDecoder() throws Throwable {
    server.enqueue(new MockResponse().setBody("success!"));

    final TestInterfaceAsync api = newAsyncBuilder()
        .decoder((response, type) -> "fail").target("http://localhost:" + server.getPort());

    assertEquals("fail", unwrap(api.post()));
  }

  @Test
  public void doesntRetryAfterResponseIsSent() throws Throwable {
    server.enqueue(new MockResponse().setBody("success!"));
    thrown.expect(FeignException.class);
    thrown.expectMessage("timeout reading POST http://");

    final TestInterfaceAsync api = newAsyncBuilder().decoder((response, type) -> {
      throw new IOException("timeout");
    }).target("http://localhost:" + server.getPort());

    final CompletableFuture<?> cf = api.post();
    server.takeRequest();
    unwrap(cf);
  }

  @Test
  public void throwsFeignExceptionIncludingBody() throws Throwable {
    server.enqueue(new MockResponse().setBody("success!"));

    final TestInterfaceAsync api = newAsyncBuilder().decoder((response, type) -> {
      throw new IOException("timeout");
    }).target("http://localhost:" + server.getPort());

    final CompletableFuture<?> cf = api.body("Request body");
    server.takeRequest();
    try {
      unwrap(cf);
    } catch (final FeignException e) {
      Assertions.assertThat(e.getMessage())
          .isEqualTo("timeout reading POST http://localhost:" + server.getPort() + "/");
      Assertions.assertThat(e.contentUTF8()).isEqualTo("Request body");
      return;
    }
    fail();
  }

  @Test
  public void throwsFeignExceptionWithoutBody() {
    server.enqueue(new MockResponse().setBody("success!"));

    final TestInterfaceAsync api = newAsyncBuilder().decoder((response, type) -> {
      throw new IOException("timeout");
    }).target("http://localhost:" + server.getPort());

    try {
      api.noContent();
    } catch (final FeignException e) {
      Assertions.assertThat(e.getMessage())
          .isEqualTo("timeout reading POST http://localhost:" + server.getPort() + "/");
      Assertions.assertThat(e.contentUTF8()).isEqualTo("");
    }
  }

  @SuppressWarnings("deprecation")
  @Test
  public void whenReturnTypeIsResponseNoErrorHandling() throws Throwable {
    final Map<String, Collection<String>> headers = new LinkedHashMap<String, Collection<String>>();
    headers.put("Location", Arrays.asList("http://bar.com"));
    final Response response = Response.builder().status(302).reason("Found").headers(headers)
        .request(Request.create(HttpMethod.GET, "/", Collections.emptyMap(), null, Util.UTF_8))
        .body(new byte[0]).build();

    final ExecutorService execs = Executors.newSingleThreadExecutor();

    // fake client as Client.Default follows redirects.
    final TestInterfaceAsync api = AsyncFeign.<Void>asyncBuilder()
        .client(new AsyncClient.Default<>((request, options) -> response, execs))
        .target(TestInterfaceAsync.class, "http://localhost:" + server.getPort());

    assertEquals(Collections.singletonList("http://bar.com"),
        unwrap(api.response()).headers().get("Location"));

    execs.shutdown();
  }

  @Test
  public void okIfDecodeRootCauseHasNoMessage() throws Throwable {
    server.enqueue(new MockResponse().setBody("success!"));
    thrown.expect(DecodeException.class);

    final TestInterfaceAsync api = newAsyncBuilder().decoder((response, type) -> {
      throw new RuntimeException();
    }).target("http://localhost:" + server.getPort());

    unwrap(api.post());
  }

  @Test
  public void decodingExceptionGetWrappedInDecode404Mode() throws Throwable {
    server.enqueue(new MockResponse().setResponseCode(404));
    thrown.expect(DecodeException.class);
    thrown.expectCause(isA(NoSuchElementException.class));

    final TestInterfaceAsync api =
        newAsyncBuilder().decode404().decoder((response, type) -> {
          assertEquals(404, response.status());
          throw new NoSuchElementException();
        }).target("http://localhost:" + server.getPort());

    unwrap(api.post());
  }

  @Test
  public void decodingDoesNotSwallow404ErrorsInDecode404Mode() throws Throwable {
    server.enqueue(new MockResponse().setResponseCode(404));
    thrown.expect(IllegalArgumentException.class);

    final TestInterfaceAsync api = newAsyncBuilder().decode404()
        .errorDecoder(new IllegalArgumentExceptionOn404())
        .target("http://localhost:" + server.getPort());

    final CompletableFuture<Void> cf = api.queryMap(Collections.<String, Object>emptyMap());
    server.takeRequest();
    unwrap(cf);
  }

  @Test
  public void okIfEncodeRootCauseHasNoMessage() throws Throwable {
    server.enqueue(new MockResponse().setBody("success!"));
    thrown.expect(EncodeException.class);

    final TestInterfaceAsync api =
        newAsyncBuilder().encoder((object, bodyType, template) -> {
          throw new RuntimeException();
        }).target("http://localhost:" + server.getPort());

    unwrap(api.body(Arrays.asList("foo")));
  }

  @Test
  public void equalsHashCodeAndToStringWork() {
    final Target<TestInterfaceAsync> t1 =
        new HardCodedTarget<TestInterfaceAsync>(TestInterfaceAsync.class,
            "http://localhost:8080");
    final Target<TestInterfaceAsync> t2 =
        new HardCodedTarget<TestInterfaceAsync>(TestInterfaceAsync.class,
            "http://localhost:8888");
    final Target<OtherTestInterfaceAsync> t3 =
        new HardCodedTarget<OtherTestInterfaceAsync>(OtherTestInterfaceAsync.class,
            "http://localhost:8080");
    final TestInterfaceAsync i1 = newAsyncBuilder().target(t1);
    final TestInterfaceAsync i2 = newAsyncBuilder().target(t1);
    final TestInterfaceAsync i3 = newAsyncBuilder().target(t2);
    final OtherTestInterfaceAsync i4 = newAsyncBuilder().target(t3);

    Assertions.assertThat(i1).isEqualTo(i2).isNotEqualTo(i3).isNotEqualTo(i4);

    Assertions.assertThat(i1.hashCode()).isEqualTo(i2.hashCode()).isNotEqualTo(i3.hashCode())
        .isNotEqualTo(i4.hashCode());

    Assertions.assertThat(i1.toString()).isEqualTo(i2.toString()).isNotEqualTo(i3.toString())
        .isNotEqualTo(i4.toString());

    Assertions.assertThat(t1).isNotEqualTo(i1);

    Assertions.assertThat(t1.hashCode()).isEqualTo(i1.hashCode());

    Assertions.assertThat(t1.toString()).isEqualTo(i1.toString());
  }

  @SuppressWarnings("resource")
  @Test
  public void decodeLogicSupportsByteArray() throws Throwable {
    final byte[] expectedResponse = {12, 34, 56};
    server.enqueue(new MockResponse().setBody(new Buffer().write(expectedResponse)));

    final OtherTestInterfaceAsync api =
        newAsyncBuilder().target(new HardCodedTarget<>(
            OtherTestInterfaceAsync.class,
            "http://localhost:" + server.getPort()));

    Assertions.assertThat(unwrap(api.binaryResponseBody())).containsExactly(expectedResponse);
  }

  @Test
  public void encodeLogicSupportsByteArray() throws Exception {
    final byte[] expectedRequest = {12, 34, 56};
    server.enqueue(new MockResponse());

    final OtherTestInterfaceAsync api =
        newAsyncBuilder().encoder(new Encoder.Default()).target(new HardCodedTarget<>(
            OtherTestInterfaceAsync.class,
            "http://localhost:" + server.getPort()));

    final CompletableFuture<?> cf = api.binaryRequestBody(expectedRequest);

    assertThat(server.takeRequest()).hasBody(expectedRequest);

    checkCFCompletedSoon(cf);
  }

  @Test
  public void encodedQueryParam() throws Exception {
    server.enqueue(new MockResponse());

    final TestInterfaceAsync api =
        newAsyncBuilder().target("http://localhost:" + server.getPort());

    final CompletableFuture<?> cf = api.encodedQueryParam("5.2FSi+");

    assertThat(server.takeRequest()).hasPath("/?trim=5.2FSi%2B");

    checkCFCompletedSoon(cf);
  }

  private void checkCFCompletedSoon(CompletableFuture<?> cf) {
    try {
      unwrap(cf);
    } catch (final RuntimeException e) {
      throw e;
    } catch (final Throwable t) {
      throw new RuntimeException(t);
    }
  }

  @Test
  public void responseMapperIsAppliedBeforeDelegate() throws IOException {
    final ResponseMappingDecoder decoder =
        new ResponseMappingDecoder(upperCaseResponseMapper(), new StringDecoder());
    final String output = (String) decoder.decode(responseWithText("response"), String.class);

    Assertions.assertThat(output).isEqualTo("RESPONSE");
  }

  private static TestInterfaceAsyncBuilder newAsyncBuilder() {
    return new TestInterfaceAsyncBuilder();
  }

  private ResponseMapper upperCaseResponseMapper() {
    return new ResponseMapper() {
      @SuppressWarnings("deprecation")
      @Override
      public Response map(Response response, Type type) {
        try {
          return response.toBuilder()
              .body(Util.toString(response.body().asReader()).toUpperCase().getBytes())
              .build();
        } catch (final IOException e) {
          throw new RuntimeException(e);
        }
      }
    };
  }

  @SuppressWarnings("deprecation")
  private Response responseWithText(String text) {
    return Response.builder().body(text, Util.UTF_8).status(200)
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .headers(new HashMap<>()).build();
  }

  @Test
  public void mapAndDecodeExecutesMapFunction() throws Throwable {
    server.enqueue(new MockResponse().setBody("response!"));

    final TestInterfaceAsync api =
        AsyncFeign.asyncBuilder().mapAndDecode(upperCaseResponseMapper(), new StringDecoder())
            .target(TestInterfaceAsync.class, "http://localhost:" + server.getPort());

    assertEquals("RESPONSE!", unwrap(api.post()));
  }

  @Test
  public void beanQueryMapEncoderWithPrivateGetterIgnored() throws Exception {
    final TestInterfaceAsync api =
        newAsyncBuilder().queryMapEndcoder(new BeanQueryMapEncoder())
            .target("http://localhost:" + server.getPort());

    final PropertyPojo.ChildPojoClass propertyPojo = new PropertyPojo.ChildPojoClass();
    propertyPojo.setPrivateGetterProperty("privateGetterProperty");
    propertyPojo.setName("Name");
    propertyPojo.setNumber(1);

    server.enqueue(new MockResponse());
    final CompletableFuture<?> cf = api.queryMapPropertyPojo(propertyPojo);
    assertThat(server.takeRequest()).hasQueryParams(Arrays.asList("name=Name", "number=1"));
    checkCFCompletedSoon(cf);
  }

  @Test
  public void queryMap_with_child_pojo() throws Exception {
    final TestInterfaceAsync api =
        newAsyncBuilder().queryMapEndcoder(new FieldQueryMapEncoder())
            .target("http://localhost:" + server.getPort());

    final ChildPojo childPojo = new ChildPojo();
    childPojo.setChildPrivateProperty("first");
    childPojo.setParentProtectedProperty("second");
    childPojo.setParentPublicProperty("third");

    server.enqueue(new MockResponse());
    final CompletableFuture<?> cf = api.queryMapPropertyInheritence(childPojo);
    assertThat(server.takeRequest()).hasQueryParams("parentPublicProperty=third",
        "parentProtectedProperty=second",
        "childPrivateProperty=first");
    checkCFCompletedSoon(cf);
  }

  @Test
  public void beanQueryMapEncoderWithNullValueIgnored() throws Exception {
    final TestInterfaceAsync api =
        newAsyncBuilder().queryMapEndcoder(new BeanQueryMapEncoder())
            .target("http://localhost:" + server.getPort());

    final PropertyPojo.ChildPojoClass propertyPojo = new PropertyPojo.ChildPojoClass();
    propertyPojo.setName(null);
    propertyPojo.setNumber(1);

    server.enqueue(new MockResponse());
    final CompletableFuture<?> cf = api.queryMapPropertyPojo(propertyPojo);

    assertThat(server.takeRequest()).hasQueryParams("number=1");

    checkCFCompletedSoon(cf);
  }

  @Test
  public void beanQueryMapEncoderWithEmptyParams() throws Exception {
    final TestInterfaceAsync api =
        newAsyncBuilder().queryMapEndcoder(new BeanQueryMapEncoder())
            .target("http://localhost:" + server.getPort());

    final PropertyPojo.ChildPojoClass propertyPojo = new PropertyPojo.ChildPojoClass();

    server.enqueue(new MockResponse());
    final CompletableFuture<?> cf = api.queryMapPropertyPojo(propertyPojo);
    assertThat(server.takeRequest()).hasQueryParams("/");

    checkCFCompletedSoon(cf);
  }

  public interface TestInterfaceAsync {

    @RequestLine("POST /")
    CompletableFuture<Response> response();

    @RequestLine("POST /")
    CompletableFuture<String> post() throws TestInterfaceException;

    @RequestLine("POST /")
    @Body("%7B\"customer_name\": \"{customer_name}\", \"user_name\": \"{user_name}\", \"password\": \"{password}\"%7D")
    CompletableFuture<Void> login(@Param("customer_name") String customer,
                                  @Param("user_name") String user,
                                  @Param("password") String password);

    @RequestLine("POST /")
    CompletableFuture<Void> body(List<String> contents);

    @RequestLine("POST /")
    CompletableFuture<String> body(String content);

    @RequestLine("POST /")
    CompletableFuture<String> noContent();

    @RequestLine("POST /")
    @Headers("Content-Encoding: gzip")
    CompletableFuture<Void> gzipBody(List<String> contents);

    @RequestLine("POST /")
    @Headers("Content-Encoding: deflate")
    CompletableFuture<Void> deflateBody(List<String> contents);

    @RequestLine("POST /")
    CompletableFuture<Void> form(@Param("customer_name") String customer,
                                 @Param("user_name") String user,
                                 @Param("password") String password);

    @RequestLine("GET /{1}/{2}")
    CompletableFuture<Response> uriParam(@Param("1") String one,
                                         URI endpoint,
                                         @Param("2") String two);

    @RequestLine("GET /?1={1}&2={2}")
    CompletableFuture<Response> queryParams(@Param("1") String one,
                                            @Param("2") Iterable<String> twos);

    @RequestLine("POST /?date={date}")
    CompletableFuture<Void> expand(@Param(value = "date", expander = DateToMillis.class) Date date);

    @RequestLine("GET /?date={date}")
    CompletableFuture<Void> expandList(@Param(value = "date",
        expander = DateToMillis.class) List<Date> dates);

    @RequestLine("GET /?date={date}")
    CompletableFuture<Void> expandArray(@Param(value = "date",
        expander = DateToMillis.class) Date[] dates);

    @RequestLine("GET /")
    CompletableFuture<Void> headerMap(@HeaderMap Map<String, Object> headerMap);

    @RequestLine("GET /")
    @Headers("Content-Encoding: deflate")
    CompletableFuture<Void> headerMapWithHeaderAnnotations(@HeaderMap Map<String, Object> headerMap);

    @RequestLine("GET /")
    CompletableFuture<Void> queryMap(@QueryMap Map<String, Object> queryMap);

    @RequestLine("GET /")
    CompletableFuture<Void> queryMapEncoded(@QueryMap(encoded = true) Map<String, Object> queryMap);

    @RequestLine("GET /?name={name}")
    CompletableFuture<Void> queryMapWithQueryParams(@Param("name") String name,
                                                    @QueryMap Map<String, Object> queryMap);

    @RequestLine("GET /?trim={trim}")
    CompletableFuture<Void> encodedQueryParam(@Param(value = "trim", encoded = true) String trim);

    @RequestLine("GET /")
    CompletableFuture<Void> queryMapPojo(@QueryMap CustomPojo object);

    @RequestLine("GET /")
    CompletableFuture<Void> queryMapPropertyPojo(@QueryMap PropertyPojo object);

    @RequestLine("GET /")
    CompletableFuture<Void> queryMapPropertyInheritence(@QueryMap ChildPojo object);

    class DateToMillis implements Param.Expander {

      @Override
      public String expand(Object value) {
        return String.valueOf(((Date) value).getTime());
      }
    }
  }

  class TestInterfaceException extends Exception {
    private static final long serialVersionUID = 1L;

    TestInterfaceException(String message) {
      super(message);
    }
  }

  public interface OtherTestInterfaceAsync {

    @RequestLine("POST /")
    CompletableFuture<String> post();

    @RequestLine("POST /")
    CompletableFuture<byte[]> binaryResponseBody();

    @RequestLine("POST /")
    CompletableFuture<Void> binaryRequestBody(byte[] contents);
  }

  static class ForwardedForInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
      template.header("X-Forwarded-For", "origin.host.com");
    }
  }

  static class UserAgentInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
      template.header("User-Agent", "Feign");
    }
  }

  static class IllegalArgumentExceptionOn400 extends ErrorDecoder.Default {

    @Override
    public Exception decode(String methodKey, Response response) {
      if (response.status() == 400) {
        return new IllegalArgumentException("bad zone name");
      }
      return super.decode(methodKey, response);
    }
  }

  static class IllegalArgumentExceptionOn404 extends ErrorDecoder.Default {

    @Override
    public Exception decode(String methodKey, Response response) {
      if (response.status() == 404) {
        return new IllegalArgumentException("bad zone name");
      }
      return super.decode(methodKey, response);
    }
  }

  static final class TestInterfaceAsyncBuilder {

    private final AsyncFeign.AsyncBuilder<Object> delegate =
        AsyncFeign.asyncBuilder()
            .client(new AsyncHttpClient())
            .decoder(new Decoder.Default()).encoder(new Encoder() {

              @SuppressWarnings("deprecation")
              @Override
              public void encode(Object object, Type bodyType, RequestTemplate template) {
                if (object instanceof Map) {
                  template.body(new Gson().toJson(object));
                } else {
                  template.body(object.toString());
                }
              }
            });

    TestInterfaceAsyncBuilder requestInterceptor(RequestInterceptor requestInterceptor) {
      delegate.requestInterceptor(requestInterceptor);
      return this;
    }

    TestInterfaceAsyncBuilder encoder(Encoder encoder) {
      delegate.encoder(encoder);
      return this;
    }

    TestInterfaceAsyncBuilder decoder(Decoder decoder) {
      delegate.decoder(decoder);
      return this;
    }

    TestInterfaceAsyncBuilder errorDecoder(ErrorDecoder errorDecoder) {
      delegate.errorDecoder(errorDecoder);
      return this;
    }

    TestInterfaceAsyncBuilder decode404() {
      delegate.decode404();
      return this;
    }

    TestInterfaceAsyncBuilder queryMapEndcoder(QueryMapEncoder queryMapEncoder) {
      delegate.queryMapEncoder(queryMapEncoder);
      return this;
    }

    TestInterfaceAsync target(String url) {
      return delegate.target(TestInterfaceAsync.class, url);
    }

    <T> T target(Target<T> target) {
      return delegate.target(target);
    }
  }

  static final class ExtendedCF<T> extends CompletableFuture<T> {

  }

  static abstract class NonInterface {
    @RequestLine("GET /")
    abstract CompletableFuture<Void> x();
  }

  interface NonCFApi {
    @RequestLine("GET /")
    void x();
  }

  interface ExtendedCFApi {
    @RequestLine("GET /")
    ExtendedCF<Void> x();
  }

  interface LowerWildApi {
    @RequestLine("GET /")
    CompletableFuture<? extends Object> x();
  }

  interface UpperWildApi {
    @RequestLine("GET /")
    CompletableFuture<? super Object> x();
  }

  interface WildApi {
    @RequestLine("GET /")
    CompletableFuture<?> x();
  }
}
