package ir.clubcore;

import org.springframework.boot.SpringApplication;

public class TestClubcoreApplication {

	public static void main(String[] args) {
		SpringApplication.from(ClubcoreApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
