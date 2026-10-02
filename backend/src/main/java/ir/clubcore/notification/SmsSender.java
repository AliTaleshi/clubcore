package ir.clubcore.notification;

public interface SmsSender {

    String name();

    boolean send(String phone, String message);
}
