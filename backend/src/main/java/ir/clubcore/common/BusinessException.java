package ir.clubcore.common;

import org.springframework.http.HttpStatus;

/** Domain rule violation; message is shown to the user, so it is written in Persian. */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;

    public BusinessException(String message) {
        this(HttpStatus.BAD_REQUEST, message);
    }

    public BusinessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static BusinessException notFound(String what) {
        return new BusinessException(HttpStatus.NOT_FOUND, what + " یافت نشد");
    }

    public static BusinessException forbidden() {
        return new BusinessException(HttpStatus.FORBIDDEN, "شما به این بخش دسترسی ندارید");
    }

    public static BusinessException conflict(String message) {
        return new BusinessException(HttpStatus.CONFLICT, message);
    }
}
