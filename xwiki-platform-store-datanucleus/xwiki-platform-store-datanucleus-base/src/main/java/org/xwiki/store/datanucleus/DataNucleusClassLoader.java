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
package org.xwiki.store.datanucleus;

import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.jdo.JDOObjectNotFoundException;
import org.xwiki.store.objects.PersistableClass;
import org.xwiki.store.objects.AbstractPersistableClassLoader;

/**
 * The DataNucleusClassLoader is designed to load java classes from the data store.
 */
public class DataNucleusClassLoader extends AbstractPersistableClassLoader
{
    private final ThreadLocal<PersistenceManager> managers = new ThreadLocal<PersistenceManager>();

    /**
     * The Constructor.
     *
     * @param factory the means of persisting and loading classes.
     * @param parent the ClassLoader which will be used to try loading before trying to load from storage.
     */
    public DataNucleusClassLoader(final ClassLoader parent)
    {
        super(parent);
    }

    public void setPersistenceManager(final PersistenceManager manager)
    {
        this.managers.set(manager);
    }

    public PersistenceManager removePersistenceManager()
    {
        final PersistenceManager out = this.managers.get();
        this.managers.remove();
        return out;
    }

    protected PersistableClass getClassFromStorage(final String name) throws ClassNotFoundException
    {
        final PersistenceManager manager = this.managers.get();
        try {
            return manager.getObjectById(PersistableClass.class, name);
        } catch (JDOObjectNotFoundException e) {
            throw new ClassNotFoundException();
        } catch (NullPointerException e) {
            throw new NullPointerException("No PersistenceManager was set for this thread, "
                                           + "is this ClassLoader being used outside of a "
                                           + "TransactionRunnable?");
        }
    }
}
