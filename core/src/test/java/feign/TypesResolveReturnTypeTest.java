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
package feign;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TypesResolveReturnTypeTest {

  interface Simple {
    String get();
  }

  interface ConcreteSimple extends Simple {
  }

  interface OverridingConcreteSimple extends Simple {
    @Override
    String get();
  }

  @Test
  public void simple() {
    Method[] methods = Simple.class.getMethods();
    Assertions.assertThat(methods.length).isEqualTo(1);
    Type resolved =
        Types.resolve(Simple.class, Simple.class, methods[0].getGenericReturnType());
    Assertions.assertThat(resolved).isEqualTo(String.class);
    // included for completeness sake only
    Type resolvedType = Types.resolveReturnType(Object.class, resolved);
    Assertions.assertThat(resolvedType).isEqualTo(resolved);
  }

  @Test
  public void concreteSimple() {
    Method[] methods = ConcreteSimple.class.getMethods();
    Assertions.assertThat(methods.length).isEqualTo(1);
    Type resolved = Types.resolve(ConcreteSimple.class, ConcreteSimple.class,
        methods[0].getGenericReturnType());
    Assertions.assertThat(resolved).isEqualTo(String.class);
    // included for completeness sake only
    Type resolvedType = Types.resolveReturnType(Object.class, resolved);
    Assertions.assertThat(resolvedType).isEqualTo(resolved);
  }

  @Test
  public void overridingConcreteSimple() {
    Method[] methods = OverridingConcreteSimple.class.getMethods();
    Assertions.assertThat(methods.length).isEqualTo(1);
    Type resolved = Types.resolve(OverridingConcreteSimple.class,
        OverridingConcreteSimple.class, methods[0].getGenericReturnType());
    Assertions.assertThat(resolved).isEqualTo(String.class);
    // included for completeness sake only
    Type resolvedType = Types.resolveReturnType(Object.class, resolved);
    Assertions.assertThat(resolvedType).isEqualTo(resolved);
  }

  interface SimplePrimitive {
    long get();
  }

  interface ConcreteSimplePrimitive extends SimplePrimitive {
  }

  interface OverridingConcreteSimplePrimitive extends SimplePrimitive {
    @Override
    long get();
  }

  @Test
  public void simplePrimitive() {
    Method[] methods = SimplePrimitive.class.getMethods();
    Assertions.assertThat(methods.length).isEqualTo(1);
    Type resolved = Types.resolve(SimplePrimitive.class, SimplePrimitive.class,
        methods[0].getGenericReturnType());
    Assertions.assertThat(resolved).isEqualTo(long.class);
    // included for completeness sake only
    Type resolvedType = Types.resolveReturnType(long.class, resolved);
    Assertions.assertThat(resolvedType).isEqualTo(resolved);
  }

  @Test
  public void concreteSimplePrimitive() {
    Method[] methods = ConcreteSimplePrimitive.class.getMethods();
    Assertions.assertThat(methods.length).isEqualTo(1);
    Type resolved = Types.resolve(ConcreteSimplePrimitive.class,
        ConcreteSimplePrimitive.class, methods[0].getGenericReturnType());
    Assertions.assertThat(resolved).isEqualTo(long.class);
    // included for completeness sake only
    Type resolvedType = Types.resolveReturnType(long.class, resolved);
    Assertions.assertThat(resolvedType).isEqualTo(resolved);
  }

  @Test
  public void overridingConcreteSimplePrimitive() {
    Method[] methods = OverridingConcreteSimplePrimitive.class.getMethods();
    Assertions.assertThat(methods.length).isEqualTo(1);
    Type resolved = Types.resolve(OverridingConcreteSimplePrimitive.class,
        OverridingConcreteSimplePrimitive.class, methods[0].getGenericReturnType());
    Assertions.assertThat(resolved).isEqualTo(long.class);
    // included for completeness sake only
    Type resolvedType = Types.resolveReturnType(long.class, resolved);
    Assertions.assertThat(resolvedType).isEqualTo(resolved);
  }

  interface Generic<T> {
    T get();
  }

  interface ConcreteSimpleClassGenericSecondLevel extends Generic<Long> {
  }

  interface OverridingConcreteSimpleClassGenericSecondLevel extends Generic<Long> {
    @Override
    Long get();
  }

  @Test
  public void generic() {
    Method[] methods = Generic.class.getMethods();
    Assertions.assertThat(methods.length).isEqualTo(1);
    Type resolved = Types.resolve(Generic.class, Generic.class,
        Generic.class.getMethods()[0].getGenericReturnType());
    Assertions.assertThat(resolved instanceof TypeVariable).isTrue();
    Type resolvedType = Types.resolveReturnType(Object.class, resolved);
    Assertions.assertThat(resolvedType).isEqualTo(resolved);
  }

  @Test
  public void concreteSimpleClassGenericSecondLevel() {
    Method[] methods = ConcreteSimpleClassGenericSecondLevel.class.getMethods();
    Assertions.assertThat(methods.length).isEqualTo(1);
    Type resolved = Types.resolve(ConcreteSimpleClassGenericSecondLevel.class,
        ConcreteSimpleClassGenericSecondLevel.class, methods[0].getGenericReturnType());
    Assertions.assertThat(resolved).isEqualTo(Long.class);
    Type resolvedType = Types.resolveReturnType(Object.class, resolved);
    Assertions.assertThat(resolvedType).isEqualTo(resolved);
  }

  @Test
  public void overridingConcreteSimpleClassGenericSecondLevel() {
    Method[] methods = OverridingConcreteSimpleClassGenericSecondLevel.class.getMethods();
    Assertions.assertThat(methods.length).isEqualTo(2);
    Type resolved = Types.resolve(OverridingConcreteSimpleClassGenericSecondLevel.class,
        OverridingConcreteSimpleClassGenericSecondLevel.class, methods[0].getGenericReturnType());
    Assertions.assertThat(resolved).isEqualTo(Long.class);
    Type resolved2 = Types.resolve(OverridingConcreteSimpleClassGenericSecondLevel.class,
        OverridingConcreteSimpleClassGenericSecondLevel.class, methods[1].getGenericReturnType());
    Assertions.assertThat(resolved2).isEqualTo(Object.class);
    Type resolvedType = Types.resolveReturnType(resolved, resolved2);
    Assertions.assertThat(resolvedType).isEqualTo(resolved);
  }

  interface SecondGeneric<T> {
    T get();
  }

  interface ConcreteSimpleClassMultipleGenericSecondLevel
      extends Generic<Long>, SecondGeneric<Long> {
  }

  interface OverridingConcreteSimpleClassMultipleGenericSecondLevel
      extends Generic<Long>, SecondGeneric<Long> {
    @Override
    Long get();
  }

  interface RealizingSimpleClassGenericThirdLevel
      extends ConcreteSimpleClassMultipleGenericSecondLevel {
  }

  interface RealizingSimpleClassMultipleGenericThirdLevel
      extends OverridingConcreteSimpleClassMultipleGenericSecondLevel {
  }

  interface RealizingOverridingSimpleClassGenericThirdLevel
      extends ConcreteSimpleClassMultipleGenericSecondLevel {
    @Override
    Long get();
  }

  interface RealizingOverridingSimpleClassMultipleGenericThirdLevel
      extends OverridingConcreteSimpleClassMultipleGenericSecondLevel {
    @Override
    Long get();
  }

  @Test
  public void concreteSimpleClassMultipleGenericSecondLevel() {
    Method[] methods = ConcreteSimpleClassMultipleGenericSecondLevel.class.getMethods();
    Assertions.assertThat(methods.length).isEqualTo(2);
    Type resolved = Types.resolve(ConcreteSimpleClassMultipleGenericSecondLevel.class,
        ConcreteSimpleClassMultipleGenericSecondLevel.class, methods[0].getGenericReturnType());
    Assertions.assertThat(resolved).isEqualTo(Long.class);
    Type resolved2 = Types.resolve(ConcreteSimpleClassMultipleGenericSecondLevel.class,
        ConcreteSimpleClassMultipleGenericSecondLevel.class, methods[1].getGenericReturnType());
    Assertions.assertThat(resolved).isEqualTo(Long.class);
    Type resolvedType = Types.resolveReturnType(resolved, resolved2);
    Assertions.assertThat(resolvedType).isEqualTo(resolved);
  }

  @Test
  public void overridingConcreteSimpleClassMultipleGenericSecondLevel() {
    Method[] methods = OverridingConcreteSimpleClassMultipleGenericSecondLevel.class.getMethods();
    Assertions.assertThat(methods.length).isEqualTo(2);
    Type resolved =
        Types.resolve(OverridingConcreteSimpleClassMultipleGenericSecondLevel.class,
            OverridingConcreteSimpleClassMultipleGenericSecondLevel.class,
            methods[0].getGenericReturnType());
    Assertions.assertThat(resolved).isEqualTo(Long.class);
    Type resolved2 =
        Types.resolve(OverridingConcreteSimpleClassMultipleGenericSecondLevel.class,
            OverridingConcreteSimpleClassMultipleGenericSecondLevel.class,
            methods[1].getGenericReturnType());
    Assertions.assertThat(resolved2).isEqualTo(Object.class);
    Type resolvedType = Types.resolveReturnType(resolved, resolved2);
    Assertions.assertThat(resolvedType).isEqualTo(resolved);
  }

  @Test
  public void realizingSimpleClassGenericThirdLevel() {
    Method[] methods = RealizingSimpleClassGenericThirdLevel.class.getMethods();
    // TODO: BUG IN Java Compiler? Multiple same name methods with same return type for same
    // parameters
    Assertions.assertThat(methods.length).isEqualTo(2);
    Type resolved = Types.resolve(RealizingSimpleClassGenericThirdLevel.class,
        RealizingSimpleClassGenericThirdLevel.class, methods[0].getGenericReturnType());
    Assertions.assertThat(resolved).isEqualTo(Long.class);
    Type resolved2 = Types.resolve(RealizingSimpleClassGenericThirdLevel.class,
        RealizingSimpleClassGenericThirdLevel.class, methods[1].getGenericReturnType());
    Assertions.assertThat(resolved2).isEqualTo(Long.class);
    Type resolvedType = Types.resolveReturnType(resolved, resolved2);
    Assertions.assertThat(resolvedType).isEqualTo(resolved);
  }

  @Test
  public void realizingSimpleClassMultipleGenericThirdLevel() {
    Method[] methods = RealizingSimpleClassMultipleGenericThirdLevel.class.getMethods();
    Assertions.assertThat(methods.length).isEqualTo(2);
    Type resolved = Types.resolve(RealizingSimpleClassMultipleGenericThirdLevel.class,
        RealizingSimpleClassMultipleGenericThirdLevel.class, methods[0].getGenericReturnType());
    Assertions.assertThat(resolved).isEqualTo(Long.class);
    Type resolved2 = Types.resolve(RealizingSimpleClassMultipleGenericThirdLevel.class,
        RealizingSimpleClassMultipleGenericThirdLevel.class, methods[1].getGenericReturnType());
    Assertions.assertThat(resolved2).isEqualTo(Object.class);
    Type resolvedType = Types.resolveReturnType(resolved, resolved2);
    Assertions.assertThat(resolvedType).isEqualTo(resolved);
  }

  @Test
  public void realizingOverridingSimpleClassGenericThirdLevel() {
    Method[] methods = RealizingOverridingSimpleClassGenericThirdLevel.class.getMethods();
    Assertions.assertThat(methods.length).isEqualTo(2);
    Type resolved = Types.resolve(RealizingOverridingSimpleClassGenericThirdLevel.class,
        RealizingOverridingSimpleClassGenericThirdLevel.class, methods[0].getGenericReturnType());
    Assertions.assertThat(resolved).isEqualTo(Long.class);
    Type resolved2 = Types.resolve(RealizingOverridingSimpleClassGenericThirdLevel.class,
        RealizingOverridingSimpleClassGenericThirdLevel.class, methods[1].getGenericReturnType());
    Assertions.assertThat(resolved2).isEqualTo(Object.class);
    Type resolvedType = Types.resolveReturnType(resolved, resolved2);
    Assertions.assertThat(resolvedType).isEqualTo(resolved);
  }

  @Test
  public void realizingOverridingSimpleClassMultipleGenericThirdLevel() {
    Method[] methods = RealizingOverridingSimpleClassMultipleGenericThirdLevel.class.getMethods();
    Assertions.assertThat(methods.length).isEqualTo(2);
    Type resolved =
        Types.resolve(RealizingOverridingSimpleClassMultipleGenericThirdLevel.class,
            RealizingOverridingSimpleClassMultipleGenericThirdLevel.class,
            methods[0].getGenericReturnType());
    Assertions.assertThat(resolved).isEqualTo(Long.class);
    Type resolved2 =
        Types.resolve(RealizingOverridingSimpleClassMultipleGenericThirdLevel.class,
            RealizingOverridingSimpleClassMultipleGenericThirdLevel.class,
            methods[1].getGenericReturnType());
    Assertions.assertThat(resolved2).isEqualTo(Object.class);
    Type resolvedType = Types.resolveReturnType(resolved, resolved2);
    Assertions.assertThat(resolvedType).isEqualTo(resolved);
    Assertions.assertThat(resolvedType).isNotEqualTo(resolved2);
  }

  interface MultipleInheritedGeneric<T> extends Generic<T>, SecondGeneric<T> {
  }

  @Test
  public void multipleInheritedGeneric() {
    Method[] methods = MultipleInheritedGeneric.class.getMethods();
    Assertions.assertThat(methods.length).isEqualTo(2);
    Type resolved = Types.resolve(MultipleInheritedGeneric.class,
        MultipleInheritedGeneric.class, methods[0].getGenericReturnType());
    Assertions.assertThat(resolved instanceof TypeVariable).isTrue();
    Type resolved2 = Types.resolve(MultipleInheritedGeneric.class,
        MultipleInheritedGeneric.class, methods[1].getGenericReturnType());
    Assertions.assertThat(resolved2 instanceof TypeVariable).isTrue();
    Type resolvedType = Types.resolveReturnType(resolved, resolved2);
    Assertions.assertThat(resolvedType).isEqualTo(resolved2);
    Assertions.assertThat(resolvedType).isEqualTo(resolved);
  }

  interface SecondLevelSimpleClassGeneric<T extends Number> extends Generic<T> {
  }

  interface ConcreteSimpleClassGenericThirdLevel extends SecondLevelSimpleClassGeneric<Long> {
  }

  interface OverridingConcreteSimpleClassGenericThirdLevel
      extends SecondLevelSimpleClassGeneric<Long> {
    @Override
    Long get();
  }

  @Test
  public void secondLevelSimpleClassGeneric() {
    Method[] methods = SecondLevelSimpleClassGeneric.class.getMethods();
    Assertions.assertThat(methods.length).isEqualTo(1);
    Type resolved = Types.resolve(SecondLevelSimpleClassGeneric.class,
        SecondLevelSimpleClassGeneric.class, methods[0].getGenericReturnType());
    Assertions.assertThat(resolved instanceof TypeVariable).isTrue();
    Type resolvedType = Types.resolveReturnType(Object.class, resolved);
    Assertions.assertThat(resolvedType).isEqualTo(resolved);
  }

  @Test
  public void concreteSimpleClassGenericThirdLevel() {
    Method[] methods = ConcreteSimpleClassGenericThirdLevel.class.getMethods();
    Assertions.assertThat(methods.length).isEqualTo(1);
    Type resolved = Types.resolve(ConcreteSimpleClassGenericThirdLevel.class,
        ConcreteSimpleClassGenericThirdLevel.class, methods[0].getGenericReturnType());
    Assertions.assertThat(resolved).isEqualTo(Long.class);
    Type resolvedType = Types.resolveReturnType(Object.class, resolved);
    Assertions.assertThat(resolvedType).isEqualTo(resolved);
  }

  @Test
  public void OverridingConcreteSimpleClassGenericThirdLevel() {
    Method[] methods = OverridingConcreteSimpleClassGenericThirdLevel.class.getMethods();
    Assertions.assertThat(methods.length).isEqualTo(2);
    Type resolved = Types.resolve(OverridingConcreteSimpleClassGenericThirdLevel.class,
        OverridingConcreteSimpleClassGenericThirdLevel.class, methods[0].getGenericReturnType());
    Assertions.assertThat(resolved).isEqualTo(Long.class);
    Type resolved2 = Types.resolve(OverridingConcreteSimpleClassGenericThirdLevel.class,
        OverridingConcreteSimpleClassGenericThirdLevel.class, methods[1].getGenericReturnType());
    Assertions.assertThat(resolved2).isEqualTo(Object.class);
    Type resolvedType = Types.resolveReturnType(resolved, resolved2);
    Assertions.assertThat(resolvedType).isEqualTo(resolved);
  }

  interface SecondLevelGenericClassGeneric<T extends Generic<?>> extends Generic<T> {
  }

  interface RealizedGeneric extends Generic<Long> {
  }

  interface ConcreteGenericClassThirdLevel extends SecondLevelGenericClassGeneric<RealizedGeneric> {
  }

  interface SecondLevelCollectionGeneric<T extends Collection<?>> extends Generic<T> {
  }

  interface ConcreteCollectionGenericThirdLevel
      extends SecondLevelCollectionGeneric<ArrayList<Long>> {
  }

  interface ThirdLevelCollectionGeneric<T extends List<?>> extends SecondLevelCollectionGeneric<T> {
  }

  interface ConcreteCollectionGenericFourthLevel extends ThirdLevelCollectionGeneric<List<Long>> {
  }

  interface OverridingConcreteCollectionGenericFourthLevel
      extends ThirdLevelCollectionGeneric<List<Long>> {
    @Override
    List<Long> get();
  }

  interface OverrideOverridingConcreteCollectionGenericFourthLevel
      extends OverridingConcreteCollectionGenericFourthLevel {
    @Override
    List<Long> get();
  }

  interface GenericFourthLevelCollectionGeneric<T> extends ThirdLevelCollectionGeneric<List<T>> {
  }

  interface SecondGenericFourthLevelCollectionGeneric<T>
      extends ThirdLevelCollectionGeneric<List<T>> {
  }

  interface ConcreteGenericCollectionGenericFifthLevel
      extends GenericFourthLevelCollectionGeneric<Long> {
  }

  interface OverridingConcreteGenericCollectionGenericFifthLevel extends
      GenericFourthLevelCollectionGeneric<Long>, SecondGenericFourthLevelCollectionGeneric<Long> {
    @Override
    List<Long> get();
  }

  @Test
  public void secondLevelCollectionGeneric() {
    Method[] methods = SecondLevelCollectionGeneric.class.getMethods();
    Assertions.assertThat(methods.length).isEqualTo(1);
    Type resolved = Types.resolve(SecondLevelCollectionGeneric.class,
        SecondLevelCollectionGeneric.class, methods[0].getGenericReturnType());
    Assertions.assertThat(resolved instanceof TypeVariable).isTrue();
    Type resolvedType = Types.resolveReturnType(Object.class, resolved);
    Assertions.assertThat(resolvedType).isEqualTo(resolved);
  }

  @Test
  public void thirdLevelCollectionGeneric() {
    Method[] methods = ThirdLevelCollectionGeneric.class.getMethods();
    Assertions.assertThat(methods.length).isEqualTo(1);
    Type resolved = Types.resolve(ThirdLevelCollectionGeneric.class,
        ThirdLevelCollectionGeneric.class, methods[0].getGenericReturnType());
    Assertions.assertThat(resolved instanceof TypeVariable).isTrue();
    Type resolvedType = Types.resolveReturnType(Object.class, resolved);
    Assertions.assertThat(resolvedType).isEqualTo(resolved);
  }

  @Test
  public void concreteCollectionGenericFourthLevel() {
    Method[] methods = ConcreteCollectionGenericFourthLevel.class.getMethods();
    Assertions.assertThat(methods.length).isEqualTo(1);
    Type resolved = Types.resolve(ConcreteCollectionGenericFourthLevel.class,
        ConcreteCollectionGenericFourthLevel.class, methods[0].getGenericReturnType());
    Assertions.assertThat(resolved instanceof ParameterizedType).isTrue();
    Type resolvedType = Types.resolveReturnType(Object.class, resolved);
    Assertions.assertThat(resolvedType).isEqualTo(resolved);
  }

  @Test
  public void overridingConcreteCollectionGenericFourthLevel() {
    Method[] methods = OverridingConcreteCollectionGenericFourthLevel.class.getMethods();
    Assertions.assertThat(methods.length).isEqualTo(2);
    Type resolved = Types.resolve(OverridingConcreteCollectionGenericFourthLevel.class,
        OverridingConcreteCollectionGenericFourthLevel.class, methods[0].getGenericReturnType());
    Assertions.assertThat(resolved instanceof ParameterizedType).isTrue();
    Type resolved2 = Types.resolve(OverridingConcreteCollectionGenericFourthLevel.class,
        OverridingConcreteCollectionGenericFourthLevel.class, methods[1].getGenericReturnType());
    Assertions.assertThat(resolved2).isEqualTo(Object.class);
    Type resolvedType = Types.resolveReturnType(resolved, resolved2);
    Assertions.assertThat(resolvedType).isEqualTo(resolved);
  }

  @Test
  public void overrideOverridingConcreteCollectionGenericFourthLevel() {
    Method[] methods = OverrideOverridingConcreteCollectionGenericFourthLevel.class.getMethods();
    Assertions.assertThat(methods.length).isEqualTo(2);
    Type resolved =
        Types.resolve(OverrideOverridingConcreteCollectionGenericFourthLevel.class,
            OverrideOverridingConcreteCollectionGenericFourthLevel.class,
            methods[0].getGenericReturnType());
    Assertions.assertThat(resolved instanceof ParameterizedType).isTrue();
    Type resolved2 =
        Types.resolve(OverrideOverridingConcreteCollectionGenericFourthLevel.class,
            OverrideOverridingConcreteCollectionGenericFourthLevel.class,
            methods[1].getGenericReturnType());
    Assertions.assertThat(resolved2).isEqualTo(Object.class);
    Type resolvedType = Types.resolveReturnType(resolved, resolved2);
    Assertions.assertThat(resolvedType).isEqualTo(resolved);
  }

  @Test
  public void genericFourthLevelCollectionGeneric() {
    Method[] methods = GenericFourthLevelCollectionGeneric.class.getMethods();
    Assertions.assertThat(methods.length).isEqualTo(1);
    Type resolved = Types.resolve(GenericFourthLevelCollectionGeneric.class,
        GenericFourthLevelCollectionGeneric.class, methods[0].getGenericReturnType());
    Assertions.assertThat(resolved instanceof ParameterizedType).isTrue();
    Type resolvedType = Types.resolveReturnType(Object.class, resolved);
    Assertions.assertThat(resolvedType).isEqualTo(resolved);
  }

  @Test
  public void concreteGenericCollectionGenericFifthLevel() {
    Method[] methods = ConcreteGenericCollectionGenericFifthLevel.class.getMethods();
    Assertions.assertThat(methods.length).isEqualTo(1);
    Type resolved = Types.resolve(ConcreteGenericCollectionGenericFifthLevel.class,
        ConcreteGenericCollectionGenericFifthLevel.class, methods[0].getGenericReturnType());
    Assertions.assertThat(resolved instanceof ParameterizedType).isTrue();
    ParameterizedType parameterizedType = (ParameterizedType) resolved;
  }

  @Test
  public void overridingConcreteGenericCollectionGenericFifthLevel() {
    Method[] methods = OverridingConcreteGenericCollectionGenericFifthLevel.class.getMethods();
    Assertions.assertThat(methods.length).isEqualTo(2);
    Type resolved =
        Types.resolve(OverridingConcreteGenericCollectionGenericFifthLevel.class,
            OverridingConcreteGenericCollectionGenericFifthLevel.class,
            methods[0].getGenericReturnType());
    Assertions.assertThat(resolved instanceof ParameterizedType).isTrue();
    Type resolved2 =
        Types.resolve(OverridingConcreteGenericCollectionGenericFifthLevel.class,
            OverridingConcreteGenericCollectionGenericFifthLevel.class,
            methods[1].getGenericReturnType());
    Assertions.assertThat(resolved2).isEqualTo(Object.class);
    Type resolvedType = Types.resolveReturnType(methods[0].getGenericReturnType(),
        methods[1].getGenericReturnType());
    Assertions.assertThat(resolvedType).isEqualTo(resolved);
  }

  interface SecondLevelMapGeneric<T extends Map<?, ?>> extends Generic<T> {
  }

  interface ThirdLevelMapGeneric<T extends HashMap<?, ?>> extends SecondLevelMapGeneric<T> {
  }

  interface SimpleArrayGeneric<T> {
    T[] get();
  }

  interface SimpleClassNonGeneric {
    Object get();
  }

  interface OverrideNonGenericSimpleClass extends SimpleClassNonGeneric {
    @Override
    Number get();
  }

  interface GenerifiedOverrideNonGenericSimpleClass<T extends Number>
      extends OverrideNonGenericSimpleClass {
    @Override
    T get();
  }

  interface RealizedGenerifiedOverrideNonGenericSimpleClass
      extends GenerifiedOverrideNonGenericSimpleClass<Long> {
  }

  interface OverridingGenerifiedOverrideNonGenericSimpleClass
      extends GenerifiedOverrideNonGenericSimpleClass<Long> {
    @Override
    Long get();
  }
}
