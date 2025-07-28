package com.example.jira.helloworld;

import com.atlassian.jira.bc.JiraServiceContext;
import com.atlassian.jira.bc.JiraServiceContextImpl;
import com.atlassian.jira.bc.user.search.UserSearchService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.UserDetails;
import com.atlassian.jira.user.UserPropertyManager;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.templaterenderer.TemplateRenderer;
import jdk.vm.ci.meta.Local;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static java.lang.Long.parseLong;
import static java.time.LocalTime.now;

public class MyPluginDashboardServlet extends HttpServlet {

    private final TemplateRenderer templateRenderer = ComponentAccessor.getOSGiComponentInstanceOfType(TemplateRenderer.class);

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        resp.setContentType("text/html");



        ApplicationUser currentUser = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser();
        JiraServiceContext context = new JiraServiceContextImpl(currentUser);
        List<ApplicationUser> users = ComponentAccessor.getComponent(UserSearchService.class).findUsersAllowEmptyQuery(context, "");

        UserPropertyManager userPropertyManager = ComponentAccessor.getUserPropertyManager();
        List<UserRow> userRows = new ArrayList<>();
        for(ApplicationUser user: users) {
            String lastLoginString = "Never";

            // placeholder for now because apparently jira is written by middle schoolers, thus can't fetch a simple property
            // userPropertyManager.getPropertySet(user).setString("login.lastLoginMillis", String.valueOf(System.currentTimeMillis()));

            String loginMillisString  = userPropertyManager.getPropertySet(user).getString("login.lastLoginMillis");
//            System.out.println(
//                    "User: " + user.getUsername() +
//                            ", userKey: " + user.getKey() +
//                            ", id: " + user.getId() +
//                            ", directoryId: " + user.getDirectoryId()
//            );
//
//            System.out.println("Last Login Millis: " + loginMillisString);
            if(loginMillisString != null && !loginMillisString.isEmpty()) {
                Long millis = Long.parseLong(loginMillisString);
//                LocalDateTime lastLogin = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDateTime();
//                lastLoginString = lastLogin.toString();
                LocalDateTime lastLogin = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDateTime();
                LocalDateTime today = LocalDateTime.now(ZoneId.systemDefault());

                long daysAgo = ChronoUnit.DAYS.between(lastLogin, today);
                if(daysAgo == 0) {
                    long hoursAgo = ChronoUnit.HOURS.between(lastLogin, today);
                    if(hoursAgo == 0) {
                        long minutesAgo = ChronoUnit.MINUTES.between(lastLogin, today);
                        if(minutesAgo == 0) lastLoginString = "Just now";
                        else
                            lastLoginString = minutesAgo + " minutes ago";
                    }
                    else if (hoursAgo == 1)
                        lastLoginString = "1 hour ago";
                    else
                        lastLoginString = hoursAgo + " hours ago";
                }

                else if(daysAgo == 1)
                    lastLoginString = "1 day ago";
                else if (daysAgo > 1)
                    lastLoginString = daysAgo + " days ago";
                else
                    lastLoginString = "Wrong data";


            }
            userRows.add(new UserRow(user.getUsername(), user.getDisplayName(), lastLoginString));
        }

        Map<String, Object> contextMap = new HashMap<>();
        contextMap.put("users", userRows);

        // render the template defined with .vm file
        templateRenderer.render("templates/dashboard.vm", contextMap, resp.getWriter());
    }
}
