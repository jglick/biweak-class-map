/*
 * The MIT License
 *
 * Copyright 2012 Jesse Glick.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

// COPY OF https://github.com/jenkinsci/jenkins-test-harness/blob/d0fc8acc9be04ba3f4b81317734f23a96626a6d4/src/main/java/org/jvnet/hudson/test/MemoryAssert.java

package com.github.jglick.biweak_class_map;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.BoundedRangeModel;
import static org.junit.Assert.*;
import org.netbeans.insane.impl.LiveEngine;
import org.netbeans.insane.live.LiveReferences;
import org.netbeans.insane.live.Path;
import org.netbeans.insane.scanner.Filter;
import org.netbeans.insane.scanner.ObjectMap;
import org.netbeans.insane.scanner.ScannerUtils;

/**
 * Static utility methods for verifying heap memory usage.
 * Uses the <a href="http://performance.netbeans.org/insane/">INSANE library</a>
 * to traverse the heap from within your test.
 * <p>Object sizes are in an idealized JVM in which pointers are 4 bytes
 * (realistic even for modern 64-bit JVMs in which {@code -XX:+UseCompressedOops} is the default)
 * but objects are aligned on 8-byte boundaries (so dropping an {@code int} field does not always save memory).
 * <p>{@code import static org.jvnet.hudson.test.MemoryAssert.*;} to use.
 */
public class MemoryAssert {

    private MemoryAssert() {}

    /**
     * <strong>Assumes Java runtime is &le; Java 8. I.e. tests will be skipped if Java 9+</strong>
     * Forces GC by causing an OOM and then verifies the given {@link WeakReference} has been garbage collected.
     * @param reference object used to verify garbage collection.
     * @param allowSoft if true, pass even if {@link SoftReference}s apparently needed to be cleared by forcing an {@link OutOfMemoryError};
     *                  if false, fail in such a case (though the failure will be slow)
     */
    public static void assertGC(WeakReference<?> reference, boolean allowSoft) {
        // Disabled on Java 9+, because below will call Netbeans Insane Engine, which in turns tries to call setAccessible
        /* TODO version-number 1.6+:
        assumeTrue(JavaSpecificationVersion.forCurrentJVM().isOlderThanOrEqualTo(JavaSpecificationVersion.JAVA_8));
        */
        /*
        assumeTrue(new VersionNumber(System.getProperty("java.specification.version")).isOlderThan(new VersionNumber("9")));
        */
        assertTrue(true); reference.get(); // preload any needed classes!
        System.err.println("Trying to collect " + reference.get() + "…");
        Set<Object[]> objects = new HashSet<Object[]>();
        int size = 1024;
        String softErr = null;
        while (reference.get() != null) {
            try {
                objects.add(new Object[size]);
                size *= 1.3;
            } catch (OutOfMemoryError ignore) {
                if (softErr != null) {
                    fail(softErr);
                } else {
                    break;
                }
            }
            System.gc();
            System.err.println("GC after allocation of size " + size);
            if (!allowSoft) {
                Object obj = reference.get();
                if (obj != null) {
                    softErr = "Apparent soft references to " + obj + ": " + fromRoots(Collections.singleton(obj), null, null, new Filter() {
                        final Field referent;
                        {
                            try {
                                referent = Reference.class.getDeclaredField("referent");
                            } catch (NoSuchFieldException x) {
                                throw new AssertionError(x);
                            }
                        }
                        @Override public boolean accept(Object obj, Object referredFrom, Field reference) {
                            return !referent.equals(reference) || !(referredFrom instanceof WeakReference);
                        }
                    }) + "; apparent weak references: " + fromRoots(Collections.singleton(obj), null, null, ScannerUtils.skipObjectsFilter(Collections.singleton(reference), true));
                    System.err.println(softErr);
                }
            }
        }
        objects = null;
        System.gc();
        Object obj = reference.get();
        if (obj == null) {
            System.err.println("Successfully collected.");
        } else {
            System.err.println("Failed to collect " + obj + ", looking for strong references…");
            Map<Object,Path> rootRefs = fromRoots(Collections.singleton(obj), null, null, ScannerUtils.skipNonStrongReferencesFilter());
            if (!rootRefs.isEmpty()) {
                fail(rootRefs.toString());
            } else {
                System.err.println("Did not find any strong references to " + obj + ", looking for soft references…");
                rootRefs = fromRoots(Collections.singleton(obj), null, null, new Filter() {
                    final Field referent;
                    {
                        try {
                            referent = Reference.class.getDeclaredField("referent");
                        } catch (NoSuchFieldException x) {
                            throw new AssertionError(x);
                        }
                    }
                    @Override public boolean accept(Object obj, Object referredFrom, Field reference) {
                        return !referent.equals(reference) || !(referredFrom instanceof WeakReference);
                    }
                });
                if (!rootRefs.isEmpty()) {
                    fail(rootRefs.toString());
                } else {
                    System.err.println("Did not find any soft references to " + obj + ", looking for weak references…");
                    rootRefs = fromRoots(Collections.singleton(obj), null, null, ScannerUtils.skipObjectsFilter(Collections.singleton(reference), true));
                    if (!rootRefs.isEmpty()) {
                        fail(rootRefs.toString());
                    } else {
                        fail("Did not find any root references to " + obj + " whatsoever. Unclear why it is not being collected.");
                    }
                }
            }
        }
    }

