package dev.luisvergara.manabi.entity.phrase;
import java.util.*;
import jakarta.persistence.*;
import lombok.*;
import dev.luisvergara.manabi.enums.phrase.PhraseKind;
@Entity @Table(name="phrase") @Getter @Setter @NoArgsConstructor
public class Phrase {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="public_id",nullable=false,unique=true,updatable=false,length=128) private String publicId;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private PhraseKind kind;
 @Column(name="display_order",nullable=false) private Long displayOrder;
 @Column(nullable=false,columnDefinition="text") private String japanese;
 @Column(nullable=false,columnDefinition="text") private String kana;
 @Column(nullable=false,columnDefinition="text") private String romaji;
 @Column(nullable=false,columnDefinition="text") private String meaning;
 @Column(name="icon_type",nullable=false,length=32) private String iconType;
 @Column(nullable=false,columnDefinition="text") private String introduction;
 @Column(name="usage_note",columnDefinition="text") private String usageNote;
 @Version @Column(nullable=false) private Long version;
 @Embedded private PhrasePattern pattern;
 @ManyToMany(fetch=FetchType.LAZY)
 @JoinTable(name="phrase_category_membership",joinColumns=@JoinColumn(name="phrase_id"),inverseJoinColumns=@JoinColumn(name="category_id"))
 private Set<PhraseCategory> categories=new LinkedHashSet<>();
 @OneToMany(mappedBy="phrase",cascade=CascadeType.ALL,orphanRemoval=true) @OrderBy("displayOrder ASC, publicId ASC")
 private List<PhraseExample> examples=new ArrayList<>();
 @OneToMany(mappedBy="phrase",cascade=CascadeType.ALL,orphanRemoval=true) @OrderBy("position ASC")
 private List<PhraseMeaning> meanings=new ArrayList<>();
 @OneToMany(mappedBy="phrase",cascade=CascadeType.ALL,orphanRemoval=true) @OrderBy("position ASC")
 private List<PhraseSlot> slots=new ArrayList<>();
 @OneToMany(mappedBy="phrase",cascade=CascadeType.ALL,orphanRemoval=true) @OrderBy("position ASC")
 private List<PhraseLearningKey> learningKeys=new ArrayList<>();
 @ElementCollection @CollectionTable(name="phrase_key_point",joinColumns=@JoinColumn(name="phrase_id"))
 @OrderColumn(name="position") @Column(name="text",nullable=false,columnDefinition="text")
 private List<String> keyPoints=new ArrayList<>();
}
