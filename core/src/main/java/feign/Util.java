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

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import static java.lang.String.format;

/**
 * Utilities, typically copied in from guava, so as to avoid dependency conflicts.
 */
public class Util {

  /**
   * The HTTP Content-Length header field name.
   */
  public static final String CONTENT_LENGTH = "Content-Length";
  /**
   * The HTTP Content-Encoding header field name.
   */
  public static final String CONTENT_ENCODING = "Content-Encoding";
  /**
   * The HTTP Retry-After header field name.
   */
  public static final String RETRY_AFTER = "Retry-After";
  /**
   * Value for the Content-Encoding header that indicates that GZIP encoding is in use.
   */
  public static final String ENCODING_GZIP = "gzip";
  /**
   * Value for the Content-Encoding header that indicates that DEFLATE encoding is in use.
   */
  public static final String ENCODING_DEFLATE = "deflate";
  /**
   * UTF-8: eight-bit UCS Transformation Format.
   */
  public static final Charset UTF_8 = Charset.forName("UTF-8");

  // com.google.common.base.Charsets
  /**
   * ISO-8859-1: ISO Latin Alphabet Number 1 (ISO-LATIN-1).
   */
  public static final Charset ISO_8859_1 = Charset.forName("ISO-8859-1");
  private static final int BUF_SIZE = 0x800; // 2K chars (4K bytes)


  /**
   * Type literal for {@code Map<String, ?>}.
   */
  public static final Type MAP_STRING_WILDCARD =
      new Types.ParameterizedTypeImpl(null, Map.class, String.class,
          new Types.WildcardTypeImpl(new Type[] {Object.class}, new Type[0]));

  private Util() { // no instances
  }

  /**
   * Copy of {@code com.google.common.base.Preconditions#checkArgument}.
   */
  public static void checkArgument(boolean expression,
                                   String errorMessageTemplate,
                                   Object... errorMessageArgs) {
    if (!expression) {
      throw new IllegalArgumentException(
          format(errorMessageTemplate, errorMessageArgs));
    }
  }

  /**
   * Copy of {@code com.google.common.base.Preconditions#checkNotNull}.
   */
  public static <T> T checkNotNull(T reference,
                                   String errorMessageTemplate,
                                   Object... errorMessageArgs) {
    if (reference == null) {
      // If either of these parameters is null, the right thing happens anyway
      throw new NullPointerException(
          format(errorMessageTemplate, errorMessageArgs));
    }
    return reference;
  }

  /**
   * Copy of {@code com.google.common.base.Preconditions#checkState}.
   */
  public static void checkState(boolean expression,
                                String errorMessageTemplate,
                                Object... errorMessageArgs) {
    if (!expression) {
      throw new IllegalStateException(
          format(errorMessageTemplate, errorMessageArgs));
    }
  }

  /**
   * Identifies a method as a default instance method.
   */
  public static boolean isDefault(Method method) {
    // Default methods are public non-abstract, non-synthetic, and non-static instance methods
    // declared in an interface.
    // method.isDefault() is not sufficient for our usage as it does not check
    // for synthetic methods. As a result, it picks up overridden methods as well as actual default
    // methods.
    final int SYNTHETIC = 0x00001000;
    return ((method.getModifiers()
        & (Modifier.ABSTRACT | Modifier.PUBLIC | Modifier.STATIC | SYNTHETIC)) == Modifier.PUBLIC)
        && method.getDeclaringClass().isInterface();
  }

  /**
   * Adapted from {@code com.google.common.base.Strings#emptyToNull}.
   */
  public static String emptyToNull(String string) {
    return string == null || string.isEmpty() ? null : string;
  }

  /**
   * Removes values from the array that meet the criteria for removal via the supplied
   * {@link Predicate} value
   */
  @SuppressWarnings("unchecked")
  public static <T> T[] removeValues(T[] values, Predicate<T> shouldRemove, Class<T> type) {
    Collection<T> collection = new ArrayList<>(values.length);
    for (T value : values) {
      if (shouldRemove.negate().test(value)) {
        collection.add(value);
      }
    }
    T[] array = (T[]) Array.newInstance(type, collection.size());
    return collection.toArray(array);
  }

  /**
   * Adapted from {@code com.google.common.base.Strings#emptyToNull}.
   */
  @SuppressWarnings("unchecked")
  public static <T> T[] toArray(Iterable<? extends T> iterable, Class<T> type) {
    Collection<T> collection;
    if (iterable instanceof Collection) {
      collection = (Collection<T>) iterable;
    } else {
      collection = new ArrayList<T>();
      for (T element : iterable) {
        collection.add(element);
      }
    }
    T[] array = (T[]) Array.newInstance(type, collection.size());
    return collection.toArray(array);
  }

  /**
   * Returns an unmodifiable collection which may be empty, but is never null.
   */
  public static <T> Collection<T> valuesOrEmpty(Map<String, Collection<T>> map, String key) {
    Collection<T> values = map.get(key);
    return values != null ? values : Collections.emptyList();
  }

  public static void ensureClosed(Closeable closeable) {
    if (closeable != null) {
      try {
        closeable.close();
      } catch (IOException ignored) { // NOPMD
      }
    }
  }

