package org.example;

import java.util.Set;

public record UserDto(
        Long id,
        String username,
        String email,
        Set<Role> roles)
{}
