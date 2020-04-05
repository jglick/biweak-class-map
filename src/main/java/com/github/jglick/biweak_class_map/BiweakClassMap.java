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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Operates like a {@link WeakHashMap} keyed by {@link Class} except that the values may refer strongly to the keys.
 */
public final class BiweakClassMap<T> {

    private static final String holderName = Holder.class.getName().replace("Holder", "Hold3r");
    private static final byte[] holderBytes;
    private static final Method defineClass;
    static {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (InputStream is = BiweakClassMap.class.getClassLoader().getResourceAsStream(Holder.class.getName().replace('.', '/') + ".class")) {
            byte[] buf = new byte[4096];
            int read;
            while ((read = is.read(buf)) != -1) {
                baos.write(buf, 0, read);
            }
            holderBytes = baos.toByteArray();
            for (int i = 0; i < holderBytes.length - 5; i++) {
                if (holderBytes[i] == 'H' && holderBytes[i + 1] == 'o' && holderBytes[i + 2] == 'l' && holderBytes[i + 3] == 'd' && holderBytes[i + 4] == 'e' && holderBytes[i + 5] == 'r') {
                    holderBytes[i + 4] = '3';
                }
            }
            defineClass = ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class);
            defineClass.setAccessible(true);
        } catch (Exception x) {
            throw new ExceptionInInitializerError(x);
        }
    }

    private final WeakHashMap<Class<?>, WeakReference<T>> cache = new WeakHashMap<>();

    public T getOrCreate(Class<?> c, Supplier<T> factory) {
        return cache.computeIfAbsent(c, _c -> {
            T v = factory.get();
            ClassLoader cl = c.getClassLoader();
            Class<?> holderClass;
            try {
                try {
                    holderClass = cl.loadClass(holderName);
                } catch (ClassNotFoundException x) {
                    holderClass = (Class) defineClass.invoke(cl, holderName, holderBytes, 0, holderBytes.length);
                }
                Method register = holderClass.getDeclaredMethod("register", Object.class);
                register.setAccessible(true);
                register.invoke(null, v);
            } catch (Exception x) {
                throw new AssertionError(x);
            }
            return new WeakReference<>(v);
        }).get();
    }

    public T getOrCreate(Class<?> c, Function<Class<?>, T> factory) {
        return getOrCreate(c, () -> factory.apply(c));
    }

}
