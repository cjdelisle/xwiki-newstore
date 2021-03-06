/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.store.datanucleus.internal;

import groovy.lang.GroovyClassLoader;
import javax.jdo.JDOEnhancer;
import javax.jdo.JDOHelper;
import org.codehaus.groovy.control.Phases;
import org.codehaus.groovy.tools.GroovyClass;
import org.codehaus.groovy.control.CompilationUnit;
import org.datanucleus.jdo.JDODataNucleusEnhancer;
import org.xwiki.store.objects.PersistableClass;
import org.xwiki.store.objects.PersistableClassLoader;

/**
 * A converter which takes groovy source code and makes a PersistableClass
 * It must be annotated as PersistanceCapable otherwise compilation will fail.
 */
public class GroovyPersistableClassCompiler
{
    private final PersistableClassLoader loader;

    public GroovyPersistableClassCompiler(final PersistableClassLoader loader)
    {
        this.loader = loader;
    }

    public PersistableClass compile(final String source)
    {
        // Compile
        final CompilationUnit cu = new CompilationUnit();
        cu.addSource("UserDefinedClass", source);
        cu.compile(Phases.CLASS_GENERATION);
        final GroovyClass gclass = (GroovyClass) cu.getClasses().get(0);

        // Load
        final GroovyClassLoader groovyLoader =
            new GroovyClassLoader(
                new BlockingClassLoader(this.loader.asNativeLoader(), gclass.getName()));
        final Class cls = groovyLoader.defineClass(gclass.getName(), gclass.getBytes());

        // Enhance!
        // It is critical that if there are old versions of the same class,
        // the enhancer can only access the newest version.
        final ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();
        final byte[] enhancedClass;
        try {
            Thread.currentThread().setContextClassLoader(groovyLoader);
            final JDOEnhancer enhancer = new JDODataNucleusEnhancer();
            enhancer.addClass(gclass.getName(), gclass.getBytes());
            enhancer.enhance();
            enhancedClass = enhancer.getEnhancedBytes(gclass.getName());
        } finally {
            Thread.currentThread().setContextClassLoader(oldClassLoader);
        }

        return this.loader.definePersistableClass(gclass.getName(), enhancedClass);
    }
}
