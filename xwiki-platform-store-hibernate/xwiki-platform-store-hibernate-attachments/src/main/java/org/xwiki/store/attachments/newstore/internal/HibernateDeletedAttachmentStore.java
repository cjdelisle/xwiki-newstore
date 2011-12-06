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
package org.xwiki.store.attachments.newstore.internal;

import java.util.Date;
import java.util.List;

import com.xpn.xwiki.doc.DeletedAttachment;
import com.xpn.xwiki.doc.XWikiAttachment;
import com.xpn.xwiki.doc.XWikiDocument;
import javax.inject.Inject;
import javax.inject.Named;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Criteria;
import org.hibernate.criterion.Restrictions;
import org.hibernate.criterion.Order;
import org.hibernate.Session;
import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.AttachmentReference;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.store.TransactionRunnable;
import org.xwiki.store.UnexpectedException;

/**
 * A means of storing deleted attachment metadata in Hibernate.
 *
 * @version $Id$
 * @since TODO
 */
@Component
@Named("hibernate")
public class HibernateDeletedAttachmentStore implements DeletedAttachmentStore<Session>
{
    /** An order object which will sort by a field called "date" in decending order. */
    private static final Order ORDER_DATE_DESCENDING = Order.desc("date");

    /**
     * The name of the field in DeletedAttachment for the document id
     * which the attachment was associated with.
     */
    private static final String DELETED_ATTACH_DOC_ID = "docId";

    /** The name of the field in DeletedAttachment for the file name. */
    private static final String DELETED_ATTACH_FILENAME = "filename";

    /** This is a hack to get the database id from a DocumentReference, see: getDocId(). */
    private final XWikiDocument idTool = new XWikiDocument(null);

    /**
     * The entity reference serializer used to serialize the name of
     * the user who deleted the attachment.
     */
    @Inject
    private EntityReferenceSerializer<String> deleterSerializer;

    @Override
    public TransactionRunnable<Session> getDeletedAttachmentSaveRunnable(
        final XWikiAttachment attachment,
        final EntityReference deleter,
        final Date dateOfDeletion)
    {
        final String deleterStr = this.deleterSerializer.serialize(deleter);
        final HibernateDeletedAttachment trashAttach;
        try {
            trashAttach = new HibernateDeletedAttachment(attachment, deleterStr, dateOfDeletion);
        } catch (Exception e) {
            // Unless DeletedAttachment is changed, this will never happen.
            throw new UnexpectedException(
                "HibernateDeletedAttachment constructor threw an exception.", e);
        }

        return (new TransactionRunnable<Session>() {
            @Override
            protected void onRun()
            {
                this.getContext().save(trashAttach);
            }
        });
    }

    @Override
    public TransactionRunnable<Session> getDeletedAttachmentLoadRunnable(
        final AttachmentReference reference,
        final List<DeletedAttachment> output)
    {
        final Long docId = (reference != null)
            ? Long.valueOf(this.getDocId(reference.getDocumentReference())) : Long.valueOf(0);
        return (new TransactionRunnable<Session>() {
            @Override
            protected void onRun()
            {
                final Criteria c = this.getContext().createCriteria(DeletedAttachment.class);
                if (reference != null) {
                    c.add(Restrictions.eq(DELETED_ATTACH_DOC_ID, docId));
                    final String fileName = reference.getName();
                    if (!StringUtils.isBlank(fileName)) {
                        c.add(Restrictions.eq(DELETED_ATTACH_FILENAME, fileName));
                    }
                }
                output.addAll((List<DeletedAttachment>) c.addOrder(ORDER_DATE_DESCENDING).list());
            }
        });
    }

    @Override
    public TransactionRunnable<Session> getDeletedAttachmentLoadRunnable(
        final DocumentReference reference,
        final List<DeletedAttachment> output)
    {
        if (reference == null) {
            throw new NullPointerException("document reference was null");
        }
        final Long docId = Long.valueOf(this.getDocId(reference));
        return (new TransactionRunnable<Session>() {
            @Override
            protected void onRun()
            {
                final Criteria c = this.getContext().createCriteria(DeletedAttachment.class);
                c.add(Restrictions.eq(DELETED_ATTACH_DOC_ID, docId));
                output.addAll((List<DeletedAttachment>) c.addOrder(ORDER_DATE_DESCENDING).list());
            }
        });
    }

    @Override
    public TransactionRunnable<Session> getDeletedAttachmentPurgeRunnable(
        final AttachmentReference reference,
        final DeletedAttachment toPurge)
    {
        return (new TransactionRunnable<Session>() {
            @Override
            protected void onRun()
            {
                this.getContext().delete(toPurge);
            }
        });
    }

    /**
     * The a document's database id from a document reference.
     * This uses a bad method of getting the document id by setting the reference in a special
     * document to the one given then asking that document it's id.
     * It's done this way because the only way to get a document id at the moment is to have a
     * document and one synchronization to use the shared document is better than the million
     * synchronizations which happen in the XWikiDocument constructor.
     *
     * @param reference the document reference to turn into an id.
     * @return the database id for this reference.
     */
    private synchronized long getDocId(final DocumentReference reference)
    {
        idTool.setDocumentReference(reference);
        return idTool.getId();
    }
}
