package org.icechamps.emailnotifications;

import com.google.common.base.Preconditions;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Enumeration;
import java.util.Map;
import java.util.Properties;

/**
 * Exception utility class used to assist in the handling of exceptions.
 * <p/>
 * The supported properties in the properties file are as follows:
 * <ul>
 * <li>mail.host - The SMTP server host</li>
 * <li>mail.port - The SMTP server port</li>
 * <li>mail.username - The username used for the SMTP server</li>
 * <li>mail.password - The password used for the SMTP server</li>
 * <li>subject.prefix - The prefix used in the subject</li>
 * <li>to.list - The list of email addresses to send emails to. This is a comma separated list of emails (spaces are allowed in the list)</li>
 * <li>from - The email address to use as the sender address</li>
 * </ul>
 *
 * @author Robert.Diaz
 * @since 1.0, 01/14/2014
 */
public class ExceptionNotifier {
    /*
    What am I doing?
    I am writing a method that will send an email notification when an exception is thrown.
    I need to add a check on the types of exceptions to limit certain ones so I don't get overly spammed with notifications.
    This needs to be able to be called from anywhere in the application, so we can more elegantly handle exceptions

    How will I accomplish this?
    In the emails, I'll send as much data as I can about:
        The request
        The exception (including stack traces)
     */

    /**
     * Static instance of the ExceptionNotifier
     */
    private static ExceptionNotifier INSTANCE = null;

    /**
     * The mailSender instance used to send email notifications. This is setup elsewhere
     */
    private JavaMailSender mailSender;

    /**
     * The prefix used in the email's subject
     */
    private String subjectPrefix;

    /**
     * The list of people to send emails to
     */
    private String[] toList;

    /**
     * The email address to use in the 'from' field
     */
    private String from;

    /**
     * Singleton method used to get the single static instance of this class. NOTE: The notifier must be configured before use!
     *
     * @return The singleton instance of the ExceptionNotifier
     * @throws IllegalStateException If the notifier has not been configured
     */
    public static ExceptionNotifier instance() {
        if (INSTANCE == null) {
            throw new IllegalStateException("Exception Notifier must be configured before it can be used!");
        }

        return INSTANCE;
    }

    /**
     * Destroys the configured instance
     */
    public static void destroy() {
        INSTANCE = null;
    }

    /**
     * Configures the ExceptionNotifier using a configuration file on the classpath
     *
     * @param configFile The name of the config file on the classpath that will
     * @throws IOException If we cannot load the configuration file
     */
    public static void configure(String configFile) throws IOException {
        Preconditions.checkNotNull(configFile, "Configuration file name cannot be null!");
        Resource configFileResource = new ClassPathResource(configFile);

        Properties properties = new Properties();
        properties.load(configFileResource.getInputStream());

        String host = getProperty("mail.host", properties);
        int port = Integer.valueOf(getProperty("mail.port", properties));
        String username = getProperty("mail.username", properties);
        String password = getProperty("mail.password", properties);

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setUsername(username);
        sender.setPassword(password);

        String subjectPrefix = getProperty("subject.prefix", properties);
        String toListString = getProperty("to.list", properties);
        String from = getProperty("from", properties);

        String[] toList = toListString.split(",");

        configureInternal(sender, subjectPrefix, toList, from);
    }

    /**
     * Internal helper used to check the value returned from obtaining a property
     *
     * @param key        The key used for lookup in the properties file
     * @param properties The properties file to search
     * @return The string value of the key
     * @throws IllegalArgumentException If the key's value was missing or empty
     */
    private static String getProperty(String key, Properties properties) {
        String value = properties.getProperty(key);

        if (value == null || value.equalsIgnoreCase(""))
            throw new IllegalArgumentException(String.format("%s's value was not found. Please make sure it is set", key));

        return value;
    }

    /**
     * Configures the ExceptionNotifier using the given parameters
     *
     * @param mailSender    The mail sender to use
     * @param subjectPrefix The subject prefix to use
     * @param toList        The list of people to send the emails to
     * @param from          The email address to send the emails from
     */
    public static void configure(JavaMailSender mailSender, String subjectPrefix, String[] toList, String from) {
        configureInternal(mailSender, subjectPrefix, toList, from);
    }

    /**
     * Internal wrapper for initialization. Checks to make sure the instance hasn't already been initialized
     *
     * @param mailSender    The mail sender to use
     * @param subjectPrefix The subject prefix to use
     * @param toList        The list of people to send the emails to
     * @param from          The email address to send the emails from
     * @throws IllegalStateException If the notifier has already been configured
     */
    private static void configureInternal(JavaMailSender mailSender, String subjectPrefix, String[] toList, String from) {
        if (INSTANCE == null) {
            INSTANCE = new ExceptionNotifier(mailSender, subjectPrefix, toList, from);
        } else {
            throw new IllegalStateException("Exception Notifier has already been configured!");
        }
    }

    /**
     * Internal constructor to support the singleton pattern
     *
     * @param mailSender    The mail sender to use
     * @param subjectPrefix The subject prefix to use
     * @param toList        The list of people to send the emails to
     * @param from          The email address to send the emails from
     */
    private ExceptionNotifier(JavaMailSender mailSender, String subjectPrefix, String[] toList, String from) {
        this.from = from;
        this.mailSender = mailSender;
        this.subjectPrefix = subjectPrefix;
        this.toList = toList;
    }

