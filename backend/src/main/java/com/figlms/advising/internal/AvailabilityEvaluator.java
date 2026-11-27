package com.figlms.advising.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.figlms.ApiException;
import com.figlms.advising.api.Availability;
import com.figlms.advising.api.AvailabilityService;
import com.figlms.advising.api.CompletionState;
import com.figlms.course.api.AccessControl;
import com.figlms.grading.api.GradeItemInfo;
import com.figlms.grading.api.GradebookService;
import com.figlms.identity.api.Capability;
import com.figlms.identity.api.UserAccount;
import com.figlms.identity.api.UserDirectory;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Access restrictions (REQ-ADV-01..04, 06, 07), the logic of Moodle's {@code core_availability\tree} and the
 * completion, date and grade conditions:
 *
 * <ul>
 *   <li>{@code &} needs every child, {@code |} one child. {@code !&} and {@code !|} negate the children; a NOT is
 *       pushed down with De Morgan, so {@code !&} = (NOT a) OR (NOT b) and {@code !|} = (NOT a) AND (NOT b)
 *       ({@code tree::get_logic_flags()}).</li>
 *   <li>Completion "complete" accepts complete-and-passed but not complete-and-failed; "incomplete" accepts
 *       complete-and-failed. A missing or deleted activity is never met, also under NOT.</li>
 *   <li>Date "from" is inclusive, "until" exclusive; NOT swaps the direction.</li>
 *   <li>Grade: percentage of the item's range, minimum inclusive, maximum exclusive; no grade = not met (NOT flips
 *       this, as in Moodle).</li>
 *   <li>A failed child whose {@code showc} flag is false, or a root {@code show:false}, hides the item completely.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
class AvailabilityEvaluator implements AvailabilityService {

    private sealed interface Node permits Tree, CompletionCondition, DateCondition, GradeCondition {
    }

    private record Tree(boolean negative, boolean and, List<Node> children, List<Boolean> showChildren, boolean show)
            implements Node {
    }

    private record CompletionCondition(String activityKey, CompletionState expected) implements Node {
    }

    private record DateCondition(boolean from, Instant time) implements Node {
    }

    private record GradeCondition(long itemId, Double min, Double max) implements Node {
    }

    private record Result(boolean available, boolean totallyHide, List<String> failed) {
    }

    private record Facts(long courseId, long userId) {
    }

    private final ObjectMapper json;
    private final AdvisingRepository repository;
    private final GradebookService gradebook;
    private final AccessControl access;
    private final UserDirectory users;
    private final Clock clock;

    AvailabilityEvaluator(ObjectMapper json, AdvisingRepository repository, GradebookService gradebook,
            AccessControl access, UserDirectory users, Clock clock) {
        this.json = json;
        this.repository = repository;
        this.gradebook = gradebook;
        this.access = access;
        this.users = users;
        this.clock = clock;
    }

    @Override
    public void validate(String restriction) {
        parse(restriction);
    }

    @Override
    public Availability evaluate(String restriction, long courseId, String username) {
        Optional<Tree> tree = parse(restriction);
        if (tree.isEmpty() || tree.get().children().isEmpty()) {
            return Availability.open();
        }
        UserAccount user = users.require(username);
        Result result = check(tree.get(), false, new Facts(courseId, user.id()));
        boolean bypass = access.has(username, courseId, Capability.IGNORE_AVAILABILITY);
        String information = result.available() ? "" : information(tree.get(), result);
        if (bypass) {
            return new Availability(true, true, information);
        }
        if (result.available()) {
            return Availability.open();
        }
        return result.totallyHide() ? new Availability(false, false, "") : new Availability(false, true, information);
    }

    // ---- evaluation

    private Result check(Node node, boolean not, Facts facts) {
        return switch (node) {
            case Tree tree -> checkTree(tree, not, facts);
            case CompletionCondition c -> condition(isAvailable(c, not, facts), describe(c, not));
            case DateCondition d -> condition(isAvailable(d, not), describe(d, not));
            case GradeCondition g -> condition(isAvailable(g, not, facts), describe(g, not));
        };
    }

    private Result checkTree(Tree tree, boolean not, Facts facts) {
        if (tree.children().isEmpty()) {
            return new Result(true, false, List.of());
        }
        boolean innerNot = tree.negative() != not;
        boolean and = innerNot ? !tree.and() : tree.and();
        boolean allow = and;
        boolean totallyHide = !tree.show();
        List<String> failed = new ArrayList<>();
        for (int i = 0; i < tree.children().size(); i++) {
            Result child = check(tree.children().get(i), innerNot, facts);
            if (!child.available()) {
                failed.addAll(child.failed());
                if (tree.showChildren() != null && !tree.showChildren().get(i)) {
                    totallyHide = true;
                }
                if (child.totallyHide()) {
                    totallyHide = true;
                }
            }
            if (and && !child.available()) {
                allow = false;
            } else if (!and && child.available()) {
                allow = true;
                break;
            }
        }
        return allow ? new Result(true, false, List.of()) : new Result(false, totallyHide, failed);
    }

    private static Result condition(boolean available, String description) {
        return available ? new Result(true, false, List.of()) : new Result(false, false, List.of(description));
    }

