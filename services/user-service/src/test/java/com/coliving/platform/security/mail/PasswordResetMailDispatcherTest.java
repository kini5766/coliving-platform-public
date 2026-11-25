package com.coliving.platform.security.mail;

import com.coliving.platform.security.dao.PasswordResetTokenDAO;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class PasswordResetMailDispatcherTest {

    private final PasswordResetMailSender sender = mock(PasswordResetMailSender.class);
    private final PasswordResetTokenDAO tokens = mock(PasswordResetTokenDAO.class);
    private final PasswordResetMailDispatcher dispatcher = new PasswordResetMailDispatcher(sender, tokens);

    @Test
    void sendFailureRevokesTheToken() {
        doThrow(new IllegalStateException("smtp"))
                .when(sender)
                .send("resident-a@test.com", "http://localhost:5173/password-reset?token=raw-token");

        dispatcher.send(7L, "resident-a@test.com", "http://localhost:5173/password-reset?token=raw-token");

        verify(tokens).revoke(7L);
    }

    @Test
    void successfulSendKeepsTheToken() {
        dispatcher.send(7L, "resident-a@test.com", "http://localhost:5173/password-reset?token=raw-token");

        verify(sender).send("resident-a@test.com", "http://localhost:5173/password-reset?token=raw-token");
        verifyNoInteractions(tokens);
    }
}
