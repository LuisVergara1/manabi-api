package dev.luisvergara.manabi.entity.phrase;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="particle") @Getter @Setter @NoArgsConstructor
public class Particle {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="public_id",nullable=false,unique=true,updatable=false,length=128) private String publicId;
 @Column(nullable=false,columnDefinition="text") private String japanese;
 @Column(nullable=false,columnDefinition="text") private String title;
 @Column(nullable=false,columnDefinition="text") private String explanation;
 @Version @Column(nullable=false) private Long version;
}
