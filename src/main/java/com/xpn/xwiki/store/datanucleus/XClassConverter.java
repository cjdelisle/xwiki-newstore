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
package com.xpn.xwiki.doc;

import com.xpn.xwiki.objects.classes.BaseClass;
import com.xpn.xwiki.objects.classes.PropertyClass;
import com.xpn.xwiki.objects.BaseProperty;
import com.xpn.xwiki.objects.LargeStringProperty;
import com.xpn.xwiki.objects.StringProperty;
import com.xpn.xwiki.objects.StringListProperty;
import com.xpn.xwiki.objects.DBStringListProperty;
import com.xpn.xwiki.objects.IntegerProperty;
import com.xpn.xwiki.objects.FloatProperty;
import com.xpn.xwiki.objects.DoubleProperty;
import com.xpn.xwiki.objects.LongProperty;
import com.xpn.xwiki.objects.DateProperty;
import groovy.lang.GroovyClassLoader;
import javax.jdo.JDOEnhancer;
import javax.jdo.JDOHelper;
import org.codehaus.groovy.control.Phases;
import org.codehaus.groovy.tools.GroovyClass;
import org.codehaus.groovy.control.CompilationUnit;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.store.datanucleus.internal.JavaIdentifierEscaper;

/**
 * A converter which takes an XWiki class and converts it to a PersistanceCapable
 * Java class so that it can be stored using JDO.
 */
public class XClassConverter
{
    public Class convert(final BaseClass xwikiClass)
    {
        return convertClass(xwikiClass);
    }

    private static Class convertClass(final BaseClass xwikiClass)
    {
        // Generate source
        final StringBuilder sb = new StringBuilder();
        writeClass(xwikiClass, sb);
        final String source = sb.toString();

        // Compile
        final CompilationUnit cu = new CompilationUnit();
        cu.addSource("UserDefinedXWikiClass", source);
        cu.compile(Phases.CLASS_GENERATION);
        final GroovyClass gclass = (GroovyClass) cu.getClasses().get(0);

        // Load
        GroovyClassLoader loader = new GroovyClassLoader();
        loader.defineClass(gclass.getName(), gclass.getBytes());

        // Enhance!
        final JDOEnhancer enhancer = JDOHelper.getEnhancer();
        enhancer.setClassLoader(loader);
        enhancer.addClass(gclass.getName(), gclass.getBytes());
        enhancer.enhance();
        final byte[] enhancedClass = enhancer.getEnhancedBytes(gclass.getName());

        // Reload, we need a new loader since the unenhanced class is loaded already.
        return new GroovyClassLoader().defineClass(gclass.getName(), enhancedClass);
    }

    private static void writeClass(final BaseClass xwikiClass, final StringBuilder writeTo)
    {
        final EntityReference docRef = xwikiClass.getDocumentReference();
        final EntityReference spaceRef = docRef.getParent();

        // package
        writeTo.append("package ");
        writeReference(spaceRef, writeTo);
        writeTo.append(";\n");

        writeTo.append("\n");

        // imports
        writeTo.append("import javax.jdo.annotations.IdentityType;\n");
        writeTo.append("import javax.jdo.annotations.Index;\n");
        writeTo.append("import javax.jdo.annotations.PersistenceCapable;\n");
        writeTo.append("import javax.jdo.annotations.PrimaryKey;\n");

        writeTo.append("\n");

        // persistance capaible...
        writeTo.append("@PersistanceCapable\n");

        // class name
        writeTo.append("public class ");
        writeTo.append(JavaIdentifierEscaper.escape(docRef.getName()));
        writeTo.append("\n{\n");

        writeFields(xwikiClass, writeTo);

        // closer
        writeTo.append("}\n");
    }

    /** Write all of the field entries for the property. */
    private static void writeFields(final BaseClass xwikiClass, final StringBuilder writeTo)
    {
        final String[] fieldNames = xwikiClass.getPropertyNames();
        for (int i = 0; i < fieldNames.length; i++) {
            final PropertyClass field = (PropertyClass) xwikiClass.getField(fieldNames[i]);
            final BaseProperty prop = field.newProperty();
            if (fieldNames[i] != null) {
                writeField(fieldNames[i], prop, writeTo);
            }
        }
    }

    /**
     * Write a field corrisponding to a class property/field.
     * for example:
     * public String myName;
     *
     * @param fieldName the myName part.
     * @param BaseProperty the String part.
     * @param writeTo the place to write the output to.
     */
    private static void writeField(final String fieldName,
                                   final BaseProperty prop,
                                   final StringBuilder writeTo)
    {
        writeTo.append("\n@Index");
        writeTo.append("\npublic ");
        Class propClass = prop.getClass();
        if (propClass == StringProperty.class
            || propClass == LargeStringProperty.class
            || propClass == StringListProperty.class)
        {
            writeTo.append("String");

        } else if (propClass == IntegerProperty.class) {
            writeTo.append("Integer");

        } else if (propClass == DateProperty.class) {
            writeTo.append("Date");

        } else if (propClass == DBStringListProperty.class) {
            writeTo.append("List<String>");

        } else if (propClass == DoubleProperty.class) {
            writeTo.append("Double");

        } else if (propClass == FloatProperty.class) {
            writeTo.append("Float");

        } else if (propClass == LongProperty.class) {
            writeTo.append("Long");

        } else {
            throw new RuntimeException("Encountered a " + prop.getClass().getName()
                                       + " property which is not handled.");
        };
        writeTo.append(" ");
        writeTo.append(JavaIdentifierEscaper.escape(fieldName));
        writeTo.append(";\n");
    }
    
    /** Write the space reference as dot delimniated, used to create the package name. */
    private static void writeReference(final EntityReference ref, final StringBuilder writeTo)
    {
        if (ref.getParent() != null) {
            writeReference(ref.getParent(), writeTo);
            writeTo.append(".");
        }
        writeTo.append(JavaIdentifierEscaper.escape(ref.getName()));
    }
}
