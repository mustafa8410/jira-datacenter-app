package com.example.jira.helloworld.servlet;

import com.atlassian.jira.bc.JiraServiceContext;
import com.atlassian.jira.bc.JiraServiceContextImpl;
import com.atlassian.jira.bc.security.login.LoginService;
import com.atlassian.jira.bc.user.search.UserSearchService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.search.SearchException;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.templaterenderer.TemplateRenderer;
import com.example.jira.helloworld.util.UserWarningUtil;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import static com.example.jira.helloworld.util.UserWarningUtil.countIssues;
import static com.example.jira.helloworld.util.UserWarningUtil.getProjectsForUserLastGivenDays;

public class MyPluginUserDetailServlet extends HttpServlet {

    private final UserSearchService userSearchService = ComponentAccessor.getComponent(UserSearchService.class);
    private final ApplicationUser adminUser = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser();
    private final JiraServiceContext serviceContext = new JiraServiceContextImpl(adminUser);
    private final LoginService loginService = ComponentAccessor.getComponent(LoginService.class);
    private final TemplateRenderer templateRenderer = ComponentAccessor.getOSGiComponentInstanceOfType(TemplateRenderer.class);

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String username = req.getParameter("username");
        if (username == null || username.isEmpty()) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Username parameter is required");
            return;
        }
        ApplicationUser user = userSearchService.getUserByName(serviceContext, username);

        if (user == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "User not found");
            return;
        }

        Map<String, Object> context = new HashMap<>();

        Long lastLoginMillis = loginService.getLoginInfo(user.getUsername()).getLastLoginTime();
        if (lastLoginMillis != null) {
            context.put("lastLogin",
                    MyPluginDashboardServlet.getTimeString(
                            LocalDateTime.ofInstant(Instant.ofEpochMilli(lastLoginMillis), ZoneId.systemDefault())
                    ));
//        System.out.println("User: " + user.getUsername() + ", Last Login: " + context.get("lastLogin"));
        } else {
            context.put("lastLogin", "Never");
        }
        try {
            int assignedAll = countIssues("assignee = \"" + user.getUsername() + "\"", adminUser);
            context.put("assignedAllTime", assignedAll);
            int assigned30Days = countIssues("assignee = \"" + user.getUsername() + "\" AND updated >= -30d", adminUser);
            context.put("assigned30", assigned30Days);
            int resolvedAll = countIssues("assignee = \"" + user.getUsername() + "\" AND statusCategory = Done", adminUser);
            context.put("resolvedAllTime", resolvedAll);
            int resolved30Days = countIssues("assignee = \"" + user.getUsername() + "\" AND statusCategory = Done AND updated >= -30d", adminUser);
            context.put("resolved30", resolved30Days);
            int createdAll = countIssues("reporter = \"" + user.getUsername() + "\"", adminUser);
            context.put("createdAllTime", createdAll);
            int created30Days = countIssues("reporter = \"" + user.getUsername() + "\" AND created >= -30d", adminUser);
            context.put("created30", created30Days);
        } catch (Exception e) {
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error retrieving issue counts for user: " + user.getUsername());
        }

        // get the groups of the user

        Collection<String> groups = ComponentAccessor.getGroupManager().getGroupNamesForUser(user);
        context.put("groups", groups);
        context.put("user", user);
        context.put("isActive", groups.isEmpty() ? "Inactive" : "Active");
        try {
            context.put("projectsLast30Days", getProjectsForUserLastGivenDays(adminUser, user, 30));
        } catch (SearchException e) {
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error retrieving projects for user: " + user.getUsername());
        }

        List<String> warnings = new ArrayList<>();
        try {
            warnings = UserWarningUtil.getWarningsForUser(user, adminUser);
        } catch (Exception e) {
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error retrieving warnings for user: " + user.getUsername());
        }
        context.put("warnings", warnings);


        resp.setContentType("text/html");
        templateRenderer.render("templates/user-detail.vm", context, resp.getWriter());


    }
}



