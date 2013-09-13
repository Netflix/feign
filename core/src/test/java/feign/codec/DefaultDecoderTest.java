/*
 * Copyright 2013 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package feign.codec;

import feign.FeignException;
import feign.Response;
import org.testng.annotations.Test;
import org.w3c.dom.Document;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class DefaultDecoderTest {
  private final Decoder decoder = new Decoder.Default();

  @Test public void testDecodesToVoid() throws Exception {
    assertEquals(decoder.decode(knownResponse(), void.class), null);
  }

  @Test public void testDecodesToResponse() throws Exception {
    Response response = knownResponse();
    Object decodedObject = decoder.decode(response, Response.class);
    assertEquals(decodedObject.getClass(), Response.class, "");
    Response decodedResponse = (Response) decodedObject;
    assertEquals(decodedResponse.status(), response.status());
    assertEquals(decodedResponse.reason(), response.reason());
    assertEquals(decodedResponse.headers(), response.headers());
    assertEquals(decodedResponse.body().toString(), response.body().toString());
  }

  @Test public void testDecodesToString() throws Exception {
    Response response = knownResponse();
    Object decodedObject = decoder.decode(response, String.class);
    assertEquals(decodedObject.getClass(), String.class);
    assertEquals(decodedObject.toString(), response.body().toString());
  }

  @Test public void testDecodesNullBodyToNull() throws Exception {
    assertNull(decoder.decode(nullBodyResponse(), Document.class));
  }

  @Test(expectedExceptions = DecodeException.class, expectedExceptionsMessageRegExp = ".* is not a type supported by this decoder.")
  public void testRefusesToDecodeOtherTypes() throws Exception {
    decoder.decode(knownResponse(), Document.class);
  }

  private Response knownResponse() {
    Map<String, Collection<String>> headers = new HashMap<String, Collection<String>>();
    headers.put("Content-Type", Collections.singleton("text/plain"));
    return Response.create(200, "OK", headers, "response body");
  }

  private Response nullBodyResponse() {
    return Response.create(200, "OK", Collections.<String, Collection<String>>emptyMap(), null);
  }
}
