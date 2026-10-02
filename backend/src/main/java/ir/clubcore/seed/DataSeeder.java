package ir.clubcore.seed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ir.clubcore.config.AppProperties;
import ir.clubcore.plan.PlanRepository;
import ir.clubcore.user.Role;
import ir.clubcore.user.UserRepository;
import ir.clubcore.user.UserService;

/** Ensures an admin account exists and optionally loads demo data on an empty database. */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final AppProperties props;
    private final UserRepository users;
    private final UserService userService;
    private final PlanRepository plans;
    private final DemoData demo;

    public DataSeeder(AppProperties props, UserRepository users, UserService userService, PlanRepository plans,
            DemoData demo) {
        this.props = props;
        this.users = users;
        this.userService = userService;
        this.plans = plans;
        this.demo = demo;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppProperties.Bootstrap b = props.bootstrap();
        if (users.countByRole(Role.ADMIN) == 0) {
            userService.create(b.adminPhone(), b.adminName(), Role.ADMIN, b.adminPassword());
            log.info("Created bootstrap admin {}", b.adminPhone());
        }
        if (b.seedDemo() && plans.count() == 0) {
            demo.load();
            log.info("Demo data loaded");
        }
    }
}
