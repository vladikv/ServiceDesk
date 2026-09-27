package com.servicedesk.ui;

import com.servicedesk.config.SecurityUsersProperties;
import com.servicedesk.reporting.ReportService;
import com.servicedesk.request.RequestPriority;
import com.servicedesk.request.RequestService;
import com.servicedesk.request.RequestStatus;
import com.servicedesk.request.ServiceRequest;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
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
    private final SecurityUsersProperties securityUsers;
    private final Authentication actor;
    private final Grid<ServiceRequest> grid = new Grid<>(ServiceRequest.class, false);
    private final TextField search = new TextField("Search");
    private final ComboBox<RequestStatus> statusFilter = new ComboBox<>("Status");
    private final Checkbox slaAttentionOnly = new Checkbox("SLA attention only");
    private final Button createButton = new Button("New request");

    public RequestView(
            RequestService requests,
            ReportService reports,
            SecurityUsersProperties securityUsers) {
        this.requests = requests;
        this.reports = reports;
        this.securityUsers = securityUsers;
        this.actor = SecurityContextHolder.getContext().getAuthentication();
        setSizeFull();
        configureGrid();

        Button logout = new Button("Sign out", event -> getUI().ifPresent(ui -> ui.getPage().setLocation("/logout")));
        HorizontalLayout header = new HorizontalLayout(new H1("Service Desk"), logout);
        header.setWidthFull();
        header.setAlignItems(Alignment.CENTER);
        header.expand(header.getComponentAt(0));

        search.setClearButtonVisible(true);
        search.setPlaceholder("Subject, description, or assignee");
        statusFilter.setItems(RequestStatus.values());
        statusFilter.setClearButtonVisible(true);
        slaAttentionOnly.setVisible(isAgentOrAdmin());
        Button apply = new Button("Apply filters", event -> refresh());
        createButton.addClickListener(event -> openCreateDialog());
        HorizontalLayout filters = new HorizontalLayout(search, statusFilter, slaAttentionOnly, apply, createButton);
        filters.setAlignItems(Alignment.END);
        add(header, filters, grid);
        setFlexGrow(1, grid);

        if (isAgentOrAdmin()) {
            var summary = reports.summary(actor);
            add(new Span("Requests: " + summary.total()
                    + " | SLA attention: " + summary.slaBreaches()));
        } else {
            createButton.setVisible(true);
        }
        refresh();
    }

    private void configureGrid() {
        grid.addColumn(ServiceRequest::getId).setHeader("#").setAutoWidth(true);
        grid.addColumn(ServiceRequest::getSubject).setHeader("Subject").setFlexGrow(1);
        grid.addColumn(ServiceRequest::getRequesterUsername).setHeader("Requester");
        grid.addColumn(request -> valueOrDash(request.getAssignedAgentUsername())).setHeader("Assignee");
        grid.addColumn(ServiceRequest::getStatus).setHeader("Status");
        grid.addColumn(ServiceRequest::getPriority).setHeader("Priority");
        grid.addColumn(request -> request.getSlaBreachedAt() == null
                        ? "Due " + DATE_FORMAT.format(request.getSlaDueAt())
                        : "BREACHED — attention needed")
                .setHeader("SLA");
        grid.addColumn(request -> DATE_FORMAT.format(request.getUpdatedAt())).setHeader("Updated");
        grid.addComponentColumn(request -> new Button("Open", event -> openDetails(request.getId())))
                .setHeader("Details")
                .setAutoWidth(true);
    }

    private void refresh() {
        grid.setItems(requests.search(actor, statusFilter.getValue(), search.getValue(), slaAttentionOnly.getValue()));
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
            assignee.setItems(securityUsers.getAssignableUsernames());
            if (request.getAssignedAgentUsername() != null
                    && securityUsers.isAssignable(request.getAssignedAgentUsername())) {
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

    private static String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
