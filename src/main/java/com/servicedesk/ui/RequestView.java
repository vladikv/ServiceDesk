package com.servicedesk.ui;

import com.servicedesk.reporting.ReportService;
import com.servicedesk.request.RequestPriority;
import com.servicedesk.request.RequestService;
import com.servicedesk.request.RequestStatus;
import com.servicedesk.request.ServiceRequest;
import com.servicedesk.user.UserManagementService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Route("")
@PageTitle("Requests | Service Desk")
@RolesAllowed({"REQUESTER", "AGENT", "ADMIN"})
public class RequestView extends VerticalLayout {
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final RequestService requests;
    private final ReportService reports;
    private final UserManagementService userManagement;
    private final Authentication actor;
    private final Grid<ServiceRequest> grid = new Grid<>(ServiceRequest.class, false);
    private final TextField search = new TextField("Search");
    private final ComboBox<RequestStatus> statusFilter = new ComboBox<>("Status");
    private final Checkbox slaAttentionOnly = new Checkbox("SLA attention only");
    private final Button createButton = new Button("New request");
    private final Span resultCount = new Span();
    private final Span totalRequestCount = badge("All requests: 0", "contrast");
    private final Span slaBreachCount = badge("SLA attention: 0", "success");

    public RequestView(
            RequestService requests,
            ReportService reports,
            UserManagementService userManagement) {
        this.requests = requests;
        this.reports = reports;
        this.userManagement = userManagement;
        this.actor = SecurityContextHolder.getContext().getAuthentication();
        setSizeFull();
        setPadding(true);
        setSpacing(true);
        configureGrid();

        Button logout = new Button("Sign out", event -> getUI().ifPresent(ui -> ui.getPage().setLocation("/logout")));
        HorizontalLayout header = new HorizontalLayout(new H1("Service Desk"));
        if (isAdmin()) {
            header.add(new Anchor("/users", "Manage users"));
        }
        header.add(logout);
        header.setWidthFull();
        header.setAlignItems(Alignment.CENTER);
        header.expand(header.getComponentAt(0));

        search.setClearButtonVisible(true);
        search.setPlaceholder("Subject, description, or assignee");
        search.setWidth("min(28rem, 100%)");
        search.addKeyPressListener(Key.ENTER, event -> refresh());
        statusFilter.setItems(RequestStatus.values());
        statusFilter.setItemLabelGenerator(RequestView::statusLabel);
        statusFilter.setClearButtonVisible(true);
        statusFilter.setWidth("14rem");
        slaAttentionOnly.setVisible(isAgentOrAdmin());
        Button apply = new Button("Apply", event -> refresh());
        apply.addThemeName("primary");
        Button clear = new Button("Clear filters", event -> clearFilters());
        clear.addThemeName("tertiary");
        createButton.addClickListener(event -> openCreateDialog());
        createButton.addThemeName("primary");
        FlexLayout filters = new FlexLayout(search, statusFilter, slaAttentionOnly, apply, clear, createButton);
        filters.setWidthFull();
        filters.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        filters.setAlignItems(FlexComponent.Alignment.END);
        filters.setJustifyContentMode(FlexComponent.JustifyContentMode.START);
        filters.getStyle().set("gap", "var(--lumo-space-m)");
        resultCount.getElement().getThemeList().add("badge");
        resultCount.getElement().getThemeList().add("contrast");

        HorizontalLayout overview = new HorizontalLayout();
        if (isAgentOrAdmin()) {
            overview.add(totalRequestCount, slaBreachCount);
        }
        overview.setSpacing(true);
        add(header, filters, resultCount, overview, grid);
        setFlexGrow(1, grid);
        refresh();
    }

    private void configureGrid() {
        grid.setEmptyStateText("No requests match these filters.");
        grid.addColumn(ServiceRequest::getId).setHeader("ID").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(ServiceRequest::getSubject).setHeader("Subject").setFlexGrow(1).setWidth("16rem");
        grid.addColumn(ServiceRequest::getRequesterUsername).setHeader("Requester").setAutoWidth(true);
        grid.addColumn(request -> valueOrDash(request.getAssignedAgentUsername()))
                .setHeader("Assignee").setAutoWidth(true);
        grid.addComponentColumn(request -> badge(statusLabel(request.getStatus()), statusTheme(request.getStatus())))
                .setHeader("Status").setAutoWidth(true);
        grid.addComponentColumn(request -> badge(
                        priorityLabel(request.getPriority()), priorityTheme(request.getPriority())))
                .setHeader("Priority").setAutoWidth(true);
        grid.addComponentColumn(request -> slaBadge(request))
                .setHeader("SLA").setAutoWidth(true);
        grid.addColumn(request -> DATE_FORMAT.format(request.getUpdatedAt()))
                .setHeader("Updated").setAutoWidth(true);
        grid.addComponentColumn(request -> {
                    Button open = new Button("View", event -> openDetails(request.getId()));
                    open.addThemeName("tertiary");
                    return open;
                })
                .setHeader("Details")
                .setAutoWidth(true);
    }

