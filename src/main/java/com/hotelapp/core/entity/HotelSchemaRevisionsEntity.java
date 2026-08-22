package com.hotelapp.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "hotel_schema_revisions")
public class HotelSchemaRevisionsEntity {

    @Id
    @Column(name = "generation")
    private Integer generation;

    @Column(name = "version")
    private Integer version;

    @Column(name = "name", columnDefinition = "text")
    private String name;

    @Column(name = "checksum", columnDefinition = "text")
    private String checksum;

    @Column(name = "applied_at")
    private java.time.OffsetDateTime applied_at;

    @Column(name = "app_build", columnDefinition = "text")
    private String app_build;

    public Integer getGeneration() { return generation; }
    public void setGeneration(Integer generation) { this.generation = generation; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }

    public java.time.OffsetDateTime getAppliedAt() { return applied_at; }
    public void setAppliedAt(java.time.OffsetDateTime applied_at) { this.applied_at = applied_at; }

    public String getAppBuild() { return app_build; }
    public void setAppBuild(String app_build) { this.app_build = app_build; }
}
