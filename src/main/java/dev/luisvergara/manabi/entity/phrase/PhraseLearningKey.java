package dev.luisvergara.manabi.entity.phrase;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="phrase_learning_key") @Getter @Setter @NoArgsConstructor
public class PhraseLearningKey {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="public_id",nullable=false,updatable=false,length=128) private String publicId;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="phrase_id",nullable=false) private Phrase phrase;
 @Column(nullable=false) private int position;
 @Column(name="icon_type",nullable=false,length=32) private String iconType;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="particle_id") private Particle particle;
 @Column(columnDefinition="text") private String title;
 @Column(columnDefinition="text") private String explanation;
 @Embedded private PhraseAuxiliaryExample example;
}