    private void refresh() {
        var matching = requests.search(actor, statusFilter.getValue(), search.getValue(), slaAttentionOnly.getValue());
        grid.setItems(matching);
        resultCount.setText(matching.size() == 1 ? "1 request" : matching.size() + " requests");
        if (isAgentOrAdmin()) {
            var summary = reports.summary(actor);
            totalRequestCount.setText("All requests: " + summary.total());
            slaBreachCount.setText("SLA attention: " + summary.slaBreaches());
            slaBreachCount.getElement().getThemeList().remove("error");
            slaBreachCount.getElement().getThemeList().remove("success");
            slaBreachCount.getElement().getThemeList().add(summary.slaBreaches() > 0 ? "error" : "success");
        }
    }

    private void clearFilters() {
        search.clear();
        statusFilter.clear();
        slaAttentionOnly.setValue(false);
        refresh();
    }

    private static Span badge(String label, String theme) {
        Span badge = new Span(label);
        badge.getElement().getThemeList().add("badge");
        badge.getElement().getThemeList().add(theme);
        return badge;
    }

    private static Span slaBadge(ServiceRequest request) {
        if (request.getSlaBreachedAt() != null) {
            Span badge = badge("Breached", "error");
            badge.setTitle("Breached at " + DATE_FORMAT.format(request.getSlaBreachedAt())
                    + "; agent attention is needed.");
            return badge;
        }
        if (request.getSlaPausedAt() != null) {
            Span badge = badge("Paused", "contrast");
            badge.setTitle("SLA paused while waiting for the requester. Due "
                    + DATE_FORMAT.format(request.getSlaDueAt()));
            return badge;
        }
        if (Instant.now().isAfter(request.getSlaDueAt().plusSeconds(request.getSlaPausedSeconds()))) {
            Span badge = badge("Overdue", "warning");
            badge.setTitle("Past the SLA target; breach processing is pending.");
            return badge;
        }
        Span badge = badge("Due " + DATE_FORMAT.format(request.getSlaDueAt()), "success");
        badge.setTitle("SLA target: " + DATE_FORMAT.format(request.getSlaDueAt()));
        return badge;
    }

    private static String statusLabel(RequestStatus status) {
        return switch (status) {
            case NEW -> "New";
            case ASSIGNED -> "Assigned";
            case IN_PROGRESS -> "In progress";
            case WAITING_FOR_REQUESTER -> "Waiting for requester";
            case RESOLVED -> "Resolved";
            case CLOSED -> "Closed";
        };
    }

    private static String statusTheme(RequestStatus status) {
        return switch (status) {
            case NEW, CLOSED -> "contrast";
            case ASSIGNED, IN_PROGRESS -> "primary";
            case WAITING_FOR_REQUESTER -> "warning";
            case RESOLVED -> "success";
        };
    }

    private static String priorityLabel(RequestPriority priority) {
        return switch (priority) {
            case LOW -> "Low";
            case NORMAL -> "Normal";
            case HIGH -> "High";
            case URGENT -> "Urgent";
        };
    }

    private static String priorityTheme(RequestPriority priority) {
        return switch (priority) {
            case LOW, NORMAL -> "contrast";
            case HIGH -> "warning";
            case URGENT -> "error";
        };
    }

