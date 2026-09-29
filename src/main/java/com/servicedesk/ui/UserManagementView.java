package com.servicedesk.ui;

import com.servicedesk.user.DeskUserRole;
import com.servicedesk.user.DeskUserSummary;
import com.servicedesk.user.UserManagementService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
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
    private final Span accountCount = statCard("Accounts", "0");
    private final Span activeCount = statCard("Active", "0");
    private final Span adminCount = statCard("Active admins", "0");

    public UserManagementView(UserManagementService users) {
        this.users = users;
        actor = SecurityContextHolder.getContext().getAuthentication();
        setSizeFull();
        setPadding(true);
        setSpacing(true);
        configureGrid();

        H1 title = new H1("User management");
        title.getStyle().set("margin", "0");
        Span description = new Span("Create accounts, assign roles, and control who can sign in.");
        description.getStyle().set("color", "var(--lumo-secondary-text-color)");
        VerticalLayout titleBlock = new VerticalLayout(title, description);
        titleBlock.setPadding(false);
        titleBlock.setSpacing(false);

        Button create = new Button("Create user", event -> openCreateDialog());
        create.addThemeName("primary");
        Button home = new Button("Back to requests", event -> getUI().ifPresent(ui -> ui.navigate("")));
        FlexLayout header = new FlexLayout(titleBlock, new HorizontalLayout(create, home));
        header.setWidthFull();
        header.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        header.getStyle().set("gap", "var(--lumo-space-m)");

        FlexLayout summary = new FlexLayout(accountCount, activeCount, adminCount);
        summary.setWidthFull();
        summary.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        summary.getStyle().set("gap", "var(--lumo-space-m)");
        add(header, summary, grid);
        setFlexGrow(1, grid);
        refresh();
    }

    private void configureGrid() {
        grid.setEmptyStateText("No accounts yet. Create the first user to get started.");
        grid.addColumn(DeskUserSummary::username).setHeader("Username").setAutoWidth(true);
        grid.addComponentColumn(user -> badge(roleLabel(user.role()), roleTheme(user.role())))
                .setHeader("Role").setAutoWidth(true);
        grid.addComponentColumn(user -> badge(
                        user.enabled() ? "Active" : "Disabled", user.enabled() ? "success" : "contrast"))
                .setHeader("Account status").setAutoWidth(true);
        grid.addColumn(user -> DATE_FORMAT.format(user.createdAt())).setHeader("Created").setAutoWidth(true);
        grid.addColumn(DeskUserSummary::createdBy).setHeader("Created by").setAutoWidth(true);
        grid.addComponentColumn(this::actions).setHeader("Actions").setAutoWidth(true);
    }

    private FlexLayout actions(DeskUserSummary user) {
        Button enabled = new Button(user.enabled() ? "Disable" : "Enable", event -> {
            if (user.enabled()) {
                confirmDisable(user);
            } else {
                runAction(() -> {
                    users.setEnabled(actor, user.username(), true);
                    refresh();
                    Notification.show(user.username() + " can sign in again.");
                });
            }
        });
        enabled.addThemeName(user.enabled() ? "tertiary" : "primary");
        Button resetPassword = new Button("Reset password", event -> openPasswordReset(user));
        resetPassword.addThemeName("tertiary");
        FlexLayout actions = new FlexLayout(enabled, resetPassword);
        actions.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        actions.getStyle().set("gap", "var(--lumo-space-xs)");
        return actions;
    }

    private void openCreateDialog() {
        TextField username = new TextField("Username");
        username.setMaxLength(120);
        username.setPattern("[a-zA-Z0-9._-]{3,120}");
        username.setRequired(true);
        username.setWidthFull();
        username.setHelperText("3-120 characters: letters, numbers, dot, dash, underscore.");

        PasswordField password = new PasswordField("Temporary password");
        password.setMinLength(12);
        password.setRequired(true);
        password.setWidthFull();
        password.setHelperText("At least 12 characters. Share it with the user securely and ask them to change it.");

        ComboBox<DeskUserRole> role = new ComboBox<>("Role");
        role.setItems(DeskUserRole.values());
        role.setItemLabelGenerator(UserManagementView::roleLabel);
        role.setRequired(true);
        role.setWidthFull();
        role.setHelperText("Choose the minimum access the user needs.");

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Create account");
        dialog.setWidth("min(520px, 96vw)");
        dialog.setModal(true);
        dialog.setCloseOnOutsideClick(false);
        Span note = new Span("There is no public registration. Accounts are created by an administrator.");
        note.getStyle().set("color", "var(--lumo-secondary-text-color)");
        Button cancel = new Button("Cancel", event -> dialog.close());
        Button save = new Button("Create account", event -> {
            if (username.isEmpty() || password.isEmpty() || role.isEmpty()) {
                Notification.show("Complete all fields to create the account.",
                        4000, Notification.Position.MIDDLE);
                return;
            }
            runAction(() -> {
                users.create(actor, username.getValue(), password.getValue(), role.getValue());
                dialog.close();
                refresh();
                Notification.show("Account created.");
            });
        });
        save.addThemeName("primary");
        VerticalLayout form = new VerticalLayout(note, username, password, role);
        form.setPadding(false);
        form.setSpacing(true);
        form.setWidthFull();
        dialog.add(form);
        dialog.getFooter().add(cancel, save);
        dialog.open();
        username.focus();
    }

    private void confirmDisable(DeskUserSummary user) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Disable " + user.username() + "?");
        dialog.setWidth("min(440px, 96vw)");
        dialog.setModal(true);
        dialog.setCloseOnOutsideClick(false);
        Span explanation = new Span(
                "This account will no longer be able to sign in or receive new assignments. Its historical records are kept.");
        Button cancel = new Button("Keep active", event -> dialog.close());
        Button disable = new Button("Disable account", event -> runAction(() -> {
            users.setEnabled(actor, user.username(), false);
            dialog.close();
            refresh();
            Notification.show(user.username() + " has been disabled.");
        }));
        disable.addThemeName("error");
        dialog.add(explanation);
        dialog.getFooter().add(cancel, disable);
        dialog.open();
    }

    private void openPasswordReset(DeskUserSummary user) {
        PasswordField password = new PasswordField("New temporary password");
        password.setMinLength(12);
        password.setRequired(true);
        password.setWidthFull();
        password.setHelperText("At least 12 characters. Share it with the user securely.");

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Reset password");
        dialog.setWidth("min(480px, 96vw)");
        dialog.setModal(true);
        dialog.setCloseOnOutsideClick(false);
        Span explanation = new Span("Set a new temporary password for " + user.username() + ".");
        Button cancel = new Button("Cancel", event -> dialog.close());
        Button save = new Button("Reset password", event -> {
            if (password.isEmpty()) {
                Notification.show("Enter a new password to continue.",
                        4000, Notification.Position.MIDDLE);
                return;
            }
            runAction(() -> {
                users.resetPassword(actor, user.username(), password.getValue());
                dialog.close();
                Notification.show("Password reset.");
            });
        });
        save.addThemeName("primary");
        VerticalLayout form = new VerticalLayout(explanation, password);
        form.setPadding(false);
        form.setSpacing(true);
        dialog.add(form);
        dialog.getFooter().add(cancel, save);
        dialog.open();
        password.focus();
    }

    private void refresh() {
        List<DeskUserSummary> accounts = users.list(actor);
        grid.setItems(accounts);
        long active = accounts.stream().filter(DeskUserSummary::enabled).count();
        long admins = accounts.stream()
                .filter(user -> user.enabled() && user.role() == DeskUserRole.ADMIN)
                .count();
        updateStat(accountCount, "Accounts", accounts.size());
        updateStat(activeCount, "Active", active);
        updateStat(adminCount, "Active admins", admins);
    }

    private static Span statCard(String label, String value) {
        Span card = new Span(label + ": " + value);
        card.getElement().getThemeList().add("badge");
        card.getElement().getThemeList().add("contrast");
        card.getStyle().set("padding", "var(--lumo-space-m)");
        card.getStyle().set("font-size", "var(--lumo-font-size-m)");
        return card;
    }

    private static void updateStat(Span card, String label, long value) {
        card.setText(label + ": " + value);
    }

    private static Span badge(String label, String theme) {
        Span badge = new Span(label);
        badge.getElement().getThemeList().add("badge");
        badge.getElement().getThemeList().add(theme);
        return badge;
    }

    private static String roleLabel(DeskUserRole role) {
        return switch (role) {
            case REQUESTER -> "Requester";
            case AGENT -> "Agent";
            case ADMIN -> "Administrator";
        };
    }

    private static String roleTheme(DeskUserRole role) {
        return switch (role) {
            case REQUESTER -> "contrast";
            case AGENT -> "primary";
            case ADMIN -> "warning";
        };
    }

    private void runAction(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            Notification.show(exception.getMessage(), 5000, Notification.Position.MIDDLE);
        }
    }
}