    /**
     * Method used to prepare and send email notifications. Applications will pass as much information to this method as possible, so the emails can be as helpful as possible.
     *
     * @param message   A message used to describe the exception that was thrown
     * @param exception The exception itself
     * @param request   The incoming request, if available
     */
    public void handleException(String message, Exception exception, ServletRequest request) {
        Preconditions.checkNotNull(exception, "Exception must not be null!");

        SimpleMailMessage mailMessage = new SimpleMailMessage();

        StringBuilder builder = new StringBuilder();

        if (request != null) {
            builder.append("-------------------------------\nRequest:\n-------------------------------\n\n");

            builder.append(format("remote address", request.getRemoteAddr()));
            builder.append(format("remote port", request.getRemotePort()));
            builder.append(format("content type", request.getContentType()));
            builder.append(format("content length", request.getContentLength()));
            builder.append(format("character encoding", request.getCharacterEncoding()));
            builder.append(format("protocol", request.getProtocol()));
            builder.append(format("scheme", request.getScheme()));
            builder.append(format("locales", extract(request.getLocales())));
            builder.append(format("parameters", map(request.getParameterMap())));
            builder.append(format("body", getBody(request)));

            if (request instanceof HttpServletRequest) {
                HttpServletRequest servletRequest = (HttpServletRequest) request;

                builder.append(format("auth", servletRequest.getAuthType()));
                builder.append(format("cookies", list(servletRequest.getCookies())));
                builder.append(format("context path", servletRequest.getContextPath()));

                Enumeration<String> headerNames = servletRequest.getHeaderNames();
                if (headerNames != null) {
                    builder.append("headers:\n");

                    while (headerNames.hasMoreElements()) {
                        String header = headerNames.nextElement();

                        builder.append("\t").append(header).append(":\n");

                        Enumeration<String> headers = servletRequest.getHeaders(header);
                        if (headers != null) {
                            while (headers.hasMoreElements()) {
                                String value = headers.nextElement();

                                builder.append("\t\t").append(value).append("\n");
                            }
                        }
                    }
                }

                builder.append(format("method", servletRequest.getMethod()));
                builder.append(format("path info", servletRequest.getPathInfo()));
                builder.append(format("path translated", servletRequest.getPathTranslated()));
                builder.append(format("query string", servletRequest.getQueryString()));
                builder.append(format("remote user", servletRequest.getRemoteUser()));
                builder.append(format("requested session id", servletRequest.getRequestedSessionId()));
                builder.append(format("request uri", servletRequest.getRequestURI()));
                builder.append(format("request url", servletRequest.getRequestURL()));
                builder.append(format("servlet path", servletRequest.getServletPath()));
            }

            builder.append("\n\n");
        }

        builder.append("-------------------------------\nException:\n-------------------------------\n\n");

        builder.append(org.apache.commons.lang.exception.ExceptionUtils.getFullStackTrace(exception));

        mailMessage.setText(builder.toString());

        mailMessage.setSubject(String.format("[%s] %s: %s", subjectPrefix, exception.getClass().getName(), message));
        mailMessage.setTo(toList);
        mailMessage.setFrom(from);

        mailSender.send(mailMessage);
    }

    /**
     * Internal helper method used to nicely format key/value pairs
     *
     * @param key   The key to print
     * @param value The value to print
     * @return The formatted string
     */
    private String format(String key, Object value) {
        return String.format("%s: %s\n", key, value);
    }

    /**
     * Internal helper method used to print enumerations
     *
     * @param enumeration The enumeration to print
     * @param <T>         The type of object in the enumeration
     * @return The string that was built containing the enumeration
     */
    private <T> String extract(Enumeration<T> enumeration) {
        if (enumeration == null) {
            return "null";
        }

        StringBuilder builder = new StringBuilder();

        while (enumeration.hasMoreElements()) {
            builder.append(enumeration.nextElement().toString()).append("\n");
        }

        builder.replace(builder.length() - 1, builder.length(), "");

        return builder.toString();
    }

    /**
     * Internal helper method used to print maps
     *
     * @param map The map to print
     * @param <K> The type of the key
     * @param <V> The type of the value
     * @return The string that was built containing the map
     */
    private <K, V> String map(Map<K, V[]> map) {
        if (map == null) {
            return "null";
        }

        StringBuilder builder = new StringBuilder();

        for (Map.Entry<K, V[]> entry : map.entrySet()) {
            builder.append(entry).append(" = {");

            for (V v : entry.getValue()) {
                builder.append(v).append(", ");
            }

            builder.replace(builder.length() - 2, builder.length(), "}");
        }

        return builder.toString();
    }

    /**
     * Internal helper method used to print arrays of objects
     *
     * @param ts  The array to print
     * @param <T> The type of object in the array
     * @return The string that was built containing the array
     */
    private <T> String list(T[] ts) {
        if (ts == null) {
            return "null";
        }

        StringBuilder builder = new StringBuilder();

        for (T t : ts) {
            builder.append(t.toString()).append("\n");
        }

        builder.replace(builder.length() - 1, builder.length(), "");

        return builder.toString();
    }

    /**
     * Internal helper method used to print the body of the request
     *
     * @param request The incoming request
     * @return The string containing the body of the request
     */
    private String getBody(ServletRequest request) {
        String body;
        StringBuilder stringBuilder = new StringBuilder();
        BufferedReader bufferedReader = null;

        try {
            InputStream inputStream = request.getInputStream();
            if (inputStream != null) {
                bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                char[] charBuffer = new char[1024];
                int bytesRead;
                while ((bytesRead = bufferedReader.read(charBuffer)) > 0) {
                    stringBuilder.append(charBuffer, 0, bytesRead);
                }
            } else {
                stringBuilder.append("");
            }
        } catch (IOException ignored) {
        } finally {
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (IOException ignored) {
                }
            }
        }

        body = stringBuilder.toString();
        return body;
    }
}