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
package feign.error;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Parameterized.class)
public class AnnotationErrorDecoderInheritanceClassLevelAnnotationTest extends
    AbstractAnnotationErrorDecoderTest<AnnotationErrorDecoderInheritanceClassLevelAnnotationTest.SecondLevelInterface> {
  @Override
  public Class<SecondLevelInterface> interfaceAtTest() {
    return SecondLevelInterface.class;
  }

  @Parameters(
      name = "{0}: When error code ({1}) on method ({2}) should return exception type ({3})")
  public static Iterable<Object[]> data() {
    return Arrays.asList(new Object[][] {
        {"Test Code Specific At Method", 403, "topLevelMethod",
            SecondLevelClassDefaultException.class},
        {"Test Code Specific At Method", 403, "secondLevelMethod",
            SecondLevelMethodDefaultException.class},
        {"Test Code Specific At Method", 404, "topLevelMethod",
            SecondLevelClassAnnotationException.class},
        {"Test Code Specific At Method", 404, "secondLevelMethod",
            SecondLevelMethodErrorHandlingException.class},
    });
  }

  @Parameter // first data value (0) is default
  public String testType;

  @Parameter(1)
  public int errorCode;

  @Parameter(2)
  public String method;

  @Parameter(3)
  public Class<? extends Exception> expectedExceptionClass;

  @Test
  public void test() throws Exception {
    AnnotationErrorDecoder decoder =
        AnnotationErrorDecoder.builderFor(SecondLevelInterface.class).build();

    assertThat(decoder.decode(feignConfigKey(method), testResponse(errorCode)).getClass())
        .isEqualTo(expectedExceptionClass);
  }

  @TopLevelClassError
  interface TopLevelInterface {
    @ErrorHandling
    void topLevelMethod();
  }

  @SecondLevelClassError
  interface SecondLevelInterface extends TopLevelInterface {
    @ErrorHandling(
        codeSpecific = {
            @ErrorCodes(codes = {404}, generate = SecondLevelMethodErrorHandlingException.class)},
        defaultException = SecondLevelMethodDefaultException.class)
    void secondLevelMethod();
  }

  @ErrorHandling(
      codeSpecific = {
          @ErrorCodes(codes = {403, 404}, generate = TopLevelClassAnnotationException.class),},
      defaultException = TopLevelClassDefaultException.class)
  @Retention(RetentionPolicy.RUNTIME)
  @interface TopLevelClassError {
  }

  @ErrorHandling(
      codeSpecific = {
          @ErrorCodes(codes = {404}, generate = SecondLevelClassAnnotationException.class),},
      defaultException = SecondLevelClassDefaultException.class)
  @Retention(RetentionPolicy.RUNTIME)
  @interface SecondLevelClassError {
  }

  static class TopLevelClassDefaultException extends Exception {
    public TopLevelClassDefaultException() {}
  }
  static class TopLevelClassAnnotationException extends Exception {
    public TopLevelClassAnnotationException() {}
  }
  static class SecondLevelClassDefaultException extends Exception {
    public SecondLevelClassDefaultException() {}
  }
  static class SecondLevelMethodDefaultException extends Exception {
    public SecondLevelMethodDefaultException() {}
  }
  static class SecondLevelClassAnnotationException extends Exception {
    public SecondLevelClassAnnotationException() {}
  }
  static class SecondLevelMethodErrorHandlingException extends Exception {
    public SecondLevelMethodErrorHandlingException() {}
  }
}
