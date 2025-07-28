package com.example.jira.helloworld.jql;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.jql.parser.JqlParseException;
import com.atlassian.jira.jql.parser.JqlQueryParser;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.web.bean.PagerFilter;
import com.atlassian.query.Query;
import com.atlassian.sal.api.search.SearchProvider;
import com.atlassian.sal.api.search.SearchResults;


public class UserDetailsJql {

    private static final JqlQueryParser jqlQueryParser = ComponentAccessor.getComponent(JqlQueryParser.class);
    private static final SearchProvider searchProvider = ComponentAccessor.getComponent(SearchProvider.class);

    public static int countIssues(String jql, ApplicationUser applicationUser) throws JqlParseException {
        Query query = jqlQueryParser.parseQuery(jql);
        SearchResults results = searchProvider.search(applicationUser.getKey(), jql);
        System.out.println(results.getTotalResults());
        return results.getTotalResults();

    }

}
