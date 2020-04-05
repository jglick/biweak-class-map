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

import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashSet;
import java.util.Set;

final class SelectiveClassLoader extends URLClassLoader {

    static Class<?> reload(Class<?> c) {
        try {
            return new SelectiveClassLoader(c).loadClass(c.getName());
        } catch (ClassNotFoundException x) {
            throw new AssertionError(x);
        }
    }

    private final Set<String> names = new HashSet<>();

    SelectiveClassLoader(Class<?>... classes) {
        super(new URL[] {SelectiveClassLoader.class.getProtectionDomain().getCodeSource().getLocation()}, SelectiveClassLoader.class.getClassLoader());
        for (Class<?> c : classes) {
            names.add(c.getName());
        }
    }

    @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (names.contains(name)) {
            synchronized (getClassLoadingLock(name)) {
                Class<?> c = findLoadedClass(name);
                if (c == null) {
                    c = findClass(name);
                }
                if (resolve) {
                    resolveClass(c);
                }
                return c;
            }
        } else {
            return super.loadClass(name, resolve);
        }
    }

}
