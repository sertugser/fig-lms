# Module: Files (`REQ-FIL`)

| Owner | Issue | Moodle version | Status |
| --- | --- | --- | --- |
| @sertugser | (core modules PR) | 5.3.1 (Build 20261112, commit `cfe123f`) | Draft for review |

**Scope.** Storing uploaded files, naming them safely, upload limits and protected download, as far as the other modules need it (assignment submissions first). Draft areas of the file picker, repositories, previews, image resizing and the trash directory are out of scope. ADR-0001 lists file handling as one of the six Moodle sub-functions; [ADR-0002](../../adr/0002-module-dependencies-and-files.md) makes it its own module.

**Source baseline.** Rules come from reading the Moodle 5.3.1 source (paths relative to `public/`). They are checked by the backend tests named in the last column.

## Requirements

| ID | Type | Description | Moodle source | Acceptance criteria | Owner |
| --- | --- | --- | --- | --- | --- |
| REQ-FIL-01 | preserved | **Content-addressed storage.** File contents are stored once per distinct content, named by the hash of the bytes, in a two-level directory `ab/cd/abcd…`. Two files with the same bytes share one stored content. The new system uses SHA-256 instead of Moodle's SHA-1, because SHA-1 collisions can be produced on purpose. | [`file_system_filedir::get_contentdir_from_hash()`](https://github.com/moodle/moodle/blob/cfe123f26fc7071981fe949cb16cd5a08a8234f1/public/lib/filestorage/file_system_filedir.php#L185-L202); `contenthash` in [`lib/db/install.xml`](https://github.com/moodle/moodle/blob/cfe123f26fc7071981fe949cb16cd5a08a8234f1/public/lib/db/install.xml#L2659) | 1. Store the same bytes under two names: both records have the same hash and one content file exists. 2. Reading either record returns the bytes. (`FileStoreTest`) | @sertugser |
| REQ-FIL-02 | preserved | **One file per name and place.** A file is identified by component, area, item and file name; that combination is unique. Storing a file with a name that already exists in the same place replaces it. | [`file_storage::get_pathname_hash()`](https://github.com/moodle/moodle/blob/cfe123f26fc7071981fe949cb16cd5a08a8234f1/public/lib/filestorage/file_storage.php#L113-L120); unique index `pathnamehash` in [`lib/db/install.xml`](https://github.com/moodle/moodle/blob/cfe123f26fc7071981fe949cb16cd5a08a8234f1/public/lib/db/install.xml#L2688) | 1. Store `a.txt` twice in the same area: one record remains, with the second content. 2. The database rejects a duplicate even without the service check (unique constraint `fil_file_name_uq`). | @sertugser |
| REQ-FIL-03 | preserved | **Content is removed only when unused.** Deleting a file record removes the stored content only if no other record refers to the same content. The check runs after the transaction commits, so a rollback never leaves a record without content. | [`file_system::is_file_removable()`](https://github.com/moodle/moodle/blob/cfe123f26fc7071981fe949cb16cd5a08a8234f1/public/lib/filestorage/file_system.php#L226-L240) | 1. Two records share a content; delete one: the content stays. 2. Delete the second: the content is gone. (`FileStoreTest`) | @sertugser |
| REQ-FIL-04 | preserved | **Safe file names.** Control characters and the characters `` & < > " ` \| ' : \ / `` are removed from an uploaded name; `.` and `..` become empty. A name that is empty after cleaning is rejected with "Invalid file name". | [`core\param::clean_param_value_file()`](https://github.com/moodle/moodle/blob/cfe123f26fc7071981fe949cb16cd5a08a8234f1/public/lib/classes/param.php#L984-L998) (`PARAM_FILE`) | 1. `../../etc/passwd` is stored as `....etcpasswd`. 2. `..` is rejected. (`FileNamesTest`, `AssignmentFlowTest`) | @sertugser |
| REQ-FIL-05 | preserved | **Protected download.** Download needs a login (401 otherwise). The component that owns the file decides who may read it; a component that has not registered a rule gives nobody access. A missing file and a refused file get the same 404 answer, so the answer does not reveal that the file exists. Files are always sent as attachments (`Content-Disposition: attachment`, `nosniff`). For assignment submissions: the student who owns the submission while enrolled, and users with `mod/assign:grade` in the course. | [`file_pluginfile()`](https://github.com/moodle/moodle/blob/cfe123f26fc7071981fe949cb16cd5a08a8234f1/public/lib/filelib.php#L4443) and [`send_file_not_found()`](https://github.com/moodle/moodle/blob/cfe123f26fc7071981fe949cb16cd5a08a8234f1/public/lib/filelib.php#L2117); [`assignsubmission_file_pluginfile()`](https://github.com/moodle/moodle/blob/cfe123f26fc7071981fe949cb16cd5a08a8234f1/public/mod/assign/submission/file/lib.php#L38-L97) (`can_view_submission()`, "Download MUST be forced - security!") | 1. Owner and teacher: 200 with `attachment`. 2. Another student, a teacher of another course, an unknown id: 404 `FILE_NOT_FOUND`. 3. No login: 401. (`AssignmentFlowTest`) | @sertugser |
| REQ-FIL-06 | preserved | **Upload limit.** The largest allowed upload is the smallest of the site limit (`fig.files.max-bytes`, default 20 MB), the course limit and the activity limit; a limit of 0 does not count. A larger file is refused with 413 `FILE_TOO_LARGE`. | [`get_max_upload_file_size()`](https://github.com/moodle/moodle/blob/cfe123f26fc7071981fe949cb16cd5a08a8234f1/public/lib/moodlelib.php#L6337-L6367) | 1. Site only: 20 MB. 2. Course 5000, activity 1000: 1000. 3. Activity above the site limit: site limit. (`FileStoreTest`) | @sertugser |
| REQ-FIL-07 | new | **Contents stay outside the web root and the database.** Contents live under `fig.files.root` (default `./data/files`, `FIG_FILES_ROOT`), never in a public folder, and are written through a temporary file that is moved into place, so a half-written upload is never visible under its hash. | Moodle keeps `filedir` in `$CFG->dataroot` (outside the web root) and writes through a temp file in `file_system_filedir::add_file_from_path()`; the new system keeps both rules | 1. After an upload no `.tmp` file remains in the root. (`FileStoreTest`) 2. The download path goes through `GET /api/files/{id}` only. | @sertugser |

## Data model

| Table | Purpose | Moodle table | Key columns |
| --- | --- | --- | --- |
| `fil_file` | One record per stored file | `files` | `component`, `area`, `item_id`, `filename` (unique together), `content_hash` (SHA-256, indexed), `size_bytes`, `mimetype`, `user_id → idn_user`, `time_created` |

Moodle also stores a `contextid` and a `filepath` per file. The new system leaves both out: the owning component knows its context (for example the submission knows its course), and no module needs folders inside an area yet.

## API

| Call | Who | Result |
| --- | --- | --- |
| `GET /api/files/{id}` | logged in, allowed by the owning component | the file as an attachment; 404 `FILE_NOT_FOUND` otherwise |
| `FileStore` (Java API) | other modules | `store`, `list`, `find`, `open`, `delete`, `deleteArea`, `copyArea`, `maxUploadBytes` |
| `FileAccessPolicy` (Java SPI) | the module that owns a component | `canRead(file, username)`; implemented by `assignment` for submission files |
