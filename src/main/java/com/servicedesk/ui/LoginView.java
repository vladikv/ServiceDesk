package com.servicedesk.ui;

import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route("login")
@PageTitle("Sign in | Service Desk")
@AnonymousAllowed
public class LoginView extends VerticalLayout implements BeforeEnterObserver {
    private final LoginForm loginForm = new LoginForm();

    public LoginView() {
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);
        setPadding(true);
        getStyle().set("background", "var(--lumo-contrast-5pct)");

        H1 title = new H1("Service Desk");
        title.getStyle().set("margin", "0");
        Span description = new Span("Sign in to manage your service requests.");
        description.getStyle().set("color", "var(--lumo-secondary-text-color)");
        loginForm.setAction("login");
        loginForm.getStyle().set("width", "100%");
        VerticalLayout panel = new VerticalLayout(title, description, loginForm);
        panel.setWidth("min(26rem, 100%)");
        panel.setPadding(true);
        panel.setSpacing(true);
        panel.getStyle().set("background", "var(--lumo-base-color)");
        panel.getStyle().set("border-radius", "var(--lumo-border-radius-l)");
        panel.getStyle().set("box-shadow", "var(--lumo-box-shadow-m)");
        add(panel);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        loginForm.setError(event.getLocation().getQueryParameters().getParameters().containsKey("error"));
    }
}
