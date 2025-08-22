package com.example.jira.helloworld.util;

import com.atlassian.jira.bc.security.login.LoginService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.security.groups.GroupManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.Page;
import com.atlassian.jira.util.PageRequest;
import com.atlassian.jira.util.PageRequests;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class FilteredUserPager {
    private static final int SCAN_BATCH = 500;
    private final GroupManager groupManager = ComponentAccessor.getComponent(GroupManager.class);
    private final LoginService loginService = ComponentAccessor.getComponent(LoginService.class);

    private final String licenseGroup;
    private final FilterParams filterParams;

    private final Map<String, Long> lastLoginCache = new HashMap<>();

    public static class PageResult {
        public final List<ApplicationUser> pageUsers;
        public final int totalMatches;

        public PageResult(List<ApplicationUser> pageUsers, int totalMatches) {
            this.pageUsers = pageUsers;
            this.totalMatches = totalMatches;
        }
    }
    public FilteredUserPager(String licenseGroup, FilterParams filterParams) {
        this.licenseGroup = licenseGroup;
        this.filterParams = filterParams;
    }

    public PageResult page(int page, int pageSize) {
        Long cutoffMillis = computeCutoffMillis(filterParams);
        List<String> groups = sortGroupsBySize();

        List<ApplicationUser> pageUsers;
        int totalMatches;

        if(filterParams.groupMode.equals("AND")){
            List<Object> result = pageUsersForGroupModeAnd(groups, page, pageSize, cutoffMillis);
            pageUsers = (List<ApplicationUser>) result.get(0);
            totalMatches = (Integer) result.get(1);
        }
        else if(filterParams.groupMode.equals("OR")) {
            List<Object> result = pageUsersForGroupModeOr(groups, page, pageSize, cutoffMillis);
            pageUsers = (List<ApplicationUser>) result.get(0);
            totalMatches = (Integer) result.get(1);
        }
        else
            throw new IllegalArgumentException("Invalid group mode: " + filterParams.groupMode);
        System.out.println("Total matches: " + totalMatches);

        return new PageResult(pageUsers, totalMatches);
    }

    private boolean passesFiltersForAnd(ApplicationUser user, Long cutoffMillis) {
        return passesGroupFilterForAnd(user) && passesInactivity(user, cutoffMillis);
    }

    private boolean passesGroupFilterForAnd(ApplicationUser user) {
        for(String group: filterParams.groups)
            if(!groupManager.isUserInGroup(user, group))
                return false;
        return true;
    }


    private boolean passesInactivity(ApplicationUser user, Long cutoffMillis) {
        if(cutoffMillis == null)
            return true;
        Long millis = cachedLastLogin(user.getUsername());
        System.out.println("Checking user: " + user.getUsername() + ", last login millis: " + millis + ", cutoff: " + cutoffMillis);
        if(millis == null)
            return true; // User has never logged in
        return millis < cutoffMillis;
    }

    private Long cachedLastLogin(String username) {
        if(lastLoginCache.containsKey(username))
            return lastLoginCache.get(username);
        Long millis = loginService.getLoginInfo(username).getLastLoginTime();
        lastLoginCache.put(username, millis);
        return millis;
    }


    private List<String> sortGroupsBySize() {
        Set<String> groups = filterParams.groups;
        if(groups == null || groups.isEmpty())
            return Collections.singletonList(licenseGroup);
        List<String> sortedGroups = new ArrayList<>(groups);
        sortedGroups.sort(Comparator.comparingInt(groupManager::getUsersInGroupCount));
        return sortedGroups;
    }

    private Long computeCutoffMillis(FilterParams filterParams) {
        if(filterParams != null) {
            if(filterParams.beforeDate != null) {
                return filterParams.beforeDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            }
            if(filterParams.inactiveDays != null && filterParams.inactiveDays > 0) {
                return Instant.now().minus(filterParams.inactiveDays, ChronoUnit.DAYS).toEpochMilli();
            }
        }
        return null;
    }

    private List<Object> pageUsersForGroupModeAnd(List<String> groups, int page, int pageSize,
                                                           Long cutoffMillis) {
        int toSkip = (page - 1) * pageSize;
        int skipped = 0, totalMatches = 0;
        List<ApplicationUser> pageUsers = new ArrayList<>(pageSize);
        Set<String> seenUsernames = new HashSet<>();
        long offset = 0;
        boolean hasNext = true;
        String group = groups.get(0); // For AND mode, we only have one group to process

        while(hasNext) {
            Page<ApplicationUser> batch =
                    groupManager.getUsersInGroup(group, true, PageRequests.request(offset, SCAN_BATCH));
            for(ApplicationUser user: batch.getValues()) {
                if(!seenUsernames.contains(user.getUsername())){
                    if(passesFiltersForAnd(user, cutoffMillis)) {
                        totalMatches++;
                        if(skipped < toSkip)
                            skipped++;
                        else if(pageUsers.size() < pageSize) {
                            pageUsers.add(user);
                        }
                    }
                    seenUsernames.add(user.getUsername());
                }
            }
            hasNext = !batch.isLast();
            offset += SCAN_BATCH;
        }
        List<Object> result = new ArrayList<>(2);
        result.add(pageUsers);
        result.add(totalMatches);
        return result;
    }

    private List<Object> pageUsersForGroupModeOr(List<String> groups, int page, int pageSize,
                                                          Long cutoffMillis) {
        int toSkip = (page - 1) * pageSize;
        int skipped = 0, totalMatches = 0;
        List<ApplicationUser> pageUsers = new ArrayList<>(pageSize);
        Set<String> seenUsernames = new HashSet<>();
        long offset;
        boolean hasNext;

        System.out.println("Groups: " + groups);
        for(String group: groups) {
            offset = 0;
            hasNext = true;
            System.out.println("Processing group: " + group);
            while(hasNext) {
                Page<ApplicationUser> batch = groupManager.getUsersInGroup(group, true,
                        PageRequests.request(offset, SCAN_BATCH));
                for(ApplicationUser user: batch.getValues()) {
                    System.out.println("Checking user: " + user.getUsername() + " for group: " + group);
                    if(!seenUsernames.contains(user.getUsername())) {
                        if(passesInactivity(user, cutoffMillis)) {
                            totalMatches++;
                            if(skipped < toSkip) {
                                skipped++;
                            } else if(pageUsers.size() < pageSize) {
                                pageUsers.add(user);
                            }
                            seenUsernames.add(user.getUsername());
                        }

                    }
                }
                hasNext = !batch.isLast();
                offset += SCAN_BATCH;
            }
        }
        List<Object> result = new ArrayList<>(2);
        result.add(pageUsers);
        result.add(totalMatches);
        return result;

    }
}
