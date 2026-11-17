package com.smartAiUniversityAssistant.seniorproject.feature.authentication.service;
/**
 * @param pendingApproval spec file 62: the password was right, but the account waits for a Super Admin's approval
 *                        (never true together with {@code accepted})
 */
public record CredentialVerificationResult(boolean accepted, Long userId, String revision, boolean pendingApproval) {
    public CredentialVerificationResult(boolean accepted, Long userId, String revision) {
        this(accepted, userId, revision, false);
    }
    public static CredentialVerificationResult rejected() { return new CredentialVerificationResult(false, null, null); }
    public static CredentialVerificationResult awaitingApproval() {
        return new CredentialVerificationResult(false, null, null, true);
    }
    @Override public String toString() {
        return "CredentialVerificationResult[accepted=" + accepted + ", pendingApproval=" + pendingApproval + "]";
    }
}
