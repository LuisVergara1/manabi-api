package dev.luisvergara.manabi.entity.phrase;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="phrase_category") @Getter @Setter @NoArgsConstructor
public class PhraseCategory {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="public_id",nullable=false,unique=true,updatable=false,length=128) private String publicId;
 @Column(nullable=false,columnDefinition="text") private String name;
 @Column(nullable=false,columnDefinition="text") private String description;
 @Column(name="display_order",nullable=false) private Long displayOrder;
 @Column(nullable=false) private boolean enabled;
 @Version @Column(nullable=false) private Long version;
}
