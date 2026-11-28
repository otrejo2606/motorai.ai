package ai.motoria.user.domain;

/**
 * LEVEL_1: email and phone (browse and contact). LEVEL_2: identity document with liveness (publish, credit, transfer).
 * LEVEL_3: certified digital signature (sign the deed).
 */
public enum IdentityLevel {
    LEVEL_1(1),
    LEVEL_2(2),
    LEVEL_3(3);

    private final int rank;

    IdentityLevel(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }
}
