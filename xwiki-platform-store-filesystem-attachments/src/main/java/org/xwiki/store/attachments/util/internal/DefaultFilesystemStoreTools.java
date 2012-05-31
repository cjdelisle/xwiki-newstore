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
package org.xwiki.store.attachments.util.internal;

import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;

import com.xpn.xwiki.XWikiContext;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.phase.Initializable;
import org.xwiki.context.Execution;
import org.xwiki.model.reference.AttachmentReference;
import org.xwiki.environment.Environment;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.store.FileDeleteTransactionRunnable;
import org.xwiki.store.FileSaveTransactionRunnable;
import org.xwiki.store.locks.LockProvider;
import org.xwiki.store.StreamProvider;
import org.xwiki.store.TransactionRunnable;


/**
 * Default tools for getting files to store data in the filesystem.
 * This should be replaced by a module which provides a secure extension of java.io.File.
 *
 * @version $Id$
 * @since 3.0M2
 */
@Component
@Singleton
public class DefaultFilesystemStoreTools implements FilesystemStoreTools, Initializable
{
    /**
     * The name of the directory in the work directory where the hirearchy will be stored.
     */
    private static final String STORAGE_DIR_NAME = "storage";

    /**
     * The name of the directory where document information is stored.
     * This must have a URL illegal character in it,
     * otherwise it will be confused if/when nested spaces are implemented.
     */
    private static final String DOCUMENT_DIR_NAME = "~this";

    /**
     * The directory within each document's directory where the document's attachments are stored.
     */
    private static final String ATTACHMENT_DIR_NAME = "attachments";

    /**
     * The directory within each document's directory for attachments which have been deleted.
     */
    private static final String DELETED_ATTACHMENT_DIR_NAME = "deleted-attachments";

    /**
     * The part of the deleted attachment directory name after this is the date of deletion,
     * The part before this is the URL encoded attachment filename.
     */
    private static final String DELETED_ATTACHMENT_NAME_SEPARATOR = "-";

    /**
     * When a file is being saved, the original will be moved to the same name with this after it.
     * If the save operation fails then this file will be moved back to the regular position to come
     * as close as possible to ACID transaction handling.
     */
    private static final String BACKUP_FILE_SUFFIX = "~bak";

    /**
     * When a file is being deleted, it will be renamed with this at the end of the filename in the
     * transaction. If the transaction succeeds then the temp file will be deleted, if it fails then
     * the temp file will be renamed back to the original filename.
     */
    private static final String TEMP_FILE_SUFFIX = "~tmp";

    /**
     * Serializer used for obtaining a safe file path from a document reference.
     */
    @Inject
    @Named("path")
    private EntityReferenceSerializer<String> pathSerializer;

    /**
     * We need to get the XWiki object in order to get the work directory.
     */
    @Inject
    private Execution exec;

    /**
     * A means of acquiring locks for attachments.
     */
    @Inject
    private LockProvider lockProvider;

    /**
     * Used to get store directory.
     */
    @Inject
    private Environment environment;

    /**
     * This is the directory where all of the attachments will stored.
     */
    private File storageDir;

    /**
     * Testing Constructor.
     *
     * @param pathSerializer an EntityReferenceSerializer for generating file paths.
     * @param storageDir the directory to store the content in.
     * @param lockProvider a means of getting locks for making sure
     * only one thread accesses an attachment at a time.
     */
    public DefaultFilesystemStoreTools(final EntityReferenceSerializer<String> pathSerializer,
        final File storageDir,
        final LockProvider lockProvider)
    {
        this.pathSerializer = pathSerializer;
        this.storageDir = storageDir;
        this.lockProvider = lockProvider;
    }

    /**
     * Constructor for component manager.
     */
    public DefaultFilesystemStoreTools()
    {
    }

    @Override
    public void initialize()
    {
        this.storageDir = new File(this.environment.getPermanentDirectory(), STORAGE_DIR_NAME);
        deleteEmptyDirs(this.storageDir);
    }

