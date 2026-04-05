package org.chappyGolf.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.apache.cayenne.Cayenne;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.query.ObjectSelect;
import org.chappyGolf.dto.AuthUserResponse;
import org.chappyGolf.dto.LoginRequest;
import org.chappyGolf.model.cayenne.Users;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public static final String ADMIN_USER_ID_SESSION_KEY = "adminUserId";

    private final ObjectContext context;
    private final BCryptPasswordEncoder passwordEncoder;

    public AuthController(ObjectContext context, BCryptPasswordEncoder passwordEncoder) {
        this.context = context;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/login")
    public AuthUserResponse login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new RuntimeException("Email is required");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new RuntimeException("Password is required");
        }

        Users user = ObjectSelect.query(Users.class)
                .where(Users.EMAIL.eq(request.getEmail().trim().toLowerCase()))
                .selectOne(context);

        if (user == null) {
            throw new RuntimeException("Invalid email or password");
        }

        if (!"ADMIN".equalsIgnoreCase(user.getRole())) {
            throw new RuntimeException("Not authorized");
        }

        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid email or password");
        }

        HttpSession session = httpRequest.getSession(true);
        session.setAttribute(ADMIN_USER_ID_SESSION_KEY, Cayenne.intPKForObject(user));

        return new AuthUserResponse(
                Cayenne.intPKForObject(user),
                user.getName(),
                user.getEmail(),
                user.getRole()
        );
    }

    @GetMapping("/me")
    public AuthUserResponse me(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new RuntimeException("Not authenticated");
        }

        Integer userId = (Integer) session.getAttribute(ADMIN_USER_ID_SESSION_KEY);
        if (userId == null) {
            throw new RuntimeException("Not authenticated");
        }

        Users user = Cayenne.objectForPK(context, Users.class, userId);
        if (user == null || !"ADMIN".equalsIgnoreCase(user.getRole())) {
            throw new RuntimeException("Not authenticated");
        }

        return new AuthUserResponse(
                Cayenne.intPKForObject(user),
                user.getName(),
                user.getEmail(),
                user.getRole()
        );
    }

    @PostMapping("/logout")
    public String logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return "Logged out";
    }
}