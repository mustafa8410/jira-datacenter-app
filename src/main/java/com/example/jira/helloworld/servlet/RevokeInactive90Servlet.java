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
import org.codehaus.jackson.map.ObjectMapper;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
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


    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        final ApplicationUser adminUser = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser();
        if (adminUser == null || !groupManager.isUserInGroup(adminUser, "jira-administrators")) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            resp.setContentType("application/json");
            Map<String, Object> result = new HashMap<>();
            result.put("error", "You do not have permission to perform this action");
            new ObjectMapper().writeValue(resp.getWriter(), result);
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

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Done. Users affected: " + removedUsers.size());
        result.put("count", removedUsers.size());
        result.put("removedUsers", removedUsers);
        if(!failedUsers.isEmpty()) {
            result.put("failedUsers", failedUsers);
        }
        resp.setContentType("application/json");
        mapper.writeValue(resp.getWriter(), result);

    }
}
