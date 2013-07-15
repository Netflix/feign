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
package feign;

import com.google.common.base.Joiner;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.google.mockwebserver.SocketPolicy;
import dagger.Module;
import dagger.Provides;
import feign.codec.Decoder;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import feign.codec.StringDecoder;
import org.testng.annotations.Test;

import javax.inject.Named;
import javax.inject.Singleton;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static dagger.Provides.Type.SET;
import static org.testng.Assert.assertEquals;

@Test
// unbound wildcards are not currently injectable in dagger.
@SuppressWarnings("rawtypes")
public class FeignTest {
  interface TestInterface {
    @RequestLine("POST /") String post();

    @RequestLine("POST /")
    @Body("%7B\"customer_name\": \"{customer_name}\", \"user_name\": \"{user_name}\", \"password\": \"{password}\"%7D")
    void login(
        @Named("customer_name") String customer, @Named("user_name") String user, @Named("password") String password);

    @RequestLine("POST /")
    void body(List<String> contents);

    @RequestLine("POST /")
    void form(
        @Named("customer_name") String customer, @Named("user_name") String user, @Named("password") String password);

    @RequestLine("GET /{1}/{2}") Response uriParam(@Named("1") String one, URI endpoint, @Named("2") String two);

    @dagger.Module(overrides = true, library = true)
    static class Module {
      @Provides(type = SET) Encoder defaultEncoder() {
        return new Encoder.Text<Object>() {
          @Override public String encode(Object object) {
            return object.toString();
          }
        };
      }

      @Provides(type = SET) Encoder formEncoder() {
        return new Encoder.Text<Map<String, ?>>() {
          @Override public String encode(Map<String, ?> object) {
            return Joiner.on(',').withKeyValueSeparator("=").join(object);
          }
        };
      }
    }
  }

  @Test
  public void postTemplateParamsResolve() throws IOException, InterruptedException {
    final MockWebServer server = new MockWebServer();
    server.enqueue(new MockResponse().setResponseCode(200).setBody("foo"));
    server.play();

    try {
      TestInterface api = Feign.create(TestInterface.class, server.getUrl("").toString(), new TestInterface.Module());

      api.login("netflix", "denominator", "password");
      assertEquals(new String(server.takeRequest().getBody()),
          "{\"customer_name\": \"netflix\", \"user_name\": \"denominator\", \"password\": \"password\"}");
    } finally {
      server.shutdown();
    }
  }

  @Test
  public void postFormParams() throws IOException, InterruptedException {
    final MockWebServer server = new MockWebServer();
    server.enqueue(new MockResponse().setResponseCode(200).setBody("foo"));
    server.play();

    try {
      TestInterface api = Feign.create(TestInterface.class, server.getUrl("").toString(), new TestInterface.Module());

      api.form("netflix", "denominator", "password");
      assertEquals(new String(server.takeRequest().getBody()),
          "customer_name=netflix,user_name=denominator,password=password");
    } finally {
      server.shutdown();
    }
  }

  @Test
  public void postBodyParam() throws IOException, InterruptedException {
    final MockWebServer server = new MockWebServer();
    server.enqueue(new MockResponse().setResponseCode(200).setBody("foo"));
    server.play();

    try {
      TestInterface api = Feign.create(TestInterface.class, server.getUrl("").toString(), new TestInterface.Module());

      api.body(Arrays.asList("netflix", "denominator", "password"));
      assertEquals(new String(server.takeRequest().getBody()), "[netflix, denominator, password]");
    } finally {
      server.shutdown();
    }
  }

  @Test public void toKeyMethodFormatsAsExpected() throws Exception {
    assertEquals(Feign.configKey(TestInterface.class.getDeclaredMethod("post")), "TestInterface#post()");
    assertEquals(Feign.configKey(TestInterface.class.getDeclaredMethod("uriParam", String.class, URI.class,
        String.class)), "TestInterface#uriParam(String,URI,String)");
  }

