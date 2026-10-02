package ir.clubcore.billing;

/** Published inside the paying transaction; listeners activate memberships, post journals and award points. */
public record InvoicePaidEvent(Invoice invoice, Payment payment) {
}
