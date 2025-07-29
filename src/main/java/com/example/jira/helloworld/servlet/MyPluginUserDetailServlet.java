package com.example.jira.helloworld.servlet;

import com.atlassian.jira.bc.JiraServiceContext;
import com.atlassian.jira.bc.JiraServiceContextImpl;
import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.bc.security.login.LoginService;
import com.atlassian.jira.bc.user.search.UserSearchService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.search.SearchException;
import com.atlassian.jira.issue.search.SearchResults;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.web.bean.PagerFilter;
import com.atlassian.templaterenderer.TemplateRenderer;
import com.example.jira.helloworld.ProjectResponse;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

public class MyPluginUserDetailServlet extends HttpServlet {

    private final UserSearchService userSearchService = ComponentAccessor.getComponent(UserSearchService.class);
    private final ApplicationUser adminUser = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser();
    private final JiraServiceContext serviceContext = new JiraServiceContextImpl(adminUser);
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
        }
        catch (Exception e) {
            throw new ServletException("Error counting issues for user: " + user.getUsername(), e);
        }

        // get the groups of the user

        Collection<String> groups = ComponentAccessor.getGroupManager().getGroupNamesForUser(user);
        context.put("groups", groups);
        context.put("user", user);
        context.put("isActive", adminUser.isActive() ? "Active" : "Inactive");
        try {
            context.put("projectsLast30Days", getProjectsForUserLast30Days(adminUser, user));
        } catch (SearchException e) {
            throw new ServletException("Error retrieving projects for user: " + user.getUsername(), e);
        }


        resp.setContentType("text/html");
        templateRenderer.render("templates/user-detail.vm", context, resp.getWriter());


    }


    public int countIssues(String jql, ApplicationUser adminUser) throws Exception {
        SearchService.ParseResult parseResult = searchService.parseQuery(adminUser, jql);
        if (!parseResult.isValid()) {
            throw new RuntimeException("Invalid JQL query: " + jql);
        }
        SearchResults<?> searchResults = searchService.search(adminUser, parseResult.getQuery(), PagerFilter.getUnlimitedFilter());
        return searchResults.getTotal();

    }

    public Set<ProjectResponse> getProjectsForUserLast30Days(ApplicationUser adminUser, ApplicationUser user) throws SearchException {
        String jql = "(assignee = \"" + user.getUsername() + "\" OR reporter = \"" + user.getUsername() + "\") AND updated >= -30d";
        SearchService.ParseResult parseResult = searchService.parseQuery(adminUser, jql);
        if(parseResult.isValid()) {
            SearchResults<Issue> results = searchService.search(adminUser, parseResult.getQuery(), PagerFilter.getUnlimitedFilter());
            Set<ProjectResponse> projectResponses = new HashSet<>();
            for(Issue issue : results.getResults()) {
                ProjectResponse projectResponse = new ProjectResponse();
                projectResponse.setKey(issue.getProjectObject().getKey());
                projectResponse.setName(issue.getProjectObject().getName());
                projectResponses.add(projectResponse);
                System.out.println("Project: " + issue.getProjectObject().getKey() + ", Name: " + issue.getProjectObject().getName());
            }
            return projectResponses;
        }
        throw new SearchException("Invalid JQL query: " + jql);
    }

}
