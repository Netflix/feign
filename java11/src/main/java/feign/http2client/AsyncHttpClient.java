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

import feign.AsyncClient;
import feign.Request;
import feign.Request.Options;
import feign.Response;
import feign.Util;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class AsyncHttpClient extends AbstractHttpClient implements AsyncClient<Object> {

  private final HttpClient client;

  public AsyncHttpClient() {
    this(HttpClient.newBuilder()
        .followRedirects(Redirect.ALWAYS)
        .build());
  }

  public AsyncHttpClient(HttpClient client) {
    this.client = Util.checkNotNull(client, "HttpClient must not be null");
  }

  @Override
  public CompletableFuture<Response> execute(Request request,
                                             Options options,
                                             Optional<Object> requestContext) {
    HttpRequest httpRequest;
    try {
      httpRequest = newRequestBuilder(request, options).build();
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Invalid uri " + request.url(), e);
    }

    HttpClient clientForRequest = getOrCreateClient(client, options);
    CompletableFuture<HttpResponse<byte[]>> future =
        clientForRequest.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
    return future.thenApply(httpResponse -> toFeignResponse(request, httpResponse));
  }
}
