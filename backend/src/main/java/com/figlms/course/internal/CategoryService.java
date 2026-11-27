package com.figlms.course.internal;

import com.figlms.ApiException;
import com.figlms.course.api.AccessControl;
import com.figlms.course.api.CourseSummary;
import com.figlms.identity.api.Capability;
import com.figlms.identity.api.UserAccount;
import com.figlms.identity.api.UserDirectory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Course categories (REQ-CRS-04) and the category view (REQ-CRS-10, fix of MDL-85503).
 *
 * <p>Managing categories needs {@code moodle/category:manage}. Category-level roles are out of scope, so only the
 * site administrator has it, which matches the sample data (teacher1 does not have it).
 */
@Service
@Transactional
class CategoryService {

    static final int NAME_MAX = 255;
    static final int IDNUMBER_MAX = 100;

    /** One category in the view, with the courses the caller may see in it. */
    record CategoryView(long id, String name, String idnumber, Long parentId, int depth, String path, boolean visible,
            List<CourseRef> courses) {
    }

    record CourseRef(String shortname, String fullname, boolean visible) {
    }

    private final CourseRepository repository;
    private final UserDirectory users;
    private final AccessControl access;
    private final CourseProperties properties;

    CategoryService(CourseRepository repository, UserDirectory users, AccessControl access, CourseProperties properties) {
        this.repository = repository;
        this.users = users;
        this.access = access;
        this.properties = properties;
    }

    CourseRepository.CategoryRow create(String caller, String name, String idnumber, Long parentId, Boolean visible) {
        requireManage(caller);
        String cleanName = name == null ? "" : name.strip();
        if (cleanName.isEmpty()) {
            throw ApiException.invalid("CATEGORY_NAME_REQUIRED", "Category name is required");
        }
        if (cleanName.length() > NAME_MAX) {
            throw ApiException.invalid("CATEGORY_TOO_LONG", "Category name too long");
        }
        String cleanIdnumber = emptyToNull(idnumber);
        if (cleanIdnumber != null && cleanIdnumber.length() > IDNUMBER_MAX) {
            throw ApiException.invalid("IDNUMBER_TOO_LONG", "ID number is too long");
        }
        if (cleanIdnumber != null && repository.categoryIdnumberTaken(cleanIdnumber)) {
            throw ApiException.conflict("CATEGORY_IDNUMBER_TAKEN", "ID number is already used for another category");
        }
        CourseRepository.CategoryRow parent = parentId == null ? null : requireCategory(parentId);
        boolean own = visible == null || visible;
        boolean effective = own && (parent == null || parent.visible());
        long id = repository.insertCategory(cleanName, cleanIdnumber, parentId, parent == null ? 1 : parent.depth() + 1,
                effective, own);
        repository.setCategoryPath(id, (parent == null ? "" : parent.path()) + "/" + id);
        return repository.category(id).orElseThrow();
    }

    /** Moves a category with its whole subtree. A category cannot go into itself or into one of its subcategories. */
    CourseRepository.CategoryRow move(String caller, long id, Long newParentId) {
        requireManage(caller);
        CourseRepository.CategoryRow category = requireCategory(id);
        CourseRepository.CategoryRow parent = newParentId == null ? null : requireCategory(newParentId);
        if (parent != null && isSameOrBelow(parent, category)) {
            throw ApiException.invalid("CANNOT_MOVE_CATEGORY", "Cannot move category into itself or one of its subcategories");
        }
        List<CourseRepository.CategoryRow> subtree = repository.categories().stream()
                .filter(c -> isSameOrBelow(c, category))
                .sorted(Comparator.comparingInt(CourseRepository.CategoryRow::depth)).toList();
        Map<Long, CourseRepository.CategoryRow> updated = new HashMap<>();
        for (CourseRepository.CategoryRow c : subtree) {
            CourseRepository.CategoryRow newParent = c.id() == id ? parent : updated.get(c.parentId());
            String path = (newParent == null ? "" : newParent.path()) + "/" + c.id();
            int depth = newParent == null ? 1 : newParent.depth() + 1;
            boolean visible = c.visibleOld() && (newParent == null || newParent.visible());
            Long parentRef = newParent == null ? null : newParent.id();
            repository.updateCategoryTree(c.id(), parentRef, depth, path, visible);
            updated.put(c.id(), new CourseRepository.CategoryRow(c.id(), c.name(), c.idnumber(), parentRef, depth, path,
                    visible, c.visibleOld()));
        }
        return repository.category(id).orElseThrow();
    }

