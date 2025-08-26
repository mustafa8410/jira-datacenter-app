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
import com.example.jira.helloworld.util.AdminUtil;
import com.example.jira.helloworld.util.FilterParams;
import com.example.jira.helloworld.util.FilteredUserPager;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.*;

public class BulkActionServlet extends HttpServlet {


    private final GroupManager groupManager = ComponentAccessor.getComponent(GroupManager.class);
    private final UserUtil userUtil = ComponentAccessor.getComponent(UserUtil.class);
    private final UserSearchService userSearchService = ComponentAccessor.getComponent(UserSearchService.class);
    private final TemplateRenderer templateRenderer = ComponentAccessor.getOSGiComponentInstanceOfType(TemplateRenderer.class);
    private final VelocityManager velocityManager = ComponentAccessor.getComponent(VelocityManager.class);
    private final String LICENCE_GROUP = "jira-software-users";
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        final ApplicationUser adminUser = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser();
        final JiraServiceContext serviceContext = new JiraServiceContextImpl(adminUser);

        if(!AdminUtil.isUserAdmin(adminUser)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "You do not have permission to perform this action");
            return;
        }

        String action = req.getParameter("action");

        if(!List.of("remove-group").contains(action)) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid action specified");
            return;
        }
        List<ApplicationUser> users = new ArrayList<>();



        String selectionMode = req.getParameter("selectionMode");
        if(selectionMode != null && selectionMode.equals("ALL_FILTERED")) {
            HttpSession session = req.getSession();
            String userKey = adminUser.getKey();
            Object saved = session.getAttribute("dash:filterParams:" + userKey);
            if(!(saved instanceof FilterParams)) {
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "No saved filter found for bulk action with ALL_FILTERED mode");
                return;
            }
            FilterParams filterParams = (FilterParams) saved;
            FilteredUserPager filteredUserPager = new FilteredUserPager(LICENCE_GROUP, filterParams);
            List<ApplicationUser> allUsers = filteredUserPager.allMatches();

            String[] excludedUsernames = req.getParameterValues("exclude");
            Set<String> exclude = new HashSet<>();
            if(excludedUsernames != null) {
                exclude.addAll(List.of(excludedUsernames));
            }
            for(ApplicationUser user: allUsers) {
                if(!exclude.contains(user.getUsername())) {
                    users.add(user);
                }
            }
        }
        else {
            String[] selectedUsersArray = req.getParameterValues("selectedUsers");
            if (selectedUsersArray == null || selectedUsersArray.length == 0) {
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "No users selected for action");
                return;
            }
            List<String> selectedUsers = List.of(selectedUsersArray);
            for(String username: selectedUsers) {
                ApplicationUser user = userSearchService.getUserByName(serviceContext, username);
                if (user != null) {
                    users.add(user);
                }

            }
        }


        if(users == null || users.isEmpty()) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "No users found for the specified criteria");
            return;
        }

        boolean confirmed = "1".equals(req.getParameter("confirm"));
        if (!confirmed) {
            resp.setContentType("text/html; charset=UTF-8");
            Map<String, Object> ctx = new HashMap<>();
            ctx.put("action", action);
            ctx.put("users", users);
            ctx.put("contextPath", req.getContextPath());

            // echo original selection inputs so the confirmation POST can re-send them
            ctx.put("selectionMode", Optional.ofNullable(req.getParameter("selectionMode")).orElse("MANUAL"));
            ctx.put("selectedUsers", Optional.ofNullable(req.getParameterValues("selectedUsers")).orElse(new String[0]));
            ctx.put("exclude", Optional.ofNullable(req.getParameterValues("exclude")).orElse(new String[0]));

            String html = velocityManager.getEncodedBody("", "templates/bulk-action-confirm.vm", "UTF-8", ctx);
            resp.getWriter().write(html);
            return;
        }


        if(action.equals("remove-group")) {
            Group group = groupManager.getGroup(LICENCE_GROUP);
            for(ApplicationUser currentUser: users) {
                try {
                    userUtil.removeUserFromGroup(group, currentUser);
                } catch (PermissionException | RemoveException e) {
                    resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to remove user from group: " + e.getMessage());
                    return;
                }
            }
        }

        else {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unsupported action: " + action);
        }

        resp.setContentType("text/html; charset=UTF-8");
        Map<String, Object> context = new HashMap<>();
        context.put("action", action);
        context.put("users", users);
        context.put("contextPath", req.getContextPath());
        String html = velocityManager.getEncodedBody("", "templates/bulk-action-result.vm", "UTF-8", context);
        resp.getWriter().write(html);
    }
}
