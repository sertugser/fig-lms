package com.figlms.files.api;

/**
 * Who may download the files of one component (REQ-FIL-05). The module that owns the files implements it, as Moodle
 * plugins implement a {@code <component>_pluginfile()} callback; the files module only asks. A component without a
 * policy has no readable files: access fails closed.
 */
public interface FileAccessPolicy {

    /** The component this policy answers for, for example {@code assignment}. */
    String component();

    /**
     * @param username the logged-in caller
     * @return {@code true} if the caller may download the file
     */
    boolean canRead(StoredFile file, String username);
}
