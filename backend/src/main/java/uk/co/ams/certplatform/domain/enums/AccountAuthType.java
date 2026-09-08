package uk.co.ams.certplatform.domain.enums;

public enum AccountAuthType {
    /** Bearer token / client secret. Used by Azure and GCP; not usable against real AWS. */
    TOKEN,
    /** Assume an IAM role, optionally with an external ID. */
    IAM_ROLE,
    /** Long-lived AWS access key id + secret access key. */
    ACCESS_KEY
}
