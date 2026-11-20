package dev.bytebank.common;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/** SEG-03: el perfil demo (usuarios con contraseñas públicas) nunca puede convivir con prod. */
class DemoProfileGuardTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(DemoProfileGuard.class);

    @Test
    void refusesToStartWithDemoAndProdTogether() {
        runner.withPropertyValues("spring.profiles.active=demo,prod")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause()
                        .hasMessageContaining("demo")
                        .hasMessageContaining("prod"));
    }

    @Test
    void startsWithOnlyDemo() {
        runner.withPropertyValues("spring.profiles.active=demo")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(DemoProfileGuard.class));
    }

    @Test
    void startsWithOnlyProd() {
        runner.withPropertyValues("spring.profiles.active=prod")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(DemoProfileGuard.class));
    }

    @Test
    void isLoadedByEveryServiceThatUsesCommon() {
        // common no se escanea por paquetes: sus beans solo existen si CommonAutoConfiguration los importa.
        assertThat(CommonAutoConfiguration.class.getAnnotation(Import.class).value())
                .contains(DemoProfileGuard.class);
    }
}
