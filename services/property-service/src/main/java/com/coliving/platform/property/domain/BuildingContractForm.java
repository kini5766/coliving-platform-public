package com.coliving.platform.property.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "building_contract_forms",
        uniqueConstraints = @UniqueConstraint(columnNames = {"building_id", "language"}))
public class BuildingContractForm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "building_id", nullable = false)
    private Long buildingId;

    @Column(nullable = false, length = 10)
    private String language;

    @Column(name = "form_id", nullable = false)
    private Long formId;

    protected BuildingContractForm() {}

    public BuildingContractForm(Long buildingId, String language, Long formId) {
        this.buildingId = buildingId;
        this.language = language;
        this.formId = formId;
    }

    public Long getBuildingId() { return buildingId; }
    public String getLanguage() { return language; }
    public Long getFormId() { return formId; }
}
