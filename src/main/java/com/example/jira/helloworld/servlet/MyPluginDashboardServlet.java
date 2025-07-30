package com.example.jira.helloworld.servlet;

import com.atlassian.jira.bc.JiraServiceContext;
import com.atlassian.jira.bc.JiraServiceContextImpl;
import com.atlassian.jira.bc.security.login.LoginInfo;
import com.atlassian.jira.bc.security.login.LoginService;
import com.atlassian.jira.bc.user.search.UserSearchService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.UserDetails;
import com.atlassian.jira.user.UserPropertyManager;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.templaterenderer.TemplateRenderer;
import com.example.jira.helloworld.UserRow;
import com.example.jira.helloworld.util.UserWarningUtil;
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
    private final LoginService loginService = ComponentAccessor.getComponent(LoginService.class);

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

//            String loginMillisString  = userPropertyManager.getPropertySet(user).getString("login.lastLoginMillis");
            LoginInfo loginInfo = loginService.getLoginInfo(user.getUsername());
            Long millis = loginInfo.getLastLoginTime();
//            System.out.println(
//                    "User: " + user.getUsername() +
//                            ", userKey: " + user.getKey() +
//                            ", id: " + user.getId() +
//                            ", directoryId: " + user.getDirectoryId()
//            );
//
//            System.out.println("Last Login Millis: " + loginMillisString);
//            if(loginMillisString != null && !loginMillisString.isEmpty()) {
            if(millis != null) {
//                Long millis = Long.parseLong(loginMillisString);
//                LocalDateTime lastLogin = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDateTime();
//                lastLoginString = lastLogin.toString();
                LocalDateTime lastLogin = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDateTime();
                lastLoginString = getTimeString(lastLogin);


            }
            List<String> warnings;
            try {
                warnings = UserWarningUtil.getWarningsForUser(user, currentUser);
            } catch (Exception e) {
                resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to fetch warnings for user: " + e.getMessage());
                warnings = Collections.emptyList();
            }
            System.out.println(warnings.toString());
            userRows.add(new UserRow(user.getUsername(), user.getDisplayName(), lastLoginString, warnings));
        }

        Map<String, Object> contextMap = new HashMap<>();
        contextMap.put("users", userRows);

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

}
