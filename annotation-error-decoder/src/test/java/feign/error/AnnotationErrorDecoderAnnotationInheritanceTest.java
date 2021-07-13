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

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;

@RunWith(Parameterized.class)
public class AnnotationErrorDecoderAnnotationInheritanceTest extends
    AbstractAnnotationErrorDecoderTest<AnnotationErrorDecoderAnnotationInheritanceTest.TestClientInterfaceWithWithMetaAnnotation> {
  @Override
  public Class<TestClientInterfaceWithWithMetaAnnotation> interfaceAtTest() {
    return TestClientInterfaceWithWithMetaAnnotation.class;
  }

  @Parameters(
      name = "{0}: When error code ({1}) on method ({2}) should return exception type ({3})")
  public static Iterable<Object[]> data() {
    return Arrays.asList(new Object[][] {
        {"Test Code Specific At Method", 402, "method1Test", MethodLevelDefaultException.class},
        {"Test Code Specific At Method", 403, "method1Test", MethodLevelNotFoundException.class},
        {"Test Code Specific At Method", 404, "method1Test", MethodLevelNotFoundException.class},
        {"Test Code Specific At Method", 402, "method2Test", ClassLevelDefaultException.class},
        {"Test Code Specific At Method", 403, "method2Test", MethodLevelNotFoundException.class},
        {"Test Code Specific At Method", 404, "method2Test", ClassLevelNotFoundException.class},
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
        AnnotationErrorDecoder.builderFor(TestClientInterfaceWithWithMetaAnnotation.class).build();

    assertThat(decoder.decode(feignConfigKey(method), testResponse(errorCode)).getClass())
        .isEqualTo(expectedExceptionClass);
  }

  @ClassError
  interface TestClientInterfaceWithWithMetaAnnotation {
    @MethodError
    void method1Test();

    @ErrorHandling(
        codeSpecific = {@ErrorCodes(codes = {403}, generate = MethodLevelNotFoundException.class)})
    void method2Test();
  }

  @ErrorHandling(
      codeSpecific = {@ErrorCodes(codes = {404}, generate = ClassLevelNotFoundException.class),},
      defaultException = ClassLevelDefaultException.class)
  @Retention(RetentionPolicy.RUNTIME)
  @interface ClassError {
  }

  @ErrorHandling(
      codeSpecific = {
          @ErrorCodes(codes = {404, 403}, generate = MethodLevelNotFoundException.class),},
      defaultException = MethodLevelDefaultException.class)
  @Retention(RetentionPolicy.RUNTIME)
  @interface MethodError {
  }

  static class ClassLevelDefaultException extends Exception {
    public ClassLevelDefaultException() {}
  }
  static class ClassLevelNotFoundException extends Exception {
    public ClassLevelNotFoundException() {}
  }
  static class MethodLevelDefaultException extends Exception {
    public MethodLevelDefaultException() {}
  }
  static class MethodLevelNotFoundException extends Exception {
    public MethodLevelNotFoundException() {}
  }
}
