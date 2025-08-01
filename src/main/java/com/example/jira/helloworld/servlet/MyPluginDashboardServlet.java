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


        ApplicationUser adminUser = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser();
        JiraServiceContext context = new JiraServiceContextImpl(adminUser);
        List<ApplicationUser> users = ComponentAccessor.getComponent(UserSearchService.class).findUsersAllowEmptyQuery(context, "");

        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, users.size());
        if (start >= users.size()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "No users found for the specified page");
            return;
        }



        List<UserRow> userRows = createUserRows(users.subList(start, end), adminUser);


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

    public List<UserRow> getUsersFromServlet(HttpServletRequest req, HttpServletResponse resp, int page, int pageSize, ApplicationUser adminUser)
            throws InterruptedException, IOException {
        String jiraBaseUrl = ComponentAccessor.getApplicationProperties().getString("jira.baseurl");
        if (jiraBaseUrl == null || jiraBaseUrl.isEmpty()) {
            throw new IllegalStateException("Jira base URL is not configured");
        }
        String restUrl = jiraBaseUrl + "/rest/api/2/users/search?startAt=" + page + "&maxResults=" + pageSize;
        HttpClient httpClient = HttpClient.newBuilder().build();
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().uri(URI.create(restUrl)).header("Accept", "application/json");

        String sessionId = null;
        for(Cookie cookie : req.getCookies()) {
            if("JSESSIONID".equals(cookie.getName())) {
                sessionId = cookie.getValue();
                break;
            }
        }

        if(sessionId != null) {
            requestBuilder.header("Cookie", "JSESSIONID=" + sessionId);
        } else {
            throw new IllegalStateException("JSESSIONID cookie not found in request");
        }

        HttpRequest httpRequest = requestBuilder.GET().build();

        HttpResponse<String> response = null;
        response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (response == null || response.statusCode() != 200) {
            throw new IOException("Failed to fetch users from Jira REST API. Status code: " + (response != null ? response.statusCode() : "null"));
        }
        String responseBody = response.body();
        ObjectMapper objectMapper = new ObjectMapper();
        List<JiraUser> users = objectMapper.readValue(responseBody, new TypeReference<List<JiraUser>>(){});
        UserPropertyManager userPropertyManager = ComponentAccessor.getUserPropertyManager();

        List<UserRow> userRows = new ArrayList<>();
        for(JiraUser jiraUser : users) {
            String lastLoginString = "Never";
            ApplicationUser user = ComponentAccessor.getUserManager().getUserByKey(jiraUser.getKey());
            LoginInfo loginInfo = ComponentAccessor.getComponent(LoginService.class).getLoginInfo(jiraUser.getName());
            Long millis = loginInfo.getLastLoginTime();

            if(millis != null) {
                LocalDateTime lastLogin = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDateTime();
                lastLoginString = getTimeString(lastLogin);
            }
            List<String> warnings;
            try {
                warnings = UserWarningUtil.getWarningsForUser(user, adminUser);
            } catch (Exception e) {
                resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to fetch warnings for user: " + e.getMessage());
                warnings = Collections.emptyList();
            }
            userRows.add(new UserRow(user.getName(), user.getDisplayName(), lastLoginString, warnings));

        }
        return userRows;
    }


}

