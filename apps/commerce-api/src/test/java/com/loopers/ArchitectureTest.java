package com.loopers;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.library.Architectures;

@AnalyzeClasses(packages = "com.loopers", importOptions = ImportOption.DoNotIncludeTests.class)
public final class ArchitectureTest {
    private static final String PREFIX = "com.loopers";

    @ArchTest
    void architecture(JavaClasses classes) {
        final String application = "application";
        final String domain = "domain";
        final String infrastructure = "infrastructure";
        final String interfaces = "interfaces";
        final String support = "support";

        Architectures.layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .withOptionalLayers(true)
                .layer(application).definedBy(pkg(application))
                .layer(domain).definedBy(pkg(domain))
                .layer(infrastructure).definedBy(pkg(infrastructure))
                .layer(interfaces).definedBy(pkg(interfaces))
                .layer(support).definedBy(pkg(support))
                .whereLayer(interfaces).mayOnlyAccessLayers(application, domain, support)
                .whereLayer(application).mayOnlyAccessLayers(domain, support)
                .whereLayer(infrastructure).mayOnlyAccessLayers(domain, support)
                .whereLayer(domain).mayOnlyAccessLayers(support)
                .whereLayer(support).mayNotAccessAnyLayer()
                .check(classes);
    }

    private static String pkg(String layer) {
        return PREFIX + "." + layer + "..";
    }
}
