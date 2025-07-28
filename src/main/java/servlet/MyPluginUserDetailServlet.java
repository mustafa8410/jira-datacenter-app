package servlet;

import com.atlassian.jira.bc.JiraServiceContext;
import com.atlassian.jira.bc.JiraServiceContextImpl;
import com.atlassian.jira.bc.user.search.UserSearchService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.sal.api.user.UserManager;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class MyPluginUserDetailServlet extends HttpServlet {

    private final UserManager userManager = ComponentAccessor.getComponent(UserManager.class);
    private final UserSearchService userSearchService = ComponentAccessor.getComponent(UserSearchService.class);
    private final ApplicationUser currentUser = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser();
    private final JiraServiceContext serviceContext = new JiraServiceContextImpl(currentUser);
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String username = req.getParameter("username");
        if(username == null || username.isEmpty()) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Username parameter is required");
            return;
        }
        ApplicationUser user = userSearchService.getUserByName(serviceContext, username);

        if(user == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "User not found");
            return;
        }

        Map<String, Object> context = new HashMap<>();



    }
}
