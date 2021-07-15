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
package feign;

import org.junit.Test;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

public class FeignExceptionTest {

  @Test
  public void canCreateWithRequestAndResponse() {
    Request request = Request.create(Request.HttpMethod.GET,
        "/home", Collections.emptyMap(),
        "data".getBytes(StandardCharsets.UTF_8),
        StandardCharsets.UTF_8,
        null);

    Response response = Response.builder()
        .status(400)
        .body("response".getBytes(StandardCharsets.UTF_8))
        .request(request)
        .build();

    FeignException exception =
        FeignException.errorReading(request, response, new IOException("socket closed"));
    assertThat(exception.responseBody()).isNotEmpty();
    assertThat(exception.hasRequest()).isTrue();
    assertThat(exception.request()).isNotNull();
  }

  @Test
  public void canCreateWithRequestOnly() {
    Request request = Request.create(Request.HttpMethod.GET,
        "/home", Collections.emptyMap(),
        "data".getBytes(StandardCharsets.UTF_8),
        StandardCharsets.UTF_8,
        null);

    FeignException exception =
        FeignException.errorExecuting(request, new IOException("connection timeout"));
    assertThat(exception.responseBody()).isEmpty();
    assertThat(exception.content()).isNullOrEmpty();
    assertThat(exception.hasRequest()).isTrue();
    assertThat(exception.request()).isNotNull();
  }

  @Test
  public void createFeignExceptionWithCorrectCharsetResponse() {
    Map<String, Collection<String>> map = new HashMap<>();
    map.put("connection", new ArrayList<>(Collections.singletonList("keep-alive")));
    map.put("content-length", new ArrayList<>(Collections.singletonList("100")));
    map.put("content-type",
        new ArrayList<>(Collections.singletonList("application/json;charset=UTF-16BE")));

    Request request = Request.create(Request.HttpMethod.GET,
        "/home", Collections.emptyMap(),
        "data".getBytes(StandardCharsets.UTF_16BE),
        StandardCharsets.UTF_16BE,
        null);

    Response response = Response.builder()
        .status(400)
        .body("response".getBytes(StandardCharsets.UTF_16BE))
        .headers(map)
        .request(request)
        .build();

    FeignException exception = FeignException.errorStatus("methodKey", response);
    assertThat(exception.getMessage())
        .isEqualTo("[400] during [GET] to [/home] [methodKey]: [response]");
  }

  @Test
  public void createFeignExceptionWithErrorCharsetResponse() {
    Map<String, Collection<String>> map = new HashMap<>();
    map.put("connection", new ArrayList<>(Collections.singletonList("keep-alive")));
    map.put("content-length", new ArrayList<>(Collections.singletonList("100")));
    map.put("content-type",
        new ArrayList<>(Collections.singletonList("application/json;charset=UTF-8")));

    Request request = Request.create(Request.HttpMethod.GET,
        "/home", Collections.emptyMap(),
        "data".getBytes(StandardCharsets.UTF_16BE),
        StandardCharsets.UTF_16BE,
        null);

    Response response = Response.builder()
        .status(400)
        .body("response".getBytes(StandardCharsets.UTF_16BE))
        .headers(map)
        .request(request)
        .build();

    FeignException exception = FeignException.errorStatus("methodKey", response);
    assertThat(exception.getMessage())
        .isNotEqualTo("[400] during [GET] to [/home] [methodKey]: [response]");
  }

  @Test
  public void canGetResponseHeadersFromException() {
    Request request = Request.create(
        Request.HttpMethod.GET,
        "/home",
        Collections.emptyMap(),
        "data".getBytes(StandardCharsets.UTF_8),
        StandardCharsets.UTF_8,
        null);

    Map<String, Collection<String>> responseHeaders = new HashMap<>();
    responseHeaders.put("Content-Type", Collections.singletonList("text/plain"));
    responseHeaders.put("Cookie", Arrays.asList("cookie1", "cookie2"));

    Response response = Response.builder()
        .request(request)
        .body("some text", StandardCharsets.UTF_8)
        .headers(responseHeaders)
        .build();

    FeignException exception = FeignException.errorStatus("methodKey", response);
    assertThat(exception.responseHeaders())
        .hasEntrySatisfying("Content-Type", value -> {
          assertThat(value).contains("text/plain");
        }).hasEntrySatisfying("Cookie", value -> {
          assertThat(value).contains("cookie1", "cookie2");
        });
  }

  @Test(expected = NullPointerException.class)
  public void nullRequestShouldThrowNPEwThrowable() {
    new Derived(404, "message", null, new Throwable());
  }

  @Test(expected = NullPointerException.class)
  public void nullRequestShouldThrowNPEwThrowableAndBytes() {
    new Derived(404, "message", null, new Throwable(), new byte[1], Collections.emptyMap());
  }

  @Test(expected = NullPointerException.class)
  public void nullRequestShouldThrowNPE() {
    new Derived(404, "message", null);
  }

  @Test(expected = NullPointerException.class)
  public void nullRequestShouldThrowNPEwBytes() {
    new Derived(404, "message", null, new byte[1], Collections.emptyMap());
  }

  static class Derived extends FeignException {

    public Derived(int status, String message, Request request, Throwable cause) {
      super(status, message, request, cause);
    }

    public Derived(int status, String message, Request request, Throwable cause, byte[] content,
        Map<String, Collection<String>> headers) {
      super(status, message, request, cause, content, headers);
    }

    public Derived(int status, String message, Request request) {
      super(status, message, request);
    }

    public Derived(int status, String message, Request request, byte[] content,
        Map<String, Collection<String>> headers) {
      super(status, message, request, content, headers);
    }
  }

}
