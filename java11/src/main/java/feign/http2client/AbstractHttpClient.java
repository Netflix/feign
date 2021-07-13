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
package feign.http2client;

import feign.Request;
import feign.Request.Options;
import feign.Response;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class AbstractHttpClient {

  protected Response toFeignResponse(Request request, HttpResponse<byte[]> httpResponse) {
    final OptionalLong length = httpResponse.headers().firstValueAsLong("Content-Length");

    return Response.builder()
        .body(new ByteArrayInputStream(httpResponse.body()),
            length.isPresent() ? (int) length.getAsLong() : null)
        .reason(httpResponse.headers().firstValue("Reason-Phrase").orElse("OK"))
        .request(request)
        .status(httpResponse.statusCode())
        .headers(castMapCollectType(httpResponse.headers().map()))
        .build();
  }

  protected Builder newRequestBuilder(Request request, Options options) throws URISyntaxException {
    URI uri = new URI(request.url());

    final BodyPublisher body;
    final byte[] data = request.body();
    if (data == null) {
      body = BodyPublishers.noBody();
    } else {
      body = BodyPublishers.ofByteArray(data);
    }

    final Builder requestBuilder = HttpRequest.newBuilder()
        .uri(uri)
        .timeout(Duration.ofMillis(options.readTimeoutMillis()));

    final Map<String, Collection<String>> headers = filterRestrictedHeaders(request.headers());
    if (!headers.isEmpty()) {
      requestBuilder.headers(asString(headers));
    }

    switch (request.httpMethod()) {
      case GET:
        return requestBuilder.GET();
      case POST:
        return requestBuilder.POST(body);
      case PUT:
        return requestBuilder.PUT(body);
      case DELETE:
        return requestBuilder.DELETE();
      default:
        // fall back scenario, http implementations may restrict some methods
        return requestBuilder.method(request.httpMethod().toString(), body);
    }
  }

  private Map<String, Collection<String>> castMapCollectType(Map<String, List<String>> map) {
    final Map<String, Collection<String>> result = new HashMap<>();
    map.forEach((key, value) -> result.put(key, new HashSet<>(value)));
    return result;
  }

  private String[] asString(Map<String, Collection<String>> headers) {
    return headers.entrySet().stream()
        .flatMap(entry -> entry.getValue()
            .stream()
            .map(value -> Arrays.asList(entry.getKey(), value))
            .flatMap(List::stream))
        .toArray(String[]::new);
  }

  protected HttpClient getOrCreateClient(HttpClient client, Options options) {
    if (doesClientConfigurationDiffer(client, options)) {
      // create a new client from the existing one - but with connectTimeout and followRedirect
      // settings from options
      java.net.http.HttpClient.Builder builder = newClientBuilder(options)
          .sslContext(client.sslContext())
          .sslParameters(client.sslParameters())
          .version(client.version());
      client.authenticator().ifPresent(builder::authenticator);
      client.cookieHandler().ifPresent(builder::cookieHandler);
      client.executor().ifPresent(builder::executor);
      client.proxy().ifPresent(builder::proxy);
      return builder.build();
    }
    return client;
  }

  protected boolean doesClientConfigurationDiffer(HttpClient client, Options options) {
    if ((client.followRedirects() == Redirect.ALWAYS) != options.isFollowRedirects()) {
      return true;
    }
    return client.connectTimeout()
        .map(timeout -> timeout.toMillis() != options.connectTimeoutMillis())
        .orElse(true);
  }

  protected static java.net.http.HttpClient.Builder newClientBuilder(Options options) {
    return HttpClient
        .newBuilder()
        .followRedirects(options.isFollowRedirects() ? Redirect.ALWAYS : Redirect.NEVER)
        .connectTimeout(Duration.ofMillis(options.connectTimeoutMillis()));
  }

  /**
   * There is a bunch o headers that the http2 client do not allow to be set.
   *
   * @see jdk.internal.net.http.common.Utils.DISALLOWED_HEADERS_SET
   */
  private static final Set<String> DISALLOWED_HEADERS_SET;

  static {
    // A case insensitive TreeSet of strings.
    final TreeSet<String> treeSet = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    treeSet.addAll(Set.of("connection", "content-length", "date", "expect", "from", "host",
        "origin", "referer", "upgrade", "via", "warning"));
    DISALLOWED_HEADERS_SET = Collections.unmodifiableSet(treeSet);
  }

  protected Map<String, Collection<String>> filterRestrictedHeaders(Map<String, Collection<String>> headers) {
    final Map<String, Collection<String>> filteredHeaders = headers.keySet()
        .stream()
        .filter(headerName -> !DISALLOWED_HEADERS_SET.contains(headerName))
        .collect(Collectors.toMap(
            Function.identity(),
            headers::get));

    filteredHeaders.computeIfAbsent("Accept", key -> List.of("*/*"));

    return filteredHeaders;
  }
}
