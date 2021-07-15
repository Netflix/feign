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
package feign.hc5;

import static feign.Util.UTF_8;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.Configurable;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.net.URLEncodedUtils;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.*;
import feign.*;

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
public final class ApacheHttp5Client implements Client {
  private static final String ACCEPT_HEADER_NAME = "Accept";

  private final HttpClient client;

  public ApacheHttp5Client() {
    this(HttpClientBuilder.create().build());
  }

  public ApacheHttp5Client(HttpClient client) {
    this.client = client;
  }

  @Override
  public Response execute(Request request, Request.Options options) throws IOException {
    ClassicHttpRequest httpUriRequest;
    try {
      httpUriRequest = toClassicHttpRequest(request, options);
    } catch (final URISyntaxException e) {
      throw new IOException("URL '" + request.url() + "' couldn't be parsed into a URI", e);
    }
    final HttpHost target = HttpHost.create(URI.create(request.url()));
    final HttpClientContext context = configureTimeouts(options);

    final ClassicHttpResponse httpResponse =
        (ClassicHttpResponse) client.execute(target, httpUriRequest, context);
    return toFeignResponse(httpResponse, request);
  }

  protected HttpClientContext configureTimeouts(Request.Options options) {
    final HttpClientContext context = new HttpClientContext();
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

  ClassicHttpRequest toClassicHttpRequest(Request request, Request.Options options)
      throws URISyntaxException {
    final ClassicRequestBuilder requestBuilder =
        ClassicRequestBuilder.create(request.httpMethod().name());

    final URI uri = new URIBuilder(request.url()).build();

    requestBuilder.setUri(uri.getScheme() + "://" + uri.getAuthority() + uri.getRawPath());

    // request query params
    final List<NameValuePair> queryParams =
        URLEncodedUtils.parse(uri, requestBuilder.getCharset());
    for (final NameValuePair queryParam : queryParams) {
      requestBuilder.addParameter(queryParam);
    }

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
        requestBuilder.addHeader(headerName, headerValue);
      }
    }
    // some servers choke on the default accept string, so we'll set it to anything
    if (!hasAcceptHeader) {
      requestBuilder.addHeader(ACCEPT_HEADER_NAME, "*/*");
    }

    // request body
    // final Body requestBody = request.requestBody();
    byte[] data = request.body();
    if (data != null) {
      HttpEntity entity;
      if (request.isBinary()) {
        entity = new ByteArrayEntity(data, null);
      } else {
        final ContentType contentType = getContentType(request);
        entity = new StringEntity(new String(data), contentType);
      }

      requestBuilder.setEntity(entity);
    } else {
      requestBuilder.setEntity(new ByteArrayEntity(new byte[0], null));
    }

    return requestBuilder.build();
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

  Response toFeignResponse(ClassicHttpResponse httpResponse, Request request) throws IOException {
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
        .body(toFeignBody(httpResponse))
        .build();
  }

  Response.Body toFeignBody(ClassicHttpResponse httpResponse) {
    final HttpEntity entity = httpResponse.getEntity();
    if (entity == null) {
      return null;
    }
    return new Response.Body() {

      @Override
      public Integer length() {
        return entity.getContentLength() >= 0 && entity.getContentLength() <= Integer.MAX_VALUE
            ? (int) entity.getContentLength()
            : null;
      }

      @Override
      public boolean isRepeatable() {
        return entity.isRepeatable();
      }

      @Override
      public InputStream asInputStream() throws IOException {
        return entity.getContent();
      }

      @SuppressWarnings("deprecation")
      @Override
      public Reader asReader() throws IOException {
        return new InputStreamReader(asInputStream(), UTF_8);
      }

      @Override
      public Reader asReader(Charset charset) throws IOException {
        Util.checkNotNull(charset, "charset should not be null");
        return new InputStreamReader(asInputStream(), charset);
      }

      @Override
      public void close() throws IOException {
        EntityUtils.consume(entity);
        httpResponse.close();
      }
    };
  }
}
