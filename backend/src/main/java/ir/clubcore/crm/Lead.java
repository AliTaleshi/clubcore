package ir.clubcore.crm;

import java.time.Instant;
import java.time.LocalDate;

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
@Table(name = "leads")
@Getter
@Setter
@NoArgsConstructor
public class Lead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String fullName;
    private String phone;
    private String source;

    @Enumerated(EnumType.STRING)
    private LeadStatus status = LeadStatus.NEW;

    private String interest;
    private Long assignedTo;
    private LocalDate followUpDate;
    private String notes;
    private Long convertedMemberId;
    private Instant createdAt = Instant.now();
}
