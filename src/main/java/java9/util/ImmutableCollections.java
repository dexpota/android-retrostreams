/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package java9.util;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.RandomAccess;
import java.util.Set;

/**
 * Container class for unmodifiable collections. Not part of the public API.
 * Mainly for namespace management and shared infrastructure.
 *
 * Serial warnings are suppressed throughout because all implementation
 * classes use a serial proxy and thus have no need to declare serialVersionUID.
 */
@SuppressWarnings("serial")
final class ImmutableCollections {
    /**
     * A "salt" value used for randomizing iteration order. This is initialized once
     * and stays constant for the lifetime of the JVM. It need not be truly random, but
     * it needs to vary sufficiently from one run to the next so that iteration order
     * will vary between JVM runs.
     */
    static final int SALT;
    static {
        long nt = System.nanoTime();
        SALT = (int) ((nt >>> 32) ^ nt);
    }

    /** No instances. */
    private ImmutableCollections() { }

    /**
     * The reciprocal of load factor. Given a number of elements
     * to store, multiply by this factor to get the table size.
     */
    static final int EXPAND_FACTOR = 2;

    static UnsupportedOperationException uoe() { return new UnsupportedOperationException(); }

    static abstract class AbstractImmutableCollection<E> extends AbstractCollection<E> {
        // all mutating methods throw UnsupportedOperationException
        @Override public boolean add(E e) { throw uoe(); }
        @Override public boolean addAll(Collection<? extends E> c) { throw uoe(); }
        @Override public void    clear() { throw uoe(); }
        @Override public boolean remove(Object o) { throw uoe(); }
        @Override public boolean removeAll(Collection<?> c) { throw uoe(); }
        @Override public boolean retainAll(Collection<?> c) { throw uoe(); }
    }

    // ---------- List Implementations ----------

    // make a copy, short-circuiting based on implementation class
    @SuppressWarnings("unchecked")
    static <E> List<E> listCopy(Collection<? extends E> coll) {
        if (coll instanceof AbstractImmutableList && coll.getClass() != SubList.class) {
            return (List<E>) coll;
        } else {
            return (List<E>) Lists.of(coll.toArray());
        }
    }

    static abstract class AbstractImmutableList<E> extends AbstractImmutableCollection<E>
            implements List<E>, RandomAccess {
        // all mutating methods throw UnsupportedOperationException
        @Override public void    add(int index, E element) { throw uoe(); }
        // Fix for JDK-4802633 on Java 6 and Apache Harmony-based Android
        @Override public boolean addAll(int index, Collection<? extends E> c) { rangeCheckForAdd(index, size()); throw uoe(); }
        @Override public E       remove(int index) { throw uoe(); }
        @Override public E       set(int index, E element) { throw uoe(); }

        @Override
        public List<E> subList(int fromIndex, int toIndex) {
            int size = size();
            subListRangeCheck(fromIndex, toIndex, size);
            return SubList.fromList(this, fromIndex, toIndex);
        }

        static void subListRangeCheck(int fromIndex, int toIndex, int size) {
            if (fromIndex < 0)
                throw new IndexOutOfBoundsException("fromIndex = " + fromIndex);
            if (toIndex > size)
                throw new IndexOutOfBoundsException("toIndex = " + toIndex);
            if (fromIndex > toIndex)
                throw new IllegalArgumentException("fromIndex(" + fromIndex +
                        ") > toIndex(" + toIndex + ")");
        }

        @Override
        public Iterator<E> iterator() {
            return new ListItr<E>(this, size());
        }

        @Override
        public ListIterator<E> listIterator() {
            return listIterator(0);
        }

        @Override
        public ListIterator<E> listIterator(int index) {
            int size = size();
            if (index < 0 || index > size) {
                throw outOfBounds(index);
            }
            return new ListItr<E>(this, size, index);
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }

            if (!(o instanceof List)) {
                return false;
            }

            Iterator<?> oit = ((List<?>) o).iterator();
            for (int i = 0, s = size(); i < s; i++) {
                if (!oit.hasNext() || !get(i).equals(oit.next())) {
                    return false;
                }
            }
            return !oit.hasNext();
        }

