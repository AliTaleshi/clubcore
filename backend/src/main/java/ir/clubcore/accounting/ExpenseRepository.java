package ir.clubcore.accounting;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    Page<Expense> findByExpenseDateBetween(LocalDate from, LocalDate to, Pageable pageable);
}
