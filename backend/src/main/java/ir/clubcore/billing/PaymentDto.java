package ir.clubcore.billing;

import java.time.Instant;

import ir.clubcore.billing.gateway.GatewayType;

public record PaymentDto(Long id, Long invoiceId, String invoiceNumber, String invoiceTitle, long amount,
        PaymentMethod method, GatewayType gateway, PaymentStatus status, String refId, String cardPan,
        Instant createdAt, Instant paidAt) {

    public static PaymentDto of(Payment p) {
        return new PaymentDto(p.getId(), p.getInvoice().getId(), p.getInvoice().getNumber(), p.getInvoice().getTitle(),
                p.getAmount(), p.getMethod(), p.getGateway(), p.getStatus(), p.getRefId(), p.getCardPan(),
                p.getCreatedAt(), p.getPaidAt());
    }
}
