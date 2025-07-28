package com.example.jira.helloworld.servlet;

import com.atlassian.jira.bc.JiraServiceContext;
import com.atlassian.jira.bc.JiraServiceContextImpl;
import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.bc.security.login.LoginService;
import com.atlassian.jira.bc.user.search.UserSearchService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.search.SearchResults;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.web.bean.PagerFilter;
import com.atlassian.templaterenderer.TemplateRenderer;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class MyPluginUserDetailServlet extends HttpServlet {

    private final UserSearchService userSearchService = ComponentAccessor.getComponent(UserSearchService.class);
    private final ApplicationUser currentUser = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser();
    private final JiraServiceContext serviceContext = new JiraServiceContextImpl(currentUser);
    private final LoginService loginService = ComponentAccessor.getComponent(LoginService.class);
    private final SearchService searchService = ComponentAccessor.getComponent(SearchService.class);
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
        if(lastLoginMillis != null) {
            context.put("lastLogin",
                    MyPluginDashboardServlet.getTimeString(
                            LocalDateTime.ofInstant(Instant.ofEpochMilli(lastLoginMillis), ZoneId.systemDefault())
                    ));
//        System.out.println("User: " + user.getUsername() + ", Last Login: " + context.get("lastLogin"));
        }
        else {
            context.put("lastLogin", "Never");
        }
        try {
            int assignedAll = countIssues("assignee = \"" + user.getUsername() + "\"", currentUser);
            context.put("assignedAllTime", assignedAll);
            int assigned30Days = countIssues("assignee = \"" + user.getUsername() + "\" AND updated >= -30d", currentUser);
            context.put("assigned30", assigned30Days);
            int resolvedAll = countIssues("assignee = \"" + user.getUsername() + "\" AND statusCategory = Done", currentUser);
            context.put("resolvedAllTime", resolvedAll);
            int resolved30Days = countIssues("assignee = \"" + user.getUsername() + "\" AND statusCategory = Done AND updated >= -30d", currentUser);
            context.put("resolved30", resolved30Days);
            int createdAll = countIssues("reporter = \"" + user.getUsername() + "\"", currentUser);
            context.put("createdAllTime", createdAll);
            int created30Days = countIssues("reporter = \"" + user.getUsername() + "\" AND created >= -30d", currentUser);
            context.put("created30", created30Days);
        }
        catch (Exception e) {
            throw new ServletException("Error counting issues for user: " + user.getUsername(), e);
        }

        // get the groups of the user

        Collection<String> groups = ComponentAccessor.getGroupManager().getGroupNamesForUser(user);
        context.put("groups", groups);
        context.put("user", user);


        resp.setContentType("text/html");
        templateRenderer.render("templates/user-detail.vm", context, resp.getWriter());


    }


    public int countIssues(String jql, ApplicationUser applicationUser) throws Exception {
        SearchService.ParseResult parseResult = searchService.parseQuery(applicationUser, jql);
        if (!parseResult.isValid()) {
            throw new RuntimeException("Invalid JQL query: " + jql);
        }
        SearchResults<?> searchResults = searchService.search(applicationUser, parseResult.getQuery(), PagerFilter.getUnlimitedFilter());
        return searchResults.getTotal();

    }

}
