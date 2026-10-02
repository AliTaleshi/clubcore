package ir.clubcore.accounting;

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
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
public class Account {

    public static final String CASH = "1110";
    public static final String BANK = "1120";
    public static final String GATEWAY = "1130";
    public static final String MEMBERSHIP_REVENUE = "4100";
    public static final String OTHER_REVENUE = "4900";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String code;
    private String name;

    @Enumerated(EnumType.STRING)
    private AccountType type;

    private boolean system;
}
