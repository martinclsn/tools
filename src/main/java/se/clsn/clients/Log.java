package se.clsn.clients;

import org.slf4j.Logger;

public class Log {

    private final Level level;

    private final Logger logger;

    public Log(Logger logger, Level level) {
        this.logger = logger;
        this.level = level;
    }

    public void log(String s, Object... objects) {
        switch (level) {
            case TRACE:
                logger.trace(s, objects);
                break;
            case DEBUG:
                logger.debug(s, objects);
                break;
            case INFO:
                logger.info(s, objects);
                break;
            case WARN:
                logger.warn(s, objects);
                break;
            case ERROR:
                logger.error(s, objects);
                break;
        }
    }

    public enum Level {
        TRACE, DEBUG, INFO, WARN, ERROR;
    }

}
