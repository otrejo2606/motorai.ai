package ai.motoria.listing.domain;

import ai.motoria.common.port.OwnershipMatch;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Publication rules (design D7, task 6.3): title and description written, vehicle REGISTRY_VERIFIED, seller identity
 * LEVEL_2 or more, seller is the registry owner (F1 has no power-of-attorney flow) and every required protocol photo.
 * Applied when the listing goes to review and again when the analyst publishes it.
 */
public final class ListingPublicationPolicy {

    public static final String CONTENT_INCOMPLETE = "LISTING_CONTENT_INCOMPLETE";
    public static final String VEHICLE_NOT_VERIFIED = "LISTING_VEHICLE_NOT_VERIFIED";
    public static final String IDENTITY_LEVEL_REQUIRED = "LISTING_IDENTITY_LEVEL_REQUIRED";
    public static final String SELLER_NOT_OWNER = "LISTING_SELLER_NOT_OWNER";
    public static final String PHOTOS_INCOMPLETE = "LISTING_PHOTOS_INCOMPLETE";
    static final int REQUIRED_IDENTITY_RANK = 2;

    private ListingPublicationPolicy() {
    }

    public static Result evaluate(boolean contentComplete, boolean vehicleVerified, int identityRank,
                                  OwnershipMatch ownership, Collection<String> requiredSlots,
                                  Collection<String> presentSlots) {
        List<String> violations = new ArrayList<>();
        if (!contentComplete) {
            violations.add(CONTENT_INCOMPLETE);
        }
        if (!vehicleVerified) {
            violations.add(VEHICLE_NOT_VERIFIED);
        }
        if (identityRank < REQUIRED_IDENTITY_RANK) {
            violations.add(IDENTITY_LEVEL_REQUIRED);
        } else if (ownership != OwnershipMatch.MATCH) {
            violations.add(SELLER_NOT_OWNER);
        }
        List<String> missingSlots = requiredSlots.stream()
                .filter(slot -> !presentSlots.contains(slot))
                .toList();
        if (!missingSlots.isEmpty()) {
            violations.add(PHOTOS_INCOMPLETE);
        }
        return new Result(violations, missingSlots);
    }

    /**
     * @param violations   violation codes in priority order; empty when the listing can be published
     * @param missingSlots required photo slots without a photo
     */
    public record Result(List<String> violations, List<String> missingSlots) {

        public boolean publishable() {
            return violations.isEmpty();
        }
    }
}
