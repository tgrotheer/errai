package org.jboss.errai.security.client.shared;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.jboss.errai.security.shared.api.annotation.RestrictedAccess;

@Path("test")
public interface SecureRestService {
  
  @Path("/admin")
  @GET
  @Consumes("application/json")
  @Produces("application/json")
  @RestrictedAccess(roles = "admin")
  public void admin();
  
  @Path("/user")
  @GET
  @Consumes("application/json")
  @Produces("application/json")
  @RestrictedAccess
  public void user();
  
  @GET
  @Path("any")
  @Consumes("application/json")
  @Produces("application/json")
  public void anybody();

}
