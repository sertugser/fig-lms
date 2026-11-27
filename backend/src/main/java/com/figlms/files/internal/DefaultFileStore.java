package com.figlms.files.internal;

import com.figlms.ApiException;
import com.figlms.files.api.FileNames;
import com.figlms.files.api.FileStore;
import com.figlms.files.api.StoredFile;
import java.io.InputStream;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
class DefaultFileStore implements FileStore {

    private final FileRepository files;
    private final ContentStore contents;
    private final FilesProperties properties;
    private final Clock clock;

    DefaultFileStore(FileRepository files, ContentStore contents, FilesProperties properties, Clock clock) {
        this.files = files;
        this.contents = contents;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public StoredFile store(String component, String area, long itemId, String filename, String mimetype,
            InputStream content, long userId) {
        String name = FileNames.clean(filename);
        if (name.isEmpty()) {
            throw ApiException.invalid("INVALID_FILENAME", "Invalid file name");
        }
        if (name.length() > 255) {
            throw ApiException.invalid("INVALID_FILENAME", "The file name is longer than 255 characters");
        }
        ContentStore.Written written = contents.write(content);
        files.find(component, area, itemId, name).ifPresent(old -> delete(old.id()));
        String type = mimetype == null || mimetype.isBlank() ? "application/octet-stream" : mimetype;
        long id = files.insert(component, area, itemId, name, type, written.size(), written.hash(), userId,
                clock.instant());
        return files.find(id).orElseThrow();
    }

    @Override
    public List<StoredFile> list(String component, String area, long itemId) {
        return files.list(component, area, itemId);
    }

    @Override
    public Optional<StoredFile> find(long id) {
        return files.find(id);
    }

    @Override
    public InputStream open(StoredFile file) {
        return contents.read(file.contentHash());
    }

    @Override
    @Transactional
    public void delete(long id) {
        files.find(id).ifPresent(file -> {
            files.delete(id);
            removeContentIfUnused(file.contentHash());
        });
    }

    @Override
    @Transactional
    public void deleteArea(String component, String area, long itemId) {
        for (StoredFile file : files.list(component, area, itemId)) {
            delete(file.id());
        }
    }

    @Override
    @Transactional
    public void copyArea(String component, String area, long fromItemId, long toItemId) {
        for (StoredFile file : files.list(component, area, fromItemId)) {
            files.find(component, area, toItemId, file.filename()).ifPresent(old -> delete(old.id()));
            files.insert(component, area, toItemId, file.filename(), file.mimetype(), file.size(), file.contentHash(),
                    file.userId(), clock.instant());
        }
    }

    @Override
    public long maxUploadBytes(long courseBytes, long moduleBytes) {
        long max = properties.maxBytes().toBytes();
        if (courseBytes > 0 && courseBytes < max) {
            max = courseBytes;
        }
        if (moduleBytes > 0 && moduleBytes < max) {
            max = moduleBytes;
        }
        return max;
    }

    /**
     * REQ-FIL-03 (Moodle {@code is_file_removable}): the content goes only when no file row uses it any more. Checked
     * after commit, so a rollback never leaves a row without content.
     */
    private void removeContentIfUnused(String hash) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            if (!files.contentUsed(hash)) {
                contents.remove(hash);
            }
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                if (!files.contentUsed(hash)) {
                    contents.remove(hash);
                }
            }
        });
    }
}
