package se.clsn.clients;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import ch.ethz.ssh2.ChannelCondition;
import ch.ethz.ssh2.Connection;
import ch.ethz.ssh2.SCPClient;
import ch.ethz.ssh2.SCPInputStream;
import ch.ethz.ssh2.SCPOutputStream;
import ch.ethz.ssh2.Session;
import ch.ethz.ssh2.StreamGobbler;
import com.google.common.io.ByteStreams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Ssh implements AutoCloseable {

    public static final OutputProcessor STDOUT = new OutputLogger("stdout", Log.Level.DEBUG);
    public static final OutputProcessor STDERR = new OutputLogger("stderr", Log.Level.ERROR);

    private static final Logger logger = LoggerFactory.getLogger(Ssh.class);

    private final Connection connection;
    private final String hostname;

    public Ssh(String hostname, String user, String password) throws IOException {
        connection = new Connection(hostname);
        connection.connect();
        final boolean isAuthenticated = connection.authenticateWithPassword(user, password);
        if (!isAuthenticated) {
            close();
            throw new RuntimeException("Not able to authenticate " + hostname);
        }
        this.hostname = hostname;
    }

    public int execute(String command, final OutputProcessor stdout, final OutputProcessor stderr) throws IOException {
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

    public void upload(final Path source, final String destinationPath) throws IOException {
        logger.debug("Upload {} to {} on {}", new Object[]{source, destinationPath, hostname});
        SCPClient scpClient = new SCPClient(connection);
        final SCPOutputStream outputStream = scpClient.put(source.toString(), Files.size(source), destinationPath, "0644");
        try (InputStream inputStream = Files.newInputStream(source, StandardOpenOption.READ)) {
            ByteStreams.copy(inputStream, outputStream);
        } finally {
            outputStream.close();
        }
    }

    public void download(final String remotePath, final Path destination) throws IOException {
        logger.debug("Download {} to {} from {}", new Object[]{remotePath, destination, hostname});
        SCPClient scpClient = new SCPClient(connection);
        final SCPInputStream scpInputStream = scpClient.get(remotePath);
        try (final OutputStream outputStream = Files.newOutputStream(destination, StandardOpenOption.WRITE,StandardOpenOption.CREATE_NEW)) {
            ByteStreams.copy(scpInputStream, outputStream);
        } finally {
            scpInputStream.close();
        }
    }


    public interface OutputProcessor {

        public void processLine(final String line);
    }

    public static class OutputLogger implements OutputProcessor {

        protected final String label;
        protected Log log;

        public OutputLogger(String label, Log.Level level) {
            this.label = label;
            this.log = new Log(LoggerFactory.getLogger(Ssh.class), level);

        }

        public void processLine(final String line) {
            log.log("{}: {}", label, line);
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

