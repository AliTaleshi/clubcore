package ir.clubcore.billing;

import java.time.Instant;

public record InvoiceDto(Long id, String number, Long memberId, String memberName, String memberPhone, String title,
        InvoiceKind kind, long amount, long discount, long total, InvoiceStatus status, Instant createdAt,
        Instant paidAt) {

    public static InvoiceDto of(Invoice i) {
        return new InvoiceDto(i.getId(), i.getNumber(), i.getMember().getId(), i.getMember().getFullName(),
                i.getMember().getPhone(), i.getTitle(), i.getKind(), i.getAmount(), i.getDiscount(), i.getTotal(),
                i.getStatus(), i.getCreatedAt(), i.getPaidAt());
    }
}
