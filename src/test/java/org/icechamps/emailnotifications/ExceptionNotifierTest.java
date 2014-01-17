package org.icechamps.emailnotifications;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * TODO: Describe this class!
 *
 * @author Robert.Diaz
 * @since 1.0, 01/16/2014
 */
public class ExceptionNotifierTest {
    @After
    public void tearDown() throws Exception {
        ExceptionNotifier.destroy();
    }

    // Will throw an exception because it's not configured yet
    @Test(expected = IllegalStateException.class)
    public void testInstance() throws Exception {
        ExceptionNotifier.instance();
    }

    @Test
    public void testConfigureWithProperties() throws Exception {
        ExceptionNotifier.configure("emailConfig.properties");

        try {
            ExceptionNotifier.instance();
        } catch (IllegalStateException ex) {
            Assert.fail("Configuration failed to initialize instance!!");
        }
    }

    @Test
    public void testConfigureWithParams() throws Exception {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost("smtp.com");
        sender.setPort(100);
        sender.setUsername("some.email@domain.com");
        sender.setPassword("omgwtfbbq");

        ExceptionNotifier.configure(sender, "Exception Notification Unit Test", new String[]{"some.email@domain.com"}, "exceptions@icechamps.org");

        try {
            ExceptionNotifier.instance();
        } catch (IllegalStateException ex) {
            Assert.fail("Configuration failed to initialize instance!!");
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testMultiConfigure() throws Exception {
        ExceptionNotifier.configure(new JavaMailSenderImpl(), "", new String[]{}, "");
        ExceptionNotifier.configure(new JavaMailSenderImpl(), "", new String[]{}, "");
    }

    @Test
    public void testHandleException() throws Exception {
        ExceptionNotifier.configure("emailConfig.properties");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName("www.example.com");
        request.setRequestURI("/foo");
        request.setQueryString("param1=value1&param");

        ExceptionNotifier.instance().handleException("Testing!", new ArithmeticException("foo"), request);
    }
}
