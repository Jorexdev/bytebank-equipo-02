package dev.bytebank.notifications.notification;

import dev.bytebank.common.security.AuthUser;
import dev.bytebank.common.security.Role;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Validación de page y size del contrato. Fuera de rango se lanza ConstraintViolationException, que el
 * GlobalExceptionHandler de common convierte en 400. Depende de @Validated en el controlador: sin él no se valida nada.
 */
class NotificationControllerTest {

    private AnnotationConfigApplicationContext context;
    private NotificationService service;
    private NotificationController controller;

    @BeforeEach
    void setUp() {
        service = Mockito.mock(NotificationService.class);
        context = new AnnotationConfigApplicationContext();
        context.registerBean(LocalValidatorFactoryBean.class);
        context.registerBean(MethodValidationPostProcessor.class);
        context.registerBean(NotificationService.class, () -> service);
        context.registerBean(NotificationController.class);
        context.refresh();
        controller = context.getBean(NotificationController.class);

        var user = new AuthUser(2L, "ana@bytebank.test", Role.CLIENTE);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @Test
    void listsTheNotificationsOfTheAuthenticatedUser() {
        assertThatCode(() -> controller.listNotifications(0, 20)).doesNotThrowAnyException();

        verify(service).list(2L, 0, 20);
    }

    @Test
    void acceptsTheLimitsOfTheRange() {
        assertThatCode(() -> controller.listNotifications(0, 1)).doesNotThrowAnyException();
        assertThatCode(() -> controller.listNotifications(0, 100)).doesNotThrowAnyException();
    }

    @Test
    void rejectsSizeZero() {
        assertThatThrownBy(() -> controller.listNotifications(0, 0)).isInstanceOf(ConstraintViolationException.class);
        verifyNoInteractions(service);
    }

    @Test
    void rejectsSizeOver100() {
        assertThatThrownBy(() -> controller.listNotifications(0, 101)).isInstanceOf(ConstraintViolationException.class);
        verifyNoInteractions(service);
    }

    @Test
    void rejectsNegativePage() {
        assertThatThrownBy(() -> controller.listNotifications(-1, 20)).isInstanceOf(ConstraintViolationException.class);
        verifyNoInteractions(service);
    }
}
