package com.loopers.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "datasource.mysql-jpa.main.driver-class-name=org.h2.Driver",
    "datasource.mysql-jpa.main.jdbc-url=jdbc:h2:mem:loopers;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER",
    "datasource.mysql-jpa.main.username=sa",
    "datasource.mysql-jpa.main.password="
})
public @interface E2ETest {
}