    private boolean isAvailable(CompletionCondition c, boolean not, Facts facts) {
        Optional<AdvisingRepository.ActivityRow> activity = repository.activity(c.activityKey())
                .filter(a -> !a.deleted() && a.courseId() == facts.courseId());
        if (activity.isEmpty()) {
            return false;
        }
        CompletionState state = repository.activityState(c.activityKey(), facts.userId());
        boolean allow = switch (c.expected()) {
            case COMPLETE -> state == CompletionState.COMPLETE || state == CompletionState.COMPLETE_PASS;
            case INCOMPLETE -> state == CompletionState.INCOMPLETE || state == CompletionState.COMPLETE_FAIL;
            default -> state == c.expected();
        };
        return not != allow;
    }

    private boolean isAvailable(DateCondition d, boolean not) {
        boolean from = not ? !d.from() : d.from();
        Instant now = clock.instant();
        return from ? !now.isBefore(d.time()) : now.isBefore(d.time());
    }

    private boolean isAvailable(GradeCondition g, boolean not, Facts facts) {
        Optional<Double> score = gradebook.item(g.itemId()).filter(i -> i.courseId() == facts.courseId())
                .flatMap(i -> gradebook.gradePercent(i.id(), facts.userId()));
        boolean allow = score.isPresent() && (g.min() == null || score.get() >= g.min())
                && (g.max() == null || score.get() < g.max());
        return not != allow;
    }

    // ---- information text (Moodle lang/en/availability.php and the condition plugins' strings)

    private String information(Tree root, Result result) {
        if (result.failed().size() == 1) {
            return "Not available unless: " + result.failed().get(0);
        }
        String head = root.and() != root.negative() ? "Not available unless:" : "Not available unless any of:";
        return head + " " + String.join("; ", result.failed());
    }

    private String describe(CompletionCondition c, boolean not) {
        String name = repository.activity(c.activityKey()).filter(a -> !a.deleted()).map(a -> "The activity " + a.name())
                .orElse("(Missing activity)");
        String state = switch (c.expected()) {
            case COMPLETE -> not ? "is incomplete" : "is marked complete";
            case INCOMPLETE -> not ? "is marked complete" : "is incomplete";
            case COMPLETE_PASS -> not ? "is not complete and passed" : "is complete and passed";
            case COMPLETE_FAIL -> not ? "is not complete and failed" : "is complete and failed";
        };
        return name + " " + state;
    }

    private String describe(DateCondition d, boolean not) {
        boolean from = not ? !d.from() : d.from();
        return (from ? "From " : "Until ") + DateTimeFormatter.ISO_INSTANT.format(d.time());
    }

    private String describe(GradeCondition g, boolean not) {
        String item = gradebook.item(g.itemId()).map(GradeItemInfo::name).orElse("(missing grade item)");
        return (not ? "You do not achieve a required score in " : "You achieve a required score in ") + item;
    }

    // ---- parsing

    private Optional<Tree> parse(String restriction) {
        if (restriction == null || restriction.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode root = json.readTree(restriction);
            return Optional.of(tree(root, true));
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw invalid("not valid JSON");
        }
    }

    private Tree tree(JsonNode node, boolean root) {
        String op = node.path("op").asText(null);
        if (op == null || !List.of("&", "|", "!&", "!|").contains(op)) {
            throw invalid("unknown ->op");
        }
        boolean negative = op.startsWith("!");
        boolean and = op.endsWith("&");
        JsonNode children = node.get("c");
        if (children == null || !children.isArray()) {
            throw invalid("missing ->c");
        }
        List<Boolean> showChildren = null;
        boolean show = true;
        if (root) {
            if (op.equals("&") || op.equals("!|")) {
                JsonNode showc = node.get("showc");
                if (showc == null || !showc.isArray() || showc.size() != children.size()) {
                    throw invalid("->c, ->showc mismatch");
                }
                showChildren = new ArrayList<>();
                for (JsonNode s : showc) {
                    if (!s.isBoolean()) {
                        throw invalid("->showc value not bool");
                    }
                    showChildren.add(s.asBoolean());
                }
            } else {
                JsonNode s = node.get("show");
                if (s == null || !s.isBoolean()) {
                    throw invalid("missing ->show");
                }
                show = s.asBoolean();
            }
        }
        List<Node> parsed = new ArrayList<>();
        for (JsonNode child : children) {
            parsed.add(child.has("op") ? tree(child, false) : condition(child));
        }
        return new Tree(negative, and, parsed, showChildren, show);
    }

    private Node condition(JsonNode node) {
        String type = node.path("type").asText("");
        return switch (type) {
            case "completion" -> {
                String cm = node.path("cm").asText(null);
                int e = node.path("e").asInt(-1);
                if (cm == null || e < 0 || e > 3) {
                    throw invalid("completion condition needs cm and e (0..3)");
                }
                yield new CompletionCondition(cm, CompletionState.values()[e]);
            }
            case "date" -> {
                String d = node.path("d").asText("");
                if (!d.equals(">=") && !d.equals("<")) {
                    throw invalid("date condition direction must be >= or <");
                }
                if (!node.path("t").canConvertToLong()) {
                    throw invalid("date condition needs t");
                }
                yield new DateCondition(d.equals(">="), Instant.ofEpochSecond(node.path("t").asLong()));
            }
            case "grade" -> {
                if (!node.path("id").canConvertToLong()) {
                    throw invalid("grade condition needs id");
                }
                Double min = node.hasNonNull("min") ? node.get("min").asDouble() : null;
                Double max = node.hasNonNull("max") ? node.get("max").asDouble() : null;
                yield new GradeCondition(node.get("id").asLong(), min, max);
            }
            default -> throw invalid("unknown condition type " + type);
        };
    }

    private static ApiException invalid(String detail) {
        return ApiException.invalid("INVALID_AVAILABILITY", "Invalid availability structure (" + detail + ")");
    }
}
