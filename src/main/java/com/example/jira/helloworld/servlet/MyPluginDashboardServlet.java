package com.example.jira.helloworld.servlet;

import com.atlassian.jira.bc.JiraServiceContext;
import com.atlassian.jira.bc.JiraServiceContextImpl;
import com.atlassian.jira.bc.security.login.LoginInfo;
import com.atlassian.jira.bc.security.login.LoginService;
import com.atlassian.jira.bc.user.UserService;
import com.atlassian.jira.bc.user.search.UserSearchService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.UserPropertyManager;
import com.atlassian.templaterenderer.TemplateRenderer;
import com.example.jira.helloworld.JiraUser;
import com.example.jira.helloworld.UserRow;
import com.example.jira.helloworld.util.UserWarningUtil;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;


public class MyPluginDashboardServlet extends HttpServlet {

    private final TemplateRenderer templateRenderer = ComponentAccessor.getOSGiComponentInstanceOfType(TemplateRenderer.class);
    private final LoginService loginService = ComponentAccessor.getComponent(LoginService.class);

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

        String jiraBaseUrl = ComponentAccessor.getApplicationProperties().getString("jira.baseurl");
        if (jiraBaseUrl == null || jiraBaseUrl.isEmpty()) {
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Jira base URL is not configured");
            return;
        }
//        String restUrl = jiraBaseUrl + "/rest/api/2/users/search?startAt=" + page + "&maxResults=" + pageSize;
//        HttpClient httpClient = HttpClient.newBuilder().build();
//        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().uri(URI.create(restUrl)).header("Accept", "application/json");
//
//        String sessionId = null;
//        for(Cookie cookie : req.getCookies()) {
//            if("JSESSIONID".equals(cookie.getName())) {
//                sessionId = cookie.getValue();
//                break;
//            }
//        }
//
//        if(sessionId != null) {
//            requestBuilder.header("Cookie", "JSESSIONID=" + sessionId);
//        } else {
//            resp.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Session ID not found in cookies");
//            return;
//        }
//
//        HttpRequest httpRequest = requestBuilder.GET().build();
//
//        HttpResponse<String> response = null;
//        try {
//            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
//        } catch (InterruptedException e) {
//            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to fetch user data: " + e.getMessage());
//        }
//        if (response == null || response.statusCode() != 200) {
//            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to fetch user data: " + (response != null ? response.body() : "No response"));
//            return;
//        }
//        String responseBody = response.body();
//        ObjectMapper objectMapper = new ObjectMapper();
//        List<JiraUser> users = objectMapper.readValue(responseBody, new TypeReference<List<JiraUser>>(){});
//




        ApplicationUser currentUser = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser();
        JiraServiceContext context = new JiraServiceContextImpl(currentUser);
        List<ApplicationUser> users = ComponentAccessor.getComponent(UserSearchService.class).findUsersAllowEmptyQuery(context, "");

        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, users.size());
        if (start >= users.size()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "No users found for the specified page");
            return;
        }



        UserPropertyManager userPropertyManager = ComponentAccessor.getUserPropertyManager();
        List<UserRow> userRows = new ArrayList<>();


        for(ApplicationUser user: users) {
            String lastLoginString = "Never";



            // placeholder for now because apparently jira is written by middle schoolers, thus can't fetch a simple property
            // userPropertyManager.getPropertySet(user).setString("login.lastLoginMillis", String.valueOf(System.currentTimeMillis()));

//            String loginMillisString  = userPropertyManager.getPropertySet(user).getString("login.lastLoginMillis");
            LoginInfo loginInfo = loginService.getLoginInfo(user.getName());
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
            userRows.add(new UserRow(user.getName(), user.getDisplayName(), lastLoginString, warnings));
        }

        Map<String, Object> contextMap = new HashMap<>();
        contextMap.put("users", userRows);
        contextMap.put("currentPage", page);
        contextMap.put("pageSize", pageSize);

        int totalPages = (int) Math.ceil(users.size() / (double) pageSize);
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

}