    /**
     * Deletes an empty category, or moves its content (courses and subcategories) to {@code moveContentTo} first.
     * Deleting a category together with all its courses is not offered (it would delete data of other modules).
     */
    void delete(String caller, long id, Long moveContentTo) {
        requireManage(caller);
        CourseRepository.CategoryRow category = requireCategory(id);
        if (repository.countChildren(id) > 0) {
            if (moveContentTo == null) {
                throw ApiException.conflict("CATEGORY_NOT_EMPTY",
                        "The category is not empty. Choose a category to move its content to.");
            }
            CourseRepository.CategoryRow target = requireCategory(moveContentTo);
            if (isSameOrBelow(target, category)) {
                throw ApiException.invalid("CANNOT_MOVE_CATEGORY", "Cannot move content into the category itself or below it");
            }
            List<CourseRepository.CategoryRow> children = repository.categories().stream()
                    .filter(c -> c.parentId() != null && c.parentId() == id).toList();
            repository.moveCourses(id, target.id());
            for (CourseRepository.CategoryRow child : children) {
                move(caller, child.id(), target.id());
            }
        }
        repository.deleteCategory(id);
    }

    /**
     * The categories and courses the caller may see (REQ-CRS-10).
     *
     * <ul>
     *   <li>Site administrator: everything.</li>
     *   <li>{@code browseAllCourses} on (Moodle default): visible categories, with their visible courses and the
     *       hidden courses the caller may see.</li>
     *   <li>{@code browseAllCourses} off: the visible categories that contain a course the caller is enrolled in,
     *       their parents, and only those courses. Moodle 5.3.1 shows nothing and answers 404 here (MDL-85503).</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    List<CategoryView> view(String caller) {
        UserAccount user = users.require(caller);
        List<CourseRepository.CategoryRow> categories = repository.categories();
        List<CourseSummary> courses = repository.courses();
        Map<Long, List<CourseRef>> visibleCourses = new HashMap<>();
        Set<Long> shownCategories = new HashSet<>();
        Map<Long, CourseRepository.CategoryRow> byId = new HashMap<>();
        categories.forEach(c -> byId.put(c.id(), c));

        for (CourseSummary course : courses) {
            boolean canSee = user.siteAdmin() || course.visible()
                    || access.has(caller, course.id(), Capability.COURSE_VIEW_HIDDEN);
            boolean enrolled = access.isEnrolled(user.id(), course.id(), true);
            boolean include = canSee && (user.siteAdmin() || properties.browseAllCourses() || enrolled);
            if (include) {
                visibleCourses.computeIfAbsent(course.categoryId(), k -> new ArrayList<>())
                        .add(new CourseRef(course.shortname(), course.fullname(), course.visible()));
                if (!user.siteAdmin() && !properties.browseAllCourses()) {
                    for (CourseRepository.CategoryRow c = byId.get(course.categoryId()); c != null;
                            c = c.parentId() == null ? null : byId.get(c.parentId())) {
                        shownCategories.add(c.id());
                    }
                }
            }
        }
        List<CategoryView> view = new ArrayList<>();
        for (CourseRepository.CategoryRow c : categories) {
            boolean include = user.siteAdmin()
                    || (c.visible() && (properties.browseAllCourses() || shownCategories.contains(c.id())));
            if (include) {
                view.add(new CategoryView(c.id(), c.name(), c.idnumber(), c.parentId(), c.depth(), c.path(), c.visible(),
                        List.copyOf(visibleCourses.getOrDefault(c.id(), List.of()))));
            }
        }
        return view;
    }

    @Transactional(readOnly = true)
    CategoryView viewOne(String caller, long id) {
        return view(caller).stream().filter(c -> c.id() == id).findFirst()
                .orElseThrow(() -> ApiException.notFound("UNKNOWN_CATEGORY", "Can't find data record in database table course_categories."));
    }

    CourseRepository.CategoryRow requireCategory(long id) {
        return repository.category(id).orElseThrow(() -> ApiException.notFound("UNKNOWN_CATEGORY",
                "Can't find data record in database table course_categories."));
    }

    private void requireManage(String caller) {
        if (!users.require(caller).siteAdmin()) {
            throw ApiException.forbidden("FORBIDDEN", "Sorry, but you do not currently have permissions to do that ("
                    + Capability.CATEGORY_MANAGE.moodleName() + ").");
        }
    }

    /** {@code candidate} is {@code category} itself or somewhere below it. */
    private static boolean isSameOrBelow(CourseRepository.CategoryRow candidate, CourseRepository.CategoryRow category) {
        return candidate.path().equals(category.path()) || candidate.path().startsWith(category.path() + "/");
    }

    static String emptyToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
