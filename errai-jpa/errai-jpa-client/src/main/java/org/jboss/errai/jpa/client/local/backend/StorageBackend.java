package org.jboss.errai.jpa.client.local.backend;

import org.jboss.errai.jpa.client.local.ErraiEntityType;

/**
 * Represents a browser-local persistent storage backend.
 * <p>
 * WARNING: this interface is in an extreme state of flux. It is guaranteed to
 * change as the first few alternative implementations are developed.
 *
 * @author Jonathan Fuerth <jfuerth@gmail.com>
 */
public interface StorageBackend {

  <X, T> void put(ErraiEntityType<X> type, T key, X value);

  <X, T> X get(ErraiEntityType<X> type, T key);

}
