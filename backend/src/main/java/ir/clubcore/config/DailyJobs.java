package ir.clubcore.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ir.clubcore.attendance.AttendanceService;
import ir.clubcore.membership.MembershipService;

@Component
public class DailyJobs {

    private static final Logger log = LoggerFactory.getLogger(DailyJobs.class);

    private final MembershipService memberships;
    private final AttendanceService attendance;

    public DailyJobs(MembershipService memberships, AttendanceService attendance) {
        this.memberships = memberships;
        this.attendance = attendance;
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Tehran")
    public void nightly() {
        int expired = memberships.runDailyMaintenance();
        int closed = attendance.closeStaleSessions();
        log.info("Nightly maintenance: {} memberships expired, {} open visits closed", expired, closed);
    }
}
