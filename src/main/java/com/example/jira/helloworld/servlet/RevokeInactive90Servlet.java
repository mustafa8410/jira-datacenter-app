package com.example.jira.helloworld.servlet;

import com.atlassian.crowd.embedded.api.Group;
import com.atlassian.jira.bc.JiraServiceContext;
import com.atlassian.jira.bc.JiraServiceContextImpl;
import com.atlassian.jira.bc.security.login.LoginService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.exception.PermissionException;
import com.atlassian.jira.exception.RemoveException;
import com.atlassian.jira.security.groups.GroupManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.util.UserUtil;
import com.atlassian.jira.util.Page;
import com.atlassian.jira.util.PageRequests;
import com.atlassian.templaterenderer.TemplateRenderer;
import org.codehaus.jackson.map.ObjectMapper;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RevokeInactive90Servlet extends HttpServlet {
    private final LoginService loginService = ComponentAccessor.getComponent(LoginService.class);
    private final GroupManager groupManager = ComponentAccessor.getComponent(GroupManager.class);
    private final UserUtil userUtil = ComponentAccessor.getComponent(UserUtil.class);
    private final int batchSize = 500;
    private final int inactivityThresholdDays = 90;
    private final Group softwareUsersGroup = groupManager.getGroup("jira-software-users");
    private final TemplateRenderer templateRenderer = ComponentAccessor.getOSGiComponentInstanceOfType(TemplateRenderer.class);


    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        final ApplicationUser adminUser = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser();
        if (adminUser == null || !groupManager.isUserInGroup(adminUser, "jira-administrators")) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "You must be an administrator to perform this action.");
            return;
        }

        Long start = 0L;

        Page<ApplicationUser> users = groupManager.getUsersInGroup("jira-software-users", true,
                PageRequests.request(start, batchSize));

        boolean hasNext = true;
        List<String> removedUsers = new ArrayList<>();
        List<String> failedUsers = new ArrayList<>();

        while(hasNext) {
            for(ApplicationUser user: users.getValues()) {
                Long lastLoginMillis = loginService.getLoginInfo(user.getUsername()).getLastLoginTime();
                if(lastLoginMillis == null || ChronoUnit.DAYS.between
                        (Instant.ofEpochMilli(lastLoginMillis).atZone(ZoneId.systemDefault()).toLocalDateTime(),
                                LocalDateTime.now(ZoneId.systemDefault())) >= inactivityThresholdDays) {
                    try {
                        userUtil.removeUserFromGroup(softwareUsersGroup, user);
                        removedUsers.add(user.getUsername());
                    } catch (PermissionException | RemoveException e) {
                        failedUsers.add(user.getUsername() + ": " + e.getMessage());
                    }
                }
            }
            if(users.isLast())
                hasNext = false;
            else {
                start += batchSize;
                users = groupManager.getUsersInGroup("jira-software-users", true,
                        PageRequests.request(start, batchSize));
            }
        }

//        resp.setContentType("text/html");
//        resp.getWriter().write("<html><body> Revoke Inactive Users Action Completed.<br/>");
//        resp.getWriter().write("Removed Users: " + removedUsers.size() + "<br/>");
//        if (!removedUsers.isEmpty()) {
//            resp.getWriter().write("<ul>");
//            for (String username : removedUsers) {
//                resp.getWriter().write("<li>" + username + "</li>");
//            }
//            resp.getWriter().write("</ul>");
//        } else {
//            resp.getWriter().write("No users were removed.<br/>");
//        }
//        resp.getWriter().write("Failed Users: " + failedUsers.size() + "<br/>");
//        if (!failedUsers.isEmpty()) {
//            resp.getWriter().write("<ul>");
//            for (String error : failedUsers) {
//                resp.getWriter().write("<li>" + error + "</li>");
//            }
//            resp.getWriter().write("</ul>");
//        } else {
//            resp.getWriter().write("No users failed to remove.<br/>");
//        }
//        resp.getWriter().write("</body></html>");

        Map<String, Object> context = new HashMap<>();
        context.put("removedUsers", removedUsers);
        context.put("failedUsers", failedUsers);

        resp.setContentType("text/html");
        templateRenderer.render("templates/revoke-inactive-result.vm", context, resp.getWriter());

    }
}
