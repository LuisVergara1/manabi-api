package dev.luisvergara.manabi.content.editorial;

import java.io.Serializable;
import java.time.Instant;
import jakarta.persistence.*;
import lombok.*;

/** Private editorial metadata, never public package DTOs. */
public final class EditorialModels {
    private EditorialModels() {}
    public enum Kind { KANA, KANJI, VOCABULARY, VOCABULARY_CATEGORY, PHRASE, PHRASE_CATEGORY, PARTICLE }
    public enum State { PENDING, APPROVED }

    @Embeddable @Getter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
    public static class Key implements Serializable {
        @Column(nullable=false,length=120) private String namespace;
        @Enumerated(EnumType.STRING) @Column(name="entity_kind",nullable=false,length=32) private Kind kind;
        @Column(name="public_id",nullable=false,length=128) private String publicId;
        public String label(){return kind+":"+publicId;}
    }
    @Entity(name="EditorialReview") @Table(name="editorial_review") @Getter @Setter @NoArgsConstructor
    public static class Review {
        @EmbeddedId private Key id;
        @Enumerated(EnumType.STRING) @Column(nullable=false,length=12) private State state;
        @Column(name="revision_fingerprint",nullable=false,length=64) private String revisionFingerprint;
        @Column(name="approved_by",length=160) private String approvedBy;
        @Column(name="approved_at") private Instant approvedAt;
        @Version @Column(nullable=false) private Long version;
    }
    @Entity(name="EditorialBootstrapLoad") @Table(name="editorial_bootstrap_load") @Getter @Setter @NoArgsConstructor
    public static class Load {
        @Id @Column(length=64) private String id;
        @Column(nullable=false,length=120) private String namespace;
        @Column(name="manifest_sha256",nullable=false,length=64) private String manifestSha256;
        @Column(name="approved_plan_sha256",nullable=false,length=64) private String approvedPlanSha256;
        @Column(name="identities_sha256",nullable=false,length=64) private String identitiesSha256;
        @Column(name="source_hashes",nullable=false,columnDefinition="text") private String sourceHashes;
        @Column(name="applied_at",nullable=false) private Instant appliedAt;
    }
}