        @Override
        public int indexOf(Object o) {
            Objects.requireNonNull(o);
            for (int i = 0, s = size(); i < s; i++) {
                if (o.equals(get(i))) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public int lastIndexOf(Object o) {
            Objects.requireNonNull(o);
            for (int i = size() - 1; i >= 0; i--) {
                if (o.equals(get(i))) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public int hashCode() {
            int hash = 1;
            for (int i = 0, s = size(); i < s; i++) {
                hash = 31 * hash + get(i).hashCode();
            }
            return hash;
        }

        @Override
        public boolean contains(Object o) {
            return indexOf(o) >= 0;
        }

        IndexOutOfBoundsException outOfBounds(int index) {
            return new IndexOutOfBoundsException("Index: " + index + " Size: " + size());
        }
    }

    static final class ListItr<E> implements ListIterator<E> {

        private final List<E> list;
        private final int size;
        private final boolean isListIterator;
        private int cursor;

        ListItr(List<E> list, int size) {
            this.list = list;
            this.size = size;
            this.cursor = 0;
            isListIterator = false;
        }

        ListItr(List<E> list, int size, int index) {
            this.list = list;
            this.size = size;
            this.cursor = index;
            isListIterator = true;
        }

        public boolean hasNext() {
            return cursor != size;
        }

        public E next() {
            try {
                int i = cursor;
                E next = list.get(i);
                cursor = i + 1;
                return next;
            } catch (IndexOutOfBoundsException e) {
                throw new NoSuchElementException();
            }
        }

        public void remove() {
            throw uoe();
        }

        public boolean hasPrevious() {
            if (!isListIterator) {
                throw uoe();
            }
            return cursor != 0;
        }

        public E previous() {
            if (!isListIterator) {
                throw uoe();
            }
            try {
                int i = cursor - 1;
                E previous = list.get(i);
                cursor = i;
                return previous;
            } catch (IndexOutOfBoundsException e) {
                throw new NoSuchElementException();
            }
        }

        public int nextIndex() {
            if (!isListIterator) {
                throw uoe();
            }
            return cursor;
        }

        public int previousIndex() {
            if (!isListIterator) {
                throw uoe();
            }
            return cursor - 1;
        }

        public void set(E e) {
            throw uoe();
        }

        public void add(E e) {
            throw uoe();
        }
    }

    static final class SubList<E> extends AbstractImmutableList<E>
            implements RandomAccess {

        private final List<E> root;
        private final int offset;
        private final int size;

        private SubList(List<E> root, int offset, int size) {
            this.root = root;
            this.offset = offset;
            this.size = size;
        }

        /**
         * Constructs a sublist of another SubList.
         */
        static <E> SubList<E> fromSubList(SubList<E> parent, int fromIndex, int toIndex) {
            return new SubList<E>(parent.root, parent.offset + fromIndex, toIndex - fromIndex);
        }

        /**
         * Constructs a sublist of an arbitrary AbstractImmutableList, which is
         * not a SubList itself.
         */
        static <E> SubList<E> fromList(List<E> list, int fromIndex, int toIndex) {
            return new SubList<E>(list, fromIndex, toIndex - fromIndex);
        }

        public E get(int index) {
            Objects.checkIndex(index, size);
            return root.get(offset + index);
        }

        public int size() {
            return size;
        }

        public Iterator<E> iterator() {
            return new ListItr<E>(this, size());
        }

        public ListIterator<E> listIterator(int index) {
            rangeCheck(index);
            return new ListItr<E>(this, size(), index);
        }

        public List<E> subList(int fromIndex, int toIndex) {
            subListRangeCheck(fromIndex, toIndex, size);
            return SubList.fromSubList(this, fromIndex, toIndex);
        }

        private void rangeCheck(int index) {
            if (index < 0 || index > size) {
                throw outOfBounds(index);
            }
        }

        @Override
        public Object[] toArray() {
            Object[] array = new Object[size];
            for (int i = 0; i < size; i++) {
                array[i] = get(i);
            }
            return array;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T[] toArray(T[] a) {
            T[] array = a.length >= size ? a :
                    (T[]) Array
                            .newInstance(a.getClass().getComponentType(), size);
            for (int i = 0; i < size; i++) {
                array[i] = (T) get(i);
            }
            if (array.length > size) {
                array[size] = null; // null-terminate
            }
            return array;
        }
    }

    static final class List12<E> extends AbstractImmutableList<E>
            implements Serializable {

        private final E e0;
        private final E e1;

        List12(E e0) {
            this.e0 = Objects.requireNonNull(e0);
            this.e1 = null;
        }

        List12(E e0, E e1) {
            this.e0 = Objects.requireNonNull(e0);
            this.e1 = Objects.requireNonNull(e1);
        }

        @Override
        public int size() {
            return e1 != null ? 2 : 1;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public E get(int index) {
            if (index == 0) {
                return e0;
            } else if (index == 1 && e1 != null) {
                return e1;
            }
            throw outOfBounds(index);
        }

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            throw new InvalidObjectException("not serial proxy");
        }

        private Object writeReplace() {
            if (e1 == null) {
                return new ColSer(ColSer.IMM_LIST, e0);
            } else {
                return new ColSer(ColSer.IMM_LIST, e0, e1);
            }
        }

        @Override
        public Object[] toArray() {
            if (e1 == null) {
                return new Object[] { e0 };
            } else {
                return new Object[] { e0, e1 };
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T[] toArray(T[] a) {
            int size = e1 == null ? 1 : 2;
            T[] array = a.length >= size ? a :
                    (T[]) Array.newInstance(a.getClass().getComponentType(), size);
            array[0] = (T) e0;
            if (size == 2) {
                array[1] = (T) e1;
            }
            if (array.length > size) {
                array[size] = null; // null-terminate
            }
            return array;
        }
    }

    static final class ListN<E> extends AbstractImmutableList<E>
            implements Serializable {

        static final List<?> EMPTY_LIST = new ListN<Object>();

        private final E[] elements;

        ListN(E... input) {
            // copy and check manually to avoid TOCTOU
            @SuppressWarnings("unchecked")
            E[] tmp = (E[]) new Object[input.length]; // implicit nullcheck of input
            for (int i = 0; i < input.length; i++) {
                tmp[i] = Objects.requireNonNull(input[i]);
            }
            elements = tmp;
        }

        @Override
        public boolean isEmpty() {
            return elements.length == 0;
        }

        @Override
        public int size() {
            return elements.length;
        }

        @Override
        public E get(int index) {
            return elements[index];
        }

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            throw new InvalidObjectException("not serial proxy");
        }

        private Object writeReplace() {
            return new ColSer(ColSer.IMM_LIST, elements);
        }

        @Override
        public Object[] toArray() {
            return Arrays.copyOf(elements, elements.length);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T[] toArray(T[] a) {
            int size = elements.length;
            if (a.length < size) {
                // Make a new array of a's runtime type, but my contents:
                return (T[]) Arrays.copyOf(elements, size, a.getClass());
            }
            System.arraycopy(elements, 0, a, 0, size);
            if (a.length > size) {
                a[size] = null; // null-terminate
            }
            return a;
        }
    }

    // ---------- Set Implementations ----------

    static abstract class AbstractImmutableSet<E> extends AbstractImmutableCollection<E>
            implements Set<E> {

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof Set)) {
                return false;
            }

            Collection<?> c = (Collection<?>) o;
            if (c.size() != size()) {
                return false;
            }
            for (Object e : c) {
                if (e == null || !contains(e)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public abstract int hashCode();
    }

    static final class Set12<E> extends AbstractImmutableSet<E>
            implements Serializable {

        final E e0;
        final E e1;

        Set12(E e0) {
            this.e0 = Objects.requireNonNull(e0);
            this.e1 = null;
        }

        Set12(E e0, E e1) {
            if (e0.equals(Objects.requireNonNull(e1))) { // implicit nullcheck of e0
                throw new IllegalArgumentException("duplicate element: " + e0);
            }

            this.e0 = e0;
            this.e1 = e1;
        }

        @Override
        public int size() {
            return (e1 == null) ? 1 : 2;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public boolean contains(Object o) {
            return o.equals(e0) || o.equals(e1); // implicit nullcheck of o
        }

        @Override
        public int hashCode() {
            return e0.hashCode() + (e1 == null ? 0 : e1.hashCode());
        }

        @Override
        public Iterator<E> iterator() {
            return new Iterators.ImmutableIt<E>() {
                private int idx = size();

                @Override
                public boolean hasNext() {
                    return idx > 0;
                }

                @Override
                public E next() {
                    if (idx == 1) {
                        idx = 0;
                        return SALT >= 0 || e1 == null ? e0 : e1;
                    } else if (idx == 2) {
                        idx = 1;
                        return SALT >= 0 ? e1 : e0;
                    } else {
                        throw new NoSuchElementException();
                    }
                }
            };
        }

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            throw new InvalidObjectException("not serial proxy");
        }

        private Object writeReplace() {
            if (e1 == null) {
                return new ColSer(ColSer.IMM_SET, e0);
            } else {
                return new ColSer(ColSer.IMM_SET, e0, e1);
            }
        }

        @Override
        public Object[] toArray() {
            if (e1 == null) {
                return new Object[] { e0 };
            } else if (SALT >= 0) {
                return new Object[] { e1, e0 };
            } else {
                return new Object[] { e0, e1 };
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T[] toArray(T[] a) {
            int size = e1 == null ? 1 : 2;
            T[] array = a.length >= size ? a :
                    (T[]) Array.newInstance(a.getClass().getComponentType(), size);
            if (size == 1) {
                array[0] = (T) e0;
            } else if (SALT >= 0) {
                array[0] = (T) e1;
                array[1] = (T) e0;
            } else {
                array[0] = (T) e0;
                array[1] = (T) e1;
            }
            if (array.length > size) {
                array[size] = null; // null-terminate
            }
            return array;
        }
    }

    /**
     * An array-based Set implementation. The element array must be strictly
     * larger than the size (the number of contained elements) so that at
     * least one null is always present.
     * @param <E> the element type
     */
    static final class SetN<E> extends AbstractImmutableSet<E>
            implements Serializable {

        static final Set<?> EMPTY_SET = new SetN<Object>();

        final E[] elements;
        private final int size;

        @SuppressWarnings("unchecked")
        SetN(E... input) {
            size = input.length; // implicit nullcheck of input

            elements = (E[]) new Object[EXPAND_FACTOR * input.length];
            for (int i = 0; i < input.length; i++) {
                E e = input[i];
                int idx = probe(e); // implicit nullcheck of e
                if (idx >= 0) {
                    throw new IllegalArgumentException("duplicate element: " + e);
                } else {
                    elements[-(idx + 1)] = e;
                }
            }
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public boolean isEmpty() {
            return size == 0;
        }

        @Override
        public boolean contains(Object o) {
            Objects.requireNonNull(o);
            return size > 0 && probe(o) >= 0;
        }

        private final class SetNIterator extends Iterators.ImmutableIt<E> implements Iterator<E> {

            private int remaining;

            private int idx;

            SetNIterator() {
                remaining = size();
                if (remaining > 0) {
                    idx = floorMod(SALT, elements.length);
                }
            }

            @Override
            public boolean hasNext() {
                return remaining > 0;
            }

            private int nextIndex() {
                int idx = this.idx;
                if (SALT >= 0) {
                    if (++idx >= elements.length) {
                        idx = 0;
                    }
                } else {
                    if (--idx < 0) {
                        idx = elements.length - 1;
                    }
                }
                return this.idx = idx;
            }

            @Override
            public E next() {
                if (remaining > 0) {
                    E element;
                    // skip null elements
                    while ((element = elements[nextIndex()]) == null) {}
                    remaining--;
                    return element;
                } else {
                    throw new NoSuchElementException();
                }
            }
        }

        @Override
        public Iterator<E> iterator() {
            return new SetNIterator();
        }

        @Override
        public int hashCode() {
            int h = 0;
            for (E e : elements) {
                if (e != null) {
                    h += e.hashCode();
                }
            }
            return h;
        }

        // returns index at which element is present; or if absent,
        // (-i - 1) where i is location where element should be inserted.
        // Callers are relying on this method to perform an implicit nullcheck
        // of pe
        private int probe(Object pe) {
            int idx = floorMod(pe.hashCode(), elements.length);
            while (true) {
                E ee = elements[idx];
                if (ee == null) {
                    return -idx - 1;
                } else if (pe.equals(ee)) {
                    return idx;
                } else if (++idx == elements.length) {
                    idx = 0;
                }
            }
        }

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            throw new InvalidObjectException("not serial proxy");
        }

        private Object writeReplace() {
            Object[] array = new Object[size];
            int dest = 0;
            for (Object o : elements) {
                if (o != null) {
                    array[dest++] = o;
                }
            }
            return new ColSer(ColSer.IMM_SET, array);
        }

        @Override
        public Object[] toArray() {
            Object[] array = new Object[size];
            Iterator<E> it = iterator();
            for (int i = 0; i < size; i++) {
                array[i] = it.next();
            }
            return array;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T[] toArray(T[] a) {
            T[] array = a.length >= size ? a :
                    (T[]) Array.newInstance(a.getClass().getComponentType(), size);
            Iterator<E> it = iterator();
            for (int i = 0; i < size; i++) {
                array[i] = (T) it.next();
            }
            if (array.length > size) {
                array[size] = null; // null-terminate
            }
            return array;
        }
    }

    // ---------- Map Implementations ----------

    abstract static class AbstractImmutableMap<K,V> extends AbstractMap<K,V> implements Serializable {
        @Override public void clear() { throw uoe(); }
        @Override public V put(K key, V value) { throw uoe(); }
        @Override public void putAll(Map<? extends K,? extends V> m) { throw uoe(); }
        @Override public V remove(Object key) { throw uoe(); }
    }

    static final class Map1<K,V> extends AbstractImmutableMap<K,V> {
        private final K k0;
        private final V v0;

        Map1(K k0, V v0) {
            this.k0 = Objects.requireNonNull(k0);
            this.v0 = Objects.requireNonNull(v0);
        }

        @Override
        public Set<Map.Entry<K,V>> entrySet() {
            return Sets.of(new KeyValueHolder<K, V>(k0, v0));
        }

        @Override
        public V get(Object o) {
            return o.equals(k0) ? v0 : null; // implicit nullcheck of o
        }

        @Override
        public boolean containsKey(Object o) {
            return o.equals(k0); // implicit nullcheck of o
        }

        @Override
        public boolean containsValue(Object o) {
            return o.equals(v0); // implicit nullcheck of o
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            throw new InvalidObjectException("not serial proxy");
        }

        private Object writeReplace() {
            return new ColSer(ColSer.IMM_MAP, k0, v0);
        }

        @Override
        public int hashCode() {
            return k0.hashCode() ^ v0.hashCode();
        }
    }

    /**
     * An array-based Map implementation. There is a single array "table" that
     * contains keys and values interleaved: table[0] is kA, table[1] is vA,
     * table[2] is kB, table[3] is vB, etc. The table size must be even. It must
     * also be strictly larger than the size (the number of key-value pairs contained
     * in the map) so that at least one null key is always present.
     * @param <K> the key type
     * @param <V> the value type
     */
    static final class MapN<K,V> extends AbstractImmutableMap<K,V> {

        static final Map<?,?> EMPTY_MAP = new MapN<Object, Object>();

        final Object[] table; // pairs of key, value
        final int size; // number of pairs

        MapN(Object... input) {
            if ((input.length & 1) != 0) { // implicit nullcheck of input
                throw new InternalError("length is odd");
            }
            size = input.length >> 1;

            int len = EXPAND_FACTOR * input.length;
            len = (len + 1) & ~1; // ensure table is even length
            table = new Object[len];

            for (int i = 0; i < input.length; i += 2) {
                Object k = Objects.requireNonNull(input[i]);
                Object v = Objects.requireNonNull(input[i + 1]);
                int idx = probe(k);
                if (idx >= 0) {
                    throw new IllegalArgumentException("duplicate key: " + k);
                } else {
                    int dest = -(idx + 1);
                    table[dest] = k;
                    table[dest + 1] = v;
                }
            }
        }

        @Override
        public boolean containsKey(Object o) {
            Objects.requireNonNull(o);
            return size > 0 && probe(o) >= 0;
        }

        @Override
        public boolean containsValue(Object o) {
            Objects.requireNonNull(o);
            for (int i = 1; i < table.length; i += 2) {
                Object v = table[i];
                if (v != null && o.equals(v)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public int hashCode() {
            int hash = 0;
            for (int i = 0; i < table.length; i += 2) {
                Object k = table[i];
                if (k != null) {
                    hash += k.hashCode() ^ table[i + 1].hashCode();
                }
            }
            return hash;
        }

        @Override
        @SuppressWarnings("unchecked")
        public V get(Object o) {
            if (size == 0) {
                Objects.requireNonNull(o);
                return null;
            }
            int i = probe(o);
            if (i >= 0) {
                return (V) table[i + 1];
            } else {
                return null;
            }
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public boolean isEmpty() {
            return size == 0;
        }

        class MapNIterator extends Iterators.ImmutableIt<Map.Entry<K,V>> implements Iterator<Map.Entry<K, V>> {

            private int remaining;

            private int idx;

            MapNIterator() {
                remaining = size();
                if (remaining > 0) {
                    idx = floorMod(SALT, table.length >> 1) << 1;
                }
            }

            @Override
            public boolean hasNext() {
                return remaining > 0;
            }

            private int nextIndex() {
                int idx = this.idx;
                if (SALT >= 0) {
                    if ((idx += 2) >= table.length) {
                        idx = 0;
                    }
                } else {
                    if ((idx -= 2) < 0) {
                        idx = table.length - 2;
                    }
                }
                return this.idx = idx;
            }

            @Override
            public Map.Entry<K, V> next() {
                if (remaining > 0) {
                    int idx;
                    while (table[idx = nextIndex()] == null) {}
                    @SuppressWarnings("unchecked")
                    Map.Entry<K, V> e =
                            new KeyValueHolder<K, V>((K) table[idx], (V) table[idx+1]);
                    remaining--;
                    return e;
                } else {
                    throw new NoSuchElementException();
                }
            }
        }

        @Override
        public Set<Map.Entry<K,V>> entrySet() {
            return new AbstractSet<Map.Entry<K,V>>() {
                @Override
                public int size() {
                    return MapN.this.size;
                }

                @Override
                public Iterator<Map.Entry<K,V>> iterator() {
                    return new MapNIterator();
                }
            };
        }

        // returns index at which the probe key is present; or if absent,
        // (-i - 1) where i is location where element should be inserted.
        // Callers are relying on this method to perform an implicit nullcheck
        // of pk.
        private int probe(Object pk) {
            int idx = floorMod(pk.hashCode(), table.length >> 1) << 1;
            while (true) {
                Object ek = table[idx];
                if (ek == null) {
                    return -idx - 1;
                } else if (pk.equals(ek)) {
                    return idx;
                } else if ((idx += 2) == table.length) {
                    idx = 0;
                }
            }
        }

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            throw new InvalidObjectException("not serial proxy");
        }

        private Object writeReplace() {
            Object[] array = new Object[2 * size];
            int len = table.length;
            int dest = 0;
            for (int i = 0; i < len; i += 2) {
                if (table[i] != null) {
                    array[dest++] = table[i];
                    array[dest++] = table[i + 1];
                }
            }
            return new ColSer(ColSer.IMM_MAP, array);
        }
    }

    static void rangeCheckForAdd(int index, int size) {
        if (index < 0 || index > size) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: "
                    + size);
        }
    }

    private static int floorMod(int x, int y) {
        int mod = x % y;
        // if the signs are different and modulo not zero, adjust result
        if ((mod ^ y) < 0 && mod != 0) {
            mod += y;
        }
        return mod;
    }
}

// ---------- Serialization Proxy ----------

/**
 * A unified serialization proxy class for the unmodifiable collections.
 *
 * @serial
 * @since 9
 */
final class ColSer implements Serializable {
    private static final long serialVersionUID = 6309168927139932177L;

    static final int IMM_LIST = 1;
    static final int IMM_SET = 2;
    static final int IMM_MAP = 3;

    /**
     * Indicates the type of collection that is serialized.
     * The low order 8 bits have the value 1 for an unmodifiable
     * {@code List}, 2 for an unmodifiable {@code Set}, and 3 for
     * an unmodifiable {@code Map}. Any other value causes an
     * {@link InvalidObjectException} to be thrown. The high
     * order 24 bits are zero when an instance is serialized,
     * and they are ignored when an instance is deserialized.
     * They can thus be used by future implementations without
     * causing compatibility issues.
     *
     * <p>The tag value also determines the interpretation of the
     * transient {@code Object[] array} field.
     * For {@code List} and {@code Set}, the array's length is the size
     * of the collection, and the array contains the elements of the collection.
     * Null elements are not allowed. For {@code Set}, duplicate elements
     * are not allowed.
     *
     * <p>For {@code Map}, the array's length is twice the number of mappings
     * present in the map. The array length is necessarily even.
     * The array contains a succession of key and value pairs:
     * {@code k1, v1, k2, v2, ..., kN, vN.} Nulls are not allowed,
     * and duplicate keys are not allowed.
     *
     * @serial
     * @since 9
     */
    private final int tag;

    /**
     * @serial
     * @since 9
     */
    private transient Object[] array;

    ColSer(int t, Object... a) {
        tag = t;
        array = a;
    }

    /**
     * Reads objects from the stream and stores them
     * in the transient {@code Object[] array} field.
     *
     * @serialData
     * A nonnegative int, indicating the count of objects,
     * followed by that many objects.
     *
     * @param ois the ObjectInputStream from which data is read
     * @throws IOException if an I/O error occurs
     * @throws ClassNotFoundException if a serialized class cannot be loaded
     * @throws InvalidObjectException if the count is negative
     * @since 9
     */
    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        ois.defaultReadObject();
        int len = ois.readInt();

        if (len < 0) {
            throw new InvalidObjectException("negative length " + len);
        }

        Object[] a = new Object[len];
        for (int i = 0; i < len; i++) {
            a[i] = ois.readObject();
        }

        array = a;
    }

    /**
     * Writes objects to the stream from
     * the transient {@code Object[] array} field.
     *
     * @serialData
     * A nonnegative int, indicating the count of objects,
     * followed by that many objects.
     *
     * @param oos the ObjectOutputStream to which data is written
     * @throws IOException if an I/O error occurs
     * @since 9
     */
    private void writeObject(ObjectOutputStream oos) throws IOException {
        oos.defaultWriteObject();
        oos.writeInt(array.length);
        for (int i = 0; i < array.length; i++) {
            oos.writeObject(array[i]);
        }
    }

    /**
     * Creates and returns an unmodifiable collection from this proxy class.
     * The instance returned is created as if by calling one of the
     * static factory methods for
     * <a href="Lists.html#unmodifiable">List</a>,
     * <a href="Maps.html#unmodifiable">Map</a>, or
     * <a href="Sets.html#unmodifiable">Set</a>.
     * This proxy class is the serial form for all unmodifiable collection instances,
     * regardless of implementation type. This is necessary to ensure that the
     * existence of any particular implementation type is kept out of the
     * serialized form.
     *
     * @return a collection created from this proxy object
     * @throws InvalidObjectException if the tag value is illegal or if an exception
     *         is thrown during creation of the collection
     * @throws ObjectStreamException if another serialization error has occurred
     * @since 9
     */
    private Object readResolve() throws ObjectStreamException {
        try {
            if (array == null) {
                throw new InvalidObjectException("null array");
            }

            // use low order 8 bits to indicate "kind"
            // ignore high order 24 bits
            switch (tag & 0xff) {
                case IMM_LIST:
                    return Lists.of(array);
                case IMM_SET:
                    return Sets.of(array);
                case IMM_MAP:
                    if (array.length == 0) {
                        return ImmutableCollections.MapN.EMPTY_MAP;
                    } else if (array.length == 2) {
                        return new ImmutableCollections.Map1<Object, Object>(array[0], array[1]);
                    } else {
                        return new ImmutableCollections.MapN<Object, Object>(array);
                    }
                default:
                    throw new InvalidObjectException(String.format("invalid flags 0x%x", tag));
            }
        } catch (NullPointerException ex) {
            throw ioe(ex);
        } catch (IllegalArgumentException ex) {
            throw ioe(ex);
        }
    }

    private static InvalidObjectException ioe(RuntimeException ex) {
        InvalidObjectException ioe = new InvalidObjectException("invalid object");
        ioe.initCause(ex);
        return ioe;
    }
}
