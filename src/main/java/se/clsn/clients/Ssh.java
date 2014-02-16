package se.clsn.clients;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import ch.ethz.ssh2.ChannelCondition;
import ch.ethz.ssh2.Connection;
import ch.ethz.ssh2.Session;
import ch.ethz.ssh2.StreamGobbler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Ssh implements AutoCloseable {

    public static final OutputProcessor STDOUT = new OutputProcessor("stdout");
    public static final OutputProcessor STDERR = new OutputProcessor("stderr");

    private static Logger logger = LoggerFactory.getLogger(Ssh.class);

    private final Connection connection;

    public Ssh(String hostName, String user, String password) throws IOException {
        connection = new Connection(hostName);
        connection.connect();
        final boolean isAuthenticated = connection.authenticateWithPassword(user, password);
        if (!isAuthenticated) {
            close();
            throw new RuntimeException("Not able to authenticate " + hostName);
        }
    }

    public int execute(String command, final OutputProcessor stdout, final OutputProcessor stderr) throws
            IOException {
        Session session = connection.openSession();
        try (AutoCloseSession autoCloseSession = new AutoCloseSession(session);
             BufferedReader stdOutReader = createReader(session.getStdout());
             BufferedReader stdErrReader = createReader(session.getStderr())
        ) {
            session.execCommand(command);
            logger.debug("execute: {}", command);

            readLines(stdOutReader, stdout);
            readLines(stdErrReader, stderr);

            return session.getExitStatus();
        }
    }

    private void readLines(final BufferedReader outReader, final OutputProcessor outProcessor) throws IOException {
        while (true) {
            String line = outReader.readLine();
            if (line == null) {
                break;
            }
            outProcessor.processLine(line);
        }
    }

    private BufferedReader createReader(final InputStream stream) {
        return new BufferedReader(new InputStreamReader(new StreamGobbler(stream)));
    }

    @Override
    public void close() {
        if (connection != null) {
            connection.close();
            logger.debug("Connection closed");
        }
    }

    public static class OutputProcessor {

        private final String label;

        public OutputProcessor(String label) {
            this.label = label;
        }

        public void processLine(final String line) throws IOException {
            logger.debug("{}: {}", label, line);
        }
    }

    private static class AutoCloseSession implements AutoCloseable {

        private Session session;

        public AutoCloseSession(final Session session) {
            this.session = session;
        }

        @Override
        public void close() {
            if (session != null) {
                session.close();
                logger.debug("Ssh session closed");
            }

        }
    }
}

