package com.servicedesk.ui;

import com.servicedesk.user.DeskUserRole;
import com.servicedesk.user.DeskUserSummary;
import com.servicedesk.user.UserManagementService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Route("users")
@PageTitle("User management | Service Desk")
@RolesAllowed("ADMIN")
public class UserManagementView extends VerticalLayout {
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final UserManagementService users;
    private final Authentication actor;
    private final Grid<DeskUserSummary> grid = new Grid<>(DeskUserSummary.class, false);

    public UserManagementView(UserManagementService users) {
        this.users = users;
        actor = SecurityContextHolder.getContext().getAuthentication();
        setSizeFull();
        configureGrid();
        Button create = new Button("Create user", event -> openCreateDialog());
        Button home = new Button("Back to requests", event -> getUI().ifPresent(ui -> ui.navigate("")));
        HorizontalLayout header = new HorizontalLayout(new H1("User management"), create, home);
        header.setAlignItems(Alignment.CENTER);
        add(header, grid);
        setFlexGrow(1, grid);
        refresh();
    }

    private void configureGrid() {
        grid.addColumn(DeskUserSummary::username).setHeader("Username").setAutoWidth(true);
        grid.addColumn(DeskUserSummary::role).setHeader("Role").setAutoWidth(true);
        grid.addColumn(user -> user.enabled() ? "Enabled" : "Disabled").setHeader("Account status");
        grid.addColumn(user -> DATE_FORMAT.format(user.createdAt())).setHeader("Created");
        grid.addColumn(DeskUserSummary::createdBy).setHeader("Created by");
        grid.addComponentColumn(this::actions).setHeader("Actions").setAutoWidth(true);
    }

    private HorizontalLayout actions(DeskUserSummary user) {
        Button enabled = new Button(user.enabled() ? "Disable" : "Enable", event -> runAction(() -> {
            users.setEnabled(actor, user.username(), !user.enabled());
            refresh();
        }));
        Button resetPassword = new Button("Reset password", event -> openPasswordReset(user));
        return new HorizontalLayout(enabled, resetPassword);
    }

    private void openCreateDialog() {
        TextField username = new TextField("Username");
        username.setMaxLength(120);
        username.setHelperText("3-120 characters: letters, numbers, dot, dash, underscore.");
        PasswordField password = new PasswordField("Initial password");
        password.setMinLength(12);
        ComboBox<DeskUserRole> role = new ComboBox<>("Role");
        role.setItems(DeskUserRole.values());
        role.setRequired(true);

        Dialog dialog = new Dialog();
        Button save = new Button("Create", event -> runAction(() -> {
            users.create(actor, username.getValue(), password.getValue(), role.getValue());
            dialog.close();
            refresh();
            Notification.show("Account created.");
        }));
        Button cancel = new Button("Cancel", event -> dialog.close());
        dialog.add(username, password, role, new HorizontalLayout(save, cancel));
        dialog.setWidth("480px");
        dialog.open();
    }

    private void openPasswordReset(DeskUserSummary user) {
        PasswordField password = new PasswordField("New password");
        password.setMinLength(12);
        Dialog dialog = new Dialog();
        Button save = new Button("Reset password", event -> runAction(() -> {
            users.resetPassword(actor, user.username(), password.getValue());
            dialog.close();
            Notification.show("Password reset.");
        }));
        Button cancel = new Button("Cancel", event -> dialog.close());
        dialog.add(new Span("Set a new password for " + user.username() + "."), password,
                new HorizontalLayout(save, cancel));
        dialog.setWidth("480px");
        dialog.open();
    }

    private void refresh() {
        grid.setItems(users.list(actor));
    }

    private void runAction(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            Notification.show(exception.getMessage(), 5000, Notification.Position.MIDDLE);
        }
    }
}
