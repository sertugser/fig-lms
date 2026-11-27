package com.figlms.advising.api;

/**
 * Result of {@link AvailabilityService#evaluate}.
 *
 * @param available         the user may open the item
 * @param shownOnCoursePage the item is listed (greyed out when not available); {@code false} = completely hidden
 * @param information       why it is not available, for example "Not available unless: The activity Assignment 1 is
 *                          marked complete"; empty when available and no restriction applies to the user
 */
public record Availability(boolean available, boolean shownOnCoursePage, String information) {

    public static Availability open() {
        return new Availability(true, true, "");
    }
}
