package ir.clubcore.billing.gateway;

import org.springframework.http.HttpStatus;

import ir.clubcore.common.BusinessException;

public class GatewayException extends BusinessException {

    public GatewayException(String message) {
        super(HttpStatus.BAD_GATEWAY, message);
    }
}
