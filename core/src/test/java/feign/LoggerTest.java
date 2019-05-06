/**
 * Copyright 2012-2019 The Feign Authors
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

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.assertj.core.api.SoftAssertions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.junit.runners.model.Statement;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import feign.Logger.Level;

@RunWith(Enclosed.class)
public class LoggerTest {

  public final ExpectedException thrown = ExpectedException.none();
  public final MockWebServer server = new MockWebServer();
  public final RecordingLogger logger = new RecordingLogger();

  /** Ensure expected exception handling is done before logger rule. */
  @Rule
  public final RuleChain chain = RuleChain.outerRule(server).around(logger).around(thrown);


  interface SendsStuff {

    @RequestLine("POST /")
    @Headers("Content-Type: application/json")
    @Body("%7B\"customer_name\": \"{customer_name}\", \"user_name\": \"{user_name}\", \"password\": \"{password}\"%7D")
    String login(
                 @Param("customer_name") String customer,
                 @Param("user_name") String user,
                 @Param("password") String password);
  }

  @RunWith(Parameterized.class)
  public static class LogLevelEmitsTest extends LoggerTest {

    private final Level logLevel;

    public LogLevelEmitsTest(Level logLevel, List<String> expectedMessages) {
      this.logLevel = logLevel;
      logger.expectMessages(expectedMessages);
    }

    @Parameters
    public static Iterable<Object[]> data() {
      return Arrays.asList(new Object[][] {
          {Level.NONE, Arrays.asList()},
          {Level.BASIC, Arrays.asList(
              "\\[SendsStuff#login\\] ---> POST http://localhost:[0-9]+/ HTTP/1.1",
              "\\[SendsStuff#login\\] <--- HTTP/1.1 200 OK \\([0-9]+ms\\)")},
          {Level.HEADERS, Arrays.asList(
              "\\[SendsStuff#login\\] ---> POST http://localhost:[0-9]+/ HTTP/1.1",
              "\\[SendsStuff#login\\] Content-Length: 80",
              "\\[SendsStuff#login\\] Content-Type: application/json",
              "\\[SendsStuff#login\\] ---> END HTTP \\(80-byte body\\)",
              "\\[SendsStuff#login\\] <--- HTTP/1.1 200 OK \\([0-9]+ms\\)",
              "\\[SendsStuff#login\\] content-length: 3",
              "\\[SendsStuff#login\\] <--- END HTTP \\(3-byte body\\)")},
          {Level.REQUEST_FULL, Arrays.asList(
              "\\[SendsStuff#login\\] ---> POST http://localhost:[0-9]+/ HTTP/1.1",
              "\\[SendsStuff#login\\] Content-Length: 80",
              "\\[SendsStuff#login\\] Content-Type: application/json",
              "\\[SendsStuff#login\\] ",
              "\\[SendsStuff#login\\] \\{\"customer_name\": \"netflix\", \"user_name\": \"denominator\", \"password\": \"password\"\\}",
              "\\[SendsStuff#login\\] ---> END HTTP \\(80-byte body\\)",
              "\\[SendsStuff#login\\] <--- HTTP/1.1 200 OK \\([0-9]+ms\\)",
              "\\[SendsStuff#login\\] content-length: 3",
              "\\[SendsStuff#login\\] <--- END HTTP \\(3-byte body\\)")},
          {Level.RESPONSE_FULL, Arrays.asList(
              "\\[SendsStuff#login\\] ---> POST http://localhost:[0-9]+/ HTTP/1.1",
              "\\[SendsStuff#login\\] Content-Length: 80",
              "\\[SendsStuff#login\\] Content-Type: application/json",
              "\\[SendsStuff#login\\] ---> END HTTP \\(80-byte body\\)",
              "\\[SendsStuff#login\\] <--- HTTP/1.1 200 OK \\([0-9]+ms\\)",
              "\\[SendsStuff#login\\] content-length: 3",
              "\\[SendsStuff#login\\] ",
              "\\[SendsStuff#login\\] foo",
              "\\[SendsStuff#login\\] <--- END HTTP \\(3-byte body\\)")},
          {Level.FULL, Arrays.asList(
              "\\[SendsStuff#login\\] ---> POST http://localhost:[0-9]+/ HTTP/1.1",
              "\\[SendsStuff#login\\] Content-Length: 80",
              "\\[SendsStuff#login\\] Content-Type: application/json",
              "\\[SendsStuff#login\\] ",
              "\\[SendsStuff#login\\] \\{\"customer_name\": \"netflix\", \"user_name\": \"denominator\", \"password\": \"password\"\\}",
              "\\[SendsStuff#login\\] ---> END HTTP \\(80-byte body\\)",
              "\\[SendsStuff#login\\] <--- HTTP/1.1 200 OK \\([0-9]+ms\\)",
              "\\[SendsStuff#login\\] content-length: 3",
              "\\[SendsStuff#login\\] ",
              "\\[SendsStuff#login\\] foo",
              "\\[SendsStuff#login\\] <--- END HTTP \\(3-byte body\\)")}
      });
    }

    @Test
    public void levelEmits() throws IOException, InterruptedException {
      server.enqueue(new MockResponse().setBody("foo"));

      SendsStuff api = Feign.builder()
          .logger(logger)
          .logLevel(logLevel)
          .target(SendsStuff.class, "http://localhost:" + server.getPort());

      api.login("netflix", "denominator", "password");
    }
  }

  @RunWith(Parameterized.class)
  public static class ReasonPhraseOptional extends LoggerTest {

    private final Level logLevel;

    public ReasonPhraseOptional(Level logLevel, List<String> expectedMessages) {
      this.logLevel = logLevel;
      logger.expectMessages(expectedMessages);
    }

    @Parameters
    public static Iterable<Object[]> data() {
      return Arrays.asList(new Object[][] {
          {Level.BASIC, Arrays.asList(
              "\\[SendsStuff#login\\] ---> POST http://localhost:[0-9]+/ HTTP/1.1",
              "\\[SendsStuff#login\\] <--- HTTP/1.1 200 \\([0-9]+ms\\)")},
      });
    }

    @Test
    public void reasonPhraseOptional() throws IOException, InterruptedException {
      server.enqueue(new MockResponse().setStatus("HTTP/1.1 " + 200));

      SendsStuff api = Feign.builder()
          .logger(logger)
          .logLevel(logLevel)
          .target(SendsStuff.class, "http://localhost:" + server.getPort());

      api.login("netflix", "denominator", "password");
    }
  }

  @RunWith(Parameterized.class)
  public static class ReadTimeoutEmitsTest extends LoggerTest {

    private final Level logLevel;

    public ReadTimeoutEmitsTest(Level logLevel, List<String> expectedMessages) {
      this.logLevel = logLevel;
      logger.expectMessages(expectedMessages);
    }

    @Parameters
    public static Iterable<Object[]> data() {
      return Arrays.asList(new Object[][] {
          {Level.NONE, Arrays.asList()},
          {Level.BASIC, Arrays.asList(
              "\\[SendsStuff#login\\] ---> POST http://localhost:[0-9]+/ HTTP/1.1",
              "\\[SendsStuff#login\\] <--- ERROR SocketTimeoutException: Read timed out \\([0-9]+ms\\)")},
          {Level.HEADERS, Arrays.asList(
              "\\[SendsStuff#login\\] ---> POST http://localhost:[0-9]+/ HTTP/1.1",
              "\\[SendsStuff#login\\] Content-Length: 80",
              "\\[SendsStuff#login\\] Content-Type: application/json",
              "\\[SendsStuff#login\\] ---> END HTTP \\(80-byte body\\)",
              "\\[SendsStuff#login\\] <--- ERROR SocketTimeoutException: Read timed out \\([0-9]+ms\\)")},
          {Level.REQUEST_FULL, Arrays.asList(
              "\\[SendsStuff#login\\] ---> POST http://localhost:[0-9]+/ HTTP/1.1",
              "\\[SendsStuff#login\\] Content-Length: 80",
              "\\[SendsStuff#login\\] Content-Type: application/json",
              "\\[SendsStuff#login\\] ",
              "\\[SendsStuff#login\\] \\{\"customer_name\": \"netflix\", \"user_name\": \"denominator\", \"password\": \"password\"\\}",
              "\\[SendsStuff#login\\] ---> END HTTP \\(80-byte body\\)",
              "\\[SendsStuff#login\\] <--- ERROR SocketTimeoutException: Read timed out \\([0-9]+ms\\)",
              "(?s)\\[SendsStuff#login\\] java.net.SocketTimeoutException: Read timed out.*",
              "\\[SendsStuff#login\\] <--- END ERROR")},
          {Level.RESPONSE_FULL, Arrays.asList(
              "\\[SendsStuff#login\\] ---> POST http://localhost:[0-9]+/ HTTP/1.1",
              "\\[SendsStuff#login\\] Content-Length: 80",
              "\\[SendsStuff#login\\] Content-Type: application/json",
              "\\[SendsStuff#login\\] ---> END HTTP \\(80-byte body\\)",
              "\\[SendsStuff#login\\] <--- ERROR SocketTimeoutException: Read timed out \\([0-9]+ms\\)",
              "(?s)\\[SendsStuff#login\\] java.net.SocketTimeoutException: Read timed out.*",
              "\\[SendsStuff#login\\] <--- END ERROR")},
          {Level.FULL, Arrays.asList(
              "\\[SendsStuff#login\\] ---> POST http://localhost:[0-9]+/ HTTP/1.1",
              "\\[SendsStuff#login\\] Content-Length: 80",
              "\\[SendsStuff#login\\] Content-Type: application/json",
              "\\[SendsStuff#login\\] ",
              "\\[SendsStuff#login\\] \\{\"customer_name\": \"netflix\", \"user_name\": \"denominator\", \"password\": \"password\"\\}",
              "\\[SendsStuff#login\\] ---> END HTTP \\(80-byte body\\)",
              "\\[SendsStuff#login\\] <--- ERROR SocketTimeoutException: Read timed out \\([0-9]+ms\\)",
              "(?s)\\[SendsStuff#login\\] java.net.SocketTimeoutException: Read timed out.*",
              "\\[SendsStuff#login\\] <--- END ERROR")}
      });
    }

    @Test
    public void levelEmitsOnReadTimeout() throws IOException, InterruptedException {
      server.enqueue(new MockResponse().throttleBody(1, 1, TimeUnit.SECONDS).setBody("foo"));
      thrown.expect(FeignException.class);

      SendsStuff api = Feign.builder()
          .logger(logger)
          .logLevel(logLevel)
          .options(new Request.Options(10 * 1000, 50))
          .retryer(new Retryer() {
            @Override
            public void continueOrPropagate(RetryableException e) {
              throw e;
            }

            @Override
            public Retryer clone() {
              return this;
            }
          })
          .target(SendsStuff.class, "http://localhost:" + server.getPort());

      api.login("netflix", "denominator", "password");
    }
  }

  @RunWith(Parameterized.class)
  public static class UnknownHostEmitsTest extends LoggerTest {

    private final Level logLevel;

    public UnknownHostEmitsTest(Level logLevel, List<String> expectedMessages) {
      this.logLevel = logLevel;
      logger.expectMessages(expectedMessages);
    }

    @Parameters
    public static Iterable<Object[]> data() {
      return Arrays.asList(new Object[][] {
          {Level.NONE, Arrays.asList()},
          {Level.BASIC, Arrays.asList(
              "\\[SendsStuff#login\\] ---> POST http://robofu.abc/ HTTP/1.1",
              "\\[SendsStuff#login\\] <--- ERROR UnknownHostException: robofu.abc \\([0-9]+ms\\)")},
          {Level.HEADERS, Arrays.asList(
              "\\[SendsStuff#login\\] ---> POST http://robofu.abc/ HTTP/1.1",
              "\\[SendsStuff#login\\] Content-Length: 80",
              "\\[SendsStuff#login\\] Content-Type: application/json",
              "\\[SendsStuff#login\\] ---> END HTTP \\(80-byte body\\)",
              "\\[SendsStuff#login\\] <--- ERROR UnknownHostException: robofu.abc \\([0-9]+ms\\)")},
          {Level.REQUEST_FULL, Arrays.asList(
              "\\[SendsStuff#login\\] ---> POST http://robofu.abc/ HTTP/1.1",
              "\\[SendsStuff#login\\] Content-Length: 80",
              "\\[SendsStuff#login\\] Content-Type: application/json",
              "\\[SendsStuff#login\\] ",
              "\\[SendsStuff#login\\] \\{\"customer_name\": \"netflix\", \"user_name\": \"denominator\", \"password\": \"password\"\\}",
              "\\[SendsStuff#login\\] ---> END HTTP \\(80-byte body\\)",
              "\\[SendsStuff#login\\] <--- ERROR UnknownHostException: robofu.abc \\([0-9]+ms\\)",
              "(?s)\\[SendsStuff#login\\] java.net.UnknownHostException: robofu.abc.*",
              "\\[SendsStuff#login\\] <--- END ERROR")},
          {Level.RESPONSE_FULL, Arrays.asList(
              "\\[SendsStuff#login\\] ---> POST http://robofu.abc/ HTTP/1.1",
              "\\[SendsStuff#login\\] Content-Length: 80",
              "\\[SendsStuff#login\\] Content-Type: application/json",
              "\\[SendsStuff#login\\] ---> END HTTP \\(80-byte body\\)",
              "\\[SendsStuff#login\\] <--- ERROR UnknownHostException: robofu.abc \\([0-9]+ms\\)",
              "(?s)\\[SendsStuff#login\\] java.net.UnknownHostException: robofu.abc.*",
              "\\[SendsStuff#login\\] <--- END ERROR")},
          {Level.FULL, Arrays.asList(
              "\\[SendsStuff#login\\] ---> POST http://robofu.abc/ HTTP/1.1",
              "\\[SendsStuff#login\\] Content-Length: 80",
              "\\[SendsStuff#login\\] Content-Type: application/json",
              "\\[SendsStuff#login\\] ",
              "\\[SendsStuff#login\\] \\{\"customer_name\": \"netflix\", \"user_name\": \"denominator\", \"password\": \"password\"\\}",
              "\\[SendsStuff#login\\] ---> END HTTP \\(80-byte body\\)",
              "\\[SendsStuff#login\\] <--- ERROR UnknownHostException: robofu.abc \\([0-9]+ms\\)",
              "(?s)\\[SendsStuff#login\\] java.net.UnknownHostException: robofu.abc.*",
              "\\[SendsStuff#login\\] <--- END ERROR")}
      });
    }

    @Test
    public void unknownHostEmits() throws IOException, InterruptedException {
      SendsStuff api = Feign.builder()
          .logger(logger)
          .logLevel(logLevel)
          .retryer(new Retryer() {
            @Override
            public void continueOrPropagate(RetryableException e) {
              throw e;
            }

            @Override
            public Retryer clone() {
              return this;
            }
          })
          .target(SendsStuff.class, "http://robofu.abc");

      thrown.expect(FeignException.class);

      api.login("netflix", "denominator", "password");
    }
  }


  @RunWith(Parameterized.class)
  public static class FormatCharacterTest
      extends LoggerTest {

    private final Level logLevel;

    public FormatCharacterTest(Level logLevel, List<String> expectedMessages) {
      this.logLevel = logLevel;
      logger.expectMessages(expectedMessages);
    }

    @Parameters
    public static Iterable<Object[]> data() {
      return Arrays.asList(new Object[][] {
          {Level.NONE, Arrays.asList()},
          {Level.BASIC, Arrays.asList(
              "\\[SendsStuff#login\\] ---> POST http://sna%fu.abc/ HTTP/1.1",
              "\\[SendsStuff#login\\] <--- ERROR UnknownHostException: sna%fu.abc \\([0-9]+ms\\)")},
          {Level.HEADERS, Arrays.asList(
              "\\[SendsStuff#login\\] ---> POST http://sna%fu.abc/ HTTP/1.1",
              "\\[SendsStuff#login\\] Content-Length: 80",
              "\\[SendsStuff#login\\] Content-Type: application/json",
              "\\[SendsStuff#login\\] ---> END HTTP \\(80-byte body\\)",
              "\\[SendsStuff#login\\] <--- ERROR UnknownHostException: sna%fu.abc \\([0-9]+ms\\)")},
          {Level.REQUEST_FULL, Arrays.asList(
              "\\[SendsStuff#login\\] ---> POST http://sna%fu.abc/ HTTP/1.1",
              "\\[SendsStuff#login\\] Content-Length: 80",
              "\\[SendsStuff#login\\] Content-Type: application/json",
              "\\[SendsStuff#login\\] ",
              "\\[SendsStuff#login\\] \\{\"customer_name\": \"netflix\", \"user_name\": \"denominator\", \"password\": \"password\"\\}",
              "\\[SendsStuff#login\\] ---> END HTTP \\(80-byte body\\)",
              "\\[SendsStuff#login\\] <--- ERROR UnknownHostException: sna%fu.abc \\([0-9]+ms\\)",
              "(?s)\\[SendsStuff#login\\] java.net.UnknownHostException: sna%fu.abc.*",
              "\\[SendsStuff#login\\] <--- END ERROR")},
          {Level.RESPONSE_FULL, Arrays.asList(
              "\\[SendsStuff#login\\] ---> POST http://sna%fu.abc/ HTTP/1.1",
              "\\[SendsStuff#login\\] Content-Length: 80",
              "\\[SendsStuff#login\\] Content-Type: application/json",
              "\\[SendsStuff#login\\] ---> END HTTP \\(80-byte body\\)",
              "\\[SendsStuff#login\\] <--- ERROR UnknownHostException: sna%fu.abc \\([0-9]+ms\\)",
              "(?s)\\[SendsStuff#login\\] java.net.UnknownHostException: sna%fu.abc.*",
              "\\[SendsStuff#login\\] <--- END ERROR")},
          {Level.FULL, Arrays.asList(
              "\\[SendsStuff#login\\] ---> POST http://sna%fu.abc/ HTTP/1.1",
              "\\[SendsStuff#login\\] Content-Length: 80",
              "\\[SendsStuff#login\\] Content-Type: application/json",
              "\\[SendsStuff#login\\] ",
              "\\[SendsStuff#login\\] \\{\"customer_name\": \"netflix\", \"user_name\": \"denominator\", \"password\": \"password\"\\}",
              "\\[SendsStuff#login\\] ---> END HTTP \\(80-byte body\\)",
              "\\[SendsStuff#login\\] <--- ERROR UnknownHostException: sna%fu.abc \\([0-9]+ms\\)",
              "(?s)\\[SendsStuff#login\\] java.net.UnknownHostException: sna%fu.abc.*",
              "\\[SendsStuff#login\\] <--- END ERROR")}
      });
    }

    @Test
    public void formatCharacterEmits() throws IOException, InterruptedException {
      SendsStuff api = Feign.builder()
          .logger(logger)
          .logLevel(logLevel)
          .retryer(new Retryer() {
            @Override
            public void continueOrPropagate(RetryableException e) {
              throw e;
            }

            @Override
            public Retryer clone() {
              return this;
            }
          })
          .target(SendsStuff.class, "http://sna%25fu.abc");

      thrown.expect(FeignException.class);

      api.login("netflix", "denominator", "password");
    }
  }


  @RunWith(Parameterized.class)
  public static class RetryEmitsTest extends LoggerTest {

    private final Level logLevel;

    public RetryEmitsTest(Level logLevel, List<String> expectedMessages) {
      this.logLevel = logLevel;
      logger.expectMessages(expectedMessages);
    }

    @Parameters
    public static Iterable<Object[]> data() {
      return Arrays.asList(new Object[][] {
          {Level.NONE, Arrays.asList()},
          {Level.BASIC, Arrays.asList(
              "\\[SendsStuff#login\\] ---> POST http://robofu.abc/ HTTP/1.1",
              "\\[SendsStuff#login\\] <--- ERROR UnknownHostException: robofu.abc \\([0-9]+ms\\)",
              "\\[SendsStuff#login\\] ---> RETRYING",
              "\\[SendsStuff#login\\] ---> POST http://robofu.abc/ HTTP/1.1",
              "\\[SendsStuff#login\\] <--- ERROR UnknownHostException: robofu.abc \\([0-9]+ms\\)")}
      });
    }

    @Test
    public void retryEmits() throws IOException, InterruptedException {
      thrown.expect(FeignException.class);

      SendsStuff api = Feign.builder()
          .logger(logger)
          .logLevel(logLevel)
          .retryer(new Retryer() {
            boolean retried;

            @Override
            public void continueOrPropagate(RetryableException e) {
              if (!retried) {
                retried = true;
                return;
              }
              throw e;
            }

            @Override
            public Retryer clone() {
              return this;
            }
          })
          .target(SendsStuff.class, "http://robofu.abc");

      api.login("netflix", "denominator", "password");
    }
  }

  private static final class RecordingLogger extends Logger implements TestRule {

    private final List<String> messages = new ArrayList<String>();
    private final List<String> expectedMessages = new ArrayList<String>();

    RecordingLogger expectMessages(List<String> expectedMessages) {
      this.expectedMessages.addAll(expectedMessages);
      return this;
    }

    @Override
    protected void log(String configKey, String format, Object... args) {
      messages.add(methodTag(configKey) + String.format(format, args));
    }

    @Override
    public Statement apply(final Statement base, Description description) {
      return new Statement() {
        @Override
        public void evaluate() throws Throwable {
          base.evaluate();
          SoftAssertions softly = new SoftAssertions();
          softly.assertThat(messages.size()).isEqualTo(expectedMessages.size());
          for (int i = 0; i < messages.size() && i < expectedMessages.size(); i++) {
            softly.assertThat(messages.get(i)).matches(expectedMessages.get(i));
          }
          softly.assertAll();
        }
      };
    }
  }
}
