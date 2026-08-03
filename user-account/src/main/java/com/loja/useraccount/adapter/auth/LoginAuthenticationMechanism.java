package com.loja.useraccount.adapter.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.security.enterprise.AuthenticationStatus;
import jakarta.security.enterprise.authentication.mechanism.http.AutoApplySession;
import jakarta.security.enterprise.authentication.mechanism.http.HttpAuthenticationMechanism;
import jakarta.security.enterprise.authentication.mechanism.http.HttpMessageContext;
import jakarta.security.enterprise.credential.Credential;
import jakarta.security.enterprise.credential.UsernamePasswordCredential;
import jakarta.security.enterprise.identitystore.CredentialValidationResult;
import jakarta.security.enterprise.identitystore.IdentityStoreHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Custom form-based authentication mechanism for the JSF login page.
 *
 * <p>Login is driven programmatically from {@code LoginBean}: it copies the typed
 * credentials into request attributes and calls
 * {@code SecurityContext.authenticate(...)}. The container re-enters this mechanism's
 * {@code validateRequest} with {@link HttpMessageContext#isAuthenticationRequest()}
 * {@code true}; the credentials are validated through {@link IdentityStoreHandler}
 * (backed by {@link UserIdentityStore}) and the caller is established with
 * {@code notifyContainerAboutLogin}. Keeping the challenge in the bean means a failed
 * login renders the inline FacesMessage instead of a container 401.</p>
 *
 * <p>For protected resources (admin pages) the mechanism challenges anonymous callers
 * by redirecting them to the login page. {@link AutoApplySession} persists the
 * authenticated caller in the session so subsequent requests keep their identity.</p>
 */
@ApplicationScoped
@AutoApplySession
public class LoginAuthenticationMechanism implements HttpAuthenticationMechanism {

    public static final String USERNAME_ATTRIBUTE = "jakarta.security.username";
    public static final String PASSWORD_ATTRIBUTE = "jakarta.security.password";
    public static final String LOGIN_PAGE = "/user-account/login.xhtml";

    @Inject
    private IdentityStoreHandler identityStoreHandler;

    @Override
    public AuthenticationStatus validateRequest(HttpServletRequest request,
                                                HttpServletResponse response,
                                                HttpMessageContext context) {
        if (context.isAuthenticationRequest()) {
            String username = (String) request.getAttribute(USERNAME_ATTRIBUTE);
            String password = (String) request.getAttribute(PASSWORD_ATTRIBUTE);
            Credential credential = username != null && password != null
                    ? new UsernamePasswordCredential(username, password)
                    : null;
            return credential != null ? authenticate(context, credential) : context.doNothing();
        }

        if (context.isProtected()
                && context.getCallerPrincipal() == null
                && !isLoginPage(request)) {
            return context.redirect(request.getContextPath() + LOGIN_PAGE);
        }

        return context.doNothing();
    }

    private AuthenticationStatus authenticate(HttpMessageContext context, Credential credential) {
        CredentialValidationResult result = identityStoreHandler.validate(credential);
        if (result.getStatus() == CredentialValidationResult.Status.VALID) {
            return context.notifyContainerAboutLogin(result);
        }
        return context.responseUnauthorized();
    }

    private static boolean isLoginPage(HttpServletRequest request) {
        return request.getRequestURI().endsWith(LOGIN_PAGE);
    }
}
