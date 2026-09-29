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
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
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
        subject.setRequired(true);
        TextArea description = new TextArea("Description");
        description.setWidthFull();
        description.setRequired(true);
        ComboBox<RequestPriority> priority = new ComboBox<>("Priority");
        priority.setItems(RequestPriority.values());
        priority.setItemLabelGenerator(RequestView::priorityLabel);
        priority.setValue(RequestPriority.NORMAL);

        Dialog dialog = new Dialog();
        Button submit = new Button("Create", event -> {
            try {
                requests.create(actor, subject.getValue(), description.getValue(), priority.getValue());
                dialog.close();
                refresh();
                Notification.show("Service request created.");
            } catch (RuntimeException exception) {
                Notification.show(exception.getMessage(), 5000, Notification.Position.MIDDLE);
            }
        });
        Button cancel = new Button("Cancel", event -> dialog.close());
        dialog.add(new H3("New service request"), subject, description, priority, new HorizontalLayout(submit, cancel));
        dialog.setWidth("640px");
        dialog.open();
    }

    private void openDetails(Long id) {
        ServiceRequest request = requests.get(actor, id);
        Dialog dialog = new Dialog();
        dialog.setWidth("760px");
        VerticalLayout content = new VerticalLayout();
        content.add(new H3("#" + request.getId() + " " + request.getSubject()),
                new Span(request.getDescription()),
                new Span("Requester: " + request.getRequesterUsername()),
                new Span("Priority: " + request.getPriority()),
                new Span("SLA target: " + DATE_FORMAT.format(request.getSlaDueAt())),
                new Span(request.getSlaBreachedAt() == null
                        ? "SLA status: within target"
                        : "SLA status: breached at " + DATE_FORMAT.format(request.getSlaBreachedAt())
                                + " — needs agent attention"));
        if (isAgentOrAdmin()) {
            ComboBox<String> assignee = new ComboBox<>("Assign to");
            assignee.setItems(userManagement.assignableUsernames());
            if (request.getAssignedAgentUsername() != null
                    && userManagement.isAssignableAgent(request.getAssignedAgentUsername())) {
                assignee.setValue(request.getAssignedAgentUsername());
            }
            Button assign = new Button("Assign", event -> {
                try {
                    requests.assign(actor, id, assignee.getValue());
                    dialog.close();
                    refresh();
                    openDetails(id);
                } catch (RuntimeException exception) {
                    Notification.show(exception.getMessage(), 5000, Notification.Position.MIDDLE);
                }
            });
            ComboBox<RequestStatus> nextStatus = new ComboBox<>("Change status");
            nextStatus.setItems(requests.allowedTransitions(request));
            Button transition = new Button("Update status", event -> {
                try {
                    requests.transition(actor, id, nextStatus.getValue());
                    dialog.close();
                    refresh();
                    openDetails(id);
                } catch (RuntimeException exception) {
                    Notification.show(exception.getMessage(), 5000, Notification.Position.MIDDLE);
                }
            });
            content.add(new HorizontalLayout(assignee, assign), new HorizontalLayout(nextStatus, transition));
        }

        TextArea comment = new TextArea("Comment");
        comment.setWidthFull();
        Button addComment = new Button("Add comment", event -> {
            try {
                requests.addComment(actor, id, comment.getValue());
                dialog.close();
                refresh();
                openDetails(id);
            } catch (RuntimeException exception) {
                Notification.show(exception.getMessage(), 5000, Notification.Position.MIDDLE);
            }
        });
        content.add(comment, addComment, new H3("Comments"));
        requests.comments(actor, id).forEach(item ->
                content.add(new Span(item.getAuthorUsername() + " · " + DATE_FORMAT.format(item.getCreatedAt())
                        + " — " + item.getBody())));
        content.add(new H3("History"));
        requests.history(actor, id).forEach(item ->
                content.add(new Span(item.getActorUsername() + " · " + item.getAction()
                        + " · " + item.getDetails() + " · " + DATE_FORMAT.format(item.getOccurredAt()))));
        Button close = new Button("Close", event -> dialog.close());
        content.add(close);
        dialog.add(content);
        dialog.open();
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
