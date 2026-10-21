package com.soloeats.system.service.command;

public record PasswordLoginCommand(
        String username,
        String password
) {
}
