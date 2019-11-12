// Copyright 2014 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.devtools.build.lib.syntax;

import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.skylarkinterface.SkylarkPrinter;
import com.google.devtools.build.lib.syntax.StarlarkMutable.BaseMutableList;
import java.util.Collections;
import java.util.List;
import java.util.RandomAccess;
import javax.annotation.Nullable;

/**
 * A Skylark list or tuple.
 *
 * <p>Although this implements the {@link List} interface, it is not mutable via that interface's
 * methods. Instead, use the mutators that take in a {@link Mutability} object.
 */
// TODO(adonovan): rename to Sequence.
@SkylarkModule(
    name = "sequence",
    documented = false,
    category = SkylarkModuleCategory.BUILTIN,
    doc = "common type of lists and tuples.")
public abstract class SkylarkList<E> extends BaseMutableList<E>
    implements List<E>, RandomAccess, SkylarkIndexable {

  @Override
  public final boolean truth() {
    return !isEmpty();
  }

  /**
   * Returns an ImmutableList object with the current underlying contents of this SkylarkList.
   */
  public abstract ImmutableList<E> getImmutableList();

  /**
   * Retrieve an entry from a SkylarkList.
   *
   * @param key the index
   * @param loc a {@link Location} in case of error
   * @throws EvalException if the key is invalid
   */
  @Override
  public E getIndex(Object key, Location loc) throws EvalException {
    List<E> list = getContentsUnsafe();
    int index = EvalUtils.getSequenceIndex(key, list.size(), loc);
    return list.get(index);
  }

  @Override
  public boolean containsKey(Object key, Location loc) throws EvalException {
    for (Object obj : this) {
      if (obj.equals(key)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Constructs a version of this {@code SkylarkList} containing just the items in a slice.
   *
   * <p>{@code mutability} will be used for the resulting list. If it is null, the list will be
   * immutable. For {@code Tuple}s, which are always immutable, this argument is ignored.
   *
   * @see EvalUtils#getSliceIndices
   * @throws EvalException if the key is invalid; uses {@code loc} for error reporting
   */
  public abstract SkylarkList<E> getSlice(
      Object start, Object end, Object step, Location loc, Mutability mutability)
      throws EvalException;

  /**
   * Constructs a repetition of this {@code SkylarkList}.
   *
   * <p>{@code mutability} will be used for the resulting list. If it is null, the list will be
   * immutable. For {@code Tuple}s, which are always immutable, this argument is ignored.
   */
  public abstract SkylarkList<E> repeat(int times, Mutability mutability);

  @Override
  public void repr(SkylarkPrinter printer) {
    printer.printList(getContentsUnsafe(), this instanceof Tuple);
  }

  @Override
  public String toString() {
    return Printer.repr(this);
  }

  // Note that the following two functions slightly violate the Java List protocol,
  // in that it does NOT consider that a SkylarkList .equals() an arbitrary List with same contents.
  // This is because we use .equals() to model skylark equality, which like Python
  // distinguishes a StarlarkList from a Tuple.
  @Override
  public boolean equals(Object object) {
    return (this == object)
        || ((object != null)
            && (this.getClass() == object.getClass())
            && getContentsUnsafe().equals(((SkylarkList) object).getContentsUnsafe()));
  }

  @Override
  public int hashCode() {
    return getClass().hashCode() + 31 * getContentsUnsafe().hashCode();
  }

  /**
   * Casts a {@code List<?>} to an unmodifiable {@code List<T>}, after checking that its contents
   * all have type {@code type}.
   *
   * <p>The returned list may or may not be a view that is affected by updates to the original list.
   *
   * @param list the original list to cast
   * @param type the expected type of all the list's elements
   * @param description a description of the argument being converted, or null, for debugging
   */
  // We could have used bounded generics to ensure that only downcasts are possible (i.e. cast
  // List<S> to List<T extends S>), but this would be less convenient for some callers, and it would
  // disallow casting an empty list to any type.
  @SuppressWarnings("unchecked")
  public static <T> List<T> castList(List<?> list, Class<T> type, @Nullable String description)
      throws EvalException {
    Object desc = description == null ? null : Printer.formattable("'%s' element", description);
    for (Object value : list) {
      SkylarkType.checkType(value, type, desc);
    }
    return Collections.unmodifiableList((List<T>) list);
  }

  /**
   * If {@code obj} is a {@code SkylarkList}, casts it to an unmodifiable {@code List<T>} after
   * checking that each element has type {@code type}. If {@code obj} is {@code None} or null,
   * treats it as an empty list. For all other values, throws an {@link EvalException}.
   *
   * <p>The returned list may or may not be a view that is affected by updates to the original list.
   *
   * @param obj the object to cast. null and None are treated as an empty list.
   * @param type the expected type of all the list's elements
   * @param description a description of the argument being converted, or null, for debugging
   */
  public static <T> List<T> castSkylarkListOrNoneToList(
      Object obj, Class<T> type, @Nullable String description)
      throws EvalException {
    if (EvalUtils.isNullOrNone(obj)) {
      return ImmutableList.of();
    }
    if (obj instanceof SkylarkList) {
      return ((SkylarkList<?>) obj).getContents(type, description);
    }
    throw new EvalException(null,
        String.format("Illegal argument: %s is not of expected type list or NoneType",
            description == null ? Printer.repr(obj) : String.format("'%s'", description)));
  }

  /**
   * Casts this list as an unmodifiable {@code List<T>}, after checking that each element has
   * type {@code type}.
   *
   * @param type the expected type of all the list's elements
   * @param description a description of the argument being converted, or null, for debugging
   */
  public <T> List<T> getContents(Class<T> type, @Nullable String description)
      throws EvalException {
    return castList(getContentsUnsafe(), type, description);
  }

  /**
   * Creates an immutable Skylark list with the given elements.
   *
   * <p>It is unspecified whether this is a Skylark list or tuple. For more control, use one of the
   * factory methods in {@link StarlarkList} or {@link Tuple}.
   *
   * <p>The caller must ensure that the elements of {@code contents} are not mutable.
   */
  // TODO(bazel-team): Eliminate this function in favor of a new StarlarkList factory method. With
  // such a method, we may no longer need to take null as a possible value for the Mutability or
  // StarlarkThread. That in turn would allow us to overload StarlarkList#of to take either a
  // Mutability or StarlarkThread.
  public static <E> SkylarkList<E> createImmutable(Iterable<? extends E> contents) {
    return StarlarkList.copyOf(Mutability.IMMUTABLE, contents);
  }
}
