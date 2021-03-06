package org.jboss.errai.security.client.local;

import org.jboss.errai.bus.client.api.BusErrorCallback;
import org.jboss.errai.bus.client.api.messaging.Message;
import org.jboss.errai.common.client.api.RemoteCallback;
import org.jboss.errai.common.client.framework.ProxyProvider;
import org.jboss.errai.common.client.framework.RemoteServiceProxyFactory;
import org.jboss.errai.enterprise.client.cdi.AbstractErraiCDITest;
import org.jboss.errai.ioc.client.container.IOC;
import org.jboss.errai.security.shared.api.annotation.RestrictedAccess;
import org.jboss.errai.security.shared.service.AuthenticationService;
import org.jboss.errai.ui.shared.api.style.StyleBindingExecutor;
import org.jboss.errai.ui.shared.api.style.StyleBindingsRegistry;

import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.Window;

/**
 * @author edewit@redhat.com
 */
public class SecurityTest extends AbstractErraiCDITest {

  private SpyAbstractRpcProxy spy = new SpyAbstractRpcProxy();

  @Override
  public String getModuleName() {
    return "org.jboss.errai.security.SecurityTest";
  }

  @Override
  protected void gwtSetUp() throws Exception {
    disableBus = true;
    super.gwtSetUp();
    RemoteServiceProxyFactory.addRemoteProxy(AuthenticationService.class, new ProxyProvider() {
      @Override
      public Object getProxy() {
        return spy;
      }
    });
  }

  public void testLoginIsPreformed() {
    // given
    SecurityTestModule module = IOC.getBeanManager().lookupBean(SecurityTestModule.class).getInstance();

    // when
    module.login(
            null, new BusErrorCallback() {
      @Override
      public boolean error(Message message, Throwable throwable) {
        fail("no error should have occurred as the the SpyAbstractRpcProxy got the call");
        return false;
      }
    });

    // then
    assertEquals(new Integer(1), spy.getCallCount("login"));
    assertEquals("", Window.Location.getHash());
  }

  public void testLogoutIsPreformed() {
    // given
    SecurityTestModule module = IOC.getBeanManager().lookupBean(SecurityTestModule.class).getInstance();
    final Boolean[] executedStyleBinding = {Boolean.FALSE};
    StyleBindingsRegistry.get().addStyleBinding(null, RestrictedAccess.class, new StyleBindingExecutor() {
      @Override
      public void invokeBinding(Element element) {
        executedStyleBinding[0] = Boolean.TRUE;
      }
    });

    // when
    module.identity.logout();

    // then
    assertEquals(new Integer(1), spy.getCallCount("logout"));
    assertEquals(Boolean.TRUE, executedStyleBinding[0]);
  }

  public void testHasPermission() {
    // given
    SecurityTestModule module = IOC.getBeanManager().lookupBean(SecurityTestModule.class).getInstance();

    // when
    module.identity.hasPermission(new RemoteCallback<Boolean>() {

      @Override
      public void callback(Boolean response) {
        assertFalse(response);
      }
    }, new BusErrorCallback() {
      
      @Override
      public boolean error(Message message, Throwable throwable) {
        fail("unexpected failure " + throwable.getMessage());
        return false;
      }
    }, "role1", "role2");

    // then
    assertEquals(new Integer(1), spy.getCallCount("getUser"));
  }
}
