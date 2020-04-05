/*
 * Copyright 2020 CloudBees, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.jglick.biweak_class_map;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import org.junit.Assert;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class BiweakClassMapTest {

    private static final class Val {
        final Class<?> c;
        final int x;
        Val(Class<?> c, int x) {
            this.c = c;
            this.x = x;
        }
    }

    private static final class ValFactory implements Function<Class<?>, Val> {
        final int x;
        ValFactory(int x) {
            this.x = x;
        }
        @Override public Val apply(Class<?> c) {
            return new Val(c, x);
        }
    }

    private static BiweakClassMap<Val> m = new BiweakClassMap<>();

    @Test public void smokes() {
        Class<?> a = SelectiveClassLoader.reload(A.class);
        Class<?> b = SelectiveClassLoader.reload(B.class);
        Class<?> c = SelectiveClassLoader.reload(C.class);
        Class<?> a2 = SelectiveClassLoader.reload(A.class);
        // TODO also test multiple classes in one loader
        Val va = m.getOrCreate(a, new ValFactory(1));
        Val vb = m.getOrCreate(b, new ValFactory(1));
        Val vc = m.getOrCreate(c, new ValFactory(1));
        Val va2 = m.getOrCreate(a2, new ValFactory(1));
        assertEquals(1, m.getOrCreate(a, new ValFactory(2)).x);
        assertEquals(1, m.getOrCreate(b, new ValFactory(2)).x);
        assertEquals(1, m.getOrCreate(c, new ValFactory(2)).x);
        assertEquals(1, m.getOrCreate(a2, new ValFactory(2)).x);
        WeakReference<Class<?>> ra = new WeakReference<>(a);
        WeakReference<Val> rva = new WeakReference<>(va);
        a = null;
        va = null;
        MemoryAssert.assertGC(ra, false);
        MemoryAssert.assertGC(rva, false);
        WeakReference<Val> rvb = new WeakReference<>(vb);
        vb = null;
        assertCannotCC(rvb);
        assertEquals(1, m.getOrCreate(b, new ValFactory(3)).x);
    }

    private static void assertCannotCC(WeakReference<?> r) {
        System.err.println("Expecting to fail to collect " + r.get() + "…");
        Set<Object[]> objects = new HashSet<>();
        int size = 1024;
        while (true) {
            try {
                objects.add(new Object[size]);
                size *= 1.3;
            } catch (OutOfMemoryError ignore) {
                System.err.println("was not collected, good");
                return;
            }
            System.gc();
            System.err.println("GC after allocation of size " + size);
            Assert.assertNotNull(r.get());
        }
    }

    public static final class A {}
    public static final class B {}
    public static final class C {}

}