  @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "zone not found")
  public void canOverrideErrorDecoder() throws IOException, InterruptedException {
    @dagger.Module(overrides = true, includes = TestInterface.Module.class) class Overrides {
      @Provides @Singleton ErrorDecoder errorDecoder() {
        return new ErrorDecoder.Default() {

          @Override
          public Exception decode(String methodKey, Response response) {
            if (response.status() == 404)
              return new IllegalArgumentException("zone not found");
            return super.decode(methodKey, response);
          }

        };
      }
    }

    final MockWebServer server = new MockWebServer();
    server.enqueue(new MockResponse().setResponseCode(404).setBody("foo"));
    server.play();

    try {
      TestInterface api = Feign.create(TestInterface.class, server.getUrl("").toString(), new Overrides());

      api.post();
    } finally {
      server.shutdown();
    }
  }

  @Test public void retriesLostConnectionBeforeRead() throws IOException, InterruptedException {
    MockWebServer server = new MockWebServer();
    server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("success!".getBytes()));
    server.play();

    try {
      TestInterface api = Feign.create(TestInterface.class, server.getUrl("").toString(),
          new TestInterface.Module());

      api.post();
      assertEquals(server.getRequestCount(), 2);

    } finally {
      server.shutdown();
    }
  }

  public void overrideTypeSpecificDecoder() throws IOException, InterruptedException {
    MockWebServer server = new MockWebServer();
    server.enqueue(new MockResponse().setResponseCode(200).setBody("success!".getBytes()));
    server.play();

    try {
      @dagger.Module(overrides = true, includes = TestInterface.Module.class) class Overrides {
        @Provides(type = SET) Decoder decoder() {
          return new Decoder.TextStream<String>() {
            @Override
            public String decode(Reader reader, Type type) throws IOException {
              return "fail";
            }
          };
        }
      }
      TestInterface api = Feign.create(TestInterface.class, server.getUrl("").toString(), new Overrides());

      assertEquals(api.post(), "fail");
    } finally {
      server.shutdown();
      assertEquals(server.getRequestCount(), 1);
    }
  }

  /**
   * when you must parse a 2xx status to determine if the operation succeeded or not.
   */
  public void retryableExceptionInDecoder() throws IOException, InterruptedException {
    MockWebServer server = new MockWebServer();
    server.enqueue(new MockResponse().setResponseCode(200).setBody("retry!".getBytes()));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("success!".getBytes()));
    server.play();

    try {
      @dagger.Module(overrides = true, includes = TestInterface.Module.class) class Overrides {
        @Provides(type = SET) Decoder decoder() {
          return new StringDecoder() {
            @Override
            public String decode(Reader reader, Type type) throws RetryableException, IOException {
              String string = super.decode(reader, type);
              if ("retry!".equals(string))
                throw new RetryableException(string, null);
              return string;
            }
          };
        }
      }
      TestInterface api = Feign.create(TestInterface.class, server.getUrl("").toString(), new Overrides());

      assertEquals(api.post(), "success!");
    } finally {
      server.shutdown();
      assertEquals(server.getRequestCount(), 2);
    }
  }

  @Test(expectedExceptions = FeignException.class, expectedExceptionsMessageRegExp = "error reading response POST http://.*")
  public void doesntRetryAfterResponseIsSent() throws IOException, InterruptedException {
    MockWebServer server = new MockWebServer();
    server.enqueue(new MockResponse().setResponseCode(200).setBody("success!".getBytes()));
    server.play();

    try {
      @dagger.Module(overrides = true, includes = TestInterface.Module.class) class Overrides {
        @Provides(type = SET) Decoder decoder() {
          return new Decoder.TextStream<String>() {
            @Override
            public String decode(Reader reader, Type type) throws IOException {
              throw new IOException("error reading response");
            }
          };
        }
      }
      TestInterface api = Feign.create(TestInterface.class, server.getUrl("").toString(), new Overrides());

      api.post();
    } finally {
      server.shutdown();
      assertEquals(server.getRequestCount(), 1);
    }
  }

  @Module(injects = Client.Default.class, overrides = true)
  static class TrustSSLSockets {
    @Provides SSLSocketFactory trustingSSLSocketFactory() {
      return TrustingSSLSocketFactory.get();
    }
  }

  @Test public void canOverrideSSLSocketFactory() throws IOException, InterruptedException {
    MockWebServer server = new MockWebServer();
    server.useHttps(TrustingSSLSocketFactory.get(), false);
    server.enqueue(new MockResponse().setResponseCode(200).setBody("success!".getBytes()));
    server.play();

    try {
      TestInterface api = Feign.create(TestInterface.class, server.getUrl("").toString(),
          new TestInterface.Module(), new TrustSSLSockets());
      api.post();
    } finally {
      server.shutdown();
    }
  }

  @Test public void retriesFailedHandshake() throws IOException, InterruptedException {
    MockWebServer server = new MockWebServer();
    server.useHttps(TrustingSSLSocketFactory.get(), false);
    server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.FAIL_HANDSHAKE));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("success!".getBytes()));
    server.play();

    try {
      TestInterface api = Feign.create(TestInterface.class, server.getUrl("").toString(),
          new TestInterface.Module(), new TrustSSLSockets());
      api.post();
      assertEquals(server.getRequestCount(), 2);
    } finally {
      server.shutdown();
    }
  }
}