    /**
     * Delete all empty directories under the given directory.
     * A directory which contains only empty directories is also considered an empty ditectory.
     *
     * @param location a directory to delete.
     * @return true if the directory existed, was empty and was deleted.
     */
    private static boolean deleteEmptyDirs(final File location)
    {
        if (location != null && location.exists() && location.isDirectory()) {
            final File[] dirs = location.listFiles();
            boolean empty = true;
            for (int i = 0; i < dirs.length; i++) {
                if (!deleteEmptyDirs(dirs[i])) {
                    empty = false;
                }
            }
            if (empty) {
                location.delete();
            }
        }
        return false;
    }

    @Override
    public DeletedAttachmentFileProvider getDeletedAttachmentFileProvider(
        final AttachmentReference reference,
        final Date deleteDate)
    {
        return new DefaultDeletedAttachmentFileProvider(
            this.getDeletedAttachmentDir(reference, deleteDate), reference.getName());
    }

    @Override
    public DeletedAttachmentFileProvider getDeletedAttachmentFileProvider(
        final String pathToDirectory)
    {
        final File attachDir = new File(this.storageDir, this.getStorageLocationPath());
        return new DefaultDeletedAttachmentFileProvider(
            attachDir, getFilenameFromDeletedAttachmentDirectory(attachDir));
    }

    @Override
    public Map<String, Map<Date, DeletedAttachmentFileProvider>>
    deletedAttachmentsForDocument(final DocumentReference docRef)
    {
        final File docDir = getDocumentDir(docRef, this.storageDir, this.pathSerializer);
        final File deletedAttachmentsDir = new File(docDir, DELETED_ATTACHMENT_DIR_NAME);
        final Map<String, Map<Date, DeletedAttachmentFileProvider>> out =
            new HashMap<String, Map<Date, DeletedAttachmentFileProvider>>();

        if (!deletedAttachmentsDir.exists()) {
            return out;
        }

        for (File file : deletedAttachmentsDir.listFiles()) {
            final String currentName = getFilenameFromDeletedAttachmentDirectory(file);
            if (out.get(currentName) == null) {
                out.put(currentName, new HashMap<Date, DeletedAttachmentFileProvider>());
            }
            out.get(currentName).put(getDeleteDateFromDeletedAttachmentDirectory(file),
                new DefaultDeletedAttachmentFileProvider(file,
                    getFilenameFromDeletedAttachmentDirectory(file)));
        }
        return out;
    }

    /**
     * @param directory the location of the data for the deleted attachment.
     * @return the name of the attachment file as extracted from the directory name.
     */
    private static String getFilenameFromDeletedAttachmentDirectory(final File directory)
    {
        final String name = directory.getName();
        final String encodedOut =
            name.substring(0, name.lastIndexOf(DELETED_ATTACHMENT_NAME_SEPARATOR));
        return GenericFileUtils.getURLDecoded(encodedOut);
    }

    /**
     * @param directory the location of the data for the deleted attachment.
     * @return the deletion date as extracted from the directory name.
     */
    private static Date getDeleteDateFromDeletedAttachmentDirectory(final File directory)
    {
        final String name = directory.getName();
        // no need to url decode this since it should only contain numbers 0-9.
        long time =
            Long.parseLong(name.substring(name.lastIndexOf(DELETED_ATTACHMENT_NAME_SEPARATOR) + 1));
        return new Date(time);
    }

    @Override
    public String getStorageLocationPath()
    {
        return this.storageDir.getAbsolutePath();
    }

    @Override
    public File getGlobalFile(final String name)
    {
        return new File(this.storageDir, "~GLOBAL_" + GenericFileUtils.getURLEncoded(name));
    }

    @Override
    public AttachmentFileProvider getAttachmentFileProvider(final AttachmentReference ref)
    {
        return new DefaultAttachmentFileProvider(this.getAttachmentDir(ref), ref.getName());
    }

    @Override
    public TransactionRunnable getSaver(final StreamProvider provider, final File saveHere)
    {
        return new FileSaveTransactionRunnable(saveHere,
                                               getTempFile(saveHere),
                                               getBackupFile(saveHere),
                                               this.getLockForFile(saveHere),
                                               provider);
    }

