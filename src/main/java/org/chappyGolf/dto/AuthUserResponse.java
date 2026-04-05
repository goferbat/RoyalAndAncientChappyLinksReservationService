package org.chappyGolf.dto;

public record AuthUserResponse(
        int id,
        String name,
        String email,
        String role
) {}