package com.example.jira.helloworld.servlet;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.security.groups.GroupManager;
import org.codehaus.jackson.map.ObjectMapper;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class GroupSearchServlet extends HttpServlet {
    private final GroupManager groupManager = ComponentAccessor.getComponent(GroupManager.class);
    private final String LICENCE_GROUP = "jira-software-users";

    @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String query = Optional.ofNullable(req.getParameter("q")).orElse("").toLowerCase();
        // naive: filter in-memory; for huge directories consider paging at the source
        List<String> all = groupManager.getAllGroupNames().stream().filter(
                n -> !n.equalsIgnoreCase(LICENCE_GROUP)
        ).collect(Collectors.toList());
        List<String> out = all.stream()
                .filter(n -> n.toLowerCase().contains(query))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .limit(100) // cap response
                .collect(Collectors.toList());

        resp.setContentType("application/json; charset=UTF-8");
        resp.getWriter().write(new ObjectMapper().writeValueAsString(out));
    }
}

