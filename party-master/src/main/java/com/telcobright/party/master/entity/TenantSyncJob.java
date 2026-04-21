package com.telcobright.party.master.entity;

import com.telcobright.party.domain.EntityType;
import com.telcobright.party.domain.SyncOperation;
import com.telcobright.party.domain.SyncStatus;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "tenant_sync_job")
public class TenantSyncJob extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "tenant_id", nullable = false)
    public Long tenantId;

    @Column(name = "workflow_id", nullable = false, length = 200)
    public String workflowId;

    @Column(name = "run_id", length = 100)
    public String runId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 40)
    public EntityType entityType;

    @Column(name = "entity_id", length = 80)
    public String entityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public SyncOperation operation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public SyncStatus status;

    @Column(nullable = false)
    public Integer attempts;

    @Column(name = "started_at")
    public Instant startedAt;

    @Column(name = "finished_at")
    public Instant finishedAt;

    @Column(columnDefinition = "TEXT")
    public String error;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    public Instant createdAt;
}
