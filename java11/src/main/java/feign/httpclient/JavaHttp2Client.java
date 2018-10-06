/**
 * Copyright 2012-2018 The Feign Authors
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
package feign.httpclient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import feign.Client;
import feign.Request;
import feign.Request.Options;
import feign.Response;

public class JavaHttp2Client implements Client {

  public Response execute(Request request, Options options) throws IOException {
    final HttpClient client = HttpClient.newBuilder()
        .followRedirects(Redirect.ALWAYS)
        .build();

    URI uri;
    try {
      uri = new URI(request.url());
    } catch (final URISyntaxException e) {
      throw new IOException("Invalid uri " + request.url(), e);
    }

    final BodyPublisher body;
    if (request.body() == null) {
      body = BodyPublishers.noBody();
    } else {
      body = BodyPublishers.ofByteArray(request.body());
    }

    final HttpRequest httpRequest = HttpRequest.newBuilder()
        .uri(uri)
        .method(request.method(), body)
        .headers(asString(filterRestrictedHeaders(request.headers())))
        .build();

    HttpResponse<byte[]> httpResponse;
    try {
      httpResponse = client.send(httpRequest, BodyHandlers.ofByteArray());
    } catch (final InterruptedException e) {
      throw new IOException("Invalid uri " + request.url(), e);
    }

    System.out.println(httpResponse.headers().map());

    final OptionalLong length = httpResponse.headers().firstValueAsLong("Content-Length");

    final Response response = Response.builder()
        .body(new ByteArrayInputStream(httpResponse.body()),
            length.isPresent() ? (int) length.getAsLong() : null)
        .reason(httpResponse.headers().firstValue("Reason-Phrase").orElse(null))
        .request(request)
        .status(httpResponse.statusCode())
        .headers(castMapCollectType(httpResponse.headers().map()))
        .build();
    return response;
  }

  /**
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

  private Map<String, Collection<String>> filterRestrictedHeaders(Map<String, Collection<String>> headers) {
    return headers.keySet()
        .stream()
        .filter(headerName -> !DISALLOWED_HEADERS_SET.contains(headerName))
        .collect(Collectors.toMap(
            Function.identity(),
            headers::get));
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
        .collect(Collectors.toList())
        .toArray(new String[0]);
  }

}
