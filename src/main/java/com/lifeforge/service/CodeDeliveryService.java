package com.lifeforge.service;

/**
 * Abstraction for delivering a verification code to the account holder.
 *
 * <p>LifeForge ships with a development-only console implementation; a real
 * deployment can plug in an SMTP/email implementation behind this same
 * interface without changing any of the reset flow logic.
 */
public interface CodeDeliveryService {

    /**
     * Deliver the plaintext verification code to {@code destination}
     * (for password resets this is the account email address).
     */
    void deliver(String destination, String code);
}