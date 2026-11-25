package com.coliving.platform.property.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "room_facilities")
public class RoomFacility {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id")
    private Room room;

    @Column(name = "facility_type", nullable = false, length = 50)
    private String facilityType;

    @Column(name = "facility_name", nullable = false, length = 100)
    private String facilityName;

    protected RoomFacility() {}

    public RoomFacility(String facilityType, String facilityName) {
        this.facilityType = facilityType;
        this.facilityName = facilityName;
    }

    void setRoom(Room room) { this.room = room; }

    public String getFacilityType() { return facilityType; }
    public String getFacilityName() { return facilityName; }
}
