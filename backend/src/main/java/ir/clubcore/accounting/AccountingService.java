package ir.clubcore.accounting;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ir.clubcore.billing.Invoice;
import ir.clubcore.billing.InvoiceKind;
import ir.clubcore.billing.InvoicePaidEvent;
import ir.clubcore.billing.Payment;
import ir.clubcore.billing.PaymentMethod;
import ir.clubcore.common.BusinessException;
import ir.clubcore.common.Paging;
import ir.clubcore.common.PageResponse;
import ir.clubcore.membership.MembershipRepository;

@Service
public class AccountingService {

    public record LineInput(Long accountId, long debit, long credit) {
    }

    public record LineDto(Long accountId, String accountCode, String accountName, long debit, long credit) {
    }

    public record EntryDto(Long id, LocalDate entryDate, String description, String sourceType, Long sourceId,
            List<LineDto> lines, long total) {
        static EntryDto of(JournalEntry e) {
            List<LineDto> lines = e.getLines().stream().map(l -> new LineDto(l.getAccount().getId(),
                    l.getAccount().getCode(), l.getAccount().getName(), l.getDebit(), l.getCredit())).toList();
            return new EntryDto(e.getId(), e.getEntryDate(), e.getDescription(), e.getSourceType(), e.getSourceId(),
                    lines, lines.stream().mapToLong(LineDto::debit).sum());
        }
    }

    public record ExpenseDto(Long id, Long accountId, String accountName, Long paidFromAccountId,
            String paidFromName, long amount, LocalDate expenseDate, String description, Long journalEntryId) {
        static ExpenseDto of(Expense x) {
            return new ExpenseDto(x.getId(), x.getAccount().getId(), x.getAccount().getName(),
                    x.getPaidFrom().getId(), x.getPaidFrom().getName(), x.getAmount(), x.getExpenseDate(),
                    x.getDescription(), x.getJournalEntryId());
        }
    }

    public record TrialBalanceRow(Long accountId, String code, String name, AccountType type, long debit,
            long credit, long balance) {
    }

    public record IncomeStatement(LocalDate from, LocalDate to, List<TrialBalanceRow> income,
            List<TrialBalanceRow> expenses, long totalIncome, long totalExpense, long netProfit) {
    }

    public record SeriesPoint(LocalDate date, long income, long expense) {
    }

    public record LedgerRow(LocalDate date, Long entryId, String description, long debit, long credit,
            long balance) {
    }

    public record PlanSales(String plan, long count, long revenue) {
    }

    private final AccountRepository accounts;
    private final JournalEntryRepository entries;
    private final ExpenseRepository expenses;
    private final MembershipRepository memberships;
    private final Clock clock;

