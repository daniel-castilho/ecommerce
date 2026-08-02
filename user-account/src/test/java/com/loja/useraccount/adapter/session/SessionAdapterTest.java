package com.loja.useraccount.adapter.session;

import com.loja.useraccount.domain.model.Email;
import com.loja.useraccount.domain.model.User;
import com.loja.useraccount.domain.model.UserPassword;
import com.loja.useraccount.domain.model.UserProfile;
import com.loja.useraccount.domain.port.out.PasswordHasherPort;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class SessionAdapterTest {

    private static final PasswordHasherPort TEST_HASHER = new PasswordHasherPort() {
        @Override
        public String hash(String plainPassword) { return "hash:" + plainPassword; }
        @Override
        public boolean verify(String plainPassword, String hash) {
            return ("hash:" + plainPassword).equals(hash);
        }
    };

    private static User createTestUser() {
        return User.create(new Email("session@test.com"),
                UserPassword.hash("password1234", TEST_HASHER),
                UserProfile.fromFullName("Session User"));
    }

    @Test
    void shouldCreateAndRetrieveUserFromHttpSession() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        HttpSession session = Mockito.mock(HttpSession.class);
        Mockito.when(request.getSession(true)).thenReturn(session);
        Mockito.when(request.getSession(false)).thenReturn(session);
        Mockito.when(session.getAttribute("user")).thenAnswer(inv -> {
            return Mockito.mockingDetails(session).getInvocations().stream()
                    .filter(i -> i.getMethod().getName().equals("setAttribute"))
                    .findFirst()
                    .map(i -> i.getArguments()[1])
                    .orElse(null);
        });

        SessionAdapter adapter = new SessionAdapter();
        adapter.request = request;

        User user = createTestUser();
        adapter.createSession(user);

        Mockito.verify(session).setAttribute("user", user);
        assertThat(adapter.getCurrentUser()).containsSame(user);
    }

    @Test
    void shouldReturnEmptyWhenNoSessionExists() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getSession(false)).thenReturn(null);

        SessionAdapter adapter = new SessionAdapter();
        adapter.request = request;

        assertThat(adapter.getCurrentUser()).isEmpty();
    }

    @Test
    void shouldInvalidateSession() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        HttpSession session = Mockito.mock(HttpSession.class);
        Mockito.when(request.getSession(false)).thenReturn(session);

        SessionAdapter adapter = new SessionAdapter();
        adapter.request = request;

        adapter.invalidateSession();

        Mockito.verify(session).invalidate();
    }
}
