package ir.clubcore.crm;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "crm_activities")
@Getter
@Setter
@NoArgsConstructor
public class CrmActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long leadId;
    private Long memberId;

    @Enumerated(EnumType.STRING)
    private ActivityType type;

    private String content;
    private Long createdBy;
    private Instant createdAt = Instant.now();
}
