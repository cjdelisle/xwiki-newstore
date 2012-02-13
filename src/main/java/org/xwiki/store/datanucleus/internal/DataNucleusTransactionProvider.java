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

import javax.inject.Named;
import javax.jdo.JDOHelper;
import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.jdo.Transaction;
import org.xwiki.component.annotation.Component;
import org.xwiki.store.TransactionProvider;
import org.xwiki.store.StartableTransactionRunnable;
import org.xwiki.store.datanucleus.DataNucleusTransaction;

/**
 * A provider for acquiring transaction based on XWikiHibernateStore.
 * This is the default provider because XWikiHibernateStore is the default storage component.
 *
 * @version $Id$
 * @since 3.2M1
 */
@Component
@Named("datanucleus")
public class DataNucleusTransactionProvider implements TransactionProvider<PersistenceManager>
{
    private final PersistenceManagerFactory factory;

    private final DataNucleusClassLoader dnClassLoader;

    public DataNucleusTransactionProvider()
    {
        this.factory = JDOHelper.getPersistenceManagerFactory("Test");
        this.dnClassLoader = new DataNucleusClassLoader(this.getClass().getClassLoader());
    }

    @Override
    public StartableTransactionRunnable<PersistenceManager> get()
    {
        return new DataNucleusTransaction(this.factory.getPersistenceManager(), this.dnClassLoader);
    }
}
