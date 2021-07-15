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

import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.junit.Test;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.*;
import feign.codec.Decoder;
import static feign.Util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class UtilTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void removesEmptyStrings() {
    String[] values = new String[] {"", null};
    assertThat(removeValues(values, (value) -> emptyToNull(value) == null, String.class))
        .isEmpty();
  }

  @Test
  public void removesEvenNumbers() {
    Integer[] values = new Integer[] {22, 23};
    assertThat(removeValues(values, (number) -> number % 2 == 0, Integer.class))
        .containsExactly(23);
  }

  @Test
  public void emptyValueOf() throws Exception {
    assertEquals(false, Util.emptyValueOf(boolean.class));
    assertEquals(false, Util.emptyValueOf(Boolean.class));
    assertThat((byte[]) Util.emptyValueOf(byte[].class)).isEmpty();
    assertEquals(Collections.emptyList(), Util.emptyValueOf(Collection.class));
    assertThat((Iterator<?>) Util.emptyValueOf(Iterator.class)).isEmpty();
    assertEquals(Collections.emptyList(), Util.emptyValueOf(List.class));
    assertEquals(Collections.emptyMap(), Util.emptyValueOf(Map.class));
    assertEquals(Collections.emptySet(), Util.emptyValueOf(Set.class));
    assertEquals(Optional.empty(), Util.emptyValueOf(Optional.class));
  }

  /** In other words, {@code List<String>} is as empty as {@code List<?>}. */
  @Test
  public void emptyValueOf_considersRawType() throws Exception {
    Type listStringType = LastTypeParameter.class.getDeclaredField("LIST_STRING").getGenericType();
    assertThat((List<?>) Util.emptyValueOf(listStringType)).isEmpty();
  }

  /** Ex. your {@code Foo} object would be null, but so would things like Number. */
  @Test
  public void emptyValueOf_nullForUndefined() throws Exception {
    assertThat(Util.emptyValueOf(Number.class)).isNull();
    assertThat(Util.emptyValueOf(Parameterized.class)).isNull();
  }

  @Test
  public void resolveLastTypeParameterWhenNotSubtype() throws Exception {
    Type context =
        LastTypeParameter.class.getDeclaredField("PARAMETERIZED_LIST_STRING").getGenericType();
    Type listStringType = LastTypeParameter.class.getDeclaredField("LIST_STRING").getGenericType();
    Type last = resolveLastTypeParameter(context, Parameterized.class);
    assertEquals(listStringType, last);
  }

  @Test
  public void lastTypeFromInstance() throws Exception {
    Parameterized<?> instance = new ParameterizedSubtype();
    Type last = resolveLastTypeParameter(instance.getClass(), Parameterized.class);
    assertEquals(String.class, last);
  }

  @Test
  public void lastTypeFromAnonymous() throws Exception {
    Parameterized<?> instance = new Parameterized<Reader>() {};
    Type last = resolveLastTypeParameter(instance.getClass(), Parameterized.class);
    assertEquals(Reader.class, last);
  }

  @Test
  public void resolveLastTypeParameterWhenWildcard() throws Exception {
    Type context =
        LastTypeParameter.class.getDeclaredField("PARAMETERIZED_WILDCARD_LIST_STRING")
            .getGenericType();
    Type listStringType = LastTypeParameter.class.getDeclaredField("LIST_STRING").getGenericType();
    Type last = resolveLastTypeParameter(context, Parameterized.class);
    assertEquals(listStringType, last);
  }

  @Test
  public void resolveLastTypeParameterWhenParameterizedSubtype() throws Exception {
    Type context =
        LastTypeParameter.class.getDeclaredField("PARAMETERIZED_DECODER_LIST_STRING")
            .getGenericType();
    Type listStringType = LastTypeParameter.class.getDeclaredField("LIST_STRING").getGenericType();
    Type last = resolveLastTypeParameter(context, ParameterizedDecoder.class);
    assertEquals(listStringType, last);
  }

  @Test
  public void unboundWildcardIsObject() throws Exception {
    Type context =
        LastTypeParameter.class.getDeclaredField("PARAMETERIZED_DECODER_UNBOUND").getGenericType();
    Type last = resolveLastTypeParameter(context, ParameterizedDecoder.class);
    assertEquals(Object.class, last);
  }

  @Test
  public void checkArgumentInputFalseNotNullNullOutputIllegalArgumentException() {
    // Arrange
    final boolean expression = false;
    final String errorMessageTemplate = "";
    final Object[] errorMessageArgs = null;
    // Act
    thrown.expect(IllegalArgumentException.class);
    Util.checkArgument(expression, errorMessageTemplate, errorMessageArgs);
    // Method is not expected to return due to exception thrown
  }

  @Test
  public void checkNotNullInputNullNotNullNullOutputNullPointerException() {
    // Arrange
    final Object reference = null;
    final String errorMessageTemplate = "";
    final Object[] errorMessageArgs = null;
    // Act
    thrown.expect(NullPointerException.class);
    Util.checkNotNull(reference, errorMessageTemplate, errorMessageArgs);
    // Method is not expected to return due to exception thrown
  }

  @Test
  public void checkNotNullInputZeroNotNull0OutputZero() {
    // Arrange
    final Object reference = 0;
    final String errorMessageTemplate = "   ";
    final Object[] errorMessageArgs = {};
    // Act
    final Object retval = Util.checkNotNull(reference, errorMessageTemplate, errorMessageArgs);
    // Assert result
    assertEquals(new Integer(0), retval);
  }

  @Test
  public void checkStateInputFalseNotNullNullOutputIllegalStateException() {
    // Arrange
    final boolean expression = false;
    final String errorMessageTemplate = "";
    final Object[] errorMessageArgs = null;
    // Act
    thrown.expect(IllegalStateException.class);
    Util.checkState(expression, errorMessageTemplate, errorMessageArgs);
    // Method is not expected to return due to exception thrown
  }

  @Test
  public void emptyToNullInputNotNullOutputNotNull() {
    // Arrange
    final String string = "AAAAAAAA";
    // Act
    final String retval = Util.emptyToNull(string);
    // Assert result
    assertEquals("AAAAAAAA", retval);
  }

  @Test
  public void emptyToNullInputNullOutputNull() {
    // Arrange
    final String string = null;
    // Act
    final String retval = Util.emptyToNull(string);
    // Assert result
    assertNull(retval);
  }

  @Test
  public void isBlankInputNotNullOutputFalse() {
    // Arrange
    final String value = "AAAAAAAA";
    // Act
    final boolean retval = Util.isBlank(value);
    // Assert result
    assertEquals(false, retval);
  }

  @Test
  public void isBlankInputNullOutputTrue() {
    // Arrange
    final String value = null;
    // Act
    final boolean retval = Util.isBlank(value);
    // Assert result
    assertEquals(true, retval);
  }

  @Test
  public void isNotBlankInputNotNullOutputFalse() {
    // Arrange
    final String value = "";
    // Act
    final boolean retval = Util.isNotBlank(value);
    // Assert result
    assertEquals(false, retval);
  }

  @Test
  public void isNotBlankInputNotNullOutputTrue() {
    // Arrange
    final String value = "AAAAAAAA";
    // Act
    final boolean retval = Util.isNotBlank(value);
    // Assert result
    assertEquals(true, retval);
  }

  @Test
  public void caseInsensitiveCopyOfMap() {
    // Arrange
    Map<String, Collection<String>> sourceMap = new HashMap<>();

    sourceMap.put("First", Arrays.asList("abc", "qwerty", "xyz"));
    sourceMap.put("camelCase", Collections.singleton("123"));
    // Act
    Map<String, Collection<String>> actualMap = caseInsensitiveCopyOf(sourceMap);
    // Assert result
    assertThat(actualMap)
        .hasEntrySatisfying("First", value -> {
          assertThat(value).contains("xyz", "abc", "qwerty");
        }).hasEntrySatisfying("first", value -> {
          assertThat(value).contains("xyz", "abc", "qwerty");
        }).hasEntrySatisfying("CAMELCASE", value -> {
          assertThat(value).contains("123");
        }).hasEntrySatisfying("camelcase", value -> {
          assertThat(value).contains("123");
        });
  }

  @Test
  public void copyIsUnmodifiable() {
    // Arrange
    Map<String, Collection<String>> sourceMap = new HashMap<>();

    sourceMap.put("First", Arrays.asList("abc", "qwerty", "xyz"));
    sourceMap.put("camelCase", Collections.singleton("123"));
    // Act
    Map<String, Collection<String>> unmodifiableMap = caseInsensitiveCopyOf(sourceMap);
    // Assert result
    assertThatThrownBy(() -> unmodifiableMap.put("new", Collections.singleton("223322")))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> unmodifiableMap.get("camelCase").clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  public void nullMap() {
    // Act
    Map<String, Collection<String>> actualMap = caseInsensitiveCopyOf(null);
    // Assert result
    assertThat(actualMap)
        .isNotNull()
        .isEmpty();
  }

  interface LastTypeParameter {
    List<String> LIST_STRING = null;
    Parameterized<List<String>> PARAMETERIZED_LIST_STRING = null;
    Parameterized<? extends List<String>> PARAMETERIZED_WILDCARD_LIST_STRING = null;
    ParameterizedDecoder<List<String>> PARAMETERIZED_DECODER_LIST_STRING = null;
    ParameterizedDecoder<?> PARAMETERIZED_DECODER_UNBOUND = null;
  }

  interface ParameterizedDecoder<T extends List<String>> extends Decoder {

  }

  interface Parameterized<T> {

  }

  static class ParameterizedSubtype implements Parameterized<String> {

  }
}
