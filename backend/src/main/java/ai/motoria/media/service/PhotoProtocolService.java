package ai.motoria.media.service;

import ai.motoria.common.audit.Audited;
import ai.motoria.common.exception.InvalidRequestException;
import ai.motoria.common.exception.InvalidStateException;
import ai.motoria.common.persistence.UniqueViolations;
import ai.motoria.media.domain.PhotoProtocolSlot;
import ai.motoria.media.dto.PhotoSlotResponse;
import ai.motoria.media.dto.UpdatePhotoSlotRequest;
import ai.motoria.media.exception.MediaErrorCodes;
import ai.motoria.media.mapper.MediaMapper;
import ai.motoria.media.repository.PhotoProtocolSlotRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@ApplicationScoped
public class PhotoProtocolService {

    private static final Pattern COUNTRY = Pattern.compile("[A-Z]{2}");
    private static final Pattern SLOT_CODE = Pattern.compile("[A-Z_]{2,30}");

    @Inject
    PhotoProtocolSlotRepository photoProtocolSlotRepository;

    @Inject
    MediaMapper mediaMapper;

    public List<PhotoSlotResponse> list(String countryCode) {
        String country = normalizeCountry(countryCode);
        return photoProtocolSlotRepository.listByCountry(country).stream()
                .map(mediaMapper::toResponse)
                .toList();
    }

    @Audited
    @Transactional
    public PhotoSlotResponse upsert(String countryCode, String code, UpdatePhotoSlotRequest request) {
        String country = normalizeCountry(countryCode);
        String slotCode = code == null ? "" : code.toUpperCase(Locale.ROOT);
        if (!SLOT_CODE.matcher(slotCode).matches()) {
            throw new InvalidRequestException(MediaErrorCodes.PHOTO_PROTOCOL_INVALID, "Invalid slot code");
        }
        PhotoProtocolSlot slot = photoProtocolSlotRepository.findByCountryAndCode(country, slotCode)
                .orElseGet(() -> {
                    PhotoProtocolSlot created = PhotoProtocolSlot.create(
                            country, slotCode, request.required(), request.order(), request.instructionKey());
                    photoProtocolSlotRepository.persist(created);
                    return created;
                });
        slot.update(request.required(), request.order(), request.instructionKey());
        try {
            photoProtocolSlotRepository.flush();
        } catch (PersistenceException exception) {
            if (UniqueViolations.isUniqueViolation(exception)) {
                throw new InvalidStateException(MediaErrorCodes.PHOTO_PROTOCOL_CONFLICT,
                        "The slot was changed at the same time; reload and try again");
            }
            throw exception;
        }
        return mediaMapper.toResponse(slot);
    }

    private String normalizeCountry(String countryCode) {
        String country = countryCode == null ? "" : countryCode.toUpperCase(Locale.ROOT);
        if (!COUNTRY.matcher(country).matches()) {
            throw new InvalidRequestException(MediaErrorCodes.PHOTO_PROTOCOL_INVALID, "Invalid country code");
        }
        return country;
    }
}
