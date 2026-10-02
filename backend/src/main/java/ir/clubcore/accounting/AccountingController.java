package ir.clubcore.accounting;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ir.clubcore.common.BusinessException;
import ir.clubcore.common.PageResponse;
import ir.clubcore.config.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/accounting")
@PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
public class AccountingController {

    public record AccountRequest(@NotBlank String code, @NotBlank @Size(max = 100) String name,
            @NotNull AccountType type) {
    }

    public record ExpenseRequest(@NotNull(message = "حساب هزینه را انتخاب کنید") Long accountId,
            @NotNull(message = "محل پرداخت را انتخاب کنید") Long paidFromAccountId,
            @Min(value = 1, message = "مبلغ باید مثبت باشد") long amount, LocalDate expenseDate,
            @NotBlank(message = "شرح الزامی است") @Size(max = 250) String description) {
    }

    public record EntryRequest(LocalDate entryDate, @NotBlank(message = "شرح سند الزامی است") @Size(max = 300) String description,
            @NotEmpty List<AccountingService.LineInput> lines) {
    }

    private final AccountingService service;
    private final CurrentUser currentUser;
    private final Clock clock;

    public AccountingController(AccountingService service, CurrentUser currentUser, Clock clock) {
        this.service = service;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @GetMapping("/accounts")
    public List<Account> accounts() {
        return service.accounts();
    }

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public Account createAccount(@Valid @RequestBody AccountRequest req) {
        return service.createAccount(req.code(), req.name().trim(), req.type());
    }

    @GetMapping("/journal")
    @Transactional(readOnly = true)
    public PageResponse<AccountingService.EntryDto> journal(@RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.journal(from(from), to(to), page, size);
    }

    @GetMapping("/journal/{id}")
    @Transactional(readOnly = true)
    public AccountingService.EntryDto entry(@PathVariable Long id) {
        return service.entry(id);
    }

    @PostMapping("/journal")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public AccountingService.EntryDto manualEntry(@Valid @RequestBody EntryRequest req) {
        LocalDate date = req.entryDate() != null ? req.entryDate() : LocalDate.now(clock);
        return AccountingService.EntryDto.of(
                service.post(date, req.description().trim(), "MANUAL", null, req.lines(), currentUser.id()));
    }

    @GetMapping("/expenses")
    @Transactional(readOnly = true)
    public PageResponse<AccountingService.ExpenseDto> expenses(@RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.expenses(from(from), to(to), page, size);
    }

    @PostMapping("/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountingService.ExpenseDto addExpense(@Valid @RequestBody ExpenseRequest req) {
        return service.addExpense(req.accountId(), req.paidFromAccountId(), req.amount(), req.expenseDate(),
                req.description().trim(), currentUser.id());
    }

    @GetMapping("/reports/trial-balance")
    public List<AccountingService.TrialBalanceRow> trialBalance(@RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return service.trialBalance(from(from), to(to));
    }

    @GetMapping("/reports/income-statement")
    public AccountingService.IncomeStatement incomeStatement(@RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return service.incomeStatement(from(from), to(to));
    }

    @GetMapping("/reports/series")
    public List<AccountingService.SeriesPoint> series(@RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        LocalDate f = from(from);
        LocalDate t = to(to);
        if (f.plusDays(800).isBefore(t)) {
            throw new BusinessException("بازه گزارش حداکثر ۸۰۰ روز است");
        }
        return service.series(f, t);
    }

    @GetMapping("/reports/ledger/{accountId}")
    @Transactional(readOnly = true)
    public List<AccountingService.LedgerRow> ledger(@PathVariable Long accountId,
            @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        return service.ledger(accountId, from(from), to(to));
    }

    @GetMapping("/reports/sales-by-plan")
    public List<AccountingService.PlanSales> salesByPlan(@RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return service.salesByPlan(from(from), to(to));
    }

    private LocalDate from(LocalDate from) {
        return from != null ? from : LocalDate.now(clock).minusDays(29);
    }

    private LocalDate to(LocalDate to) {
        return to != null ? to : LocalDate.now(clock);
    }
}
