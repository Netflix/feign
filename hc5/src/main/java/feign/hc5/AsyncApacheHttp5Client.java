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
package feign.hc5;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.config.Configurable;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.io.CloseMode;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import feign.*;
import feign.Request.Options;

/**
 * This module directs Feign's http requests to Apache's
 * <a href="https://hc.apache.org/httpcomponents-client-5.0.x/index.html">HttpClient 5</a>. Ex.
 *
 * <pre>
 * GitHub github = Feign.builder().client(new ApacheHttp5Client()).target(GitHub.class,
 * "https://api.github.com");
 */
/*
 */
public final class AsyncApacheHttp5Client implements AsyncClient<HttpClientContext>, AutoCloseable {

  private static final String ACCEPT_HEADER_NAME = "Accept";

  private final CloseableHttpAsyncClient client;

  public AsyncApacheHttp5Client() {
    this(createStartedClient());
  }

  private static CloseableHttpAsyncClient createStartedClient() {
    final CloseableHttpAsyncClient client = HttpAsyncClients.custom().build();
    client.start();
    return client;
  }

  public AsyncApacheHttp5Client(CloseableHttpAsyncClient client) {
    this.client = client;
  }

  @Override
  public CompletableFuture<Response> execute(Request request,
                                             Options options,
                                             Optional<HttpClientContext> requestContext) {
    final SimpleHttpRequest httpUriRequest = toClassicHttpRequest(request, options);

    final CompletableFuture<Response> result = new CompletableFuture<>();
    final FutureCallback<SimpleHttpResponse> callback = new FutureCallback<SimpleHttpResponse>() {

      @Override
      public void completed(SimpleHttpResponse httpResponse) {
        result.complete(toFeignResponse(httpResponse, request));
      }

      @Override
      public void failed(Exception ex) {
        result.completeExceptionally(ex);
      }

      @Override
      public void cancelled() {
        result.cancel(false);
      }
    };

    client.execute(httpUriRequest,
        configureTimeouts(options, requestContext.orElseGet(HttpClientContext::new)),
        callback);

    return result;
  }

  protected HttpClientContext configureTimeouts(Request.Options options,
                                                HttpClientContext context) {
    // per request timeouts
    final RequestConfig requestConfig =
        (client instanceof Configurable
            ? RequestConfig.copy(((Configurable) client).getConfig())
            : RequestConfig.custom())
                .setConnectTimeout(options.connectTimeout(), options.connectTimeoutUnit())
                .setResponseTimeout(options.readTimeout(), options.readTimeoutUnit())
                .build();
    context.setRequestConfig(requestConfig);
    return context;
  }

  SimpleHttpRequest toClassicHttpRequest(Request request,
                                         Request.Options options) {
    final SimpleHttpRequest httpRequest =
        new SimpleHttpRequest(request.httpMethod().name(), request.url());

    // request headers
    boolean hasAcceptHeader = false;
    for (final Map.Entry<String, Collection<String>> headerEntry : request.headers().entrySet()) {
      final String headerName = headerEntry.getKey();
      if (headerName.equalsIgnoreCase(ACCEPT_HEADER_NAME)) {
        hasAcceptHeader = true;
      }

      if (headerName.equalsIgnoreCase(Util.CONTENT_LENGTH)) {
        // The 'Content-Length' header is always set by the Apache client and it
        // doesn't like us to set it as well.
        continue;
      }

      for (final String headerValue : headerEntry.getValue()) {
        httpRequest.addHeader(headerName, headerValue);
      }
    }
    // some servers choke on the default accept string, so we'll set it to anything
    if (!hasAcceptHeader) {
      httpRequest.addHeader(ACCEPT_HEADER_NAME, "*/*");
    }

    // request body
    // final Body requestBody = request.requestBody();
    final byte[] data = request.body();
    if (data != null) {
      httpRequest.setBody(data, getContentType(request));
    }

    return httpRequest;
  }

  private ContentType getContentType(Request request) {
    ContentType contentType = null;
    for (final Map.Entry<String, Collection<String>> entry : request.headers().entrySet()) {
      if (entry.getKey().equalsIgnoreCase("Content-Type")) {
        final Collection<String> values = entry.getValue();
        if (values != null && !values.isEmpty()) {
          contentType = ContentType.parse(values.iterator().next());
          if (contentType.getCharset() == null) {
            contentType = contentType.withCharset(request.charset());
          }
          break;
        }
      }
    }
    return contentType;
  }

  Response toFeignResponse(SimpleHttpResponse httpResponse, Request request) {
    final int statusCode = httpResponse.getCode();

    final String reason = httpResponse.getReasonPhrase();

    final Map<String, Collection<String>> headers = new HashMap<String, Collection<String>>();
    for (final Header header : httpResponse.getHeaders()) {
      final String name = header.getName();
      final String value = header.getValue();

      Collection<String> headerValues = headers.get(name);
      if (headerValues == null) {
        headerValues = new ArrayList<String>();
        headers.put(name, headerValues);
      }
      headerValues.add(value);
    }

    return Response.builder()
        .status(statusCode)
        .reason(reason)
        .headers(headers)
        .request(request)
        .body(httpResponse.getBodyBytes())
        .build();
  }

  @Override
  public void close() throws Exception {
    client.close(CloseMode.GRACEFUL);
  }

}
