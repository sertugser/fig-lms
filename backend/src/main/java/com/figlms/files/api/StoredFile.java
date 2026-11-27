package com.figlms.files.api;

import java.time.Instant;

/**
 * One stored file (Moodle {@code stored_file}). A file is named by {@code (component, area, itemId, filename)}, which
 * is unique (REQ-FIL-02); its content is shared with every other file that has the same bytes (REQ-FIL-01).
 *
 * @param component   owner module, for example {@code assignment}; decides who may read the file (REQ-FIL-05)
 * @param area        area inside the component, for example {@code submission}
 * @param itemId      id of the owning row, for example the submission attempt
 * @param contentHash SHA-256 of the content, lower-case hex
 * @param userId      who uploaded the file
 */
public record StoredFile(long id, String component, String area, long itemId, String filename, String mimetype,
        long size, String contentHash, long userId, Instant timeCreated) {
}
