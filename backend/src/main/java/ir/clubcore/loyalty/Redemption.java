package ir.clubcore.loyalty;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "redemptions")
@Getter
@Setter
@NoArgsConstructor
public class Redemption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long memberId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "reward_id")
    private Reward reward;

    private String code;

    @Enumerated(EnumType.STRING)
    private RedemptionStatus status = RedemptionStatus.ISSUED;

    private Long invoiceId;
    private Instant createdAt = Instant.now();
    private Instant usedAt;
}