  /**
   * Resolves the last type parameter of the parameterized {@code supertype}, based on the {@code
   * genericContext}, into its upper bounds.
   * <p/>
   * Implementation copied from {@code
   * retrofit.RestMethodInfo}.
   *
   * @param genericContext Ex. {@link java.lang.reflect.Field#getGenericType()}
   * @param supertype Ex. {@code Decoder.class}
   * @return in the example above, the type parameter of {@code Decoder}.
   * @throws IllegalStateException if {@code supertype} cannot be resolved into a parameterized type
   *         using {@code context}.
   */
  public static Type resolveLastTypeParameter(Type genericContext, Class<?> supertype)
      throws IllegalStateException {
    Type resolvedSuperType =
        Types.getSupertype(genericContext, Types.getRawType(genericContext), supertype);
    checkState(resolvedSuperType instanceof ParameterizedType,
        "could not resolve %s into a parameterized type %s",
        genericContext, supertype);
    Type[] types = ParameterizedType.class.cast(resolvedSuperType).getActualTypeArguments();
    for (int i = 0; i < types.length; i++) {
      Type type = types[i];
      if (type instanceof WildcardType) {
        types[i] = ((WildcardType) type).getUpperBounds()[0];
      }
    }
    return types[types.length - 1];
  }

  /**
   * This returns well known empty values for well-known java types. This returns null for types not
   * in the following list.
   *
   * <ul>
   * <li>{@code [Bb]oolean}</li>
   * <li>{@code byte[]}</li>
   * <li>{@code Collection}</li>
   * <li>{@code Iterator}</li>
   * <li>{@code List}</li>
   * <li>{@code Map}</li>
   * <li>{@code Set}</li>
   * </ul>
   *
   * <p/>
   * When {@link Feign.Builder#decode404() decoding HTTP 404 status}, you'll need to teach decoders
   * a default empty value for a type. This method cheaply supports typical types by only looking at
   * the raw type (vs type hierarchy). Decorate for sophistication.
   */
  public static Object emptyValueOf(Type type) {
    return EMPTIES.getOrDefault(Types.getRawType(type), () -> null).get();
  }

  private static final Map<Class<?>, Supplier<Object>> EMPTIES;
  static {
    final Map<Class<?>, Supplier<Object>> empties = new LinkedHashMap<Class<?>, Supplier<Object>>();
    empties.put(boolean.class, () -> false);
    empties.put(Boolean.class, () -> false);
    empties.put(byte[].class, () -> new byte[0]);
    empties.put(Collection.class, Collections::emptyList);
    empties.put(Iterator.class, Collections::emptyIterator);
    empties.put(List.class, Collections::emptyList);
    empties.put(Map.class, Collections::emptyMap);
    empties.put(Set.class, Collections::emptySet);
    empties.put(Optional.class, Optional::empty);
    empties.put(Stream.class, Stream::empty);
    EMPTIES = Collections.unmodifiableMap(empties);
  }

  /**
   * Adapted from {@code com.google.common.io.CharStreams.toString()}.
   */
  public static String toString(Reader reader) throws IOException {
    if (reader == null) {
      return null;
    }
    try {
      StringBuilder to = new StringBuilder();
      CharBuffer charBuf = CharBuffer.allocate(BUF_SIZE);
      // must cast to super class Buffer otherwise break when running with java 11
      Buffer buf = charBuf;
      while (reader.read(charBuf) != -1) {
        buf.flip();
        to.append(charBuf);
        buf.clear();
      }
      return to.toString();
    } finally {
      ensureClosed(reader);
    }
  }

  /**
   * Adapted from {@code com.google.common.io.ByteStreams.toByteArray()}.
   */
  public static byte[] toByteArray(InputStream in) throws IOException {
    checkNotNull(in, "in");
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      copy(in, out);
      return out.toByteArray();
    } finally {
      ensureClosed(in);
    }
  }

  /**
   * Adapted from {@code com.google.common.io.ByteStreams.copy()}.
   */
  private static long copy(InputStream from, OutputStream to)
      throws IOException {
    checkNotNull(from, "from");
    checkNotNull(to, "to");
    byte[] buf = new byte[BUF_SIZE];
    long total = 0;
    while (true) {
      int r = from.read(buf);
      if (r == -1) {
        break;
      }
      to.write(buf, 0, r);
      total += r;
    }
    return total;
  }

  public static String decodeOrDefault(byte[] data, Charset charset, String defaultValue) {
    if (data == null) {
      return defaultValue;
    }
    checkNotNull(charset, "charset");
    try {
      return charset.newDecoder().decode(ByteBuffer.wrap(data)).toString();
    } catch (CharacterCodingException ex) {
      return defaultValue;
    }
  }

  /**
   * If the provided String is not null or empty.
   *
   * @param value to evaluate.
   * @return true of the value is not null and not empty.
   */
  public static boolean isNotBlank(String value) {
    return value != null && !value.isEmpty();
  }

  /**
   * If the provided String is null or empty.
   *
   * @param value to evaluate.
   * @return true if the value is null or empty.
   */
  public static boolean isBlank(String value) {
    return value == null || value.isEmpty();
  }

  /**
   * Copy entire map of string collection.
   *
   * The copy is unmodifiable map of unmodifiable collections.
   *
   * @param map string collection map
   * @return copy of the map or an empty map if the map is null.
   */
  public static Map<String, Collection<String>> caseInsensitiveCopyOf(Map<String, Collection<String>> map) {
    if (map == null) {
      return Collections.emptyMap();
    }

    Map<String, Collection<String>> result =
        new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    for (Map.Entry<String, Collection<String>> entry : map.entrySet()) {
      String key = entry.getKey();
      if (!result.containsKey(key)) {
        result.put(key.toLowerCase(Locale.ROOT), new LinkedList<>());
      }
      result.get(key).addAll(entry.getValue());
    }
    result.replaceAll((key, value) -> Collections.unmodifiableCollection(value));

    return Collections.unmodifiableMap(result);
  }

}
