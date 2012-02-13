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
package org.xwiki.store.datanucleus.test;

import java.util.Collection;

import javax.jdo.JDOHelper;
import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import org.apache.cassandra.service.EmbeddedCassandraService;
import org.junit.After;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import org.codehaus.groovy.control.Phases;
import org.codehaus.groovy.tools.GroovyClass;
import org.codehaus.groovy.control.CompilationUnit;
import javax.jdo.JDOEnhancer;
import groovy.lang.GroovyClassLoader;

@Ignore
public class AppTest
{
    private static PersistenceManagerFactory FACTORY;

    private PersistenceManager manager;

    @BeforeClass
    public static void init() throws Exception
    {
        FACTORY = JDOHelper.getPersistenceManagerFactory("Test");

        final PersistenceManager pm = FACTORY.getPersistenceManager();

        // Store some documents
        pm.makePersistent(new Document("Title1", "Alice", "ContentA", "Hello"));
        pm.makePersistent(new Document("Title2", "Bob", "ContentA", "Hi"));
        pm.makePersistent(new Document("Title3", "Charlie", "ContentB", "Goodbye"));
    }

    @Before
    public void setUp() throws Exception
    {
        this.manager = FACTORY.getPersistenceManager();
    }

    @After
    public void tearDown() throws Exception
    {
        this.manager.close();
    }

    @Test
    public void testSearchByTitle() throws Exception
    {
        searchDocuments("SELECT FROM org.xwiki.store.datanucleus.test.Document WHERE title == \"Title1\"");
    }

    @Test
    public void testSearchByAuthor() throws Exception
    {
        searchDocuments("SELECT FROM org.xwiki.store.datanucleus.test.Document WHERE author == \"Alice\"");
    }

    @Test
    public void testSearchByContent() throws Exception
    {
        searchDocuments("SELECT FROM org.xwiki.store.datanucleus.test.Document WHERE "
                        + "content == \"ContentA\"");
    }

    @Test
    public void testSearchByContentAndNonIndexed() throws Exception
    {
        searchDocuments("SELECT FROM org.xwiki.store.datanucleus.test.Document WHERE "
                        + "content == \"ContentA\" && notIndexed == \"Hello\"");
    }

    @Test
    public void testSearchByNonIndexed() throws Exception
    {
        searchDocuments("SELECT FROM org.xwiki.store.datanucleus.test.Document WHERE "
                        + "notIndexed == \"Hello\"");
    }

    @Test
    public void testSearchByTwoNonIndexed() throws Exception
    {
        searchDocuments("SELECT FROM org.xwiki.store.datanucleus.test.Document WHERE "
                        + "notIndexed == \"Hello\" && alsoNotIndexed == \"Hi\"");
    }

    @Test
    public void testStoreLoadGroovyGeneratedClass() throws Exception
    {
        // If the package is not specified the enhancer will silently ignore the class!
        final String script =
            "package org.xwiki.store.datanucleus.test;\n"
          + "import javax.jdo.annotations.IdentityType;\n"
          + "import javax.jdo.annotations.Index;\n"
          + "import javax.jdo.annotations.PersistenceCapable;\n"
          + "import javax.jdo.annotations.PrimaryKey;\n"

          + "@PersistenceCapable(identityType = IdentityType.APPLICATION)\n"
          + "public class GroovyPersist\n"
          + "{\n"
          + "    @Index\n"
          + "    public String author;\n"

          + "    public String notIndexed;\n"

          + "    @Index\n"
          + "    public String content;\n"

          + "    @PrimaryKey\n"
          + "    @Index\n"
          + "    public String title;\n"

          + "    @Index\n"
          + "    public GroovyPersist innerDocument;\n"

          + "    public String toString()\n"
          + "    {\n"
          + "        return author + ' | ' + notIndexed + ' | ' + content + ' | '"
          + "               + title + ' | ' + innerDocument.toString();\n"
          + "    }\n"
          + "}\n";

        // Compile
        final CompilationUnit cu = new CompilationUnit();
        cu.addSource("test", script);
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
        loader = new GroovyClassLoader();
        loader.defineClass(gclass.getName(), enhancedClass);

        // Create instance
        Class groovyPersistClass = loader.loadClass(gclass.getName());
        Object groovyPersist = groovyPersistClass.newInstance();
        Object groovyPersist2 = groovyPersistClass.newInstance();
        // We can't do this in java without reflection since the class doesn't exist at compile time.
        final groovy.lang.GroovyShell gs = new groovy.lang.GroovyShell();
        gs.setVariable("groovyPersist", groovyPersist);
        gs.setVariable("groovyPersist2", groovyPersist2);
        gs.evaluate("groovyPersist.author = 'Me';\n"
                  + "groovyPersist.notIndexed = 'Not indexed';\n"
                  + "groovyPersist.content = 'Generated persistance capable class!';\n"
                  + "groovyPersist.title = 'GroovyClass';\n"
                  + "groovyPersist.innerDocument = groovyPersist2;\n"
                  + "groovyPersist.innerDocument.author = 'MeMeMe';\n"
                  + "groovyPersist.innerDocument.notIndexed = 'ni';\n"
                  + "groovyPersist.innerDocument.content = 'I am a nested object, yay!';\n"
                  + "groovyPersist.innerDocument.title = 'GroovyClass#2';\n");

        // Store
        this.manager.makePersistent(groovyPersist);

        // Query

        // This doesn't work because JPQL is not supported (yet...)
        /*Collection c = (Collection)
            this.manager.newQuery("javax.jdo.query.JPQL", "SELECT doc FROM " + gclass.getName()
                                + " as doc WHERE doc.title = 'GroovyClass'").execute();*/

        // This doesn't work because querying against nested objects is not supported. (yet)
        /*Collection c = (Collection)
            this.manager.newQuery("SELECT FROM " + gclass.getName()
                                  + " WHERE innerDocument.title == 'GroovyClass#2'").execute();*/

        Collection c = (Collection)
            this.manager.newQuery("SELECT FROM " + gclass.getName()
                                  + " WHERE title == 'GroovyClass'").execute();

        Assert.assertEquals(c.size(), 1);
        //System.out.println(c.iterator().next().toString());
        Assert.assertEquals("Me | Not indexed | Generated persistance capable class! | GroovyClass | "
                              +"MeMeMe | ni | I am a nested object, yay! | GroovyClass#2 | null",
                            c.iterator().next().toString());
    }

    private void searchDocuments(final String jdoqlQuery)
    {
        System.out.println("Searching with: " + jdoqlQuery);
        for (Document doc : (Collection<Document>) manager.newQuery(jdoqlQuery).execute()) {
            System.out.println(doc.toString());
        }
        System.out.println();
    }
}
