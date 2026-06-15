package jobflow.global.cache;

import java.util.Iterator;
import java.util.List;
import org.springframework.data.redis.core.Cursor;

class TestCursor implements Cursor<String> {

    private final Iterator<String> iterator;
    private boolean closed;
    private long position;

    TestCursor(List<String> values) {
        this.iterator = values.iterator();
    }

    @Override
    public boolean hasNext() {
        return iterator.hasNext();
    }

    @Override
    public String next() {
        position++;
        return iterator.next();
    }

    @Override
    public CursorId getId() {
        return CursorId.of(0);
    }

    @Override
    public long getCursorId() {
        return 0;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public long getPosition() {
        return position;
    }

    @Override
    public void close() {
        closed = true;
    }
}
