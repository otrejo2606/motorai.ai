package ai.motoria.vehicle.domain;

public enum AttributeSource {
    REGISTRY_DOCUMENT,
    REGISTRY_AGREEMENT,
    SELLER_DECLARED,
    ANALYST;

    public boolean isOfficial() {
        return this != SELLER_DECLARED;
    }
}
