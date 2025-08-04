package com.example.jira.helloworld.servlet;

import com.atlassian.crowd.embedded.api.Group;
import com.atlassian.jira.bc.JiraServiceContext;
import com.atlassian.jira.bc.JiraServiceContextImpl;
import com.atlassian.jira.bc.security.login.LoginInfo;
import com.atlassian.jira.bc.security.login.LoginService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.security.groups.GroupManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.Page;
import com.atlassian.jira.util.PageRequest;
import com.atlassian.jira.util.PageRequests;
import com.atlassian.templaterenderer.TemplateRenderer;
import com.example.jira.helloworld.UserRow;
import com.example.jira.helloworld.util.UserWarningUtil;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;



public class MyPluginDashboardServlet extends HttpServlet {

    private final TemplateRenderer templateRenderer = ComponentAccessor.getOSGiComponentInstanceOfType(TemplateRenderer.class);
    private final LoginService loginService = ComponentAccessor.getComponent(LoginService.class);
    private final GroupManager groupManager = ComponentAccessor.getComponent(GroupManager.class);
    private final JiraAuthenticationContext jiraAuthenticationContext = ComponentAccessor.getComponent(JiraAuthenticationContext.class);
    private final ApplicationUser adminUser = jiraAuthenticationContext.getLoggedInUser();
    private final JiraServiceContext context = new JiraServiceContextImpl(adminUser);


    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        resp.setContentType("text/html");

        int page = req.getParameter("page") != null ? Integer.parseInt(req.getParameter("page")) : 1;
        if (page < 1) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Page number must be greater than 0");
            return;
        }
        int pageSize = req.getParameter("pageSize") != null ? Integer.parseInt(req.getParameter("pageSize")) : 25;
        if (pageSize < 1 || pageSize > 100) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Page size must be between 1 and 100");
            return;
        }

        Group softwareUsersGroup = groupManager.getGroup("jira-software-users");
        int userCount = groupManager.getUsersInGroupCount(softwareUsersGroup);

        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, userCount);
        if (start >= userCount) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "No users found for the specified page");
            return;
        }

        PageRequest pageRequest = PageRequests.request((long) start, pageSize);
        Page<ApplicationUser> usersPage = groupManager.getUsersInGroup("jira-software-users", true, pageRequest);

        if (usersPage == null || usersPage.getValues().isEmpty()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "No users found for the specified page");
            return;
        }


        List<UserRow> userRows = createUserRows(usersPage.getValues(), adminUser);


        Map<String, Object> contextMap = new HashMap<>();
        contextMap.put("users", userRows);
        contextMap.put("currentPage", page);
        contextMap.put("pageSize", pageSize);

        int totalPages = (int) Math.ceil(userCount / (double) pageSize);
        contextMap.put("totalPages", totalPages);

        // render the template defined with .vm file
        templateRenderer.render("templates/dashboard.vm", contextMap, resp.getWriter());
    }

    public static String getTimeString(LocalDateTime lastLogin) {
        LocalDateTime today = LocalDateTime.now(ZoneId.systemDefault());

        long daysAgo = ChronoUnit.DAYS.between(lastLogin, today);
        if(daysAgo == 0) {
            long hoursAgo = ChronoUnit.HOURS.between(lastLogin, today);
            if(hoursAgo == 0) {
                long minutesAgo = ChronoUnit.MINUTES.between(lastLogin, today);
                if(minutesAgo == 0) return "Just now";
                else
                    return minutesAgo + " minutes ago";
            }
            else if (hoursAgo == 1)
                return "1 hour ago";
            else
                return hoursAgo + " hours ago";
        }

        else if(daysAgo == 1)
            return "1 day ago";
        else if (daysAgo > 1)
            return daysAgo + " days ago";
        else
            return "Wrong data";


    }

    public List<UserRow> createUserRows(List<ApplicationUser> users, ApplicationUser adminUser) {
        List<UserRow> userRows = new ArrayList<>();
        for (ApplicationUser user : users) {
            String lastLoginString = "Never";
            LoginInfo loginInfo = loginService.getLoginInfo(user.getName());
            Long millis = loginInfo.getLastLoginTime();

            if (millis != null) {
                LocalDateTime lastLogin = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDateTime();
                lastLoginString = getTimeString(lastLogin);
            }
            List<String> warnings;
            try {
                warnings = UserWarningUtil.getWarningsForUser(user, adminUser);
            } catch (Exception e) {
                throw new RuntimeException("Failed to fetch warnings for user: " + e.getMessage(), e);
            }
            userRows.add(new UserRow(user.getName(), user.getDisplayName(), lastLoginString, warnings));
        }
        return userRows;
    }


}

