package com.figlms.files.api;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

/** Store, list, read and delete files (Moodle {@code file_storage}). */
public interface FileStore {

    /**
     * Stores the file, replacing a file of the same name in the same place.
     *
     * @param filename cleaned with {@link FileNames#clean} first
     * @throws com.figlms.ApiException 400 {@code INVALID_FILENAME} if nothing is left of the name after cleaning
     */
    StoredFile store(String component, String area, long itemId, String filename, String mimetype, InputStream content,
            long userId);

    /** Files of one area, sorted by file name. */
    List<StoredFile> list(String component, String area, long itemId);

    Optional<StoredFile> find(long id);

    /** The content; the caller closes the stream. */
    InputStream open(StoredFile file);

    /** Deletes the file; its content goes too once no other file uses it (REQ-FIL-03). */
    void delete(long id);

    /** Deletes every file of the area (Moodle {@code delete_area_files}). */
    void deleteArea(String component, String area, long itemId);

    /** Copies every file of one area to another, sharing the content (Moodle {@code copy_area_files}). */
    void copyArea(String component, String area, long fromItemId, long toItemId);

    /**
     * Largest upload allowed (REQ-FIL-06, Moodle {@code get_max_upload_file_size}): the smallest of the site limit
     * and the given limits; a limit of 0 or less does not count.
     */
    long maxUploadBytes(long courseBytes, long moduleBytes);
}
