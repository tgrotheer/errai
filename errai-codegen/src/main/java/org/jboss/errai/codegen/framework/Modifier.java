/*
 * Copyright 2011 JBoss, by Red Hat, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.errai.codegen.framework;

/**
 * @author Mike Brock <cbrock@redhat.com>
 */
public enum Modifier implements Comparable<Modifier> {
  Synchronized("synchronized"),
  Native("native"),
  JSNI("native"),
  Static("static"),
  Final("final"),
  Abstract("abstract"),
  Transient("transient"),
  Volatile("volatile");

  private final String canonicalString;

  Modifier(String cananonicalString) {
    this.canonicalString = cananonicalString;
  }

  public String getCanonicalString() {
    return canonicalString;
  }
}