    /**
     * TODO {@link LiveReferences#fromRoots(Collection, Set, BoundedRangeModel, Filter) logically ANDs the {@link Filter}
     * with {@link ScannerUtils#skipNonStrongReferencesFilter}, making it useless for our purposes.
     */
    private static Map<Object,Path> fromRoots(Collection<Object> objs, Set<Object> rootsHint, BoundedRangeModel progress, Filter f) {
        LiveEngine engine = new LiveEngine(progress) {
            // TODO InsaneEngine.processClass recognizes Class → ClassLoader but fails to notify the visitor,
            // so LiveEngine will fail to find a ClassLoader held only via one of its loaded classes.
            // The following trick substitutes for adding:
            // * to recognizeClass, before queue.add(cls): objects.getID(cls)
            // * to processClass, after recognize(cl): if (objects.isKnown(cl)) visitor.visitObjectReference(objects, cls, cl, null)
            // Also Path.getField confusingly returns "<changed>" when printing the Class → ClassLoader link.
            List<Class> classes = new ArrayList<Class>();
            @Override public void visitClass(Class cls) {
                getID(cls);
                super.visitClass(cls);
                ClassLoader loader = cls.getClassLoader();
                if (loader != null) {
                    classes.add(cls);
                }
            }
            @Override public void visitObject(ObjectMap map, Object object) {
                super.visitObject(map, object);
                if (object instanceof ClassLoader) {
                    if (isKnown(object)) {
                        for (Class c : classes) {
                            if (c.getClassLoader() == object) {
                                visitObjectReference(this, c, object, /* cannot get a Field for Class.classLoader, but unused here anyway */ null);
                            }
                        }
                    }
                }
            }
        };
        try {
            Field filter = LiveEngine.class.getDeclaredField("filter");
            filter.setAccessible(true);
            filter.set(engine, f);
        } catch (Exception x) {
            // The test has already failed at this point, so AssumptionViolatedException would inappropriately mark it as a skip.
            throw new AssertionError("could not patch INSANE", x);
        }
        
        // ScannerUtils.interestingRoots includes our own ClassLoader, thus any static fields in any classes loaded in any visible class…but not in the bootstrap classpath, since this has no ClassLoader object to traverse.
        Set<Object> rootsHint2 = new HashSet<Object>();
        if (rootsHint != null) {
            rootsHint2.addAll(rootsHint);
        }
        try {
            rootsHint2.add(Class.forName("java.io.ObjectStreamClass$Caches")); // http://stackoverflow.com/a/20461446/12916 or JDK-6232010 or http://www.szegedi.org/articles/memleak3.html
            rootsHint2.add(Class.forName("java.beans.ThreadGroupContext"));
        } catch (ClassNotFoundException x) {
            x.printStackTrace();
        }
        // TODO consider also: rootsHint2.add(Thread.getAllStackTraces().keySet()); // https://stackoverflow.com/a/3018672/12916

        return engine.trace(objs, rootsHint2);
    }

}
