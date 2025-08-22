package com.example.jira.helloworld.servlet;

import com.atlassian.crowd.embedded.api.Group;
import com.atlassian.jira.bc.group.GroupService;
import com.atlassian.jira.bc.security.login.LoginInfo;
import com.atlassian.jira.bc.security.login.LoginService;
import com.atlassian.jira.bc.user.search.UserSearchService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.security.groups.GroupManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.Page;
import com.atlassian.jira.util.PageRequest;
import com.atlassian.jira.util.PageRequests;
import com.atlassian.templaterenderer.TemplateRenderer;
import com.atlassian.velocity.VelocityManager;
import com.example.jira.helloworld.UserRow;
import com.example.jira.helloworld.util.AdminUtil;
import com.example.jira.helloworld.util.FilterParams;
import com.example.jira.helloworld.util.FilteredUserPager;
import com.example.jira.helloworld.util.UserWarningUtil;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;


public class MyPluginDashboardServlet extends HttpServlet {

    private final TemplateRenderer templateRenderer = ComponentAccessor.getOSGiComponentInstanceOfType(TemplateRenderer.class);
    private final LoginService loginService = ComponentAccessor.getComponent(LoginService.class);
    private final GroupManager groupManager = ComponentAccessor.getComponent(GroupManager.class);
    private final JiraAuthenticationContext jiraAuthenticationContext = ComponentAccessor.getComponent(JiraAuthenticationContext.class);
    private final VelocityManager velocityManager = ComponentAccessor.getComponent(VelocityManager.class);
    private final int DEFAULT_PAGE_SIZE = 10;
    private final String LICENCE_GROUP = "jira-software-users";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        final ApplicationUser adminUser = jiraAuthenticationContext.getLoggedInUser();
        HttpSession session = req.getSession();
        String userKey = adminUser.getKey();

        if(!AdminUtil.isUserAdmin(adminUser)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "You do not have permission to access this page");
            return;
        }

        Integer page, pageSize;
        try {
            pageSize = getPageSize(req, session, resp, userKey);
        }
        catch (NumberFormatException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid page size: " + e.getMessage());
            return;
        }
        try {
            page = getPageNumber(req, session, resp, userKey);
        }
        catch (NumberFormatException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid page number: " + e.getMessage());
            return;
        }
        catch (IndexOutOfBoundsException e) {
            resp.sendRedirect(req.getContextPath()
                    + "/plugins/servlet/my-plugin-dashboard?page=1&pageSize=" + pageSize);
            return;
        }

        boolean hasFilterParams =
                req.getParameter("inactiveDays") != null ||
                        req.getParameter("beforeDate")   != null ||
                        req.getParameter("groups")       != null ||
                        req.getParameter("groupMode")    != null;

        FilterParams filterParams;
        if(hasFilterParams) {
            filterParams = FilterParams.from(req);
            session.setAttribute("dash:filterParams" + userKey, filterParams);
        }
        else {
            Object saved = session.getAttribute("dash:filters:" + userKey);
            filterParams = (saved instanceof FilterParams) ? (FilterParams) saved
                    : FilterParams.from(req);
        }

        FilteredUserPager filteredUserPager = new FilteredUserPager(LICENCE_GROUP, filterParams);
        FilteredUserPager.PageResult pageResult = filteredUserPager.page(page, pageSize);
        int totalMatches = pageResult.totalMatches;
        int totalPages = Math.max(1, (int) Math.ceil(totalMatches / (double) pageSize));


//        Group softwareUsersGroup = groupManager.getGroup(LICENCE_GROUP);
//        int userCount = groupManager.getUsersInGroupCount(softwareUsersGroup);
//        int totalPages = (int) Math.ceil(userCount / (double) pageSize);
//        if (totalPages == 0) totalPages = 1;

//        int start = (page - 1) * pageSize;
        // int end = Math.min(start + pageSize, userCount);
        if (page > totalPages) {
            session.removeAttribute("dash:page" + userKey);
            resp.sendRedirect(req.getContextPath()
                    + "/plugins/servlet/my-plugin-dashboard?page=" + totalPages + "&pageSize=" + pageSize);
            return;
        }

//        PageRequest pageRequest = PageRequests.request((long) start, pageSize);
//        Page<ApplicationUser> usersPage = groupManager.getUsersInGroup(LICENCE_GROUP, true, pageRequest);


//        List<UserRow> userRows = usersPage == null ? Collections.emptyList()
//                : createUserRows(usersPage.getValues(), adminUser);

        Collection<String> allGroupNames = groupManager.getAllGroupNames()
                .stream().filter(name -> !name.equals(LICENCE_GROUP)).sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toList());

        List<UserRow> userRows = createUserRows(pageResult.pageUsers, adminUser);

        session.setAttribute("dash:page" + userKey, page);
        session.setAttribute("dash:pageSize" + userKey, pageSize);
        session.setAttribute("dash:filters:" + userKey, filterParams);

        Map<String, Object> contextMap = new HashMap<>();
        contextMap.put("users", userRows);
        contextMap.put("currentPage", page);
        contextMap.put("pageSize", pageSize);
        contextMap.put("contextPath", req.getContextPath());

        contextMap.put("totalPages", totalPages);

        contextMap.put("groupNames", allGroupNames);

        contextMap.put("inactiveDays", filterParams.inactiveDays);
        contextMap.put("beforeDate", filterParams.beforeDate == null ? "" : filterParams.beforeDate.toString());
        contextMap.put("groups", filterParams.groups);
        contextMap.put("groupMode", filterParams.groupMode);

        // render the template defined with .vm file
        resp.setContentType("text/html;charset=UTF-8");
//        templateRenderer.render("templates/dashboard.vm", contextMap, resp.getWriter());
        String html = velocityManager.getEncodedBody("", "templates/dashboard.vm", "UTF-8", contextMap);
        resp.getWriter().write(html);
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

    public Integer getPageSize(HttpServletRequest req, HttpSession session, HttpServletResponse resp, String userKey)
            throws NumberFormatException {
        Integer pageSize;
        if(req.getParameter("pageSize") == null) {
            Object ps = session.getAttribute("dash:pageSize" + userKey);
            if (ps instanceof Integer)
                pageSize = (Integer) ps;
            else
                pageSize = DEFAULT_PAGE_SIZE;
        }
        else {
            try {
                pageSize = Integer.parseInt(req.getParameter("pageSize"));
                if (pageSize <= 0) {
                    throw new NumberFormatException("Page size must be a positive integer");
                }
                if(pageSize > 100) {
                    throw new NumberFormatException("Page size cannot exceed 100");
                }
            } catch (NumberFormatException e) {
                throw new NumberFormatException("Invalid page size: " + e.getMessage());
            }
        }
        return pageSize;
    }

    public Integer getPageNumber(HttpServletRequest req, HttpSession session, HttpServletResponse resp, String userKey)
            throws NumberFormatException, IndexOutOfBoundsException {
        Integer page;

        if(req.getParameter("page") == null) {
            Object p = session.getAttribute("dash:page" + userKey);
            if (p instanceof Integer)
                page = (Integer) p;
            else {
                throw new IndexOutOfBoundsException("Page number not found in session, redirecting to page 1");
            }
        }
        else {
            try {
                page = Integer.parseInt(req.getParameter("page"));
                if(page <= 0) {
                    throw new IndexOutOfBoundsException("Page number must be a positive integer, redirecting to page 1");
                }
            } catch (NumberFormatException e) {
                throw new NumberFormatException("Invalid page number: " + e.getMessage());
            }
        }

        return page;

    }

}

