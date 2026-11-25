package com.coliving.platform.property.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "rooms")
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "building_id")
    private Building building;

    @Column(nullable = false, length = 100)
    private String roomName;

    @Column(nullable = false, length = 50)
    private String roomType;

    @Column(length = 100)
    private String roomStructure;

    /** MONTHLY / SHORT_TERM / OTHER */
    @Column(name = "rental_type", nullable = false, length = 30)
    private String leaseType;

    @Column(nullable = false)
    private long monthlyFee;

    @Column(nullable = false)
    private long deposit;

    @Column(nullable = false)
    private long managementFee;

    @Column(name = "floor_number", nullable = false)
    private Integer floor;

    @Column(name = "area", nullable = false, precision = 10, scale = 2)
    private BigDecimal areaM2;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(nullable = false)
    private Integer capacity;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RoomFacility> facilities = new ArrayList<>();

    protected Room() {}

    public Room(Building building) {
        this.building = building;
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

    public void update(Building building, String roomName, String roomType, String roomStructure, String leaseType,
                       long monthlyFee, long deposit, long managementFee,
                       Integer floor, BigDecimal areaM2, Integer capacity) {
        this.building = building;
        this.roomName = roomName;
        this.roomType = roomType;
        this.roomStructure = roomStructure;
        this.leaseType = leaseType;
        this.monthlyFee = monthlyFee;
        this.deposit = deposit;
        this.managementFee = managementFee;
        this.floor = floor;
        this.areaM2 = areaM2;
        this.capacity = capacity;
    }

    public void clearFacilities() {
        facilities.clear();
    }

    public void addFacility(RoomFacility f) {
        f.setRoom(this);
        facilities.add(f);
    }

    public void markDeleted() { this.deleted = true; }

    public Long getId() { return id; }
    public Building getBuilding() { return building; }
    public String getRoomName() { return roomName; }
    public String getRoomType() { return roomType; }
    public String getRoomStructure() { return roomStructure; }
    public String getLeaseType() { return leaseType; }
    public long getMonthlyFee() { return monthlyFee; }
    public long getDeposit() { return deposit; }
    public long getManagementFee() { return managementFee; }
    public Integer getFloor() { return floor; }
    public BigDecimal getAreaM2() { return areaM2; }
    public Integer getCapacity() { return capacity; }
    public boolean isDeleted() { return deleted; }
    public List<RoomFacility> getFacilities() { return facilities; }
}
