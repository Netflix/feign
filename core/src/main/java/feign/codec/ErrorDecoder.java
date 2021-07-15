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
package feign.codec;

import static feign.FeignException.errorStatus;
import static feign.Util.RETRY_AFTER;
import static feign.Util.checkNotNull;
import static java.util.Locale.US;
import static java.util.concurrent.TimeUnit.SECONDS;
import feign.FeignException;
import feign.Response;
import feign.RetryableException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

/**
 * Allows you to massage an exception into a application-specific one. Converting out to a throttle
 * exception are examples of this in use.
 *
 * <p/>
 * Ex:
 *
 * <pre>
 * class IllegalArgumentExceptionOn404Decoder implements ErrorDecoder {
 *
 *   &#064;Override
 *   public Exception decode(String methodKey, Response response) {
 *     if (response.status() == 400)
 *       throw new IllegalArgumentException(&quot;bad zone name&quot;);
 *     return new ErrorDecoder.Default().decode(methodKey, response);
 *   }
 *
 * }
 * </pre>
 *
 * <p/>
 * <b>Error handling</b>
 *
 * <p/>
 * Responses where {@link Response#status()} is not in the 2xx range are classified as errors,
 * addressed by the {@link ErrorDecoder}. That said, certain RPC apis return errors defined in the
 * {@link Response#body()} even on a 200 status. For example, in the DynECT api, a job still running
 * condition is returned with a 200 status, encoded in json. When scenarios like this occur, you
 * should raise an application-specific exception (which may be {@link feign.RetryableException
 * retryable}).
 *
 * <p/>
 * <b>Not Found Semantics</b>
 * <p/>
 * It is commonly the case that 404 (Not Found) status has semantic value in HTTP apis. While the
 * default behavior is to raise exeception, users can alternatively enable 404 processing via
 * {@link feign.Feign.Builder#decode404()}.
 */
public interface ErrorDecoder {

  /**
   * Implement this method in order to decode an HTTP {@link Response} when
   * {@link Response#status()} is not in the 2xx range. Please raise application-specific exceptions
   * where possible. If your exception is retryable, wrap or subclass {@link RetryableException}
   *
   * @param methodKey {@link feign.Feign#configKey} of the java method that invoked the request. ex.
   *        {@code IAM#getUser()}
   * @param response HTTP response where {@link Response#status() status} is greater than or equal
   *        to {@code 300}.
   * @return Exception IOException, if there was a network error reading the response or an
   *         application-specific exception decoded by the implementation. If the throwable is
   *         retryable, it should be wrapped, or a subtype of {@link RetryableException}
   */
  public Exception decode(String methodKey, Response response);

  public static class Default implements ErrorDecoder {

    private final RetryAfterDecoder retryAfterDecoder = new RetryAfterDecoder();

    private final XRateLimitResetDecoder xRateLimitResetDecoder = new XRateLimitResetDecoder();

    @Override
    public Exception decode(String methodKey, Response response) {
      FeignException exception = errorStatus(methodKey, response);
      Date retryAfter = retryAfterDecoder.apply(firstOrNull(response.headers(), RETRY_AFTER));
      if (retryAfter == null && exception instanceof FeignException.TooManyRequests) {
        retryAfter = xRateLimitResetDecoder.apply(firstOrNull(response.headers(), X_RATE_LIMIT_RESET));
      }
      if (retryAfter != null) {
        return new RetryableException(
            response.status(),
            exception.getMessage(),
            response.request().httpMethod(),
            exception,
            retryAfter,
            response.request());
      }
      return exception;
    }

    private <T> T firstOrNull(Map<String, Collection<T>> map, String key) {
      if (map.containsKey(key) && !map.get(key).isEmpty()) {
        return map.get(key).iterator().next();
      }
      return null;
    }
  }

  /**
   * Decodes a {@link feign.Util#RETRY_AFTER} header into an absolute date, if possible. <br>
   * See <a href="https://tools.ietf.org/html/rfc2616#section-14.37">Retry-After format</a>
   */
  static class RetryAfterDecoder {

    static final DateFormat RFC822_FORMAT =
        new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", US);
    private final DateFormat rfc822Format;

    RetryAfterDecoder() {
      this(RFC822_FORMAT);
    }

    RetryAfterDecoder(DateFormat rfc822Format) {
      this.rfc822Format = checkNotNull(rfc822Format, "rfc822Format");
    }

    protected long currentTimeMillis() {
      return System.currentTimeMillis();
    }

    /**
     * returns a date that corresponds to the first time a request can be retried.
     *
     * @param retryAfter String in
     *        <a href="https://tools.ietf.org/html/rfc2616#section-14.37" >Retry-After format</a>
     */
    public Date apply(String retryAfter) {
      if (retryAfter == null) {
        return null;
      }
      if (retryAfter.matches("^[0-9]+\\.?0*$")) {
        retryAfter = retryAfter.replaceAll("\\.0*$", "");
        long deltaMillis = SECONDS.toMillis(Long.parseLong(retryAfter));
        return new Date(currentTimeMillis() + deltaMillis);
      }
      synchronized (rfc822Format) {
        try {
          return rfc822Format.parse(retryAfter);
        } catch (ParseException ignored) {
          return null;
        }
      }
    }
  }

  /**
   * Parses a {@link feign.Util#X_RATE_LIMIT_RESET} header into an absolute date, if possible.
   *
   * Some services reports the number of seconds until the rate limit window is reset, others
   * report the number of seconds since the epoch. Assumes that the seconds will be less than 24 hours in the former case
   * or within +/- 24 hours from current time in the latter case.
   */
  static class XRateLimitResetDecoder {

    public static final long ONE_DAY_MILLIS = 24 * 60 * 60 * 1000;

    protected long currentTimeMillis() {
      return System.currentTimeMillis();
    }

    public Date apply(String rateLimitReset) {
      if (rateLimitReset == null) {
        return null;
      }
      try {
        long millis = Long.parseLong(rateLimitReset) * 1000;

        if (millis > currentTimeMillis() - ONE_DAY_MILLIS && millis < currentTimeMillis() + ONE_DAY_MILLIS) {
          return new Date(millis);
        } else if (millis < ONE_DAY_MILLIS) {
          return new Date(currentTimeMillis() + millis);
        } else {
          return null;
        }
      } catch (NumberFormatException e) {
        return null;
      }
    }
  }
}
