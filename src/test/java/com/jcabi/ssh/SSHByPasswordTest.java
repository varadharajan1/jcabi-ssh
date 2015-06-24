/**
 * Copyright (c) 2014-2015, jcabi.com
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met: 1) Redistributions of source code must retain the above
 * copyright notice, this list of conditions and the following
 * disclaimer. 2) Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided
 * with the distribution. 3) Neither the name of the jcabi.com nor
 * the names of its contributors may be used to endorse or promote
 * products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT
 * NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.jcabi.ssh;

import com.google.common.io.Files;
import com.jcabi.log.Logger;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.Collections;
import java.util.logging.Level;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.NullInputStream;
import org.apache.sshd.SshServer;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.server.Command;
import org.apache.sshd.server.CommandFactory;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.PasswordAuthenticator;
import org.apache.sshd.server.UserAuth;
import org.apache.sshd.server.auth.UserAuthPassword;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerSession;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Unit tests for {@link SSHByPassword}.
 * @todo #21:30min SSH server setup in this class is nearly identical to the
 *  one in {@link SSHTest}. It should be extracted in a separate reusable
 *  module.
 * @author Georgy Vlasov (wlasowegor@gmail.com)
 * @version $Id$
 * @since 1.4
 * @checkstyle ClassDataAbstractionCoupling (500 lines)
 */
public final class SSHByPasswordTest {
    /**
     * SSH login.
     */
    private static final String LOGIN = "test";

    /**
     * SSH password.
     */
    private static final String PASSWORD = "password";

    /**
     * Checks if {@link SSHByPassword} can execute a command on an SSH server.
     * @throws Exception If fails
     */
    @Test
    public void executesCommand() throws Exception {
        final int port = SSHByPasswordTest.port();
        final SshServer sshd = SSHByPasswordTest.server(port);
        sshd.setCommandFactory(new SSHByPasswordTest.EchoCommandCreator());
        sshd.start();
        final String cmd = "some test command";
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final Shell shell = new Shell.Verbose(
            new SSHByPassword(
                InetAddress.getLocalHost().getHostAddress(),
                port,
                LOGIN,
                PASSWORD
            )
        );
        final int exit = shell.exec(
            cmd,
            new NullInputStream(0L),
            output,
            Logger.stream(Level.WARNING, true)
        );
        sshd.stop();
        MatcherAssert.assertThat(exit, Matchers.equalTo(0));
        MatcherAssert.assertThat(output.toString(), Matchers.equalTo(cmd));
    }

    /**
     * Setup SSH server.
     * @param port Port to listen on.
     * @return SSH server.
     */
    private static SshServer server(final int port) {
        final SshServer sshd = SshServer.setUpDefaultServer();
        sshd.setPort(port);
        final PasswordAuthenticator auth =
            Mockito.mock(PasswordAuthenticator.class);
        Mockito.when(
            auth.authenticate(
                Mockito.eq(LOGIN),
                Mockito.eq(PASSWORD),
                Mockito.any(ServerSession.class)
            )
        ).thenReturn(true);
        sshd.setPasswordAuthenticator(auth);
        sshd.setUserAuthFactories(
            Collections.<NamedFactory<UserAuth>>singletonList(
                new UserAuthPassword.Factory()
            )
        );
        sshd.setKeyPairProvider(
            new SimpleGeneratorHostKeyProvider(
                new File(Files.createTempDir(), "hostkey.ser").getAbsolutePath()
            )
        );
        return sshd;
    }

    /**
     * Allocate free port.
     * @return Found port.
     * @throws IOException In case of error.
     */
    private static int port() throws IOException {
        final ServerSocket socket = new ServerSocket(0);
        final int port = socket.getLocalPort();
        socket.close();
        return port;
    }

    /**
     * Factory for echo command.
     */
    private static final class EchoCommandCreator implements CommandFactory {
        @Override
        public Command createCommand(final String command) {
            return new SSHByPasswordTest.EchoCommand(command);
        }
    }

    /**
     * Command that displays its name.
     */
    private static final class EchoCommand implements Command {
        /**
         * Command being executed.
         */
        private final transient String command;
        /**
         * Exit callback.
         */
        private transient ExitCallback callback;
        /**
         * Output stream for use by command.
         */
        private transient OutputStream output;
        /**
         * Constructor.
         * @param cmd Command to echo.
         */
        EchoCommand(final String cmd) {
            this.command = cmd;
        }
        @Override
        public void setInputStream(final InputStream input) {
            // do nothing
        }
        @Override
        public void setOutputStream(final OutputStream stream) {
            this.output = stream;
        }
        @Override
        public void setErrorStream(final OutputStream err) {
            // do nothing
        }
        @Override
        public void setExitCallback(final ExitCallback cllbck) {
            this.callback = cllbck;
        }
        @Override
        public void start(final Environment env) throws IOException {
            IOUtils.write(this.command, this.output);
            this.output.flush();
            this.callback.onExit(0);
        }
        @Override
        public void destroy() {
            // do nothing
        }
    }
}
