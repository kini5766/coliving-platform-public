package com.coliving.platform.property.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Page;

/** 매물 서비스 요청·응답 DTO 모음 */
public final class PropertyDtos {

    private PropertyDtos() {}

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
        public static <T> PageResponse<T> of(Page<T> p) {
            return new PageResponse<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
        }
    }

    /** 프론트 Facility { category, name } 과 동일한 JSON 필드명. */
    public record FacilityDto(@NotBlank @Size(max = 30) String category, @NotBlank @Size(max = 100) String name) {}

    // ── 사용자 ────────────────────────────────────────
    public record BuildingSearchCondition(String keyword, String region, Long minMonthlyFee, Long maxMonthlyFee,
                                          Long maxDeposit, String roomType) {}

    public record BuildingSummary(Long id, String name, String address, Long lowestMonthlyFee, Long lowestDeposit,
                                  Integer availableRoomCount, String imageUrl) {}

    public record RoomSummary(Long id, String roomName, String roomType, String leaseType, long monthlyFee,
                              long deposit, long managementFee, Integer floor, BigDecimal areaM2, Integer capacity,
                              Boolean available) {}

    public record BuildingDetail(Long id, String name, String address, String region, String buildingType, Integer totalFloors,
                                 boolean parkingAvailable, boolean elevatorAvailable, String introduction,
                                 String contactPhone, String imageUrl, List<FacilityDto> facilities,
                                 List<RoomSummary> rooms) {}

    public record RoomDetail(Long id, Long buildingId, String buildingName, String address,
                             /** 대표 사진. 호실 사진 저장이 없어 현재는 null. */
                             String thumbnailUrl,
                             String roomName, String roomType,
                             String roomStructure, String leaseType, long monthlyFee, long deposit,
                             long managementFee, Integer floor, BigDecimal areaM2, Integer capacity,
                             Boolean available, String buildingType, boolean parkingAvailable,
                             boolean elevatorAvailable, String contactPhone, String buildingImageUrl,
                             List<FacilityDto> facilities) {}

    // ── 관리자 ────────────────────────────────────────
    public record AdminBuildingSummary(Long id, String name, String address, int totalRooms, Integer availableRooms,
                                       OffsetDateTime updatedAt) {}

    public record AdminRoomSummary(Long id, String roomName, String roomType, long monthlyFee, long deposit,
                                   long managementFee, Integer floor) {}

    public record ContractFormDto(@NotBlank @Pattern(regexp = "ko|en") String language, @NotNull @Positive Long formId) {}

    public record AdminBuildingDetail(Long id, String name, String address, String region, String buildingType, Integer totalFloors,
                                      boolean parkingAvailable, boolean elevatorAvailable, String introduction,
                                      String contactPhone, String imageUrl, List<FacilityDto> facilities,
                                      List<ContractFormDto> contractForms, List<AdminRoomSummary> rooms) {}

    public record BuildingSaveRequest(@NotBlank @Size(max = 200) String name,
                                      @NotBlank @Size(max = 500) String address,
                                      @NotBlank @Size(max = 100) String region,
                                      @NotBlank @Size(max = 50) String buildingType,
                                      @NotNull @Positive Integer totalFloors,
                                      boolean parkingAvailable,
                                      boolean elevatorAvailable,
                                      String introduction,
                                      @Size(max = 30) String contactPhone,
                                      @Valid List<FacilityDto> facilities) {}

    public record ContractFormsRequest(@Valid @NotNull List<ContractFormDto> forms) {}

    public record AdminRoomDetail(Long id, Long buildingId, String roomName, String roomType, String roomStructure,
                                  String leaseType, long monthlyFee, long deposit, long managementFee, Integer floor,
                                  BigDecimal areaM2, Integer capacity, List<FacilityDto> facilities) {}

    public record RoomSaveRequest(@NotNull Long buildingId,
                                  @NotBlank @Size(max = 100) String roomName,
                                  @NotBlank @Size(max = 50) String roomType,
                                  @Size(max = 100) String roomStructure,
                                  /** MONTHLY(월세) / SHORT_TERM(단기) / OTHER(기타) */
                                  @NotBlank @Pattern(regexp = "MONTHLY|SHORT_TERM|OTHER") String leaseType,
                                  @PositiveOrZero long monthlyFee,
                                  @PositiveOrZero long deposit,
                                  @PositiveOrZero long managementFee,
                                  @NotNull Integer floor,
                                  @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal areaM2,
                                  /** 비우면 1 */
                                  @Positive @Max(32767) Integer capacity,
                                  @Valid List<FacilityDto> facilities) {}

    public record IdResponse(Long id) {}

    /** 이미지 업로드 결과: 화면에서 게이트웨이 주소 뒤에 붙여 쓰는 공개 URL */
    public record ImageResponse(String imageUrl) {}
}