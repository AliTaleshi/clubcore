package ir.clubcore.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ir.clubcore.attendance.AttendanceService;
import ir.clubcore.auth.AuthHousekeeping;
import ir.clubcore.membership.MembershipService;

@Component
public class DailyJobs {

    private static final Logger log = LoggerFactory.getLogger(DailyJobs.class);

    private final MembershipService memberships;
    private final AttendanceService attendance;
    private final AuthHousekeeping auth;

    public DailyJobs(MembershipService memberships, AttendanceService attendance, AuthHousekeeping auth) {
        this.memberships = memberships;
        this.attendance = attendance;
        this.auth = auth;
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Tehran")
    public void nightly() {
        int expired = memberships.runDailyMaintenance();
        int closed = attendance.closeStaleSessions();
        int purged = auth.purge();
        log.info("Nightly maintenance: {} memberships expired, {} open visits closed, {} auth records purged",
                expired, closed, purged);
    }
}
