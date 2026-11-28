package ai.motoria.vehicle.domain;

import ai.motoria.common.port.OwnershipMatch;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Anomalies of the registry data against the seller. An unknown ownership (seller without a verified identity number
 * or registry without owner) is not an anomaly: the seller is asked to verify the identity, as in F1.
 * Declared versus registry differences are not compared: the declared data of F1 (price, mileage, province,
 * transmission, equipment) has no counterpart in the registry.
 */
public final class RegistryAnomalyPolicy {

    private RegistryAnomalyPolicy() {
    }

    public static List<RegistryAnomaly> detect(OwnershipMatch ownership, Collection<RegistryEncumbrance> encumbrances) {
        List<RegistryAnomaly> anomalies = new ArrayList<>();
        if (ownership == OwnershipMatch.MISMATCH) {
            anomalies.add(RegistryAnomaly.OWNER_MISMATCH);
        }
        if (encumbrances.stream().anyMatch(encumbrance -> encumbrance.getType() == EncumbranceType.PLEDGE)) {
            anomalies.add(RegistryAnomaly.PLEDGE);
        }
        return List.copyOf(anomalies);
    }
}
