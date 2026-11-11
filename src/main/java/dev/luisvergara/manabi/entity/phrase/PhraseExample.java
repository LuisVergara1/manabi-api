package dev.luisvergara.manabi.entity.phrase;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="phrase_example") @Getter @Setter @NoArgsConstructor
public class PhraseExample {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="public_id",nullable=false,updatable=false,length=128) private String publicId;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="phrase_id",nullable=false) private Phrase phrase;
 @Column(name="display_order",nullable=false) private Long displayOrder;
 @Column(nullable=false,columnDefinition="text") private String japanese;
 @Column(nullable=false,columnDefinition="text") private String kana;
 @Column(nullable=false,columnDefinition="text") private String romaji;
 @Column(nullable=false,columnDefinition="text") private String meaning;
 @Column(name="icon_type",nullable=false,length=32) private String iconType;
 @Column(columnDefinition="text") private String situation;
}
