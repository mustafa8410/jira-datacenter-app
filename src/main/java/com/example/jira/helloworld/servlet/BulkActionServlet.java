package com.example.jira.helloworld.servlet;
import com.atlassian.crowd.embedded.api.Group;
import com.atlassian.jira.bc.JiraServiceContext;
import com.atlassian.jira.bc.JiraServiceContextImpl;
import com.atlassian.jira.bc.user.search.UserSearchService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.exception.PermissionException;
import com.atlassian.jira.exception.RemoveException;
import com.atlassian.jira.security.groups.GroupManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.util.UserUtil;
import com.atlassian.templaterenderer.TemplateRenderer;
import com.atlassian.velocity.VelocityManager;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

@WebServlet("/plugins/servlet/bulk-action")
public class BulkActionServlet extends HttpServlet {


    private final GroupManager groupManager = ComponentAccessor.getComponent(GroupManager.class);
    private final UserUtil userUtil = ComponentAccessor.getComponent(UserUtil.class);
    private final UserSearchService userSearchService = ComponentAccessor.getComponent(UserSearchService.class);
    private final TemplateRenderer templateRenderer = ComponentAccessor.getOSGiComponentInstanceOfType(TemplateRenderer.class);
    private final VelocityManager velocityManager = ComponentAccessor.getComponent(VelocityManager.class);
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        final ApplicationUser adminUser = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser();
        final JiraServiceContext serviceContext = new JiraServiceContextImpl(adminUser);

        Set<String> adminGroups = new HashSet<>();
        adminGroups.add("jira-administrators");
        adminGroups.add("administrators");
        adminGroups.add("system-administrators");
        if(!groupManager.isUserInGroups(adminUser, adminGroups)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "You do not have permission to perform this action");
            return;
        }
        List<String> selectedUsers = List.of(req.getParameterValues("selectedUsers"));
        String action = req.getParameter("action");

        if(!List.of("remove-group", "deactivate").contains(action)) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid action specified");
            return;
        }
        if (selectedUsers.isEmpty()) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "No users selected for action");
            return;
        }
        List<ApplicationUser> users = new ArrayList<>();
        for(String username: selectedUsers) {
            ApplicationUser user = userSearchService.getUserByName(serviceContext, username);
            if (user != null) {
                users.add(user);
            }

        }
        if(action.equals("remove-group")) {
            Group group = groupManager.getGroup("jira-software-users");
            for(ApplicationUser currentUser: users) {
                try {
                    userUtil.removeUserFromGroup(group, currentUser);
                } catch (PermissionException | RemoveException e) {
                    resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to remove user from group: " + e.getMessage());
                    return;
                }
            }
        }

//        else if(action.equals("deactivate")) {
//            for(ApplicationUser currentUser: users) {
//                try {
//                    userUtil.removeUserFromGroups(groupManager.getGroupsForUser(currentUser.getUsername()), currentUser);
//                } catch (PermissionException | RemoveException e) {
//                    resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to deactivate user: " + e.getMessage());
//                    return;
//                }
//            }
//        }
        else {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unsupported action: " + action);
        }

//        resp.setContentType("text/html");
//        resp.getWriter().write("<html><body>Action " + action + " performed successfully on selected users.<br/>");
//        for(ApplicationUser user : users) {
//            resp.getWriter().write("User: " + user.getDisplayName() + "<br/>");
//        }
//        resp.getWriter().write("<a href=\"/jira/plugins/servlet//my-plugin-dashboard\">Go back to Dashboard</a></body></html>");

        resp.setContentType("text/html; charset=UTF-8");
        Map<String, Object> context = new HashMap<>();
        context.put("action", action);
        context.put("users", users);
        context.put("contextPath", req.getContextPath());
//        templateRenderer.render("templates/bulk-action-result.vm", context, resp.getWriter());
        String html = velocityManager.getEncodedBody("", "templates/bulk-action-result.vm", "UTF-8", context);
        resp.getWriter().write(html);



    }
}
