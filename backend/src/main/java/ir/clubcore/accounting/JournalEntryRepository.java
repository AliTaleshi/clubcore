package ir.clubcore.accounting;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {

    @Query("select e from JournalEntry e where e.entryDate between :from and :to")
    Page<JournalEntry> between(LocalDate from, LocalDate to, Pageable pageable);

    boolean existsBySourceTypeAndSourceId(String sourceType, Long sourceId);

    java.util.Optional<JournalEntry> findFirstBySourceTypeAndSourceId(String sourceType, Long sourceId);

    /** account id, total debit, total credit for the period. */
    @Query("""
            select l.account.id, coalesce(sum(l.debit), 0), coalesce(sum(l.credit), 0) from JournalLine l
            where l.entry.entryDate between :from and :to group by l.account.id
            """)
    List<Object[]> totalsByAccount(LocalDate from, LocalDate to);

    /** date, account type, total debit, total credit — for income/expense time series. */
    @Query("""
            select l.entry.entryDate, l.account.type, coalesce(sum(l.debit), 0), coalesce(sum(l.credit), 0)
            from JournalLine l where l.entry.entryDate between :from and :to and l.account.type in ('INCOME','EXPENSE')
            group by l.entry.entryDate, l.account.type order by l.entry.entryDate
            """)
    List<Object[]> dailyIncomeExpense(LocalDate from, LocalDate to);

    @Query("""
            select l from JournalLine l join fetch l.entry e where l.account.id = :accountId
              and e.entryDate between :from and :to order by e.entryDate, e.id
            """)
    List<JournalLine> ledger(Long accountId, LocalDate from, LocalDate to);

    @Query("""
            select coalesce(sum(l.debit), 0) - coalesce(sum(l.credit), 0) from JournalLine l
            where l.account.id = :accountId and l.entry.entryDate < :before
            """)
    long debitBalanceBefore(Long accountId, LocalDate before);
}
