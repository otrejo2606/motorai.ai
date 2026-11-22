package ai.motoria.media.domain;

import ai.motoria.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

/**
 * One slot of the photo protocol of a country (seeded in V3, editable by ADMIN).
 */
@Entity
@Table(name = "photo_protocol_slot", schema = "media")
public class PhotoProtocolSlot extends AuditableEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @Column(name = "required", nullable = false)
    private boolean required;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "instruction_key", nullable = false, length = 120)
    private String instructionKey;

    protected PhotoProtocolSlot() {
    }

    public static PhotoProtocolSlot create(String countryCode, String code, boolean required, int displayOrder, String instructionKey) {
        PhotoProtocolSlot slot = new PhotoProtocolSlot();
        slot.id = UUID.randomUUID();
        slot.countryCode = Objects.requireNonNull(countryCode);
        slot.code = Objects.requireNonNull(code);
        slot.update(required, displayOrder, instructionKey);
        return slot;
    }

    public void update(boolean required, int displayOrder, String instructionKey) {
        this.required = required;
        this.displayOrder = displayOrder;
        this.instructionKey = Objects.requireNonNull(instructionKey);
    }

    public UUID getId() {
        return id;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public String getCode() {
        return code;
    }

    public boolean isRequired() {
        return required;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public String getInstructionKey() {
        return instructionKey;
    }
}
