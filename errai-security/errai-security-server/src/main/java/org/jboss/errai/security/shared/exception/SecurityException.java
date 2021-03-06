package org.jboss.errai.security.shared.exception;

import org.jboss.errai.common.client.api.annotations.Portable;

/**
 * A base class for all Errai Security exceptions.
 *
 * @author Max Barkley <mbarkley@redhat.com>
 */
@Portable
public class SecurityException extends RuntimeException {

  public SecurityException(final String message) {
    super(message);
  }

  public SecurityException() {
  }

  private static final long serialVersionUID = 9032128765408654433L;

}
