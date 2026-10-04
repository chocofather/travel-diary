package com.tripbora.service.user;

public record RegistrationResult(String email, boolean verificationEmailRequested) {
}
