plugins {
    `java-library`
}

dependencies {
    implementation(project(":modules:jpa"))

    testImplementation(testFixtures(project(":modules:jpa")))
}
