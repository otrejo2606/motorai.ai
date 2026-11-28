package ai.motoria.media.mapper;

import ai.motoria.common.port.MediaSnapshot;
import ai.motoria.common.port.PhotoSlotSnapshot;
import ai.motoria.media.domain.MediaObject;
import ai.motoria.media.domain.PhotoProtocolSlot;
import ai.motoria.media.dto.MediaResponse;
import ai.motoria.media.dto.PhotoSlotResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "cdi")
public interface MediaMapper {

    @Mapping(target = "id", source = "media.id")
    @Mapping(target = "kind", source = "media.kind")
    @Mapping(target = "photoSlot", source = "media.photoSlot")
    @Mapping(target = "contentType", source = "media.contentType")
    @Mapping(target = "sizeBytes", source = "media.sizeBytes")
    @Mapping(target = "sha256", source = "media.sha256")
    @Mapping(target = "width", source = "media.width")
    @Mapping(target = "height", source = "media.height")
    @Mapping(target = "visibility", source = "media.visibility")
    @Mapping(target = "status", source = "media.status")
    @Mapping(target = "publicUrl", source = "publicUrl")
    @Mapping(target = "confirmedAt", source = "media.confirmedAt")
    MediaResponse toResponse(MediaObject media, String publicUrl);

    @Mapping(target = "order", source = "displayOrder")
    PhotoSlotResponse toResponse(PhotoProtocolSlot slot);

    @Mapping(target = "order", source = "displayOrder")
    PhotoSlotSnapshot toSnapshot(PhotoProtocolSlot slot);

    default MediaSnapshot toSnapshot(MediaObject media, String publicUrl) {
        return new MediaSnapshot(
                media.getId(),
                media.getOwnerId(),
                media.getKind().name(),
                media.getPhotoSlot(),
                media.getStatus().name(),
                media.getContentType(),
                publicUrl,
                media.getSha256());
    }
}
