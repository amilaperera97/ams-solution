package uk.co.ams.certplatform.domain.model;

/**
 * One provider tag on a resource. Tags are how an operator works out who owns a
 * certificate, so they travel with it from discovery all the way to the UI.
 */
public record ResourceTag(String key, String value) {
}
