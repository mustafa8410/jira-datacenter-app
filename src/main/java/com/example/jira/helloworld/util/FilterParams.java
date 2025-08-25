package com.example.jira.helloworld.util;

import javax.servlet.http.HttpServletRequest;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class FilterParams implements Serializable {

    public final Integer inactiveDays;
    public final LocalDate beforeDate;
    public final Set<String> groups;
    public final String groupMode;
    public final String query;

    public FilterParams(Integer inactiveDays, LocalDate beforeDate, Set<String> groups, String groupMode, String query) {
        this.inactiveDays = inactiveDays;
        this.beforeDate = beforeDate;
        this.groups = groups;
        this.groupMode = groupMode;
        this.query = query;
    }

    public static FilterParams from(HttpServletRequest req) {
        Integer inactiveDays = null;
        String inactiveDaysString = req.getParameter("inactiveDays");
        if(inactiveDaysString != null && !inactiveDaysString.isEmpty()) {
            inactiveDays = Integer.parseInt(inactiveDaysString);
        }


        LocalDate beforeDate = null;
        String beforeDateString = req.getParameter("beforeDate");
        if(beforeDateString != null && !beforeDateString.isEmpty()) {
            beforeDate = LocalDate.parse(beforeDateString);
        }


        Set<String> groups = Collections.emptySet();
        String groupsString = req.getParameter("groups");
        if(groupsString != null && !groupsString.trim().isEmpty()) {
            groups = Arrays.stream(groupsString.split(",")).map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        String groupMode = req.getParameter("groupMode");
        if(groupMode == null || groupMode.isEmpty())
            groupMode = "OR";
        if(!groupMode.equals("OR") && !groupMode.equals("AND")) {
            throw new IllegalArgumentException("Invalid groupMode: " + groupMode + ". Must be 'OR' or 'AND'.");
        }

        String query = req.getParameter("query");
        if(query == null)
            query = "";
        else
            query = query.trim();


        return new FilterParams(inactiveDays, beforeDate, groups, groupMode.trim(), query);

    }


    public String cacheKey(String seedGroup) {
        List<String> gs = new ArrayList<>(groups);
        Collections.sort(gs);
        return "seed=" + seedGroup
                + "|groups=" + String.join(";", gs)
                + "|inactiveDays=" + (inactiveDays == null ? "" : inactiveDays)
                + "|beforeDate=" + (beforeDate == null ? "" : beforeDate)
                + "|groupMode=" + groupMode
                + "|query=" + query;
    }
}
