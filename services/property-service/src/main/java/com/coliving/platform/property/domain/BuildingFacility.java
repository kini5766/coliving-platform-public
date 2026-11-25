package com.coliving.platform.property.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "building_facilities")
public class BuildingFacility {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "building_id")
    private Building building;

    @Column(name = "facility_type", nullable = false, length = 50)
    private String facilityType;

    @Column(name = "facility_name", nullable = false, length = 100)
    private String facilityName;

    protected BuildingFacility() {}

    public BuildingFacility(String facilityType, String facilityName) {
        this.facilityType = facilityType;
        this.facilityName = facilityName;
    }

    void setBuilding(Building building) { this.building = building; }

    public String getFacilityType() { return facilityType; }
    public String getFacilityName() { return facilityName; }
}
