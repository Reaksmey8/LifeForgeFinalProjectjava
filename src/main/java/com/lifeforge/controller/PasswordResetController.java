package com.lifeforge.controller;

import com.lifeforge.service.PasswordResetService;

/**
 * Password-reset front end for the TUI. Translates service results into the
 * shared last-error slot the same way AuthController / UserController do.
 * The screen reads lastError only for genuinely failed (rejected) outcomes.
 */
public class PasswordResetController extends BaseController {

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    public PasswordResetService.RequestResult startReset(String email) {
        PasswordResetService.RequestResult result = passwordResetService.requestReset(email);
        if (!result.accepted) {
            setError(result.message);
        }
        return result;
    }

    public PasswordResetService.RequestResult resendCode(Long userId) {
        PasswordResetService.RequestResult result = passwordResetService.resendCode(userId);
        if (!result.accepted) {
            setError(result.message);
        }
        return result;
    }

    public boolean verifyCode(Long userId, String code) {
        PasswordResetService.VerifyResult result = passwordResetService.verifyCode(userId, code);
        if (!result.success) {
            setError(result.message);
        }
        return result.success;
    }

    public boolean resetPassword(Long userId, String newPassword, String confirmPassword) {
        PasswordResetService.ResetResult result =
                passwordResetService.resetPassword(userId, newPassword, confirmPassword);
        if (!result.success) {
            setError(result.message);
        }
        return result.success;
    }

    /** Dev-only plaintext code used for the on-screen hint; null in production. */
    public String devLastCode(Long userId) {
        return passwordResetService.devLastCode(userId);
    }
}