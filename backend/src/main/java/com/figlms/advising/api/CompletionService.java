package com.figlms.advising.api;

/**
 * Activity and course completion (REQ-ADV-02, REQ-ADV-05). Activities register themselves and report each user's
 * completion state; the advising module keeps course completion up to date from that, right away. (Moodle waits for
 * the {@code completion_regular_task} cron run, so completion lags; see advising.md.)
 */
public interface CompletionService {

    /**
     * Registers (or renames) an activity so restrictions and completion criteria can refer to it.
     *
     * @param activityKey stable key of the activity, for example {@code assignment:12}
     */
    void registerActivity(long courseId, String activityKey, String name);

    /** The activity is gone: conditions on it are never met any more (REQ-ADV-02 criterion 3). */
    void removeActivity(String activityKey);

    /** Stores the user's state for the activity and updates the course completion of the user. */
    void recordActivityCompletion(String activityKey, long userId, CompletionState state);

    CompletionState activityState(String activityKey, long userId);

    boolean isCourseComplete(long courseId, long userId);
}
