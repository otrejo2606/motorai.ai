package ai.motoria.vehicle.domain;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Base requirement "Gravamenes y anotaciones bloqueantes": pledges, seizures, annotations and unresolved collisions
 * block the transfer. OTHER entries are shown to the analyst but do not block by themselves.
 * The listing can still be published with the warning visible.
 */
public final class TransferBlockPolicy {

    private TransferBlockPolicy() {
    }

    public static boolean blocksTransfer(EncumbranceType type) {
        return type != EncumbranceType.OTHER;
    }

    public static boolean isBlocked(Collection<RegistryEncumbrance> encumbrances) {
        return encumbrances.stream().anyMatch(RegistryEncumbrance::isBlocksTransfer);
    }

    /**
     * Comma separated encumbrance type codes (the frontend translates them), or null when nothing blocks.
     */
    public static String reason(Collection<RegistryEncumbrance> encumbrances) {
        String reason = encumbrances.stream()
                .filter(RegistryEncumbrance::isBlocksTransfer)
                .map(encumbrance -> encumbrance.getType().name())
                .distinct()
                .sorted()
                .collect(Collectors.joining(","));
        return reason.isEmpty() ? null : reason;
    }
}
