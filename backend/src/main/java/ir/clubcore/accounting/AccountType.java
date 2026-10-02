package ir.clubcore.accounting;

public enum AccountType {
    ASSET, LIABILITY, EQUITY, INCOME, EXPENSE;

    /** Assets and expenses grow with debits; the others grow with credits. */
    public boolean debitNormal() {
        return this == ASSET || this == EXPENSE;
    }
}