    private void openCreateDialog() {
        TextField subject = new TextField("Subject");
        subject.setMaxLength(160);
        subject.setWidthFull();
        subject.setRequired(true);
        subject.setHelperText("A short summary of the issue.");
        TextArea description = new TextArea("Description");
        description.setWidthFull();
        description.setMinHeight("8rem");
        description.setRequired(true);
        description.setHelperText("Include what happened and any steps already tried.");
        ComboBox<RequestPriority> priority = new ComboBox<>("Priority");
        priority.setItems(RequestPriority.values());
        priority.setItemLabelGenerator(RequestView::priorityLabel);
        priority.setValue(RequestPriority.NORMAL);
        priority.setWidthFull();

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Create a service request");
        dialog.setWidth("min(640px, 96vw)");
        dialog.setModal(true);
        dialog.setCloseOnOutsideClick(false);
        Button submit = new Button("Create", event -> {
            if (subject.isEmpty() || description.isEmpty()) {
                Notification.show("Enter a subject and description to continue.",
                        4000, Notification.Position.MIDDLE);
                return;
            }
            try {
                requests.create(actor, subject.getValue(), description.getValue(), priority.getValue());
                dialog.close();
                refresh();
                Notification.show("Service request created.");
            } catch (RuntimeException exception) {
                Notification.show(exception.getMessage(), 5000, Notification.Position.MIDDLE);
            }
        });
        submit.addThemeName("primary");
        Button cancel = new Button("Cancel", event -> dialog.close());
        HorizontalLayout actions = dialogActions(cancel, submit);
        VerticalLayout form = new VerticalLayout(
                new Span("Describe the issue and choose its urgency. You can add comments and details later."),
                subject, description, priority, actions);
        form.setPadding(false);
        form.setSpacing(true);
        form.setWidthFull();
        dialog.add(form);
        dialog.open();
        subject.focus();
    }

    private void openDetails(Long id) {
        ServiceRequest request = requests.get(actor, id);
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Request #" + request.getId());
        dialog.setWidth("min(900px, 96vw)");
        dialog.setMaxWidth("96vw");
        dialog.setMaxHeight("92vh");
        dialog.setModal(true);
        dialog.setCloseOnOutsideClick(false);
        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);
        content.setWidthFull();
        content.getStyle().set("overflow", "auto");
        H2 subject = new H2(request.getSubject());
        subject.getStyle().set("margin", "0");
        FlexLayout badges = new FlexLayout(
                badge(statusLabel(request.getStatus()), statusTheme(request.getStatus())),
                badge(priorityLabel(request.getPriority()), priorityTheme(request.getPriority())),
                slaBadge(request));
        badges.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        badges.getStyle().set("gap", "var(--lumo-space-s)");
        FlexLayout metadata = new FlexLayout(
                detailItem("Requester", request.getRequesterUsername()),
                detailItem("Assignee", valueOrDash(request.getAssignedAgentUsername())),
                detailItem("Created", DATE_FORMAT.format(request.getCreatedAt())),
                detailItem("Last updated", DATE_FORMAT.format(request.getUpdatedAt())));
        metadata.setWidthFull();
        metadata.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        metadata.getStyle().set("gap", "var(--lumo-space-m)");
        Div description = new Div(new H3("Description"), new Span(request.getDescription()));
        description.getStyle().set("white-space", "pre-wrap");
        description.getStyle().set("padding", "var(--lumo-space-m)");
        description.getStyle().set("background", "var(--lumo-contrast-5pct)");
        description.getStyle().set("border-radius", "var(--lumo-border-radius-m)");
        description.getStyle().set("width", "100%");
        description.getStyle().set("box-sizing", "border-box");
        content.add(subject, badges, metadata, description);
        if (isAgentOrAdmin()) {
            ComboBox<String> assignee = new ComboBox<>("Assign to");
            assignee.setItems(userManagement.assignableUsernames());
            assignee.setWidth("min(20rem, 100%)");
            assignee.setClearButtonVisible(true);
            assignee.setHelperText("Enabled agents and administrators can be assigned.");
            if (request.getAssignedAgentUsername() != null
                    && userManagement.isAssignableAgent(request.getAssignedAgentUsername())) {
                assignee.setValue(request.getAssignedAgentUsername());
            }
            Button assign = new Button("Assign", event -> runDialogAction(dialog, () -> {
                if (assignee.isEmpty()) {
                    throw new IllegalArgumentException("Choose an agent or administrator first.");
                }
                requests.assign(actor, id, assignee.getValue());
            }, id));
            ComboBox<RequestStatus> nextStatus = new ComboBox<>("Change status");
            nextStatus.setItems(requests.allowedTransitions(request));
            nextStatus.setItemLabelGenerator(RequestView::statusLabel);
            nextStatus.setWidth("min(20rem, 100%)");
            nextStatus.setClearButtonVisible(true);
            Button transition = new Button("Update status", event -> runDialogAction(dialog, () -> {
                if (nextStatus.isEmpty()) {
                    throw new IllegalArgumentException("Choose a status first.");
                }
                requests.transition(actor, id, nextStatus.getValue());
            }, id));
            assign.addThemeName("primary");
            transition.addThemeName("primary");
            FlexLayout assignmentActions = new FlexLayout(assignee, assign);
            assignmentActions.setFlexWrap(FlexLayout.FlexWrap.WRAP);
            assignmentActions.setAlignItems(FlexComponent.Alignment.END);
            assignmentActions.getStyle().set("gap", "var(--lumo-space-s)");
            VerticalLayout assignment = new VerticalLayout(new H3("Ownership"), assignmentActions);
            assignment.setPadding(false);
            FlexLayout statusActions = new FlexLayout(nextStatus, transition);
            statusActions.setFlexWrap(FlexLayout.FlexWrap.WRAP);
            statusActions.setAlignItems(FlexComponent.Alignment.END);
            statusActions.getStyle().set("gap", "var(--lumo-space-s)");
            VerticalLayout status = new VerticalLayout(new H3("Workflow"), statusActions);
            status.setPadding(false);
            FlexLayout controls = new FlexLayout(assignment, status);
            controls.setWidthFull();
            controls.setFlexWrap(FlexLayout.FlexWrap.WRAP);
            controls.getStyle().set("gap", "var(--lumo-space-l)");
            content.add(controls);
        }

