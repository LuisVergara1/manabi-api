package dev.luisvergara.manabi.entity.phrase;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="phrase_meaning") @Getter @Setter @NoArgsConstructor
public class PhraseMeaning {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="public_id",nullable=false,updatable=false,length=128) private String publicId;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="phrase_id",nullable=false) private Phrase phrase;
 @Column(nullable=false) private int position;
 @Column(nullable=false,columnDefinition="text") private String label;
 @Column(nullable=false,columnDefinition="text") private String explanation;
 @Column(name="icon_type",nullable=false,length=32) private String iconType;
}
