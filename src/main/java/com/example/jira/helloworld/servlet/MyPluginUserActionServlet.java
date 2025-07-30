package com.example.jira.helloworld.servlet;

import com.atlassian.jira.bc.JiraServiceContext;
import com.atlassian.jira.bc.JiraServiceContextImpl;
import com.atlassian.jira.bc.user.search.UserSearchService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.exception.PermissionException;
import com.atlassian.jira.exception.RemoveException;
import com.atlassian.jira.security.groups.GroupManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.UserUtils;
import com.atlassian.jira.user.util.UserUtil;
import com.atlassian.sal.api.user.UserManager;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class MyPluginUserActionServlet extends HttpServlet {

    private final ApplicationUser adminUser = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser();
    private final JiraServiceContext serviceContext = new JiraServiceContextImpl(adminUser);
    private final GroupManager groupManager = ComponentAccessor.getComponent(GroupManager.class);
    private final UserSearchService userSearchService = ComponentAccessor.getComponent(UserSearchService.class);
    private final UserUtil userUtil = ComponentAccessor.getComponent(UserUtil.class);
    private final UserManager userManager = ComponentAccessor.getComponent(UserManager.class);

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String action = req.getParameter("action");
        String username = req.getParameter("username");
        if(!groupManager.isUserInGroup(adminUser, groupManager.getGroup("jira-administrators"))) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "You do not have permission to perform this action");
            return;
        }
        if (action == null || username == null) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Action and username parameters are required");
            return;
        }
        ApplicationUser user = userSearchService.getUserByName(serviceContext, username);

        if (user == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "User not found");
            return;
        }

        if(action.equals("remove-group")) {
            try {
                userUtil.removeUserFromGroup(groupManager.getGroup("jira-software-users"), user);
            } catch (PermissionException | RemoveException e) {
                resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to remove user from group: " + e.getMessage());
                return;
            }
        }
        else if(action.equals("deactivate")) {
            try {
                userUtil.removeUserFromGroups(groupManager.getGroupsForUser(user.getUsername()), user);
            } catch (PermissionException | RemoveException e) {
                resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to deactivate user: " + e.getMessage());
                return;
            }
        }
        else {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid action parameter");
            return;
        }

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setContentType("text/html");
        resp.getWriter().write("<html><body>Action " + action + " performed successfully on user " + username + ".<br/>");
        resp.getWriter().write("<a href=\"/jira/plugins/servlet/my-plugin-user-detail?username=" + username + "\">Back to user</a></body></html>");

    }
}