    public AccountingService(AccountRepository accounts, JournalEntryRepository entries, ExpenseRepository expenses,
            MembershipRepository memberships, Clock clock) {
        this.accounts = accounts;
        this.entries = entries;
        this.expenses = expenses;
        this.memberships = memberships;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- posting

    /** Posts a balanced journal entry; rejects empty, one-sided or unbalanced entries. */
    @Transactional
    public JournalEntry post(LocalDate date, String description, String sourceType, Long sourceId,
            List<LineInput> lines, Long userId) {
        validate(lines);
        JournalEntry e = new JournalEntry();
        e.setEntryDate(date);
        e.setDescription(description);
        e.setSourceType(sourceType);
        e.setSourceId(sourceId);
        e.setCreatedBy(userId);
        for (LineInput l : lines) {
            Account a = accounts.findById(l.accountId()).orElseThrow(() -> BusinessException.notFound("حساب"));
            e.addLine(a, l.debit(), l.credit());
        }
        return entries.save(e);
    }

    static void validate(List<LineInput> lines) {
        if (lines == null || lines.size() < 2) {
            throw new BusinessException("سند باید حداقل دو ردیف داشته باشد");
        }
        long debit = 0;
        long credit = 0;
        for (LineInput l : lines) {
            if (l.debit() < 0 || l.credit() < 0 || (l.debit() > 0 && l.credit() > 0)
                    || (l.debit() == 0 && l.credit() == 0)) {
                throw new BusinessException("هر ردیف سند باید فقط بدهکار یا فقط بستانکار با مبلغ مثبت باشد");
            }
            debit += l.debit();
            credit += l.credit();
        }
        if (debit != credit) {
            throw new BusinessException("جمع بدهکار و بستانکار سند برابر نیست");
        }
    }

    @EventListener
    @Transactional
    public void onInvoicePaid(InvoicePaidEvent event) {
        Invoice invoice = event.invoice();
        Payment payment = event.payment();
        if (invoice.getTotal() == 0 || payment.getMethod() == PaymentMethod.FREE) {
            return;
        }
        String debitCode = switch (payment.getMethod()) {
            case CASH -> Account.CASH;
            case POS -> Account.BANK;
            default -> Account.GATEWAY;
        };
        String creditCode = invoice.getKind() == InvoiceKind.MEMBERSHIP ? Account.MEMBERSHIP_REVENUE
                : Account.OTHER_REVENUE;
        post(LocalDate.now(clock), "دریافت وجه فاکتور " + invoice.getNumber() + " - " + invoice.getMember().getFullName(),
                "PAYMENT", payment.getId(),
                List.of(new LineInput(byCode(debitCode).getId(), invoice.getTotal(), 0),
                        new LineInput(byCode(creditCode).getId(), 0, invoice.getTotal())),
                payment.getRecordedBy());
    }

    @Transactional
    public ExpenseDto addExpense(Long accountId, Long paidFromId, long amount, LocalDate date, String description,
            Long userId) {
        Account expense = account(accountId);
        Account from = account(paidFromId);
        if (expense.getType() != AccountType.EXPENSE) {
            throw new BusinessException("حساب انتخاب‌شده از نوع هزینه نیست");
        }
        if (from.getType() != AccountType.ASSET) {
            throw new BusinessException("محل پرداخت باید یک حساب دارایی (صندوق یا بانک) باشد");
        }
        if (amount <= 0) {
            throw new BusinessException("مبلغ هزینه باید مثبت باشد");
        }
        LocalDate d = date != null ? date : LocalDate.now(clock);
        Expense x = new Expense();
        x.setAccount(expense);
        x.setPaidFrom(from);
        x.setAmount(amount);
        x.setExpenseDate(d);
        x.setDescription(description);
        x.setCreatedBy(userId);
        expenses.save(x);
        JournalEntry e = post(d, "هزینه: " + description, "EXPENSE", x.getId(),
                List.of(new LineInput(expense.getId(), amount, 0), new LineInput(from.getId(), 0, amount)), userId);
        x.setJournalEntryId(e.getId());
        return ExpenseDto.of(x);
    }

    @Transactional
    public Account createAccount(String code, String name, AccountType type) {
        if (!code.matches("\\d{4,6}")) {
            throw new BusinessException("کد حساب باید ۴ تا ۶ رقم باشد");
        }
        if (accounts.existsByCode(code)) {
            throw BusinessException.conflict("این کد حساب قبلاً ثبت شده است");
        }
        Account a = new Account();
        a.setCode(code);
        a.setName(name);
        a.setType(type);
        return accounts.save(a);
    }

    // ---------------------------------------------------------------- queries

    public List<Account> accounts() {
        return accounts.findAllByOrderByCodeAsc();
    }

    public PageResponse<EntryDto> journal(LocalDate from, LocalDate to, int page, int size) {
        return PageResponse.of(entries.between(from, to, Paging.of(page, size, 100,
                Sort.by(Sort.Direction.DESC, "entryDate", "id"))), EntryDto::of);
    }

    public EntryDto entry(Long id) {
        return EntryDto.of(entries.findById(id).orElseThrow(() -> BusinessException.notFound("سند")));
    }

    public PageResponse<ExpenseDto> expenses(LocalDate from, LocalDate to, int page, int size) {
        return PageResponse.of(expenses.findByExpenseDateBetween(from, to, Paging.of(page, size, 100,
                Sort.by(Sort.Direction.DESC, "expenseDate", "id"))), ExpenseDto::of);
    }

    /**
     * Debit/credit columns are the period's turnover (so they always balance). The balance column is the cumulative
     * balance at {@code to} for balance-sheet accounts and the period result for income/expense accounts.
     */
    public List<TrialBalanceRow> trialBalance(LocalDate from, LocalDate to) {
        Map<Long, long[]> period = totals(from, to);
        Map<Long, long[]> cumulative = totals(LocalDate.of(1900, 1, 1), to);
        List<TrialBalanceRow> rows = new ArrayList<>();
        for (Account a : accounts.findAllByOrderByCodeAsc()) {
            long[] t = period.getOrDefault(a.getId(), new long[] {0, 0});
            boolean profitAndLoss = a.getType() == AccountType.INCOME || a.getType() == AccountType.EXPENSE;
            long[] b = profitAndLoss ? t : cumulative.getOrDefault(a.getId(), new long[] {0, 0});
            long balance = a.getType().debitNormal() ? b[0] - b[1] : b[1] - b[0];
            rows.add(new TrialBalanceRow(a.getId(), a.getCode(), a.getName(), a.getType(), t[0], t[1], balance));
        }
        return rows;
    }

    private Map<Long, long[]> totals(LocalDate from, LocalDate to) {
        Map<Long, long[]> totals = new HashMap<>();
        for (Object[] row : entries.totalsByAccount(from, to)) {
            totals.put((Long) row[0], new long[] {((Number) row[1]).longValue(), ((Number) row[2]).longValue()});
        }
        return totals;
    }

    public IncomeStatement incomeStatement(LocalDate from, LocalDate to) {
        List<TrialBalanceRow> tb = trialBalance(from, to);
        List<TrialBalanceRow> income = tb.stream().filter(r -> r.type() == AccountType.INCOME).toList();
        List<TrialBalanceRow> expense = tb.stream().filter(r -> r.type() == AccountType.EXPENSE).toList();
        long ti = income.stream().mapToLong(TrialBalanceRow::balance).sum();
        long te = expense.stream().mapToLong(TrialBalanceRow::balance).sum();
        return new IncomeStatement(from, to, income, expense, ti, te, ti - te);
    }

    /** Daily income and expense totals, with zero-filled days, for charts. */
    public List<SeriesPoint> series(LocalDate from, LocalDate to) {
        TreeMap<LocalDate, long[]> map = new TreeMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            map.put(d, new long[2]);
        }
        for (Object[] row : entries.dailyIncomeExpense(from, to)) {
            LocalDate d = (LocalDate) row[0];
            AccountType type = (AccountType) row[1];
            long debit = ((Number) row[2]).longValue();
            long credit = ((Number) row[3]).longValue();
            long[] v = map.computeIfAbsent(d, k -> new long[2]);
            if (type == AccountType.INCOME) {
                v[0] += credit - debit;
            } else {
                v[1] += debit - credit;
            }
        }
        return map.entrySet().stream().map(e -> new SeriesPoint(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .toList();
    }

    public List<LedgerRow> ledger(Long accountId, LocalDate from, LocalDate to) {
        Account a = account(accountId);
        long sign = a.getType().debitNormal() ? 1 : -1;
        long balance = sign * entries.debitBalanceBefore(accountId, from);
        List<LedgerRow> rows = new ArrayList<>();
        for (JournalLine l : entries.ledger(accountId, from, to)) {
            balance += sign * (l.getDebit() - l.getCredit());
            rows.add(new LedgerRow(l.getEntry().getEntryDate(), l.getEntry().getId(), l.getEntry().getDescription(),
                    l.getDebit(), l.getCredit(), balance));
        }
        return rows;
    }

    public List<PlanSales> salesByPlan(LocalDate from, LocalDate to) {
        var zone = clock.getZone();
        return memberships.salesByPlan(from.atStartOfDay(zone).toInstant(), to.plusDays(1).atStartOfDay(zone).toInstant())
                .stream().map(r -> new PlanSales((String) r[0], ((Number) r[1]).longValue(), ((Number) r[2]).longValue()))
                .toList();
    }

    private Account account(Long id) {
        return accounts.findById(id).orElseThrow(() -> BusinessException.notFound("حساب"));
    }

    private Account byCode(String code) {
        return accounts.findByCode(code).orElseThrow(() -> new IllegalStateException("Missing account " + code));
    }
}
