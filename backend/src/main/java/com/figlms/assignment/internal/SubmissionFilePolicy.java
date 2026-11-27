package com.figlms.assignment.internal;

import com.figlms.course.api.AccessControl;
import com.figlms.files.api.FileAccessPolicy;
import com.figlms.files.api.StoredFile;
import com.figlms.identity.api.Capability;
import com.figlms.identity.api.UserDirectory;
import org.springframework.stereotype.Component;

/**
 * Who may download submission files (REQ-FIL-05), as Moodle {@code assignsubmission_file_pluginfile()} with
 * {@code can_view_submission()}: the student who owns the submission while they may enter the course, and graders of
 * the course.
 */
@Component
class SubmissionFilePolicy implements FileAccessPolicy {

    private final AssignmentRepository repository;
    private final AccessControl access;
    private final UserDirectory users;

    SubmissionFilePolicy(AssignmentRepository repository, AccessControl access, UserDirectory users) {
        this.repository = repository;
        this.access = access;
        this.users = users;
    }

    @Override
    public String component() {
        return SubmissionService.COMPONENT;
    }

    @Override
    public boolean canRead(StoredFile file, String username) {
        if (!SubmissionService.AREA.equals(file.area())) {
            return false;
        }
        return repository.submission(file.itemId()).flatMap(s -> repository.find(s.assignmentId()).map(a -> {
            if (access.has(username, a.courseId(), Capability.ASSIGN_GRADE)) {
                return true;
            }
            return users.findByUsername(username)
                    .filter(u -> u.id() == s.userId())
                    .filter(u -> access.isEnrolled(u.id(), a.courseId(), true))
                    .filter(u -> access.has(username, a.courseId(), Capability.ASSIGN_VIEW))
                    .isPresent();
        })).orElse(false);
    }
}