    @Override
    public TransactionRunnable getDeleter(final File toDelete)
    {
        return new FileDeleteTransactionRunnable(toDelete,
                                                 getTempFile(toDelete),
                                                 this.getLockForFile(toDelete));
    }

    /**
     * Get a backup file which for a given storage file.
     * This file name will never collide with any other file gotten through this interface.
     *
     * @param storageFile the file to get a backup file for.
     * @return a backup file with a name based on the name of the given file.
     */
    private static File getBackupFile(final File storageFile)
    {
        return new File(storageFile.getAbsolutePath() + BACKUP_FILE_SUFFIX);
    }

    /**
     * Get a temporary file which for a given storage file.
     * This file name will never collide with any other file gotten through this interface.
     *
     * @param storageFile the file to get a temporary file for.
     * @return a temporary file with a name based on the name of the given file.
     */
    private static File getTempFile(final File storageFile)
    {
        return new File(storageFile.getAbsolutePath() + TEMP_FILE_SUFFIX);
    }

    /**
     * Get a {@link java.util.concurrent.locks.ReadWriteLock} which is unique to the given file.
     * This method will always return the same lock for the path on the filesystem even if the
     * {@link java.io.File} object is different.
     *
     * @param toLock the file to get a lock for.
     * @return a lock for the given file.
     */
    private ReadWriteLock getLockForFile(final File toLock)
    {
        return this.lockProvider.getLock(toLock);
    }

    /**
     * Get the directory for storing files for an attachment.
     * This will look like storage/xwiki/Main/WebHome/~this/attachments/file.name/
     *
     * @param ref a reference to the attachment to get a file for.
     * @return a File representing the directory. Note: The directory may not exist.
     */
    private File getAttachmentDir(final AttachmentReference ref)
    {
        final File docDir = getDocumentDir(ref.getDocumentReference(),
                                           this.storageDir,
                                           this.pathSerializer);
        final File attachmentsDir = new File(docDir, ATTACHMENT_DIR_NAME);
        return new File(attachmentsDir, GenericFileUtils.getURLEncoded(ref.getName()));
    }

    /**
     * Get a directory for storing the contents of a deleted attachment.
     * The format is <document name>/~this/deleted-attachments/<attachment name>-<delete date>/
     * <delete date> is expressed in "unix time" so it might look like:
     * WebHome/~this/deleted-attachments/file.txt-0123456789/
     *
     * @param reference the attachment reference for the attachment to get the file for.
     * @param deleteDate the date the attachment was deleted.
     * @return a directory which will be repeatable only with the same inputs.
     */
    private File getDeletedAttachmentDir(final AttachmentReference reference,
                                         final Date deleteDate)
    {
        final File docDir = getDocumentDir(reference.getDocumentReference(),
                                           this.storageDir,
                                           this.pathSerializer);
        final File deletedAttachmentsDir = new File(docDir, DELETED_ATTACHMENT_DIR_NAME);
        final String fileName =
            reference.getName() + DELETED_ATTACHMENT_NAME_SEPARATOR + deleteDate.getTime();
        return new File(deletedAttachmentsDir, GenericFileUtils.getURLEncoded(fileName));
    }

    /**
     * Get the directory associated with this document.
     * This is a path obtained from the owner document reference, where each reference segment
     * (wiki, spaces, document name) contributes to the final path.
     * For a document called xwiki:Main.WebHome, the directory will be:
     * <code>(storageDir)/xwiki/Main/WebHome/~this/</code>
     *
     * @param docRef the DocumentReference for the document to get the directory for.
     * @param storageDir the directory to place the directory hirearcy for attachments in.
     * @param pathSerializer an EntityReferenceSerializer which will make a directory path from an
     * an EntityReference.
     * @return a file path corresponding to the attachment location; each segment in the path is
     *         URL-encoded in order to be safe.
     */
    private static File getDocumentDir(final DocumentReference docRef,
        final File storageDir,
        final EntityReferenceSerializer<String> pathSerializer)
    {
        final File path = new File(storageDir, pathSerializer.serialize(docRef));
        return new File(path, DOCUMENT_DIR_NAME);
    }
}
