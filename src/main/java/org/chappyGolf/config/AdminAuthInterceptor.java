package org.chappyGolf.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.apache.cayenne.Cayenne;
import org.apache.cayenne.ObjectContext;
import org.chappyGolf.controller.AuthController;
import org.chappyGolf.model.cayenne.Users;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private final ObjectContext context;

    public AdminAuthInterceptor(ObjectContext context) {
        this.context = context;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            return true;
        }

        HttpSession session = request.getSession(false);
        if (session == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
            return false;
        }

        Integer userId = (Integer) session.getAttribute(AuthController.ADMIN_USER_ID_SESSION_KEY);
        if (userId == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
            return false;
        }

        Users user = Cayenne.objectForPK(context, Users.class, userId);
        if (user == null || !"ADMIN".equalsIgnoreCase(user.getRole())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden");
            return false;
        }

        return true;
    }
}