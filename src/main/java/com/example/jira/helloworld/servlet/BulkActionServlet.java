package com.example.jira.helloworld.servlet;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet("/plugins/servlet/bulk-action")
public class BulkActionServlet extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String[] selectedUsers = req.getParameterValues("selectedUsers");

        if (selectedUsers != null) {
            for (String username : selectedUsers) {
                System.out.println("İşlem yapılacak kullanıcı: " + username);
                // TODO: Kullanıcıya ait işlem yap (örneğin lisans iptali)
            }
        }

        resp.setContentType("application/json");
        resp.getWriter().write("{\"status\":\"success\"}");
    }
}