        content.add(new H3("Activity"));
        TextArea comment = new TextArea("Add a comment");
        comment.setWidthFull();
        comment.setMaxLength(5000);
        comment.setMinHeight("6rem");
        comment.setHelperText("Comments are visible to everyone who can access this request.");
        Button addComment = new Button("Post comment", event -> {
            if (comment.isEmpty()) {
                Notification.show("Write a comment before posting.",
                        4000, Notification.Position.MIDDLE);
                return;
            }
            runDialogAction(dialog, () -> requests.addComment(actor, id, comment.getValue()), id);
        });
        addComment.addThemeName("primary");
        content.add(comment, addComment);
        var comments = requests.comments(actor, id);
        if (comments.isEmpty()) {
            content.add(new Span("No comments yet."));
        } else {
            comments.forEach(item -> content.add(activityItem(
                    item.getAuthorUsername(), DATE_FORMAT.format(item.getCreatedAt()), item.getBody())));
        }
        content.add(new H3("History"));
        var history = requests.history(actor, id);
        if (history.isEmpty()) {
            content.add(new Span("No history recorded."));
        } else {
            history.forEach(item -> content.add(activityItem(
                    item.getActorUsername() + " · " + item.getAction(),
                    DATE_FORMAT.format(item.getOccurredAt()), item.getDetails())));
        }
        Button close = new Button("Done", event -> dialog.close());
        close.addThemeName("tertiary");
        dialog.add(content);
        dialog.getFooter().add(close);
        dialog.open();
    }

    private Div detailItem(String label, String value) {
        Span caption = new Span(label);
        caption.getStyle().set("color", "var(--lumo-secondary-text-color)");
        Span content = new Span(value);
        Div item = new Div(caption, content);
        item.getStyle().set("display", "flex");
        item.getStyle().set("flex-direction", "column");
        item.getStyle().set("gap", "var(--lumo-space-xs)");
        item.getStyle().set("min-width", "10rem");
        return item;
    }

    private Div activityItem(String heading, String time, String body) {
        Span title = new Span(heading);
        title.getStyle().set("font-weight", "600");
        Span timestamp = new Span(time);
        timestamp.getStyle().set("color", "var(--lumo-secondary-text-color)");
        Span text = new Span(body);
        text.getStyle().set("white-space", "pre-wrap");
        Div item = new Div(new HorizontalLayout(title, timestamp), text);
        item.getStyle().set("display", "flex");
        item.getStyle().set("flex-direction", "column");
        item.getStyle().set("gap", "var(--lumo-space-xs)");
        item.getStyle().set("padding", "var(--lumo-space-m)");
        item.getStyle().set("border-left", "2px solid var(--lumo-primary-color-50pct)");
        item.getStyle().set("background", "var(--lumo-contrast-5pct)");
        item.getStyle().set("border-radius", "var(--lumo-border-radius-m)");
        return item;
    }

    private HorizontalLayout dialogActions(Button secondary, Button primary) {
        HorizontalLayout actions = new HorizontalLayout(secondary, primary);
        actions.setWidthFull();
        actions.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        return actions;
    }

    private void runDialogAction(Dialog dialog, Runnable action, Long requestId) {
        try {
            action.run();
            dialog.close();
            refresh();
            openDetails(requestId);
        } catch (RuntimeException exception) {
            Notification.show(exception.getMessage(), 5000, Notification.Position.MIDDLE);
        }
    }

    private boolean isAgentOrAdmin() {
        return actor != null && actor.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_AGENT")
                        || authority.getAuthority().equals("ROLE_ADMIN"));
    }

    private boolean isAdmin() {
        return actor != null && actor.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }

    private static String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
