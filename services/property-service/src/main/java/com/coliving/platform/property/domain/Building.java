package com.coliving.platform.property.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "buildings")
public class Building {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "building_name", nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 500)
    private String address;

    @Column(nullable = false, length = 100)
    private String region;

    @Column(nullable = false, length = 50)
    private String buildingType;

    @Column(nullable = false)
    private Integer totalFloors;

    private boolean parkingAvailable;
    private boolean elevatorAvailable;

    @Column(name = "description", columnDefinition = "TEXT")
    private String introduction;

    @Column(length = 30)
    private String contactPhone;

    // 대표 이미지 공개 URL (예: /property-service/property/images/{파일명})
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "building", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BuildingFacility> facilities = new ArrayList<>();

    protected Building() {}

    public Building(String name, String address) {
        this.name = name;
        this.address = address;
    }

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public void update(String name, String address, String region, String buildingType, Integer totalFloors,
                       boolean parkingAvailable, boolean elevatorAvailable,
                       String introduction, String contactPhone) {
        this.name = name;
        this.address = address;
        this.region = region;
        this.buildingType = buildingType;
        this.totalFloors = totalFloors;
        this.parkingAvailable = parkingAvailable;
        this.elevatorAvailable = elevatorAvailable;
        this.introduction = introduction;
        this.contactPhone = contactPhone;
    }

    public void clearFacilities() {
        facilities.clear();
    }

    public void addFacility(BuildingFacility f) {
        f.setBuilding(this);
        facilities.add(f);
    }

    public void changeImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public void markDeleted() {
        this.deleted = true;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public String getRegion() { return region; }
    public String getBuildingType() { return buildingType; }
    public Integer getTotalFloors() { return totalFloors; }
    public boolean isParkingAvailable() { return parkingAvailable; }
    public boolean isElevatorAvailable() { return elevatorAvailable; }
    public String getIntroduction() { return introduction; }
    public String getContactPhone() { return contactPhone; }
    public String getImageUrl() { return imageUrl; }
    public boolean isDeleted() { return deleted; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public List<BuildingFacility> getFacilities() { return facilities; }
}